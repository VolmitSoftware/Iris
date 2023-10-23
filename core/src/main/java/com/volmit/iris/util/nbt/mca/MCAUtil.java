/*
 * Iris is a World Generator for Minecraft Bukkit Servers
 * Copyright (c) 2022 Arcane Arts (Volmit Software)
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

package com.volmit.iris.util.nbt.mca;

import com.volmit.iris.util.collection.KList;
import com.volmit.iris.util.math.Position2;

import java.io.File;
import java.io.IOException;
import java.io.RandomAccessFile;
import java.nio.file.Files;
import java.nio.file.StandardCopyOption;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Provides main and utility functions to read and writeNodeData .mca files and
 * to convert block, chunk and region coordinates.
 */
public final class MCAUtil {

    private static final Pattern mcaFilePattern = Pattern.compile("^.*r\\.(?<regionX>-?\\d+)\\.(?<regionZ>-?\\d+)\\.mca$");

    private MCAUtil() {
    }

    /**
     * @param file The file to read the data from.
     * @return An in-memory representation of the MCA file with decompressed chunk data.
     * @throws IOException if something during deserialization goes wrong.
     * @see MCAUtil#read(File)
     */
    public static MCAFile read(String file) throws IOException {
        return read(new File(file), LoadFlags.ALL_DATA);
    }

    /**
     * Reads an MCA file and loads all of its chunks.
     *
     * @param file The file to read the data from.
     * @return An in-memory representation of the MCA file with decompressed chunk data.
     * @throws IOException if something during deserialization goes wrong.
     */
    public static MCAFile read(File file) throws IOException {
        return read(file, LoadFlags.ALL_DATA);
    }

    /**
     * @param file      The file to read the data from.
     * @param loadFlags A logical or of {@link LoadFlags} constants indicating what data should be loaded
     * @return An in-memory representation of the MCA file with decompressed chunk data.
     * @throws IOException if something during deserialization goes wrong.
     * @see MCAUtil#read(File)
     */
    public static MCAFile read(String file, long loadFlags) throws IOException {
        return read(new File(file), loadFlags);
    }

    /**
     * Reads an MCA file and loads all of its chunks.
     *
     * @param file      The file to read the data from.
     * @param loadFlags A logical or of {@link LoadFlags} constants indicating what data should be loaded
     * @return An in-memory representation of the MCA file with decompressed chunk data
     * @throws IOException if something during deserialization goes wrong.
     */
    public static MCAFile read(File file, long loadFlags) throws IOException {
        MCAFile mcaFile = newMCAFile(file);
        try (RandomAccessFile raf = new RandomAccessFile(file, "r")) {
            mcaFile.deserialize(raf, loadFlags);
            return mcaFile;
        }
    }

    public static KList<Position2> sampleChunkPositions(File file) throws IOException {
        MCAFile mcaFile = newMCAFile(file);
        try (RandomAccessFile raf = new RandomAccessFile(file, "r")) {
            return mcaFile.samplePositions(raf);
        }
    }

    /**
     * Calls {@link MCAUtil#write(MCAFile, File, boolean)} without changing the timestamps.
     *
     * @param file    The file to writeNodeData to.
     * @param mcaFile The data of the MCA file to writeNodeData.
     * @return The amount of chunks written to the file.
     * @throws IOException If something goes wrong during serialization.
     * @see MCAUtil#write(MCAFile, File, boolean)
     */
    public static int write(MCAFile mcaFile, String file) throws IOException {
        return write(mcaFile, new File(file), false);
    }

    /**
     * Calls {@link MCAUtil#write(MCAFile, File, boolean)} without changing the timestamps.
     *
     * @param file    The file to writeNodeData to.
     * @param mcaFile The data of the MCA file to writeNodeData.
     * @return The amount of chunks written to the file.
     * @throws IOException If something goes wrong during serialization.
     * @see MCAUtil#write(MCAFile, File, boolean)
     */
    public static int write(MCAFile mcaFile, File file) throws IOException {
        return write(mcaFile, file, false);
    }

    /**
     * @param file             The file to writeNodeData to.
     * @param mcaFile          The data of the MCA file to writeNodeData.
     * @param changeLastUpdate Whether to adjust the timestamps of when the file was saved.
     * @return The amount of chunks written to the file.
     * @throws IOException If something goes wrong during serialization.
     * @see MCAUtil#write(MCAFile, File, boolean)
     */
    public static int write(MCAFile mcaFile, String file, boolean changeLastUpdate) throws IOException {
        return write(mcaFile, new File(file), changeLastUpdate);
    }

