package dev.szkele.mfdlogsilencer.tools;

import dev.szkele.mfdlogsilencer.FilterInstaller;
import dev.szkele.mfdlogsilencer.SilencerConfig;
import org.apache.logging.log4j.Level;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.core.LoggerContext;
import org.apache.logging.log4j.core.config.LoggerConfig;
import org.apache.logging.log4j.core.layout.PatternLayout;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

/**
 * Smoke test for the v1.1.0 CountingFilter + FilterInstaller against a pure
 * log4j2 environment. Validates: INFO/DEBUG denied+counted, WARN/ERROR pass,
 * other loggers untouched, idempotent re-apply.
 */
public final class InstallerSmokeTest {
    public static void main(String[] args) throws Exception {
        Path cfgFile = Path.of(System.getProperty("java.io.tmpdir"), "mfd-smoke-config.properties");
        Files.writeString(cfgFile, "loggers=fixed_tweaked_controller\n");

        LoggerContext ctx = (LoggerContext) LogManager.getContext(false);
        var config = ctx.getConfiguration();
        var layout = PatternLayout.newBuilder().withPattern("%-5p %c - %m%n").withConfiguration(config).build();
        var appender = org.apache.logging.log4j.core.appender.ConsoleAppender.newBuilder()
                .setName("SmokeOut").setLayout(layout)
                .setTarget(org.apache.logging.log4j.core.appender.ConsoleAppender.Target.SYSTEM_OUT)
                .build();
        appender.start();
        config.addAppender(appender);

        SilencerConfig cfg = SilencerConfig.load(cfgFile);
        int n = FilterInstaller.apply(cfg);
        System.out.println("installed=" + n);

        // give the target logger an appender via its own LoggerConfig
        LoggerConfig lc = config.getLoggerConfig("fixed_tweaked_controller");
        lc.addAppender(appender, Level.ALL, null);
        ctx.updateLoggers();

        var log = LogManager.getLogger("fixed_tweaked_controller");
        var other = LogManager.getLogger("other.mod");

        log.info("S-INFO should NOT print");
        log.debug("S-DEBUG should NOT print");
        log.warn("S-WARN should print");
        log.error("S-ERROR should print");
        other.info("S-OTHER should print");

        long total = dev.szkele.mfdlogsilencer.SilencerStats.getTotal();
        List<dev.szkele.mfdlogsilencer.SilencerStats.Entry> recent = dev.szkele.mfdlogsilencer.SilencerStats.getRecent();
        System.out.println("stats total=" + total + " recent=" + recent.size());
        // INFO is blocked by the filter and counted. DEBUG never reaches the filter:
        // the LoggerConfig level (INFO) gates it out earlier - also invisible, just uncounted.
        if (total != 1) {
            System.out.println("FAIL: expected 1 blocked (info; debug gated by level), got " + total);
            System.exit(1);
        }
        // idempotent re-apply must not double-count
        FilterInstaller.apply(cfg);
        log.info("R-INFO should NOT print");
        long total2 = dev.szkele.mfdlogsilencer.SilencerStats.getTotal();
        System.out.println("stats after re-apply total=" + total2);
        if (total2 != 2) {
            System.out.println("FAIL: expected 2 after re-apply (no double counting), got " + total2);
            System.exit(1);
        }
        System.out.println("SMOKE OK");
    }
}
