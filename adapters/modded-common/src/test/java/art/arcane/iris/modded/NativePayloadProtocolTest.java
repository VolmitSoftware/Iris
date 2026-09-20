package art.arcane.iris.modded;

import art.arcane.volmlib.nativelib.minecraft26_2.modded.NativePayloadProtocol;
import io.netty.buffer.Unpooled;
import io.netty.handler.codec.DecoderException;
import net.minecraft.core.RegistryAccess;
import net.minecraft.network.RegistryFriendlyByteBuf;
import org.junit.Test;

import static org.junit.Assert.assertArrayEquals;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertSame;
import static org.junit.Assert.assertThrows;

public class NativePayloadProtocolTest {
    @Test
    public void frameRoundTripKeepsItsChannelAndBytes() {
        NativePayloadProtocol protocol = new NativePayloadProtocol("terrain:frame", 32);
        byte[] frame = new byte[]{1, 4, 9, 16};
        RegistryFriendlyByteBuf buffer = new RegistryFriendlyByteBuf(Unpooled.buffer(), RegistryAccess.EMPTY);
        try {
            protocol.codec().encode(buffer, protocol.payload(frame));
            NativePayloadProtocol.Payload decoded = protocol.codec().decode(buffer);

            assertSame(protocol, decoded.protocol());
            assertArrayEquals(frame, decoded.data());
            assertEquals("terrain:frame", decoded.type().id().toString());
        } finally {
            buffer.release();
        }
    }

    @Test
    public void decoderEnforcesTheConfiguredFrameLimit() {
        NativePayloadProtocol protocol = new NativePayloadProtocol("terrain:frame", 3);
        RegistryFriendlyByteBuf buffer = new RegistryFriendlyByteBuf(Unpooled.buffer(), RegistryAccess.EMPTY);
        try {
            buffer.writeByteArray(new byte[]{1, 2, 3, 4});
            assertThrows(DecoderException.class, () -> protocol.codec().decode(buffer));
        } finally {
            buffer.release();
        }
    }
}
