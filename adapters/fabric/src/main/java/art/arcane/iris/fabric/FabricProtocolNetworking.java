package art.arcane.iris.fabric;

import art.arcane.iris.modded.ModdedProtocolDefinition;
import art.arcane.iris.modded.ModdedProtocolHandler;
import art.arcane.volmlib.nativelib.minecraft26_2.fabric.NativeFabricProtocolNetworking;
import art.arcane.volmlib.nativelib.minecraft26_2.modded.NativeProtocolCallbacks;

public final class FabricProtocolNetworking {
    private FabricProtocolNetworking() {
    }

    public static void install() {
        NativeProtocolCallbacks callbacks = new NativeProtocolCallbacks(ModdedProtocolHandler::onInbound,
                ModdedProtocolHandler::onPlayerJoin, ModdedProtocolHandler::onPlayerDisconnect);
        ModdedProtocolHandler.bindChannel(NativeFabricProtocolNetworking.install(ModdedProtocolDefinition.PROTOCOL, callbacks));
    }
}
