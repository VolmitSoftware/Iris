/*
 * Iris is a World Generator for Minecraft Bukkit Servers
 * Copyright (c) 2026 Arcane Arts (Volmit Software)
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License
 * along with this program.  If not, see <https://www.gnu.org/licenses/>.
 */

package art.arcane.iris.platform.protocol;

import art.arcane.iris.spi.protocol.IrisMessage;
import art.arcane.iris.spi.protocol.IrisMessageCodec;
import art.arcane.iris.spi.protocol.IrisProtocol;
import org.junit.Test;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.DataInputStream;
import java.util.List;
import java.util.zip.DataFormatException;
import java.util.zip.Inflater;

import static org.junit.Assert.assertArrayEquals;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

public class IrisTileEncoderTest {
    private record DecodedBlob(int width, int height, int mode, int[] pixels) {
    }

    @Test
    public void paletteModeRoundTripsToIdenticalPixels() throws Exception {
        int[] pixels = new int[256];
        for (int index = 0; index < pixels.length; index++) {
            pixels[index] = (index << 16) | (index << 8) | index;
        }
        byte[] blob = IrisTileEncoder.encodePixels(pixels, 16, 16);
        DecodedBlob decoded = decodeBlob(blob);
        assertEquals(IrisTileEncoder.MODE_PALETTE, decoded.mode());
        assertEquals(16, decoded.width());
        assertEquals(16, decoded.height());
        assertArrayEquals(pixels, decoded.pixels());
    }

    @Test
    public void rawModeRoundTripsToIdenticalPixels() throws Exception {
        int[] pixels = new int[1024];
        for (int index = 0; index < pixels.length; index++) {
            pixels[index] = index;
        }
        byte[] blob = IrisTileEncoder.encodePixels(pixels, 32, 32);
        DecodedBlob decoded = decodeBlob(blob);
        assertEquals(IrisTileEncoder.MODE_RAW_RGB, decoded.mode());
        assertArrayEquals(pixels, decoded.pixels());
    }

    @Test
    public void alphaBitsAreStrippedBeforeEncoding() throws Exception {
        int[] pixels = new int[4];
        pixels[0] = 0xFF123456;
        pixels[1] = 0x00123456;
        pixels[2] = 0xFFABCDEF;
        pixels[3] = 0x77ABCDEF;
        byte[] blob = IrisTileEncoder.encodePixels(pixels, 2, 2);
        DecodedBlob decoded = decodeBlob(blob);
        assertEquals(IrisTileEncoder.MODE_PALETTE, decoded.mode());
        assertArrayEquals(new int[]{0x123456, 0x123456, 0xABCDEF, 0xABCDEF}, decoded.pixels());
    }

    @Test
    public void chunkSplitAndReassembleReproducesBlob() {
        byte[] blob = new byte[60000];
        for (int index = 0; index < blob.length; index++) {
            blob[index] = (byte) (index * 31);
        }
        List<IrisMessage.VisionTile> chunks = IrisTileEncoder.splitIntoChunks(blob, 5, -3, 2, 9);
        int expectedCount = (blob.length + IrisProtocol.VISION_TILE_MAX_CHUNK_BYTES - 1) / IrisProtocol.VISION_TILE_MAX_CHUNK_BYTES;
        assertEquals(expectedCount, chunks.size());
        assertTrue("large blob must span multiple chunks", chunks.size() >= 2);
        for (int index = 0; index < chunks.size(); index++) {
            IrisMessage.VisionTile chunk = chunks.get(index);
            assertEquals(5, chunk.tileX());
            assertEquals(-3, chunk.tileZ());
            assertEquals(2, chunk.zoomLevel());
            assertEquals(9, chunk.sequence());
            assertEquals(index, chunk.chunkIndex());
            assertEquals(expectedCount, chunk.chunkCount());
            assertTrue(chunk.data().length <= IrisProtocol.VISION_TILE_MAX_CHUNK_BYTES);
            assertTrue("each chunk frame must fit under cap", IrisMessageCodec.encode(chunk).length <= IrisProtocol.MAX_FRAME_BYTES);
        }
        assertArrayEquals(blob, reassemble(chunks));
    }

