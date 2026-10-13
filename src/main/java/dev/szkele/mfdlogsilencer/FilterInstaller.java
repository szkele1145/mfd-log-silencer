package dev.szkele.mfdlogsilencer;

import org.apache.logging.log4j.Level;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Marker;
import org.apache.logging.log4j.core.LogEvent;
import org.apache.logging.log4j.core.Logger;
import org.apache.logging.log4j.core.LoggerContext;
import org.apache.logging.log4j.core.config.Configuration;
import org.apache.logging.log4j.core.config.LoggerConfig;
import org.apache.logging.log4j.core.filter.AbstractFilter;
import org.apache.logging.log4j.message.Message;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Installs/refreshes level-range filters on the LoggerConfigs of the configured
 * target loggers.
 *
 * IMPORTANT (validated with a standalone harness against log4j-core):
 * log4j2 compares levels by intLevel where ERROR=200, WARN=300, INFO=400, DEBUG=500,
 * TRACE=600 (LOWER int = MORE severe). To deny INFO and anything less severe
 * (DEBUG/TRACE) while keeping WARN/ERROR, the range must be min=INFO (400),
 * max=TRACE (600). Writing (min=TRACE, max=INFO) yields an empty range and
 * silently filters nothing - that was the v1.0.0 bug.
 *
 * Note: LevelRangeFilter is final in log4j 2.22, so we extend AbstractFilter and
 * do the range check ourselves.
 */
public final class FilterInstaller {
    /** Currently installed filters by logger name, for idempotent re-apply. */
    private static final Map<String, CountingFilter> INSTALLED = new ConcurrentHashMap<>();

    private FilterInstaller() {
    }

    /**
     * (Re)installs filters for every configured target logger. Idempotent: filters
     * installed by a previous apply() are removed first.
     *
     * @return number of loggers that got a fresh filter
     */
    public static synchronized int apply(SilencerConfig config) {
        int installed = 0;
        try {
            LoggerContext ctx = (LoggerContext) LogManager.getContext(false);
            Configuration logConfig = ctx.getConfiguration();

            for (Map.Entry<String, Level> e : config.getTargets().entrySet()) {
                String loggerName = e.getKey();
                Level threshold = e.getValue(); // deny this level and everything less severe

                LoggerConfig lc = logConfig.getLoggerConfig(loggerName);
                if (!loggerName.equals(lc.getName())) {
                    // No dedicated LoggerConfig yet: create one with additivity so
                    // non-denied messages still travel to root appenders.
                    lc = new LoggerConfig(loggerName, Level.INFO, true);
                    logConfig.addLogger(loggerName, lc);
                }

                // strip the filter from a previous apply (idempotency)
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
            // remove filters for loggers that were dropped from the config (reload case)
            for (Map.Entry<String, CountingFilter> stale : INSTALLED.entrySet()) {
                if (!config.getTargets().containsKey(stale.getKey())) {
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
     * A filter that denies {@code minInt <= level.intLevel() <= maxInt} (i.e. the
     * configured threshold down to TRACE) and counts every denied message.
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
        public Result filter(Logger logger, Level level, Marker marker,
                             String message, Object... params) {
            return decide(level, message);
        }

        @Override
        public Result filter(Logger logger, Level level, Marker marker,
                             Object message, Throwable t) {
            return decide(level, String.valueOf(message));
        }

        @Override
        public Result filter(Logger logger, Level level, Marker marker,
                             Message message, Throwable t) {
            return decide(level, message != null ? message.getFormattedMessage() : "");
        }

        @Override
        public Result filter(LogEvent event) {
            return decide(event.getLevel(),
                    event.getMessage() != null ? event.getMessage().getFormattedMessage() : "");
        }
    }
}
