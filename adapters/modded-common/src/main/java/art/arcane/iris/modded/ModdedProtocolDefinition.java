package art.arcane.iris.modded;

import art.arcane.iris.spi.protocol.IrisProtocol;
import art.arcane.volmlib.nativelib.minecraft26_2.modded.NativePayloadProtocol;

public final class ModdedProtocolDefinition {
    public static final NativePayloadProtocol PROTOCOL = new NativePayloadProtocol(IrisProtocol.CHANNEL, IrisProtocol.MAX_FRAME_BYTES);

    private ModdedProtocolDefinition() {
    }
}
