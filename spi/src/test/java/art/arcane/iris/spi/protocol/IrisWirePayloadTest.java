package art.arcane.iris.spi.protocol;

import org.junit.Test;

import static org.junit.Assert.assertArrayEquals;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertThrows;
import static org.junit.Assert.assertTrue;

public class IrisWirePayloadTest {
    @Test
    public void preservesMixedPayloadEncodingAndIndependentCopies() throws Exception {
        IrisWireWriter writer = new IrisWireWriter();
        byte[] payload = {0, (byte) 0xff};
        writer.writeString("é\u0000");
        writer.writeBytes(payload);
        writer.writeString("");
        writer.writeBytes(new byte[0]);
        writer.writeInt(0x12345678);
        payload[0] = 42;

        byte[] frame = writer.toByteArray();
        assertArrayEquals(new byte[]{3, (byte) 0xc3, (byte) 0xa9, 0, 2, 0, (byte) 0xff,
                0, 0, 0x12, 0x34, 0x56, 0x78}, frame);
        IrisWireReader reader = new IrisWireReader(frame);
        assertEquals("é\u0000", reader.readString());
        byte[] decoded = reader.readBytes();
        assertArrayEquals(new byte[]{0, (byte) 0xff}, decoded);
        decoded[0] = 42;
        assertEquals(0, frame[5]);
        assertEquals("", reader.readString());
        assertArrayEquals(new byte[0], reader.readBytes());
        assertEquals(0x12345678, reader.readInt());
    }

    @Test
    public void preservesPayloadLengthErrorsAndConsumesOnlyThePrefix() throws Exception {
        byte[] negative = {(byte) 0xff, (byte) 0xff, (byte) 0xff, (byte) 0xff, 0x0f, 1};
        byte[] oversized = {2, 1};
        IrisWireReader negativeString = new IrisWireReader(negative);
        IrisWireReader negativeBytes = new IrisWireReader(negative);
        IrisWireReader oversizedString = new IrisWireReader(oversized);
        IrisWireReader oversizedBytes = new IrisWireReader(oversized);

        assertEquals("negative string length", assertThrows(ProtocolException.class, negativeString::readString).getMessage());
        assertEquals("negative byte array length", assertThrows(ProtocolException.class, negativeBytes::readBytes).getMessage());
        assertEquals("string length exceeds remaining frame bytes",
                assertThrows(ProtocolException.class, oversizedString::readString).getMessage());
        assertEquals("byte array length exceeds remaining frame bytes",
                assertThrows(ProtocolException.class, oversizedBytes::readBytes).getMessage());
        assertTrue(negativeString.readBoolean());
        assertTrue(negativeBytes.readBoolean());
        assertTrue(oversizedString.readBoolean());
        assertTrue(oversizedBytes.readBoolean());
    }

    @Test
    public void preservesFrameCapAndPrefixWrittenBeforePayloadOverflow() {
        int maximumPayload = IrisProtocol.MAX_FRAME_BYTES - 3;
        IrisWireWriter exactString = new IrisWireWriter();
        IrisWireWriter exactBytes = new IrisWireWriter();
        exactString.writeString("x".repeat(maximumPayload));
        exactBytes.writeBytes(new byte[maximumPayload]);
        assertEquals(IrisProtocol.MAX_FRAME_BYTES, exactString.toByteArray().length);
        assertEquals(IrisProtocol.MAX_FRAME_BYTES, exactBytes.toByteArray().length);

        IrisWireWriter oversizedString = new IrisWireWriter();
        IrisWireWriter oversizedBytes = new IrisWireWriter();
        IrisWireWriter prefix = new IrisWireWriter();
        prefix.writeVarInt(maximumPayload + 1);
        String message = "Iris protocol frame exceeds " + IrisProtocol.MAX_FRAME_BYTES + " byte cap";
        assertEquals(message, assertThrows(IllegalStateException.class,
                () -> oversizedString.writeString("x".repeat(maximumPayload + 1))).getMessage());
        assertEquals(message, assertThrows(IllegalStateException.class,
                () -> oversizedBytes.writeBytes(new byte[maximumPayload + 1])).getMessage());
        assertArrayEquals(prefix.toByteArray(), oversizedString.toByteArray());
        assertArrayEquals(prefix.toByteArray(), oversizedBytes.toByteArray());
    }

    @Test
    public void rejectsNullPayloadsWithoutWriting() {
        IrisWireWriter writer = new IrisWireWriter();
        assertThrows(NullPointerException.class, () -> writer.writeString(null));
        assertThrows(NullPointerException.class, () -> writer.writeBytes(null));
        assertArrayEquals(new byte[0], writer.toByteArray());
    }
}