    @Test
    public void emptyBlobProducesSingleEmptyChunk() {
        List<IrisMessage.VisionTile> chunks = IrisTileEncoder.splitIntoChunks(new byte[0], 0, 0, 0, 0);
        assertEquals(1, chunks.size());
        assertEquals(1, chunks.get(0).chunkCount());
        assertEquals(0, chunks.get(0).data().length);
    }

    @Test
    public void fullPipelineReassemblesToIdenticalPixels() throws Exception {
        int[] pixels = new int[4096];
        for (int index = 0; index < pixels.length; index++) {
            pixels[index] = (index * 2654435761L & 0xFFFFFFL) == 0 ? 1 : (int) (index * 2654435761L & 0xFFFFFFL);
        }
        byte[] blob = IrisTileEncoder.encodePixels(pixels, 64, 64);
        List<IrisMessage.VisionTile> chunks = IrisTileEncoder.splitIntoChunks(blob, 1, 2, 0, 3);
        byte[] reassembled = reassemble(chunks);
        assertArrayEquals(blob, reassembled);
        DecodedBlob decoded = decodeBlob(reassembled);
        assertArrayEquals(pixels, decoded.pixels());
        for (IrisMessage.VisionTile chunk : chunks) {
            assertTrue(IrisMessageCodec.encode(chunk).length <= IrisProtocol.MAX_FRAME_BYTES);
        }
    }

    private static byte[] reassemble(List<IrisMessage.VisionTile> chunks) {
        IrisMessage.VisionTile[] ordered = new IrisMessage.VisionTile[chunks.get(0).chunkCount()];
        for (IrisMessage.VisionTile chunk : chunks) {
            ordered[chunk.chunkIndex()] = chunk;
        }
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        for (IrisMessage.VisionTile chunk : ordered) {
            out.writeBytes(chunk.data());
        }
        return out.toByteArray();
    }

    private static DecodedBlob decodeBlob(byte[] blob) throws Exception {
        byte[] raw = inflate(blob);
        DataInputStream in = new DataInputStream(new ByteArrayInputStream(raw));
        int width = in.readInt();
        int height = in.readInt();
        int mode = in.readUnsignedByte();
        int count = width * height;
        int[] pixels = new int[count];
        if (mode == IrisTileEncoder.MODE_PALETTE) {
            int paletteSize = in.readInt();
            int[] palette = new int[paletteSize];
            for (int index = 0; index < paletteSize; index++) {
                int red = in.readUnsignedByte();
                int green = in.readUnsignedByte();
                int blue = in.readUnsignedByte();
                palette[index] = (red << 16) | (green << 8) | blue;
            }
            for (int index = 0; index < count; index++) {
                pixels[index] = palette[in.readUnsignedByte()];
            }
        } else {
            for (int index = 0; index < count; index++) {
                int red = in.readUnsignedByte();
                int green = in.readUnsignedByte();
                int blue = in.readUnsignedByte();
                pixels[index] = (red << 16) | (green << 8) | blue;
            }
        }
        return new DecodedBlob(width, height, mode, pixels);
    }

    private static byte[] inflate(byte[] input) throws DataFormatException {
        Inflater inflater = new Inflater();
        inflater.setInput(input);
        ByteArrayOutputStream out = new ByteArrayOutputStream(Math.max(64, input.length * 2));
        byte[] buffer = new byte[8192];
        while (!inflater.finished()) {
            int produced = inflater.inflate(buffer);
            if (produced == 0 && inflater.needsInput()) {
                break;
            }
            out.write(buffer, 0, produced);
        }
        inflater.end();
        return out.toByteArray();
    }
}
