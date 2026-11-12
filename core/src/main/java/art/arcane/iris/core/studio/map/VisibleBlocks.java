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

package art.arcane.iris.core.studio.map;

import art.arcane.iris.spi.PlatformBlockState;
import art.arcane.iris.util.project.hunk.Hunk;
import it.unimi.dsi.fastutil.bytes.ByteArrayList;
import it.unimi.dsi.fastutil.ints.IntArrayList;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Set;
import java.util.function.IntBinaryOperator;
import java.util.function.UnaryOperator;

/**
 * The blocks of a chunk that can be seen from the open air above it, each with the sides that face that air. The open
 * air is every cell no opaque block fills that the sky reaches, through water and leaves too, so the ground, cliffs,
 * overhangs, the wood inside a tree and the floor of a lake are seen, and a cave closed to the sky is not.
 *
 * @param positions each block's place in the chunk: x in the lowest 4 bits, z in the next 4, then its height above the
 *                  dimension's bottom
 * @param blocks    each block's key
 * @param faces     the sides of each block that face the open air, a bit each in {@link #SIDES}' order
 */
record VisibleBlocks(int[] positions, String[] blocks, byte[] faces) {
    static final VisibleBlocks NONE = new VisibleBlocks(new int[0], new String[0], new byte[0]);
    /** The order of the sides in {@link #faces}: down, up, north, south, west, east. */
    static final int[][] SIDES = {{0, -1, 0}, {0, 1, 0}, {0, 0, -1}, {0, 0, 1}, {-1, 0, 0}, {1, 0, 0}};
    static final int POSITION_Y_SHIFT = 8;
    private static final int ACROSS = 16;
    private static final Set<String> ALWAYS_IN_WATER = Set.of("minecraft:kelp", "minecraft:kelp_plant", "minecraft:seagrass",
            "minecraft:tall_seagrass", "minecraft:bubble_column");
    private static final int Z_SHIFT = 4;

    int count() {
        return positions.length;
    }

    /**
     * @param terrainY      the terrain's top in a column by its x and z from the chunk's corner, which reaches into the
     *                      chunks around it; beside the chunk, what lies above it counts as open air, or water below the
     *                      fluid's height
     * @param fluidHeight   the height the sea fills to
     * @param seenByPlayers the state players see in place of a block, which decides what hides what
     */
    static VisibleBlocks of(Hunk<PlatformBlockState> chunk, IntBinaryOperator terrainY, int fluidHeight, UnaryOperator<PlatformBlockState> seenByPlayers) {
        return new Finder(chunk, terrainY, fluidHeight, seenByPlayers).find();
    }

    @Override
    public boolean equals(Object other) {
        return other instanceof VisibleBlocks visible && Arrays.equals(positions, visible.positions)
                && Arrays.equals(blocks, visible.blocks) && Arrays.equals(faces, visible.faces);
    }

    @Override
    public int hashCode() {
        return 31 * (31 * Arrays.hashCode(positions) + Arrays.hashCode(blocks)) + Arrays.hashCode(faces);
    }

    @Override
    public String toString() {
        return "VisibleBlocks" + Arrays.toString(positions) + Arrays.toString(blocks) + Arrays.toString(faces);
    }

    private static final class Finder {
        private final Hunk<PlatformBlockState> chunk;
        private final IntBinaryOperator terrainY;
        private final int fluidHeight;
        private final int height;
        private final PlatformBlockState[] seen;
        private final boolean[] open;
        private final boolean[] fluid;
        private final boolean[] skyReaches;

        Finder(Hunk<PlatformBlockState> chunk, IntBinaryOperator terrainY, int fluidHeight, UnaryOperator<PlatformBlockState> seenByPlayers) {
            this.chunk = chunk;
            this.terrainY = terrainY;
            this.fluidHeight = fluidHeight;
            this.height = chunk.getHeight();
            int cells = ACROSS * height * ACROSS;
            this.seen = new PlatformBlockState[cells];
            this.open = new boolean[cells];
            this.fluid = new boolean[cells];
            this.skyReaches = new boolean[cells];
            for (int y = 0; y < height; y++) {
                for (int z = 0; z < ACROSS; z++) {
                    for (int x = 0; x < ACROSS; x++) {
                        PlatformBlockState block = chunk.get(x, y, z);
                        int cell = cellOf(x, y, z);
                        seen[cell] = block == null || block.isAir() ? null : seenByPlayers.apply(block);
                        open[cell] = seen[cell] == null || !seen[cell].isOccluding();
                        fluid[cell] = seen[cell] != null && holdsFluid(seen[cell]);
                    }
                }
            }
        }

