package art.arcane.iris.client;

import art.arcane.iris.platform.protocol.IrisTileEncoder;
import art.arcane.iris.spi.protocol.IrisMessage;
import art.arcane.iris.spi.protocol.IrisMessageCodec;
import art.arcane.iris.spi.protocol.IrisProtocol;
import art.arcane.iris.spi.protocol.ProtocolException;
import org.junit.Test;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Random;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;

/**
 * End-to-end vision tile path: {@link IrisTileEncoder} on the server, the wire codec both ways, then
 * {@link IrisTileAssembler} and {@link IrisTileCodec} on the client. Encoder and decoder live in different
 * modules and share no code, so pixel equality is the only thing that proves they still agree - a palette
 * write order change or a header field added on one side is otherwise silent.
 */
public class IrisVisionTileRoundTripTest {
    private static final int SIZE = IrisTileEncoder.TILE_PIXELS;
    private static final int OPAQUE = 0xFF000000;

    @Test
    public void paletteTileRoundTripsToIdenticalPixels() throws ProtocolException {
        int[] pixels = bandedPixels(16);
        IrisTileImage decoded = roundTrip(pixels, 3, -4, 2, 7);
        assertNotNull(decoded);
        assertEquals(SIZE, decoded.width());
        assertEquals(SIZE, decoded.height());
        assertOpaqueRgbEquals(pixels, decoded.argb());
    }

    @Test
    public void rawTileSplitsIntoChunksAndRoundTrips() throws ProtocolException {
        int[] pixels = highEntropyPixels();
        byte[] blob = IrisTileEncoder.encodePixels(pixels, SIZE, SIZE);
        List<IrisMessage.VisionTile> chunks = IrisTileEncoder.splitIntoChunks(blob, 0, 0, 0, 1);
        assertTrue("a high-entropy tile must exceed one chunk to exercise reassembly", chunks.size() > 1);
        for (IrisMessage.VisionTile chunk : chunks) {
            assertTrue("chunk frame must fit the wire cap",
                    IrisMessageCodec.encode(chunk).length <= IrisProtocol.MAX_FRAME_BYTES);
        }

        IrisTileImage decoded = assemble(new IrisTileAssembler(), overTheWire(chunks));
        assertNotNull(decoded);
        assertOpaqueRgbEquals(pixels, decoded.argb());
    }

    @Test
    public void chunksReassembleWhenDeliveredOutOfOrder() throws ProtocolException {
        int[] pixels = highEntropyPixels();
        byte[] blob = IrisTileEncoder.encodePixels(pixels, SIZE, SIZE);
        List<IrisMessage.VisionTile> chunks = new ArrayList<>(overTheWire(IrisTileEncoder.splitIntoChunks(blob, 5, 6, 1, 42)));
        assertTrue(chunks.size() > 1);
        Collections.reverse(chunks);

        IrisTileImage decoded = assemble(new IrisTileAssembler(), chunks);
        assertNotNull(decoded);
        assertOpaqueRgbEquals(pixels, decoded.argb());
    }

    @Test
    public void incompleteChunkSetProducesNothing() throws ProtocolException {
        int[] pixels = highEntropyPixels();
        byte[] blob = IrisTileEncoder.encodePixels(pixels, SIZE, SIZE);
        List<IrisMessage.VisionTile> chunks = overTheWire(IrisTileEncoder.splitIntoChunks(blob, 1, 1, 0, 1));
        assertTrue(chunks.size() > 1);

        IrisTileAssembler assembler = new IrisTileAssembler();
        assertNull(assemble(assembler, chunks.subList(0, chunks.size() - 1)));
        assertNotNull(assembler.add(chunks.get(chunks.size() - 1)));
    }

    @Test
    public void repeatedChunkDoesNotCompleteTheSetEarly() throws ProtocolException {
        int[] pixels = highEntropyPixels();
        byte[] blob = IrisTileEncoder.encodePixels(pixels, SIZE, SIZE);
        List<IrisMessage.VisionTile> chunks = overTheWire(IrisTileEncoder.splitIntoChunks(blob, 2, 2, 0, 1));
        assertTrue(chunks.size() > 1);

        IrisTileAssembler assembler = new IrisTileAssembler();
        for (int repeat = 0; repeat < chunks.size() + 2; repeat++) {
            assertNull("a duplicated chunk must not count twice", assembler.add(chunks.get(0)));
        }
    }

    static IrisTileImage assemble(IrisTileAssembler assembler, List<IrisMessage.VisionTile> chunks) throws ProtocolException {
        IrisTileImage assembled = null;
        for (IrisMessage.VisionTile chunk : chunks) {
            IrisTileImage produced = assembler.add(chunk);
            if (produced != null) {
                assembled = produced;
            }
        }
        return assembled;
    }

    /** Pushes each chunk through encode/decode so the test covers the wire form, not just the record. */
    private static List<IrisMessage.VisionTile> overTheWire(List<IrisMessage.VisionTile> chunks) {
        List<IrisMessage.VisionTile> transported = new ArrayList<>(chunks.size());
        for (IrisMessage.VisionTile chunk : chunks) {
            try {
                transported.add((IrisMessage.VisionTile) IrisMessageCodec.decode(IrisMessageCodec.encode(chunk)));
            } catch (ProtocolException rejected) {
                throw new AssertionError("a chunk the encoder produced must survive the codec", rejected);
            }
        }
        return transported;
    }

    private static IrisTileImage roundTrip(int[] pixels, int tileX, int tileZ, int zoom, int sequence) throws ProtocolException {
        byte[] blob = IrisTileEncoder.encodePixels(pixels, SIZE, SIZE);
        return assemble(new IrisTileAssembler(), overTheWire(IrisTileEncoder.splitIntoChunks(blob, tileX, tileZ, zoom, sequence)));
    }

    private static void assertOpaqueRgbEquals(int[] source, int[] decoded) {
        assertEquals(source.length, decoded.length);
        for (int index = 0; index < source.length; index++) {
            assertEquals("pixel " + index, OPAQUE | source[index] & 0xFFFFFF, decoded[index]);
        }
    }

    /** Few enough distinct colours that the encoder picks palette mode. */
    private static int[] bandedPixels(int colors) {
        int[] pixels = new int[SIZE * SIZE];
        for (int index = 0; index < pixels.length; index++) {
            int band = index % colors;
            pixels[index] = OPAQUE | band * 16 << 16 | band * 8 << 8 | band * 4;
        }
        return pixels;
    }

    /** Enough distinct colours to force raw mode, and enough entropy that deflate cannot fold it into one chunk. */
    private static int[] highEntropyPixels() {
        Random random = new Random(1337L);
        int[] pixels = new int[SIZE * SIZE];
        for (int index = 0; index < pixels.length; index++) {
            pixels[index] = OPAQUE | random.nextInt() & 0xFFFFFF;
        }
        return pixels;
    }
}
