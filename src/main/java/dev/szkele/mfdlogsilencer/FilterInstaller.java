package dev.szkele.mfdlogsilencer;

import org.apache.logging.log4j.Level;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.core.Filter;
import org.apache.logging.log4j.core.LogEvent;
import org.apache.logging.log4j.core.Logger;
import org.apache.logging.log4j.core.LoggerContext;
import org.apache.logging.log4j.core.config.Configuration;
import org.apache.logging.log4j.core.config.LoggerConfig;
import org.apache.logging.log4j.core.filter.AbstractFilter;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Installs/refreshes level-range filters for the configured target loggers.
 *
 * Two independent layers (defense in depth):
 *
 * 1. A context-wide TurboFilter (log4j2 calls turbo filters for EVERY log event
 *    before any LoggerConfig is consulted). This is the robust layer: it cannot be
 *    bypassed by LoggerConfig re-creation or config updates.
 *
 * 2. A per-LoggerConfig filter (belt-and-suspenders, also records stats reliably
 *    when the LoggerConfig path is taken first).
 *
 * IMPORTANT (validated with a standalone harness against log4j-core):
 * log4j2 compares levels by intLevel where ERROR=200, WARN=300, INFO=400, DEBUG=500,
 * TRACE=600 (LOWER int = MORE severe). To deny INFO and anything less severe
 * (DEBUG/TRACE) while keeping WARN/ERROR, the range must be min=INFO (400),
 * max=TRACE (600).
 */
public final class FilterInstaller {
    /** Currently installed per-logger filters, for idempotent re-apply. */
    private static final Map<String, CountingFilter> INSTALLED = new ConcurrentHashMap<>();
    /** The context-wide turbo filter (single instance, updated in place). */
    private static volatile TurboSilencer TURBO;

    private FilterInstaller() {
    }

    /**
     * (Re)installs filters for every configured target logger. Idempotent.
     *
     * @return number of loggers that got a fresh config-level filter
     */
    public static synchronized int apply(SilencerConfig config) {
        int installed = 0;
        try {
            LoggerContext ctx = (LoggerContext) LogManager.getContext(false);
            Configuration logConfig = ctx.getConfiguration();

            Map<String, Level> targets = config.getTargets();

            // --- layer 1: context-wide turbo filter (single instance, rebuilt rules) ---
            TurboSilencer turbo = TURBO;
            if (turbo == null) {
                turbo = TurboSilencer.create(targets);
                turbo.start();
                logConfig.addFilter(turbo); // turbo filters live on the Configuration
                TURBO = turbo;
            } else {
                turbo.updateRules(targets);
            }

            // --- layer 2: per-LoggerConfig filters ---
            for (Map.Entry<String, Level> e : targets.entrySet()) {
                String loggerName = e.getKey();
                Level threshold = e.getValue(); // deny this level and everything less severe

                LoggerConfig lc = logConfig.getLoggerConfig(loggerName);
                if (!loggerName.equals(lc.getName())) {
                    lc = new LoggerConfig(loggerName, threshold, true);
                    logConfig.addLogger(loggerName, lc);
                }

                CountingFilter previous = INSTALLED.remove(loggerName);
                if (previous != null) {
                    lc.removeFilter(previous);
                }

                CountingFilter filter = CountingFilter.create(loggerName, threshold, Level.TRACE);
                filter.start();
                lc.addFilter(filter);
                INSTALLED.put(loggerName, filter);
                installed++;
            }

            // remove config-level filters for loggers dropped from the config
            for (Map.Entry<String, CountingFilter> stale : INSTALLED.entrySet()) {
                if (!targets.containsKey(stale.getKey())) {
                    LoggerConfig lc = logConfig.getLoggerConfig(stale.getKey());
                    if (lc != null) {
                        lc.removeFilter(stale.getValue());
                    }
                    INSTALLED.remove(stale.getKey());
                }
            }

            ctx.updateLoggers();
        } catch (Throwable t) {
            MfdLogSilencer.LOGGER.error("failed to install log filters", t);
        }
        return installed;
    }

