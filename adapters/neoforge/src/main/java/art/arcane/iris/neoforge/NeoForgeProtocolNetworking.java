package art.arcane.iris.neoforge;

import art.arcane.iris.modded.ModdedProtocolDefinition;
import art.arcane.iris.modded.ModdedProtocolHandler;
import art.arcane.volmlib.nativelib.minecraft26_2.neoforge.NativeNeoForgeProtocolNetworking;
import art.arcane.volmlib.nativelib.minecraft26_2.modded.NativeProtocolCallbacks;
import art.arcane.iris.client.IrisClient;
import net.neoforged.bus.api.IEventBus;

public final class NeoForgeProtocolNetworking {
    private NeoForgeProtocolNetworking() {
    }

    public static void register(IEventBus modBus) {
        NativeProtocolCallbacks callbacks = new NativeProtocolCallbacks(ModdedProtocolHandler::onInbound,
                ModdedProtocolHandler::onPlayerJoin, ModdedProtocolHandler::onPlayerDisconnect);
        ModdedProtocolHandler.bindChannel(NativeNeoForgeProtocolNetworking.register(modBus,
                ModdedProtocolDefinition.PROTOCOL, callbacks, IrisClient::onInbound));
    }
}
