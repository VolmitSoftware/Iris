package art.arcane.iris.forge;

import art.arcane.iris.client.IrisClient;
import net.minecraftforge.fml.javafmlmod.FMLJavaModLoadingContext;
import art.arcane.volmlib.nativelib.minecraft26_2.forge.NativeForgeClientHooks;
import art.arcane.iris.client.IrisClientHud;
import art.arcane.volmlib.nativelib.minecraft26_2.modded.NativeProtocolChannel;

public final class IrisForgeClient {
    private IrisForgeClient() {
    }

    public static void init(FMLJavaModLoadingContext context) {
        IrisClient.bindSender(IrisForgeClient::sendToServer);
        NativeForgeClientHooks.install(context, IrisClientHud.binding(), IrisClient::onWorldJoin, IrisClient::onDisconnect);
    }

    private static void sendToServer(byte[] frame) {
        NativeProtocolChannel channel = ForgeProtocolNetworking.channel();
        if (channel == null) {
            return;
        }
        channel.sendToServer(frame);
    }
}