    /**
     * Writes an {@code MCAFile} object to disk. It optionally adjusts the timestamps
     * when the file was last saved to the current date and time or leaves them at
     * the value set by either loading an already existing MCA file or setting them manually.<br>
     * If the file already exists, it is completely overwritten by the new file (no modification).
     *
     * @param file             The file to writeNodeData to.
     * @param mcaFile          The data of the MCA file to writeNodeData.
     * @param changeLastUpdate Whether to adjust the timestamps of when the file was saved.
     * @return The amount of chunks written to the file.
     * @throws IOException If something goes wrong during serialization.
     */
    public static int write(MCAFile mcaFile, File file, boolean changeLastUpdate) throws IOException {
        if (mcaFile == null) {
            return 0;
        }

        File to = file;
        if (file.exists()) {
            to = File.createTempFile(to.getName(), null);
        }
        int chunks;
        try (RandomAccessFile raf = new RandomAccessFile(to, "rw")) {
            chunks = mcaFile.serialize(raf, changeLastUpdate);
        }

        if (chunks > 0 && to != file) {
            Files.move(to.toPath(), file.toPath(), StandardCopyOption.REPLACE_EXISTING);
        }

        return chunks;
    }

    /**
     * Turns the chunks coordinates into region coordinates and calls
     * {@link MCAUtil#createNameFromRegionLocation(int, int)}
     *
     * @param chunkX The x-value of the location of the chunk.
     * @param chunkZ The z-value of the location of the chunk.
     * @return A mca filename in the format "r.{regionX}.{regionZ}.mca"
     */
    public static String createNameFromChunkLocation(int chunkX, int chunkZ) {
        return createNameFromRegionLocation(chunkToRegion(chunkX), chunkToRegion(chunkZ));
    }

    /**
     * Turns the block coordinates into region coordinates and calls
     * {@link MCAUtil#createNameFromRegionLocation(int, int)}
     *
     * @param blockX The x-value of the location of the block.
     * @param blockZ The z-value of the location of the block.
     * @return A mca filename in the format "r.{regionX}.{regionZ}.mca"
     */
    public static String createNameFromBlockLocation(int blockX, int blockZ) {
        return createNameFromRegionLocation(blockToRegion(blockX), blockToRegion(blockZ));
    }

    /**
     * Creates a filename string from provided chunk coordinates.
     *
     * @param regionX The x-value of the location of the region.
     * @param regionZ The z-value of the location of the region.
     * @return A mca filename in the format "r.{regionX}.{regionZ}.mca"
     */
    public static String createNameFromRegionLocation(int regionX, int regionZ) {
        return "r." + regionX + "." + regionZ + ".mca";
    }

    /**
     * Turns a block coordinate value into a chunk coordinate value.
     *
     * @param block The block coordinate value.
     * @return The chunk coordinate value.
     */
    public static int blockToChunk(int block) {
        return block >> 4;
    }

    /**
     * Turns a block coordinate value into a region coordinate value.
     *
     * @param block The block coordinate value.
     * @return The region coordinate value.
     */
    public static int blockToRegion(int block) {
        return block >> 9;
    }

    /**
     * Turns a chunk coordinate value into a region coordinate value.
     *
     * @param chunk The chunk coordinate value.
     * @return The region coordinate value.
     */
    public static int chunkToRegion(int chunk) {
        return chunk >> 5;
    }

    /**
     * Turns a region coordinate value into a chunk coordinate value.
     *
     * @param region The region coordinate value.
     * @return The chunk coordinate value.
     */
    public static int regionToChunk(int region) {
        return region << 5;
    }

    /**
     * Turns a region coordinate value into a block coordinate value.
     *
     * @param region The region coordinate value.
     * @return The block coordinate value.
     */
    public static int regionToBlock(int region) {
        return region << 9;
    }

    /**
     * Turns a chunk coordinate value into a block coordinate value.
     *
     * @param chunk The chunk coordinate value.
     * @return The block coordinate value.
     */
    public static int chunkToBlock(int chunk) {
        return chunk << 4;
    }

    public static MCAFile newMCAFile(File file) {
        Matcher m = mcaFilePattern.matcher(file.getName());
        if (m.find()) {
            return new MCAFile(Integer.parseInt(m.group("regionX")), Integer.parseInt(m.group("regionZ")));
        }
        throw new IllegalArgumentException("invalid mca file name: " + file.getName());
    }
}
