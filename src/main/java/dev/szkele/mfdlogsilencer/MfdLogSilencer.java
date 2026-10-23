package dev.szkele.mfdlogsilencer;

import net.neoforged.fml.common.Mod;
import net.neoforged.fml.loading.FMLPaths;
import net.neoforged.neoforge.common.NeoForge;
import net.neoforged.neoforge.event.RegisterCommandsEvent;
import net.neoforged.neoforge.event.server.ServerStartedEvent;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import java.nio.file.Path;

/**
 * MFD Log Silencer: hides the INFO log spam of fixed_tweaked_controller (and any other
 * logger configured in config/mfd-log-silencer.properties).
 *
 * The filter is installed in the mod constructor (earliest safe point for log4j2 on
 * NeoForge 1.21.1) and re-applied once the server has started, because several mods
 * touch the log4j configuration during their own construction. /mfdlogsilencer reload
 * re-reads the config file at any time.
 *
 * IMPORTANT: the {@code @Mod} annotation below is what makes NeoForge construct this
 * class. v1.1.1 accidentally dropped it while moving client-only event handling into a
 * separate class - the mod then showed up in the mod list (that comes from
 * neoforge.mods.toml) but its constructor never ran, so no filter was ever installed
 * and the log spam stayed. Never remove this annotation.
 */
@Mod(MfdLogSilencer.MODID)
public final class MfdLogSilencer {
    public static final String MODID = Constants.MODID;
    public static final String VERSION = "1.4.1";

    /** Our own logger: never silenced, so status output stays visible. */
    public static final Logger LOGGER = LogManager.getLogger(MODID);

    private static volatile SilencerConfig config;

    public MfdLogSilencer() {
        Path configFile = FMLPaths.CONFIGDIR.get().resolve("mfd-log-silencer.properties");
        config = SilencerConfig.load(configFile);
        activate(config, "startup");

        NeoForge.EVENT_BUS.addListener(MfdLogSilencer::onRegisterCommands);
        NeoForge.EVENT_BUS.addListener(MfdLogSilencer::onServerStarted);
    }

    private static void activate(SilencerConfig cfg, String when) {
        boolean active = FilterInstaller.apply(cfg);
        if (active) {
            LOGGER.info("[MFD-LogSilencer] v{} activated ({}): {} rule(s) [{}], keep={} and above",
                    VERSION, when, cfg.getNames().size(),
                    String.join(", ", cfg.getNames()), levelName(cfg.getKeepInt()));
        } else {
            LOGGER.warn("[MFD-LogSilencer] no rules active - check 'DISABLE=' in {}",
                    cfg.getFile());
        }
    }

    static String levelName(int intLevel) {
        return switch (intLevel) {
            case 100 -> "FATAL";
            case 200 -> "ERROR";
            case 300 -> "WARN";
            case 400 -> "INFO";
            case 500 -> "DEBUG";
            case 600 -> "TRACE";
            default -> "OFF";
        };
    }

    public static Path configPath() {
        SilencerConfig cfg = config;
        return cfg != null ? cfg.getFile() : FMLPaths.CONFIGDIR.get().resolve("mfd-log-silencer.properties");
    }

    public static void setConfig(SilencerConfig cfg) {
        config = cfg;
    }

    public static SilencerConfig getConfig() {
        return config;
    }

    private static void onRegisterCommands(RegisterCommandsEvent event) {
        SilencerCommands.register(event.getDispatcher());
    }

    /**
     * Re-attach the filter once the server is fully up: by then every mod has finished
     * its constructor, so any log4j reconfiguration that happened in between is covered.
     *
     * This is also the last safety net: if the filter is still not attached after
     * re-applying, something replaced the log4j configuration behind our back, and we
     * shout about it instead of silently doing nothing.
     */
    private static void onServerStarted(ServerStartedEvent event) {
        SilencerConfig cfg = config;
        if (cfg == null) {
            return;
        }
        boolean applied = FilterInstaller.apply(cfg);
        if (applied && !FilterInstaller.isAttached()) {
            LOGGER.error("[MFD-LogSilencer] the filter was installed but the log4j configuration "
                    + "was replaced afterwards - the spam will continue. Use /mfdlogsilencer reload "
                    + "to re-attach it.");
        }
    }
}
