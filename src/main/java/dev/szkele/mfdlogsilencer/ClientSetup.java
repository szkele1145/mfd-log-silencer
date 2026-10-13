package dev.szkele.mfdlogsilencer;

import net.neoforged.neoforge.client.event.RegisterClientCommandsEvent;
import net.neoforged.neoforge.common.NeoForge;

/**
 * Client-only registration helper. This class must only be classloaded when
 * FMLEnvironment.dist == Dist.CLIENT (it references client-only event classes),
 * so it is touched exclusively from the guarded branch in the mod constructor.
 */
public final class ClientSetup {
    private ClientSetup() {
    }

    public static void registerClientCommands() {
        NeoForge.EVENT_BUS.addListener(ClientSetup::onRegisterClientCommands);
    }

    private static void onRegisterClientCommands(RegisterClientCommandsEvent event) {
        SilencerCommands.register(event.getDispatcher());
    }
}
