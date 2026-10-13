package dev.szkele.mfdlogsilencer.tools;

import org.apache.logging.log4j.Level;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.core.LoggerContext;
import org.apache.logging.log4j.core.config.Configuration;
import org.apache.logging.log4j.core.config.LoggerConfig;
import org.apache.logging.log4j.core.filter.LevelRangeFilter;
import org.apache.logging.log4j.core.layout.PatternLayout;

import java.util.List;

/**
 * Standalone harness: reproduces exactly what MfdLogSilencer does at runtime,
 * against a pure log4j2 environment (no Minecraft needed).
 *
 * Scenario A = v1.0.0 code path (createFilter with 6 args including onMatch/onMismatch
 * passed as the 5th/6th args to the 4-arg overload -> actually it used the 4-arg overload;
 * this harness tests both orderings).
 */
public final class FilterHarness {
    public static void main(String[] args) {
        LoggerContext ctx = (LoggerContext) LogManager.getContext(false);
        Configuration config = ctx.getConfiguration();

        PatternLayout layout = PatternLayout.newBuilder()
                .withPattern("%-5p %c - %m%n")
                .withConfiguration(config)
                .build();
        var appender = org.apache.logging.log4j.core.appender.ConsoleAppender.newBuilder()
                .setName("HarnessOut")
                .setLayout(layout)
                .setTarget(org.apache.logging.log4j.core.appender.ConsoleAppender.Target.SYSTEM_OUT)
                .build();
        appender.start();
        config.addAppender(appender);

        // Replicate: dedicated LoggerConfig named after the target logger, additivity true
        LoggerConfig lc = new LoggerConfig("fixed_tweaked_controller", Level.INFO, true);
        lc.addAppender(appender, Level.ALL, null);

        // v1.0.0 order: min=TRACE max=INFO  (BUG: intLevel semantics make this an empty range -> no-op)
        LevelRangeFilter f1 = LevelRangeFilter.createFilter(
                Level.TRACE, Level.INFO,
                org.apache.logging.log4j.core.Filter.Result.DENY,
                org.apache.logging.log4j.core.Filter.Result.NEUTRAL);
        lc.addFilter(f1);
        config.addLogger("fixed_tweaked_controller", lc);
        ctx.updateLoggers();

        org.apache.logging.log4j.Logger log = LogManager.getLogger("fixed_tweaked_controller");
        org.apache.logging.log4j.Logger other = LogManager.getLogger("some.other.mod");

        System.out.println("=== scenario A: min=TRACE max=INFO (v1.0.0, BUGGY) ===");
        log.info("A-INFO: printed means BUG confirmed (info, target logger)");
        log.warn("A-WARN: should print (warn, target logger)");

        // Scenario B: correct ordering for intLevel semantics (min=INFO=400, max=TRACE=600):
        // deny everything with intLevel in [400,600] = INFO, DEBUG, TRACE; WARN(300)/ERROR pass.
        lc.removeFilter(f1);
        LevelRangeFilter f2 = LevelRangeFilter.createFilter(
                Level.INFO, Level.TRACE,
                org.apache.logging.log4j.core.Filter.Result.DENY,
                org.apache.logging.log4j.core.Filter.Result.NEUTRAL);
        lc.addFilter(f2);
        ctx.updateLoggers();

        System.out.println("=== scenario B: min=INFO max=TRACE (v1.1.0 fix) ===");
        log.info("B-INFO: printed means STILL BROKEN (info, target logger)");
        log.debug("B-DEBUG: printed means STILL BROKEN (debug, target logger)");
        log.warn("B-WARN: should print (warn, target logger)");
        log.error("B-ERROR: should print (error, target logger)");
        other.info("B-OTHER-INFO: should print (info, other logger)");
    }
}
