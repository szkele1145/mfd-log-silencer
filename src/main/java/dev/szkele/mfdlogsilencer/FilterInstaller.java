package dev.szkele.mfdlogsilencer;

import org.apache.logging.log4j.Level;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.core.Filter;
import org.apache.logging.log4j.core.LogEvent;
import org.apache.logging.log4j.core.Logger;
import org.apache.logging.log4j.core.LoggerContext;
import org.apache.logging.log4j.core.config.Configuration;
import org.apache.logging.log4j.core.filter.AbstractFilter;
import org.apache.logging.log4j.core.filter.CompositeFilter;

import java.util.ArrayList;
import java.util.List;

/**
 * Installs ONE context-wide turbo filter that applies the whole rule set.
 *
 * Why a turbo filter: log4j2 asks turbo filters for every event BEFORE any
 * LoggerConfig (and thus before any per-logger filter or level check) is consulted,
 * so it keeps working no matter which logger names a mod actually uses and no matter
 * whether the LoggerConfig is later replaced / reconfigured by NeoForge. 1.x attached
 * filters to exact logger names, so it did nothing for any name it did not predict.
 *
 * Reload = swapping the rule set inside the same filter instance, so it can never be
 * left dangling on a stale Configuration object.
 */
public final class FilterInstaller {
    private static volatile SilencerTurbo TURBO;

    private FilterInstaller() {
    }

    /**
     * (Re)installs / refreshes the global filter. Idempotent, safe to call repeatedly.
     *
     * @return true when the filter is active and at least one rule is configured
     */
    public static synchronized boolean apply(SilencerConfig config) {
        int ruleCount = config.getNames().size();
        try {
            LoggerContext ctx = (LoggerContext) LogManager.getContext(false);
            Configuration logConfig = ctx.getConfiguration();

            SilencerTurbo turbo = TURBO;
            if (turbo == null || turbo.getState() != org.apache.logging.log4j.core.LifeCycle.State.STARTED) {
                turbo = new SilencerTurbo(config);
                turbo.start();
                TURBO = turbo;
            } else {
                turbo.setConfig(config);
            }
            // attach to the current configuration (survives config replacement by re-attach)
            if (!CurrentConfig.hasFilter(logConfig, turbo)) {
                logConfig.addFilter(turbo);
            }
            ctx.updateLoggers();

            if (ruleCount == 0) {
                MfdLogSilencer.LOGGER.warn("no rules configured - nothing will be silenced "
                        + "({})", config.getFile());
                return false;
            }
            return true;
        } catch (Throwable t) {
            MfdLogSilencer.LOGGER.error("failed to install the log filter", t);
            return false;
        }
    }

    /**
     * Whether our filter is currently attached to the live log4j configuration.
     * Used as a self-check after re-applying.
     */
    public static synchronized boolean isAttached() {
        try {
            LoggerContext ctx = (LoggerContext) LogManager.getContext(false);
            return CurrentConfig.hasFilter(ctx.getConfiguration(), TURBO);
        } catch (Throwable t) {
            return false;
        }
    }

    /** Small helper so the (package private) filter lookup stays in one place. */
    private static final class CurrentConfig {
        /**
         * log4j2's AbstractFilterable keeps ONE filter slot. addFilter() wraps an
         * already-present filter into a CompositeFilter, so getFilter() returns that
         * wrapper - not our instance. We therefore look inside the composite as well.
         */
        static boolean hasFilter(Configuration cfg, Filter f) {
            if (cfg == null || f == null) {
                return false;
            }
            try {
                if (contains(cfg.getFilter(), f)) {
                    return true;
                }
            } catch (Throwable t) {
                // fall through
            }
            for (Filter candidate : allFilters(cfg)) {
                if (contains(candidate, f)) {
                    return true;
                }
            }
            return false;
        }

        /** Recursively look for our filter inside a (possibly composite) filter. */
        private static boolean contains(Filter holder, Filter wanted) {
            if (holder == null) {
                return false;
            }
            if (holder == wanted) {
                return true;
            }
            if (holder instanceof CompositeFilter composite) {
                for (Filter child : composite.getFilters()) {
                    if (contains(child, wanted)) {
                        return true;
                    }
                }
            }
            return false;
        }

        private static List<Filter> allFilters(Configuration cfg) {
            List<Filter> out = new ArrayList<>();
            try {
                java.lang.reflect.Field f = org.apache.logging.log4j.core.filter.AbstractFilterable.class
                        .getDeclaredField("filter");
                f.setAccessible(true);
                Object v = f.get(cfg);
                if (v instanceof Filter filter) {
                    out.add(filter);
                }
            } catch (Throwable t) {
                out.clear();
            }
            return out;
        }
    }

    /**
     * The global filter itself. Denies every event the current rule set matches,
     * counts it, and returns NEUTRAL for everything else.
     */
    public static final class SilencerTurbo extends AbstractFilter {
        private volatile SilencerConfig config;

        SilencerTurbo(SilencerConfig config) {
            super(Result.NEUTRAL, Result.NEUTRAL);
            this.config = config;
        }

        void setConfig(SilencerConfig cfg) {
            this.config = cfg;
        }

        public SilencerConfig getConfig() {
            return config;
        }

        private Result decide(String loggerName, Level level, String message) {
            SilencerConfig cfg = config;
            if (cfg == null) {
                return Result.NEUTRAL;
            }
            try {
                if (cfg.shouldDeny(loggerName, level)) {
                    SilencerStats.record(loggerName, level, message == null ? "" : message);
                    return Result.DENY;
                }
                // still feeding the diagnostics list, but only for INFO+ which is what the
                // console appenders would show anyway
                if (level != null && level.intLevel() >= Level.INFO.intLevel()) {
                    cfg.noteLogger(loggerName);
                }
            } catch (Throwable t) {
                // never break logging because of a filter bug
                return Result.NEUTRAL;
            }
            return Result.NEUTRAL;
        }

        // --- turbo entry points (used before a LogEvent exists) ---

        @Override
        public Result filter(Logger logger, Level level, org.apache.logging.log4j.Marker marker,
                             String message, Object... params) {
            return decide(logger == null ? null : logger.getName(), level, message);
        }

        @Override
        public Result filter(Logger logger, Level level, org.apache.logging.log4j.Marker marker,
                             Object message, Throwable t) {
            return decide(logger == null ? null : logger.getName(), level, String.valueOf(message));
        }

        @Override
        public Result filter(Logger logger, Level level, org.apache.logging.log4j.Marker marker,
                             org.apache.logging.log4j.message.Message message, Throwable t) {
            return decide(logger == null ? null : logger.getName(), level, text(message));
        }

        // --- regular filter entry point (filtered LogEvent) ---

        @Override
        public Result filter(LogEvent event) {
            if (event == null) {
                return Result.NEUTRAL;
            }
            return decide(event.getLoggerName(), event.getLevel(), text(event.getMessage()));
        }

        private static String text(org.apache.logging.log4j.message.Message message) {
            try {
                return message == null ? "" : message.getFormattedMessage();
            } catch (Throwable t) {
                return "";
            }
        }
    }
}