        // Water around a waterlogged block, or one that only grows in water, has no side against it.
        private static boolean holdsFluid(PlatformBlockState block) {
            return block.isFluid() || block.isWaterLogged() || ALWAYS_IN_WATER.contains(materialOf(block.key()));
        }

        private static String materialOf(String key) {
            int properties = key.indexOf('[');
            return properties < 0 ? key : key.substring(0, properties);
        }

        VisibleBlocks find() {
            spreadTheSky();
            IntArrayList positions = new IntArrayList();
            List<String> blocks = new ArrayList<>();
            ByteArrayList faces = new ByteArrayList();
            for (int y = 0; y < height; y++) {
                for (int z = 0; z < ACROSS; z++) {
                    for (int x = 0; x < ACROSS; x++) {
                        int cell = cellOf(x, y, z);
                        if (seen[cell] == null) {
                            continue;
                        }
                        int sides = sidesFacingTheSky(x, y, z);
                        // A plant or a leaf the sky reaches shows whatever it touches; its model may have faces no side hides.
                        if (sides == 0 && !(open[cell] && skyReaches[cell])) {
                            continue;
                        }
                        positions.add(x | z << Z_SHIFT | y << POSITION_Y_SHIFT);
                        blocks.add(chunk.get(x, y, z).key());
                        faces.add((byte) sides);
                    }
                }
            }
            return positions.isEmpty() ? NONE : new VisibleBlocks(positions.toIntArray(), blocks.toArray(String[]::new), faces.toByteArray());
        }

        // Fills every open cell the sky reaches, from the top of the chunk and from beside it where the ground around
        // lies lower.
        private void spreadTheSky() {
            int[] queue = new int[skyReaches.length];
            int queued = 0;
            for (int y = 0; y < height; y++) {
                for (int z = 0; z < ACROSS; z++) {
                    for (int x = 0; x < ACROSS; x++) {
                        int cell = cellOf(x, y, z);
                        if (open[cell] && touchesOpenOutside(x, y, z)) {
                            skyReaches[cell] = true;
                            queue[queued++] = cell;
                        }
                    }
                }
            }
            for (int next = 0; next < queued; next++) {
                int cell = queue[next];
                int x = cell % ACROSS;
                int z = (cell / ACROSS) % ACROSS;
                int y = cell / (ACROSS * ACROSS);
                for (int[] side : SIDES) {
                    int nx = x + side[0];
                    int ny = y + side[1];
                    int nz = z + side[2];
                    if (!isInside(nx, ny, nz)) {
                        continue;
                    }
                    int neighbour = cellOf(nx, ny, nz);
                    if (open[neighbour] && !skyReaches[neighbour]) {
                        skyReaches[neighbour] = true;
                        queue[queued++] = neighbour;
                    }
                }
            }
        }

        private boolean touchesOpenOutside(int x, int y, int z) {
            for (int[] side : SIDES) {
                int nx = x + side[0];
                int ny = y + side[1];
                int nz = z + side[2];
                if (!isInside(nx, ny, nz) && outsideAt(nx, ny, nz) != Outside.SOLID) {
                    return true;
                }
            }
            return false;
        }

        // A side faces the sky where the cell beside it is open air the sky reaches; water hides the sides of water.
        private int sidesFacingTheSky(int x, int y, int z) {
            boolean isFluid = fluid[cellOf(x, y, z)];
            int sides = 0;
            for (int side = 0; side < SIDES.length; side++) {
                int nx = x + SIDES[side][0];
                int ny = y + SIDES[side][1];
                int nz = z + SIDES[side][2];
                boolean faces;
                if (isInside(nx, ny, nz)) {
                    int neighbour = cellOf(nx, ny, nz);
                    faces = skyReaches[neighbour] && !(isFluid && fluid[neighbour]);
                } else {
                    Outside outside = outsideAt(nx, ny, nz);
                    faces = outside == Outside.AIR || (outside == Outside.FLUID && !isFluid);
                }
                if (faces) {
                    sides |= 1 << side;
                }
            }
            return sides;
        }

        private Outside outsideAt(int x, int y, int z) {
            if (y >= height) {
                return Outside.AIR;
            }
            if (y < 0 || y <= terrainY.applyAsInt(x, z)) {
                return Outside.SOLID;
            }
            return y <= fluidHeight ? Outside.FLUID : Outside.AIR;
        }

        private boolean isInside(int x, int y, int z) {
            return x >= 0 && x < ACROSS && z >= 0 && z < ACROSS && y >= 0 && y < height;
        }

        private static int cellOf(int x, int y, int z) {
            return (y * ACROSS + z) * ACROSS + x;
        }
    }

    /** What lies beside the chunk, judged by the terrain's height there alone. */
    private enum Outside {
        SOLID,
        FLUID,
        AIR
    }
}
