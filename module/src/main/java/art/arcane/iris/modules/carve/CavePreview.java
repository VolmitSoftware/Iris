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

package art.arcane.iris.modules.carve;

import art.arcane.iris.builtin.IrisSeedPurposes;
import art.arcane.iris.modules.carve.field.CaveCarver;
import art.arcane.iris.modules.carve.field.CavePart;
import com.volmit.block.BlockState;
import com.volmit.geometry.BlockBox;
import com.volmit.geometry.BlockPosition;
import com.volmit.object.GeneratedObject;
import com.volmit.object.ObjectBlock;
import com.volmit.resource.ResourceId;
import com.volmit.tooling.SceneSizes;
import com.volmit.world.PackWorld;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

/**
 * One cave carved over a square of the world under a flat surface, with the seed the world carves it with: the carved
 * space drawn as blocks, a colour per part, so the studio shows the shape of the cave instead of the rock around it.
 * Only the carved blocks on the outside of the space are kept, as the inside could not be seen. A variant is the same
 * cave somewhere else in the world.
 */
final class CavePreview {
    static final int VARIANTS = 4;
    static final SceneSizes SIZES = new SceneSizes(List.of(64, 128, 256, 512), 128);
    /** The surface the preview carves under, in world Y. */
    static final int SURFACE_WORLD_Y = 96;
    private static final int CHUNK_BLOCKS = 16;
    private static final int CHUNK_COLUMNS = CHUNK_BLOCKS * CHUNK_BLOCKS;
    /** A cave deeper than this under the surface is cut off at the bottom of the preview. */
    private static final int MAX_DEPTH_BLOCKS = 256;
    private static final int VARIANT_SPACING_BLOCKS = 4096;
    private static final byte ROCK = 0;
    private static final BlockState TUNNEL_BLOCK = new BlockState("minecraft:light_blue_concrete");
    private static final BlockState CAVERN_BLOCK = new BlockState("minecraft:orange_concrete");
    private static final int[] FLAT_SURFACE = new int[CHUNK_COLUMNS];
    private static final double[] LISTED_EVERYWHERE = new double[CHUNK_COLUMNS];
    private static final boolean[] NOTHING_SEALED = new boolean[CHUNK_COLUMNS];
    private static final int[][] NEIGHBOURS = {{1, 0, 0}, {-1, 0, 0}, {0, 1, 0}, {0, -1, 0}, {0, 0, 1}, {0, 0, -1}};

    static {
        Arrays.fill(LISTED_EVERYWHERE, 1D);
    }

    private CavePreview() {
    }

    /** @param size how many blocks across the square is, a whole number of chunks */
    static GeneratedObject of(PackWorld world, ResourceId id, Cave cave, int variant, int size) {
        int bottomWorldY = Math.max(world.minWorldY(), SURFACE_WORLD_Y - MAX_DEPTH_BLOCKS);
        int surfaceHeight = SURFACE_WORLD_Y - world.minWorldY();
        int lowestHeight = bottomWorldY - world.minWorldY();
        long seed = CaveCarver.seedOf(world.seed(IrisSeedPurposes.CARVE), id);
        CaveCarver carver = new CaveCarver(cave, world.resources().noise(), seed, world.minWorldY(), surfaceHeight + 1);
        int[] surface = FLAT_SURFACE.clone();
        Arrays.fill(surface, surfaceHeight);
        CarvedSpace carved = new CarvedSpace(size, surfaceHeight - lowestHeight + 1);
        int originX = variant * VARIANT_SPACING_BLOCKS;
        int chunks = size / CHUNK_BLOCKS;
        for (int chunkX = 0; chunkX < chunks; chunkX++) {
            for (int chunkZ = 0; chunkZ < chunks; chunkZ++) {
                int sceneX = chunkX * CHUNK_BLOCKS;
                int sceneZ = chunkZ * CHUNK_BLOCKS;
                carver.carveByPart(originX + sceneX, sceneZ, surface, LISTED_EVERYWHERE, NOTHING_SEALED,
                        (part, localX, y, localZ) -> carved.carve(sceneX + localX, y - lowestHeight, sceneZ + localZ, part));
            }
        }
        return carved.outside();
    }

    /** The carved blocks of a box, by the part that carved each first. */
    private static final class CarvedSpace {
        private final int size;
        private final int height;
        private final byte[] parts;

        CarvedSpace(int size, int height) {
            this.size = size;
            this.height = height;
            this.parts = new byte[size * height * size];
        }

        void carve(int x, int y, int z, CavePart part) {
            if (y < 0 || y >= height) {
                return;
            }
            int cell = cellOf(x, y, z);
            if (parts[cell] == ROCK) {
                parts[cell] = (byte) (part.ordinal() + 1);
            }
        }

        // A carved block is on the outside where a block beside it is rock, or where it touches the box's side.
        GeneratedObject outside() {
            List<ObjectBlock> blocks = new ArrayList<>();
            for (int y = 0; y < height; y++) {
                for (int z = 0; z < size; z++) {
                    for (int x = 0; x < size; x++) {
                        byte part = parts[cellOf(x, y, z)];
                        if (part != ROCK && touchesRock(x, y, z)) {
                            blocks.add(new ObjectBlock(new BlockPosition(x, y, z), part == CavePart.TUNNEL.ordinal() + 1 ? TUNNEL_BLOCK : CAVERN_BLOCK));
                        }
                    }
                }
            }
            BlockBox bounds = new BlockBox(0, 0, 0, size, height, size);
            return new GeneratedObject(bounds, new BlockPosition(size / 2, 0, size / 2), blocks, List.of());
        }

        private boolean touchesRock(int x, int y, int z) {
            for (int[] step : NEIGHBOURS) {
                int nx = x + step[0];
                int ny = y + step[1];
                int nz = z + step[2];
                boolean inside = nx >= 0 && ny >= 0 && nz >= 0 && nx < size && ny < height && nz < size;
                if (!inside || parts[cellOf(nx, ny, nz)] == ROCK) {
                    return true;
                }
            }
            return false;
        }

        private int cellOf(int x, int y, int z) {
            return (y * size + z) * size + x;
        }
    }
}
