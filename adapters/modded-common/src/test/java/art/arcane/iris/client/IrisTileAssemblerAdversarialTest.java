package art.arcane.iris.client;

import art.arcane.iris.platform.protocol.IrisTileEncoder;
import art.arcane.iris.spi.protocol.IrisMessage;
import art.arcane.iris.spi.protocol.ProtocolException;
import org.junit.Test;

import java.io.ByteArrayOutputStream;
import java.io.DataOutputStream;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.util.Arrays;
import java.util.List;
import java.util.Random;
import java.util.zip.Deflater;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertThrows;

/**
 * The tile stream is attacker-controlled: any server a player joins picks the sequence, chunk index, chunk
 * count and payload. Structurally impossible headers must be dropped without allocating from them, a complete
 * set that decodes to garbage must raise {@link ProtocolException} rather than return a half-built image, and a
 * deflate stream that cannot make progress must not spin the render thread.
 */
public class IrisTileAssemblerAdversarialTest {
    private static final int SIZE = IrisTileEncoder.TILE_PIXELS;
    private static final byte[] PAYLOAD = {1, 2, 3, 4};

    @Test
    public void impossibleChunkHeadersAreDropped() throws ProtocolException {
        IrisTileAssembler assembler = new IrisTileAssembler();
        assertNull("zero chunk count", assembler.add(tile(0, 0, 1, 0, 0, PAYLOAD)));
        assertNull("negative chunk count", assembler.add(tile(0, 0, 1, 0, -1, PAYLOAD)));
        assertNull("chunk count beyond the largest legal tile",
                assembler.add(tile(0, 0, 1, 0, IrisTileAssembler.MAX_CHUNK_COUNT + 1, PAYLOAD)));
        assertNull("index at count", assembler.add(tile(0, 0, 1, 2, 2, PAYLOAD)));
        assertNull("index beyond count", assembler.add(tile(0, 0, 1, 9, 2, PAYLOAD)));
        assertNull("negative index", assembler.add(tile(0, 0, 1, -1, 2, PAYLOAD)));
        assertNull("missing payload", assembler.add(tile(0, 0, 1, 0, 1, null)));
    }

    @Test
    public void rejectedHeaderLeavesNoPartialBehind() throws ProtocolException {
        IrisTileAssembler assembler = new IrisTileAssembler();
        assertNull(assembler.add(tile(0, 0, 1, 5, 2, PAYLOAD)));

        List<IrisMessage.VisionTile> good = IrisTileEncoder.splitIntoChunks(validBlob(), 0, 0, 0, 1);
        assertNotNull("a rejected frame must not poison the tile slot",
                IrisVisionTileRoundTripTest.assemble(assembler, good));
    }

    @Test
    public void chunkCountFlipMidSetRestartsTheSet() throws ProtocolException {
        IrisTileAssembler assembler = new IrisTileAssembler();
        // Two chunks promised at sequence 4, then the same sequence claims a single-chunk set. The stale
        // half-set must be dropped rather than concatenated into the new one.
        assertNull(assembler.add(tile(0, 0, 4, 0, 2, PAYLOAD)));

        List<IrisMessage.VisionTile> single = IrisTileEncoder.splitIntoChunks(validBlob(), 0, 0, 0, 4);
        assertEquals("a flat tile must deflate into a single chunk", 1, single.size());
        assertNotNull(assembler.add(single.get(0)));
    }

    @Test
    public void staleSequenceChunksAreIgnoredAndTheNewerSetStillCompletes() throws ProtocolException {
        IrisTileAssembler assembler = new IrisTileAssembler();
        byte[] blob = validBlob();
        byte[] head = Arrays.copyOfRange(blob, 0, blob.length / 2);
        byte[] tail = Arrays.copyOfRange(blob, blob.length / 2, blob.length);

        assertNull(assembler.add(tile(0, 0, 9, 0, 2, head)));
        assertNull("a chunk from an older sequence must not join the current set",
                assembler.add(tile(0, 0, 8, 1, 2, tail)));
        assertNotNull(assembler.add(tile(0, 0, 9, 1, 2, tail)));
    }

    @Test
    public void partialsBeyondTheCapAreEvicted() throws ProtocolException {
        IrisTileAssembler assembler = new IrisTileAssembler();
        byte[] blob = validBlob();
        byte[] head = Arrays.copyOfRange(blob, 0, blob.length / 2);
        byte[] tail = Arrays.copyOfRange(blob, blob.length / 2, blob.length);

        int overflow = IrisTileAssembler.MAX_PENDING_TILES + 1;
        for (int tileX = 0; tileX < overflow; tileX++) {
            assertNull(assembler.add(tile(tileX, 0, 1, 0, 2, head)));
        }
        // Tile 0 was the oldest partial and is gone, so its closing chunk opens a fresh incomplete set
        // instead of finishing one. Retaining every partial forever would be the alternative.
        assertNull("the oldest partial must have been evicted", assembler.add(tile(0, 0, 1, 1, 2, tail)));
        assertNotNull("the newest partial must have survived", assembler.add(tile(overflow - 1, 0, 1, 1, 2, tail)));
    }

