package dev.szkele.mfdlogsilencer;

import net.neoforged.fml.loading.FMLPaths;
import net.neoforged.neoforge.common.NeoForge;
import net.neoforged.neoforge.event.RegisterCommandsEvent;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import java.nio.file.Path;

/**
 * MFD Log Silencer: hides the INFO log spam of fixed_tweaked_controller (and any
 * other loggers configured in config/mfd-log-silencer.properties).
 *
 * The filter is installed in the mod constructor (earliest safe point for log4j2 on
 * NeoForge 1.21.1) and can be re-applied in-game via /mfdlogsilencer reload.
 *
 * Commands are registered only through RegisterCommandsEvent, which fires on the
 * dedicated server AND for the integrated server in single player - one code path,
 * no client/server command shadowing.
 */
public final class MfdLogSilencer {
    public static final String MODID = "mfd_log_silencer";
    public static final String VERSION = "1.2.3";

    /** Our own logger. Deliberately NOT in the silenced list, so status output stays visible. */
    public static final Logger LOGGER = LogManager.getLogger(MODID);

    private static volatile SilencerConfig config;

    public MfdLogSilencer() {
        Path configFile = FMLPaths.CONFIGDIR.get().resolve("mfd-log-silencer.properties");
        config = SilencerConfig.load(configFile);

        int n = FilterInstaller.apply(config);
        if (n > 0) {
            LOGGER.info("v{} activated: {} logger(s) silenced at INFO/DEBUG (WARN/ERROR pass through)",
                    VERSION, n);
        } else {
            LOGGER.warn("no filter installed - check 'loggers=' in config/mfd-log-silencer.properties");
        }

        NeoForge.EVENT_BUS.addListener(MfdLogSilencer::onRegisterCommands);
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
}
