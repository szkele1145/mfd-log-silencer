package dev.szkele.mfdlogsilencer;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.core.LoggerContext;
import org.slf4j.LoggerFactory;

import java.nio.file.Files;
import java.nio.file.Path;

/**
 * Standalone harness: log4j-core 2.22.1 + slf4j (same stack Minecraft 1.21.1 ships).
 *
 * Run: java -Dlog4j.configurationFile=tools/harness/log4j2-harness.xml -cp "classes;log4j-*;slf4j-api" \
 *          dev.szkele.mfdlogsilencer.Slf4jSilenceTest
 *
 * Needs a compiled mod classes dir on the classpath (MfdLogSilencer is not required:
 * only SilencerConfig / FilterInstaller / SilencerStats / Constants are exercised).
 */
public final class Slf4jSilenceTest {
    public static void main(String[] args) throws Exception {
        LoggerContext ctx = (LoggerContext) LogManager.getContext(false);
        System.out.println("backend = " + ctx.getClass().getName());
        int fails = 0;

        org.slf4j.Logger modid = LoggerFactory.getLogger("fixed_tweaked_controller");
        org.slf4j.Logger fqcn = LoggerFactory.getLogger("com.pb7ap8lthh.fmdmod.MFDMOD");
        org.slf4j.Logger mixin = LoggerFactory.getLogger("com.pb7ap8lthh.fmdmod.mixin.TweakedLinkedControllerAxisPacketDebugMixin");
        org.slf4j.Logger other = LoggerFactory.getLogger("com.simibubi.create.Create");

        System.out.println("\n--- A: default rules silence slf4j mod-id AND package loggers ---");
        FilterInstaller.apply(parse("DISABLE=fixed_tweaked_controller\nDISABLE=com.pb7ap8lthh\nKEEP=warn\n"));
        ctx.updateLoggers();
        modid.info("[MFD-FixedController] signals pos=A linked=false values={{}}");
        fqcn.info("[MFD-FixedController] input pos=A");
        mixin.debug("[MFD-FixedController] axis button packet pos=A");
        modid.warn("[MFD-FixedController] warn must pass");
        other.info("create info must pass");
        System.out.println("A stats=" + SilencerStats.getTotal() + " per=" + SilencerStats.getPerLogger());
        if (SilencerStats.getTotal() != 4) { System.out.println("FAIL A"); fails++; }

        System.out.println("\n--- B: 1.x legacy config (exact names only) reproduces the leak ---");
        SilencerStats.reset();
        FilterInstaller.apply(parse("loggers=fixed_tweaked_controller\n"));
        ctx.updateLoggers();
        modid.info("[MFD-FixedController] signals pos=B");
        fqcn.info("[MFD-FixedController] input pos=B (must leak: 1.x could not match it)");
        System.out.println("B stats=" + SilencerStats.getTotal() + " (expect 1)");
        if (SilencerStats.getTotal() != 1) { System.out.println("FAIL B"); fails++; }

        System.out.println("\n--- C: reload to no rules -> nothing denied ---");
        SilencerStats.reset();
        FilterInstaller.apply(parse("DISABLE=\n"));
        ctx.updateLoggers();
        fqcn.info("[MFD-FixedController] input pos=C");
        System.out.println("C stats=" + SilencerStats.getTotal() + " (expect 0)");
        if (SilencerStats.getTotal() != 0) { System.out.println("FAIL C"); fails++; }

        System.out.println("\n--- D: package rule survives repeated re-apply / updateLoggers ---");
        SilencerStats.reset();
        FilterInstaller.apply(parse("DISABLE=com.pb7ap8lthh.fmdmod.block\nKEEP=\n"));
        FilterInstaller.apply(parse("DISABLE=com.pb7ap8lthh.fmdmod.block\nKEEP=\n"));
        ctx.updateLoggers();
        org.slf4j.Logger child = LoggerFactory.getLogger("com.pb7ap8lthh.fmdmod.block.FixedTweakedControllerBlockEntity");
        org.slf4j.Logger sibling = LoggerFactory.getLogger("com.pb7ap8lthh.fmdmod.client.ScreenLayoutJsonLoader");
        child.info("child must be denied");
        sibling.info("sibling must pass");
        System.out.println("D stats=" + SilencerStats.getTotal() + " (expect 1)");
        if (SilencerStats.getTotal() != 1) { System.out.println("FAIL D"); fails++; }

        System.out.println("\n--- E: KEEP=error keeps ERROR, denies WARN/INFO ---");
        SilencerStats.reset();
        FilterInstaller.apply(parse("DISABLE=fixed_tweaked_controller\nKEEP=error\n"));
        ctx.updateLoggers();
        modid.error("[MFD-FixedController] error must pass");
        modid.warn("[MFD-FixedController] warn denied with KEEP=error");
        modid.info("[MFD-FixedController] info must be denied");
        System.out.println("E stats=" + SilencerStats.getTotal() + " (expect 3: stale slf4j WARN wrapper re-checks TWICE)");
        if (SilencerStats.getTotal() != 3) { System.out.println("FAIL E"); fails++; }

        System.out.println("\n--- F: own logger never silenced; seen-logger diagnostics work ---");
        SilencerStats.reset();
        FilterInstaller.apply(parse("DISABLE=dev.szkele\nKEEP=warn\n"));
        ctx.updateLoggers();
        LogManager.getLogger("mfd_log_silencer").info("own logger must pass");
        LogManager.getLogger("net.minecraft.server.MinecraftServer").info("vanilla must pass");
        System.out.println("  seen=" + SilencerConfig.seenLoggers());
        System.out.println("F stats=" + SilencerStats.getTotal() + " (expect 0)");
        if (SilencerStats.getTotal() != 0) { System.out.println("FAIL F"); fails++; }

        System.out.println("\n--- G: blank config falls back to built-in defaults ---");
        SilencerStats.reset();
        FilterInstaller.apply(parse(""));
        ctx.updateLoggers();
        fqcn.info("[MFD-FixedController] input pos=G");
        System.out.println("  rules=" + MfdLogSilencerConfigHolder.names());
        System.out.println("G stats=" + SilencerStats.getTotal() + " (expect 1)");
        if (SilencerStats.getTotal() != 1) { System.out.println("FAIL G"); fails++; }

        System.out.println(fails == 0 ? "\nALL OK" : "\nFAILURES: " + fails);
        System.exit(fails == 0 ? 0 : 1);
    }

    private static SilencerConfig parse(String text) throws Exception {
        Path f = Files.createTempFile("mfd-t", ".properties");
        Files.writeString(f, text);
        return SilencerConfig.parse(f, text);
    }

    private static final class MfdLogSilencerConfigHolder {
        static String names() {
            return SilencerConfig.parse(Path.of("x.properties"), "").getNames().toString();
        }
    }
}
