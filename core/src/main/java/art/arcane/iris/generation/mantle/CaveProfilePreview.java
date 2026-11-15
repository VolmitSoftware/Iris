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

package art.arcane.iris.generation.mantle;

import art.arcane.iris.generation.cave.IrisCaveProfile;
import art.arcane.iris.generation.runtime.Engine;
import art.arcane.iris.generation.runtime.IrisEngineMantle;
import art.arcane.volmlib.util.mantle.runtime.Mantle;
import art.arcane.volmlib.util.matter.Matter;
import art.arcane.volmlib.util.matter.MatterCavern;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Arrays;
import java.util.Comparator;
import java.util.stream.Stream;

/**
 * Carves one cave profile with the engine's own carver over a square of flat ground, in a mantle of its own so the
 * world's is left alone, and says which blocks it opened.
 */
public final class CaveProfilePreview {
    private static final int CHUNK_BLOCKS = 16;
    private static final int CHUNK_COLUMNS = CHUNK_BLOCKS * CHUNK_BLOCKS;
    private static final int PREFETCH_CHUNKS = 0;
    private static final int ACCESS_CHUNKS = 1;

    private CaveProfilePreview() {
    }

    /**
     * The carved space of a square, {@code size} blocks across and a whole number of chunks, from {@code bottomY} up to
     * and including {@code surfaceY}, both in engine-local Y (0 is the bottom of the dimension).
     *
     * @return one flag per block, {@code (y - bottomY) * size² + z * size + x}
     */
    public static boolean[] carve(Engine engine, IrisCaveProfile profile, int originX, int size, int bottomY, int surfaceY)
            throws IOException {
        int height = surfaceY - bottomY + 1;
        boolean[] carved = new boolean[height * size * size];
        Path folder = Files.createTempDirectory("iris-cave-preview");
        Mantle<Matter> mantle = IrisEngineMantle.createMantle(engine, folder, engine::getData);
        try {
            IrisCaveCarver3D carver = new IrisCaveCarver3D(engine, profile);
            int[] surface = new int[CHUNK_COLUMNS];
            Arrays.fill(surface, surfaceY);
            double[] weights = new double[CHUNK_COLUMNS];
            Arrays.fill(weights, 1D);
            int firstChunkX = Math.floorDiv(originX, CHUNK_BLOCKS);
            int chunks = size / CHUNK_BLOCKS;
            for (int chunkZ = 0; chunkZ < chunks; chunkZ++) {
                for (int chunkX = 0; chunkX < chunks; chunkX++) {
                    int worldChunkX = firstChunkX + chunkX;
                    try (MantleWriter writer = new MantleWriter(engine.getMantle(), mantle, worldChunkX, chunkZ, PREFETCH_CHUNKS, ACCESS_CHUNKS, false)) {
                        carver.carve(writer, worldChunkX, chunkZ, weights, 0D, 0D, null, surface);
                    }
                    readCarved(mantle, carved, worldChunkX, chunkZ, chunkX, chunkZ, size, bottomY, surfaceY);
                }
            }
        } finally {
            mantle.close();
            deleteQuietly(folder);
        }
        return carved;
    }

    private static void readCarved(Mantle<Matter> mantle, boolean[] carved, int worldChunkX, int worldChunkZ, int chunkX, int chunkZ,
                                   int size, int bottomY, int surfaceY) {
        for (int y = bottomY; y <= surfaceY; y++) {
            for (int z = 0; z < CHUNK_BLOCKS; z++) {
                for (int x = 0; x < CHUNK_BLOCKS; x++) {
                    MatterCavern cavern = mantle.get(worldChunkX * CHUNK_BLOCKS + x, y, worldChunkZ * CHUNK_BLOCKS + z, MatterCavern.class);
                    if (cavern == null) {
                        continue;
                    }
                    int cell = (y - bottomY) * size * size + (chunkZ * CHUNK_BLOCKS + z) * size + chunkX * CHUNK_BLOCKS + x;
                    carved[cell] = true;
                }
            }
        }
    }

    private static void deleteQuietly(Path folder) {
        try (Stream<Path> paths = Files.walk(folder)) {
            paths.sorted(Comparator.reverseOrder()).forEach(path -> path.toFile().delete());
        } catch (IOException ignored) {
        }
    }
}
