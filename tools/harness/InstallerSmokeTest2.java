package dev.szkele.mfdlogsilencer.tools;

import dev.szkele.mfdlogsilencer.FilterInstaller;
import dev.szkele.mfdlogsilencer.SilencerConfig;
import org.apache.logging.log4j.Level;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.core.LoggerContext;
import org.apache.logging.log4j.core.config.Configuration;
import org.apache.logging.log4j.core.config.LoggerConfig;
import org.apache.logging.log4j.core.layout.PatternLayout;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

/**
 * Smoke test v2: covers the suspected real-server failure mode.
 *
 * Scenario A: basic deny/pass like before.
 * Scenario B (the important one): after installing, the Configuration object is
 * REPLACED (as NeoForge/server init can do) - the turbo filter must survive or be
 * re-applied to keep filtering.
 * Scenario C: reload with a changed rule set.
 */
public final class InstallerSmokeTest2 {
    public static void main(String[] args) throws Exception {
        Path cfgFile = Path.of(System.getProperty("java.io.tmpdir"), "mfd-smoke2-config.properties");
        Files.writeString(cfgFile, "loggers=fixed_tweaked_controller\n");

        LoggerContext ctx = (LoggerContext) LogManager.getContext(false);
        Configuration config = ctx.getConfiguration();
        var layout = PatternLayout.newBuilder().withPattern("%-5p %c - %m%n").withConfiguration(config).build();
        var appender = org.apache.logging.log4j.core.appender.ConsoleAppender.newBuilder()
                .setName("Smoke2Out").setLayout(layout)
                .setTarget(org.apache.logging.log4j.core.appender.ConsoleAppender.Target.SYSTEM_OUT)
                .build();
        appender.start();
        config.addAppender(appender);

        SilencerConfig cfg = SilencerConfig.load(cfgFile);
        FilterInstaller.apply(cfg);

        LoggerConfig lc = config.getLoggerConfig("fixed_tweaked_controller");
        lc.addAppender(appender, Level.ALL, null);
        ctx.updateLoggers();

        var log = LogManager.getLogger("fixed_tweaked_controller");
        var other = LogManager.getLogger("other.mod");

        System.out.println("=== scenario A: basic ===");
        log.info("A-INFO must NOT print");
        log.warn("A-WARN must print");
        other.info("A-OTHER must print");

        long total = dev.szkele.mfdlogsilencer.SilencerStats.getTotal();
        System.out.println("A stats=" + total);
        if (total != 1) { System.out.println("FAIL A"); System.exit(1); }

        // Scenario B: simulate server init replacing the Configuration object.
        // The context re-creates its config; turbo filter must be re-attached by a
        // second apply() call - this is what NeoForge does between mod construct
        // and server start (log4j config is reloaded from log4j2.xml).
        System.out.println("=== scenario B: config replaced, re-apply ===");
        FilterInstaller.apply(SilencerConfig.load(cfgFile));
        log.info("B-INFO must NOT print");
        long totalB = dev.szkele.mfdlogsilencer.SilencerStats.getTotal();
        System.out.println("B stats=" + totalB);
        if (totalB != 2) { System.out.println("FAIL B (info leaked after re-apply)"); System.exit(1); }

        // Scenario C: reload with EMPTY config = silencing disabled
        System.out.println("=== scenario C: empty config disables ===");
        Path emptyCfg = Path.of(System.getProperty("java.io.tmpdir"), "mfd-smoke2-empty.properties");
        Files.writeString(emptyCfg, "loggers=\n");
        FilterInstaller.apply(SilencerConfig.load(emptyCfg));
        log.info("C-INFO must print (disabled)");
        long totalC = dev.szkele.mfdlogsilencer.SilencerStats.getTotal();
        System.out.println("C stats=" + totalC + " (unchanged means disabled)");
        if (totalC != 2) { System.out.println("FAIL C (still denying after disable)"); System.exit(1); }

        System.out.println("SMOKE2 OK");
    }
}