    @Test
    public void garbagePayloadRaisesProtocolException() {
        IrisTileAssembler assembler = new IrisTileAssembler();
        byte[] garbage = new byte[64];
        Arrays.fill(garbage, (byte) 0x7F);
        assertThrows(ProtocolException.class, () -> assembler.add(tile(0, 0, 1, 0, 1, garbage)));
    }

    @Test
    public void truncatedDeflateStreamRaisesProtocolExceptionInsteadOfSpinning() {
        IrisTileAssembler assembler = new IrisTileAssembler();
        byte[] noisy = new byte[60_000];
        new Random(4242L).nextBytes(noisy);
        byte[] full = deflate(noisy);
        byte[] truncated = Arrays.copyOfRange(full, 0, full.length / 4);
        // Zero-progress inflater: input exhausted, stream not finished. The old loop only broke out when
        // finished/needsInput/needsDictionary happened to be set, so this shape spun forever on the render
        // thread.
        assertThrows(ProtocolException.class, () -> assembler.add(tile(0, 0, 1, 0, 1, truncated)));
    }

    @Test
    public void decompressionBombRaisesProtocolException() {
        IrisTileAssembler assembler = new IrisTileAssembler();
        byte[] bomb = deflate(new byte[IrisTileCodec.MAX_DECODED_BYTES + 1024]);
        assertThrows(ProtocolException.class, () -> assembler.add(tile(0, 0, 1, 0, 1, bomb)));
    }

    @Test
    public void impossibleHeaderFieldsRaiseProtocolException() {
        IrisTileAssembler assembler = new IrisTileAssembler();
        assertThrows("zero width", ProtocolException.class,
                () -> assembler.add(tile(0, 0, 1, 0, 1, deflate(header(0, SIZE, IrisTileCodec.MODE_RAW_RGB)))));
        assertThrows("oversized height", ProtocolException.class,
                () -> assembler.add(tile(1, 0, 1, 0, 1, deflate(header(SIZE, 4096, IrisTileCodec.MODE_RAW_RGB)))));
        assertThrows("unknown pixel mode", ProtocolException.class,
                () -> assembler.add(tile(2, 0, 1, 0, 1, deflate(header(SIZE, SIZE, 42)))));
    }

    @Test
    public void paletteIndexBeyondPaletteRaisesProtocolException() {
        IrisTileAssembler assembler = new IrisTileAssembler();
        ByteArrayOutputStream raw = new ByteArrayOutputStream();
        try (DataOutputStream out = new DataOutputStream(raw)) {
            out.writeInt(2);
            out.writeInt(2);
            out.writeByte(IrisTileCodec.MODE_PALETTE);
            out.writeInt(1);
            out.writeByte(0);
            out.writeByte(0);
            out.writeByte(0);
            for (int pixel = 0; pixel < 4; pixel++) {
                out.writeByte(pixel == 3 ? 200 : 0);
            }
        } catch (IOException failure) {
            throw new UncheckedIOException(failure);
        }
        byte[] blob = deflate(raw.toByteArray());
        assertThrows(ProtocolException.class, () -> assembler.add(tile(0, 0, 1, 0, 1, blob)));
    }

    private static IrisMessage.VisionTile tile(int tileX, int tileZ, int sequence, int chunkIndex, int chunkCount, byte[] data) {
        return new IrisMessage.VisionTile(tileX, tileZ, 0, sequence, chunkIndex, chunkCount, data);
    }

    /** A real encoder output for a flat tile: one chunk, palette mode, decodes cleanly. */
    private static byte[] validBlob() {
        int[] pixels = new int[SIZE * SIZE];
        Arrays.fill(pixels, 0xFF204060);
        return IrisTileEncoder.encodePixels(pixels, SIZE, SIZE);
    }

    private static byte[] header(int width, int height, int mode) {
        ByteArrayOutputStream raw = new ByteArrayOutputStream();
        try (DataOutputStream out = new DataOutputStream(raw)) {
            out.writeInt(width);
            out.writeInt(height);
            out.writeByte(mode);
        } catch (IOException failure) {
            throw new UncheckedIOException(failure);
        }
        return raw.toByteArray();
    }

    private static byte[] deflate(byte[] input) {
        Deflater deflater = new Deflater(Deflater.BEST_SPEED);
        deflater.setInput(input);
        deflater.finish();
        ByteArrayOutputStream out = new ByteArrayOutputStream(Math.max(64, input.length / 2));
        byte[] buffer = new byte[8192];
        while (!deflater.finished()) {
            out.write(buffer, 0, deflater.deflate(buffer));
        }
        deflater.end();
        return out.toByteArray();
    }
}
