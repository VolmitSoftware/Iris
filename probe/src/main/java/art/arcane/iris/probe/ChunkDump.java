/*
 * Iris is a World Generator for Minecraft Servers
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

package art.arcane.iris.probe;

import art.arcane.volmlib.nativelib.terrain.NativeBiome;
import art.arcane.volmlib.nativelib.terrain.NativeBlockState;
import art.arcane.volmlib.util.hunk.Hunk;

import java.io.BufferedInputStream;
import java.io.BufferedOutputStream;
import java.io.DataInputStream;
import java.io.DataOutputStream;
import java.io.EOFException;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.IdentityHashMap;
import java.util.List;
import java.util.Map;
import java.util.zip.GZIPInputStream;
import java.util.zip.GZIPOutputStream;

public final class ChunkDump {
    static final long MAGIC = 0x4952495344554D50L;
    static final int VERSION = 1;
    static final String NULL_BLOCK_KEY = "minecraft:air";
    static final String NULL_BIOME_KEY = "null";
    private static final int MAXIMUM_PALETTE = 65_535;
    private static final int STREAM_BUFFER = 1 << 20;

    private ChunkDump() {
    }

    record Header(String dimensionKey, long seed, int minY, int height) {
    }

    record ChunkSnapshot(int chunkX, int chunkZ, int height,
                         String[] blockPalette, char[] blocks,
                         String[] biomePalette, char[] biomes) {
        ChunkSnapshot {
            int expected = 256 * height;
            if (blocks.length != expected || biomes.length != expected) {
                throw new IllegalArgumentException("Chunk " + chunkX + "," + chunkZ + " expected " + expected
                        + " entries but held " + blocks.length + " blocks and " + biomes.length + " biomes.");
            }
        }

        static int index(int localX, int localZ, int y, int height) {
            return ((localX << 4) | localZ) * height + y;
        }

        long coordinateKey() {
            return key(chunkX, chunkZ);
        }

        static long key(int chunkX, int chunkZ) {
            return ((long) chunkX << 32) | (chunkZ & 0xFFFFFFFFL);
        }
    }

    static ChunkSnapshot capture(int chunkX, int chunkZ, Hunk<NativeBlockState> blocks,
                                 Hunk<NativeBiome> biomes, int height) {
        int size = 256 * height;
        char[] blockIndices = new char[size];
        char[] biomeIndices = new char[size];
        PaletteBuilder blockPalette = new PaletteBuilder();
        PaletteBuilder biomePalette = new PaletteBuilder();
        int index = 0;
        for (int x = 0; x < 16; x++) {
            for (int z = 0; z < 16; z++) {
                for (int y = 0; y < height; y++) {
                    NativeBlockState state = blocks.get(x, y, z);
                    NativeBiome biome = biomes.get(x, y, z);
                    blockIndices[index] = blockPalette.indexOf(state, state == null ? NULL_BLOCK_KEY : null);
                    biomeIndices[index] = biomePalette.indexOf(biome, biome == null ? NULL_BIOME_KEY : null);
                    index++;
                }
            }
        }
        return new ChunkSnapshot(chunkX, chunkZ, height, blockPalette.keys(), blockIndices,
                biomePalette.keys(), biomeIndices);
    }

    static final class Writer implements AutoCloseable {
        private final DataOutputStream output;
        private boolean closed;

        Writer(Path file, Header header) throws IOException {
            this.output = new DataOutputStream(new BufferedOutputStream(new GZIPOutputStream(
                    Files.newOutputStream(file, StandardOpenOption.CREATE, StandardOpenOption.TRUNCATE_EXISTING,
                            StandardOpenOption.WRITE), STREAM_BUFFER), STREAM_BUFFER));
            output.writeLong(MAGIC);
            output.writeInt(VERSION);
            output.writeUTF(header.dimensionKey());
            output.writeLong(header.seed());
            output.writeInt(header.minY());
            output.writeInt(header.height());
        }

        void write(ChunkSnapshot snapshot) throws IOException {
            output.writeByte(1);
            output.writeInt(snapshot.chunkX());
            output.writeInt(snapshot.chunkZ());
            writePalette(snapshot.blockPalette(), snapshot.blocks());
            writePalette(snapshot.biomePalette(), snapshot.biomes());
        }

        private void writePalette(String[] palette, char[] indices) throws IOException {
            output.writeInt(palette.length);
            for (String key : palette) {
                output.writeUTF(key);
            }
            if (palette.length <= 256) {
                byte[] encoded = new byte[indices.length];
                for (int index = 0; index < indices.length; index++) {
                    encoded[index] = (byte) indices[index];
                }
                output.write(encoded);
                return;
            }
            byte[] encoded = new byte[indices.length * 2];
            for (int index = 0; index < indices.length; index++) {
                char value = indices[index];
                encoded[index * 2] = (byte) (value >>> 8);
                encoded[index * 2 + 1] = (byte) value;
            }
            output.write(encoded);
        }

        @Override
        public void close() throws IOException {
            if (closed) {
                return;
            }
            closed = true;
            try {
                output.writeByte(0);
            } finally {
                output.close();
            }
        }
    }

    static final class Reader implements AutoCloseable {
        private final DataInputStream input;
        private final Header header;
        private boolean finished;

        Reader(Path file) throws IOException {
            this.input = new DataInputStream(new BufferedInputStream(
                    new GZIPInputStream(Files.newInputStream(file), STREAM_BUFFER), STREAM_BUFFER));
            long magic = input.readLong();
            if (magic != MAGIC) {
                input.close();
                throw new IOException(file + " is not an Iris chunk dump.");
            }
            int version = input.readInt();
            if (version != VERSION) {
                input.close();
                throw new IOException(file + " uses dump version " + version + ", expected " + VERSION + ".");
            }
            this.header = new Header(input.readUTF(), input.readLong(), input.readInt(), input.readInt());
            if (header.height() < 1) {
                input.close();
                throw new IOException(file + " declares a non-positive height " + header.height() + ".");
            }
        }

        Header header() {
            return header;
        }

        ChunkSnapshot next() throws IOException {
            if (finished) {
                return null;
            }
            int marker;
            try {
                marker = input.readUnsignedByte();
            } catch (EOFException truncated) {
                throw new IOException("Chunk dump ended without its terminator.", truncated);
            }
            if (marker == 0) {
                finished = true;
                return null;
            }
            if (marker != 1) {
                throw new IOException("Chunk dump holds an unknown record marker " + marker + ".");
            }
            int chunkX = input.readInt();
            int chunkZ = input.readInt();
            int size = 256 * header.height();
            String[] blockPalette = readPalette();
            char[] blocks = readIndices(blockPalette.length, size);
            String[] biomePalette = readPalette();
            char[] biomes = readIndices(biomePalette.length, size);
            return new ChunkSnapshot(chunkX, chunkZ, header.height(), blockPalette, blocks, biomePalette, biomes);
        }

        private String[] readPalette() throws IOException {
            int length = input.readInt();
            if (length < 1 || length > MAXIMUM_PALETTE) {
                throw new IOException("Chunk dump palette length " + length + " is out of range.");
            }
            String[] palette = new String[length];
            for (int index = 0; index < length; index++) {
                palette[index] = input.readUTF();
            }
            return palette;
        }

        private char[] readIndices(int paletteLength, int size) throws IOException {
            char[] indices = new char[size];
            if (paletteLength <= 256) {
                byte[] encoded = new byte[size];
                input.readFully(encoded);
                for (int index = 0; index < size; index++) {
                    indices[index] = (char) (encoded[index] & 0xFF);
                }
            } else {
                byte[] encoded = new byte[size * 2];
                input.readFully(encoded);
                for (int index = 0; index < size; index++) {
                    indices[index] = (char) (((encoded[index * 2] & 0xFF) << 8) | (encoded[index * 2 + 1] & 0xFF));
                }
            }
            for (char value : indices) {
                if (value >= paletteLength) {
                    throw new IOException("Chunk dump index " + (int) value + " exceeds palette length " + paletteLength + ".");
                }
            }
            return indices;
        }

        @Override
        public void close() throws IOException {
            input.close();
        }
    }

    private static final class PaletteBuilder {
        private final IdentityHashMap<Object, Character> byIdentity = new IdentityHashMap<>();
        private final Map<String, Character> byKey = new HashMap<>();
        private final List<String> keys = new ArrayList<>();
        private Object lastValue;
        private char lastIndex;
        private boolean hasLast;

        char indexOf(Object value, String nullKey) {
            if (hasLast && value == lastValue) {
                return lastIndex;
            }
            Character cached = value == null ? null : byIdentity.get(value);
            char resolved;
            if (cached != null) {
                resolved = cached;
            } else {
                String key = value == null ? nullKey : keyOf(value);
                Character existing = byKey.get(key);
                if (existing != null) {
                    resolved = existing;
                } else {
                    if (keys.size() >= MAXIMUM_PALETTE) {
                        throw new IllegalStateException("Chunk palette exceeds " + MAXIMUM_PALETTE + " entries.");
                    }
                    resolved = (char) keys.size();
                    keys.add(key);
                    byKey.put(key, resolved);
                }
                if (value != null) {
                    byIdentity.put(value, resolved);
                }
            }
            lastValue = value;
            lastIndex = resolved;
            hasLast = true;
            return resolved;
        }

        String[] keys() {
            return keys.toArray(new String[0]);
        }

        private static String keyOf(Object value) {
            if (value instanceof NativeBlockState state) {
                return state.key();
            }
            return ((NativeBiome) value).key();
        }
    }
}
