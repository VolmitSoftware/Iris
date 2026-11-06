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

package art.arcane.iris.core.studio.saved;

import art.arcane.iris.spi.IrisLogging;
import art.arcane.volmlib.util.nbt.io.NBTDeserializer;
import art.arcane.volmlib.util.nbt.tag.CompoundTag;
import art.arcane.volmlib.util.nbt.tag.Tag;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.RandomAccessFile;
import java.nio.file.Path;
import java.util.Optional;
import java.util.zip.GZIPInputStream;
import java.util.zip.InflaterInputStream;

/**
 * One Anvil region file ({@code r.X.Z.mca}), read a chunk at a time. The server may be writing the file meanwhile, so a
 * chunk that can't be read counts as not saved rather than failing the view.
 */
record RegionFile(Path file) {
    static final int CHUNKS_ACROSS = 32;
    private static final int SECTOR_BYTES = 4_096;
    private static final int GZIP = 1;
    private static final int ZLIB = 2;
    private static final int UNCOMPRESSED = 3;
    // Chunks too large for the region file live in a file of their own; the view leaves them out.
    private static final int STORED_OUTSIDE_FLAG = 0x80;

    /**
     * The chunk's NBT, or empty where none was saved or it can't be read.
     *
     * @param chunkX chunk within the region, 0 to 31
     * @param chunkZ chunk within the region, 0 to 31
     */
    Optional<CompoundTag> chunk(int chunkX, int chunkZ) {
        try (RandomAccessFile region = new RandomAccessFile(file.toFile(), "r")) {
            region.seek((long) (chunkZ * CHUNKS_ACROSS + chunkX) * Integer.BYTES);
            int location = region.readInt();
            int firstSector = location >>> 8;
            if (firstSector == 0) {
                return Optional.empty();
            }
            region.seek((long) firstSector * SECTOR_BYTES);
            int length = region.readInt();
            int compression = region.readUnsignedByte();
            if ((compression & STORED_OUTSIDE_FLAG) != 0 || length <= 1) {
                return Optional.empty();
            }
            byte[] body = new byte[length - 1];
            region.readFully(body);
            return readNbt(compression, body);
        } catch (IOException | RuntimeException unreadable) {
            IrisLogging.debug("Could not read chunk " + chunkX + ", " + chunkZ + " of " + file + ": " + unreadable);
            return Optional.empty();
        }
    }

    private Optional<CompoundTag> readNbt(int compression, byte[] body) throws IOException {
        InputStream bytes = new ByteArrayInputStream(body);
        InputStream nbt = switch (compression) {
            case GZIP -> new GZIPInputStream(bytes);
            case ZLIB -> new InflaterInputStream(bytes);
            case UNCOMPRESSED -> bytes;
            default -> null;
        };
        if (nbt == null) {
            IrisLogging.debug("Chunk compression " + compression + " in " + file + " is not read by the studio");
            return Optional.empty();
        }
        try (nbt) {
            Tag<?> root = new NBTDeserializer(false).fromStream(nbt).getTag();
            return root instanceof CompoundTag chunk ? Optional.of(chunk) : Optional.empty();
        }
    }
}
