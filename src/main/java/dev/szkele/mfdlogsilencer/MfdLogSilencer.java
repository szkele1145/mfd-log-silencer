package dev.szkele.mfdlogsilencer;

import net.neoforged.api.distmarker.Dist;
import net.neoforged.fml.common.Mod;
import net.neoforged.fml.loading.FMLEnvironment;
import net.neoforged.fml.loading.FMLPaths;
import net.neoforged.neoforge.client.event.RegisterClientCommandsEvent;
import net.neoforged.neoforge.common.NeoForge;
import net.neoforged.neoforge.event.RegisterCommandsEvent;

import java.nio.file.Path;

/**
 * MFD Log Silencer: hides the INFO log spam of fixed_tweaked_controller (and any
 * other loggers configured in config/mfd-log-silencer.properties).
 *
 * The filter is installed in the mod constructor (earliest safe point for log4j2 on
 * NeoForge 1.21.1) and can be re-applied in-game via /mfdlogsilencer reload.
 */
@Mod(MfdLogSilencer.MODID)
public final class MfdLogSilencer {
    public static final String MODID = "mfd_log_silencer";
    public static final String VERSION = "1.1.0";
    private static volatile SilencerConfig config;

    public MfdLogSilencer() {
        Path configFile = FMLPaths.CONFIGDIR.get().resolve("mfd-log-silencer.properties");
        config = SilencerConfig.load(configFile);

        int n = FilterInstaller.apply(config);
        System.out.println(Constants.CONSOLE_PREFIX + "v" + VERSION + " 已激活："
                + n + " 个 logger 的 INFO/DEBUG 日志已屏蔽（WARN/ERROR 不受影响）");

        // server-side command registration (dedicated + integrated server)
        NeoForge.EVENT_BUS.addListener(MfdLogSilencer::onRegisterCommands);

        // client-only command registration (single player without opening LAN)
        if (FMLEnvironment.dist == Dist.CLIENT) {
            NeoForge.EVENT_BUS.addListener(MfdLogSilencer::onRegisterClientCommands);
        }
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

    private static void onRegisterClientCommands(RegisterClientCommandsEvent event) {
        SilencerCommands.register(event.getDispatcher());
    }
}
