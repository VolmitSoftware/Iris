package art.arcane.iris.forge;

import art.arcane.iris.modded.ModdedProtocolDefinition;
import art.arcane.iris.modded.ModdedProtocolHandler;
import art.arcane.volmlib.nativelib.minecraft26_2.forge.NativeForgeProtocolNetworking;
import art.arcane.volmlib.nativelib.minecraft26_2.modded.NativeProtocolCallbacks;
import art.arcane.iris.client.IrisClient;
import art.arcane.volmlib.nativelib.minecraft26_2.modded.NativeProtocolChannel;

public final class ForgeProtocolNetworking {
    private static volatile NativeProtocolChannel channel;

    private ForgeProtocolNetworking() {
    }

    public static void register() {
        NativeProtocolCallbacks callbacks = new NativeProtocolCallbacks(ModdedProtocolHandler::onInbound,
                ModdedProtocolHandler::onPlayerJoin, ModdedProtocolHandler::onPlayerDisconnect);
        channel = NativeForgeProtocolNetworking.register(ModdedProtocolDefinition.PROTOCOL, callbacks, IrisClient::onInbound);
        ModdedProtocolHandler.bindChannel(channel);
    }

    public static NativeProtocolChannel channel() {
        return channel;
    }
}