    /**
     * Context-wide turbo filter. log4j2 consults turbo filters for every event
     * BEFORE the LoggerConfig's level check and filters, so this cannot be bypassed
     * by logger reconfiguration. Denies {loggerName: threshold} ranges.
     */
    public static final class TurboSilencer extends AbstractFilter {
        private volatile Map<String, Integer> rules; // loggerName -> minInt (deny minInt..600)

        private TurboSilencer(Map<String, Integer> rules) {
            super(Result.DENY, Result.NEUTRAL);
            this.rules = Map.copyOf(rules);
        }

        public static TurboSilencer create(Map<String, Level> targets) {
            Map<String, Integer> r = new ConcurrentHashMap<>();
            targets.forEach((k, v) -> r.put(k, v.intLevel()));
            return new TurboSilencer(r);
        }

        /** Atomically swap the rule set (used on reload). */
        public void updateRules(Map<String, Level> targets) {
            Map<String, Integer> r = new ConcurrentHashMap<>();
            targets.forEach((k, v) -> r.put(k, v.intLevel()));
            this.rules = Map.copyOf(r);
        }

        private Result decide(String loggerName, Level level, String message) {
            if (loggerName == null || level == null) {
                return Result.NEUTRAL;
            }
            // exact-name match (mod loggers are flat names; no hierarchy prefix games)
            Integer minInt = rules.get(loggerName);
            if (minInt != null && level.intLevel() >= minInt) {
                SilencerStats.record(loggerName, level, message == null ? "" : message);
                return Result.DENY;
            }
            return Result.NEUTRAL;
        }

        @Override
        public Result filter(Logger logger, Level level, org.apache.logging.log4j.Marker marker,
                             String message, Object... params) {
            return decide(logger != null ? logger.getName() : null, level, message);
        }

        @Override
        public Result filter(Logger logger, Level level, org.apache.logging.log4j.Marker marker,
                             Object message, Throwable t) {
            return decide(logger != null ? logger.getName() : null, level, String.valueOf(message));
        }

        @Override
        public Result filter(Logger logger, Level level, org.apache.logging.log4j.Marker marker,
                             org.apache.logging.log4j.message.Message message, Throwable t) {
            return decide(logger != null ? logger.getName() : null, level,
                    message != null ? message.getFormattedMessage() : "");
        }

        @Override
        public Result filter(LogEvent event) {
            return decide(event.getLoggerName(), event.getLevel(),
                    event.getMessage() != null ? event.getMessage().getFormattedMessage() : "");
        }
    }

    /**
     * Config-level filter that denies the [threshold..TRACE] intLevel range and
     * counts every denied message (belt-and-suspenders layer).
     */
    public static final class CountingFilter extends AbstractFilter {
        private final String loggerName;
        private final int minInt;
        private final int maxInt;

        private CountingFilter(String loggerName, Level min, Level max) {
            super(Result.DENY, Result.NEUTRAL);
            this.loggerName = loggerName;
            this.minInt = min.intLevel();
            this.maxInt = max.intLevel();
        }

        public static CountingFilter create(String loggerName, Level min, Level max) {
            return new CountingFilter(loggerName, min, max);
        }

        private Result decide(Level level, String message) {
            if (level != null && level.intLevel() >= minInt && level.intLevel() <= maxInt) {
                SilencerStats.record(loggerName, level, message == null ? "" : message);
                return Result.DENY;
            }
            return Result.NEUTRAL;
        }

        @Override
        public Result filter(Logger logger, Level level, org.apache.logging.log4j.Marker marker,
                             String message, Object... params) {
            return decide(level, message);
        }

        @Override
        public Result filter(Logger logger, Level level, org.apache.logging.log4j.Marker marker,
                             Object message, Throwable t) {
            return decide(level, String.valueOf(message));
        }

        @Override
        public Result filter(Logger logger, Level level, org.apache.logging.log4j.Marker marker,
                             org.apache.logging.log4j.message.Message message, Throwable t) {
            return decide(level, message != null ? message.getFormattedMessage() : "");
        }

        @Override
        public Result filter(LogEvent event) {
            return decide(event.getLevel(),
                    event.getMessage() != null ? event.getMessage().getFormattedMessage() : "");
        }
    }
}
