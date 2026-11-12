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
import it.unimi.dsi.fastutil.ints.Int2ByteMap;
import it.unimi.dsi.fastutil.ints.IntArrayFIFOQueue;

import java.util.Set;
import java.util.function.IntBinaryOperator;
import java.util.function.UnaryOperator;

/**
 * A chunk's cells as players see them, with the sky's light in each, as Minecraft lights a world: full under the open
 * sky, through air without loss, one level less through each leaf or block of water and one less with each step aside.
 * Beside the chunk, the terrain's height alone tells what lies there: open air above it, the sea below the fluid's height.
 * <p>
 * A block face is shaded by the light of the cell it faces and by how many of the blocks around each of its corners are
 * opaque, as Minecraft's smooth lighting darkens the corners where blocks meet.
 */
final class ChunkCells {
    static final int FULL_LIGHT = 15;
    /** Where a face's light lies in its shade; each corner's opaque neighbours follow in two bits each. */
    static final int LIGHT_BITS = 4;
    static final int CORNER_BITS = 2;
    static final int MOST_OPAQUE_AROUND_CORNER = 3;
    private static final int LIGHT_MASK = (1 << LIGHT_BITS) - 1;
    private static final int CORNERS = 4;
    private static final int ACROSS = 16;
    private static final int UP = 1;
    private static final Set<String> ALWAYS_IN_WATER = Set.of("minecraft:kelp", "minecraft:kelp_plant", "minecraft:seagrass",
            "minecraft:tall_seagrass", "minecraft:bubble_column");
    private static final String LEAVES_SUFFIX = "_leaves";
    /**
     * Each side's face corners as the studio draws them, a cube's corner each, in {@link VisibleBlocks#SIDES}' order:
     * top left, bottom left, bottom right and top right as seen from outside.
     */
    private static final int[][][] FACE_CORNERS = {
            {{0, 0, 1}, {0, 0, 0}, {1, 0, 0}, {1, 0, 1}},
            {{0, 1, 0}, {0, 1, 1}, {1, 1, 1}, {1, 1, 0}},
            {{1, 1, 0}, {1, 0, 0}, {0, 0, 0}, {0, 1, 0}},
            {{0, 1, 1}, {0, 0, 1}, {1, 0, 1}, {1, 1, 1}},
            {{0, 1, 0}, {0, 0, 0}, {0, 0, 1}, {0, 1, 1}},
            {{1, 1, 1}, {1, 0, 1}, {1, 0, 0}, {1, 1, 0}}
    };

    private final Hunk<PlatformBlockState> chunk;
    private final IntBinaryOperator terrainY;
    private final int fluidHeight;
    private final int height;
    private final PlatformBlockState[] seen;
    private final boolean[] open;
    private final boolean[] fluid;
    private final byte[] dimming;
    private final byte[] light;

    private ChunkCells(Hunk<PlatformBlockState> chunk, IntBinaryOperator terrainY, int fluidHeight, UnaryOperator<PlatformBlockState> seenByPlayers) {
        this.chunk = chunk;
        this.terrainY = terrainY;
        this.fluidHeight = fluidHeight;
        this.height = chunk.getHeight();
        int cells = ACROSS * height * ACROSS;
        this.seen = new PlatformBlockState[cells];
        this.open = new boolean[cells];
        this.fluid = new boolean[cells];
        this.dimming = new byte[cells];
        this.light = new byte[cells];
        for (int y = 0; y < height; y++) {
            for (int z = 0; z < ACROSS; z++) {
                for (int x = 0; x < ACROSS; x++) {
                    PlatformBlockState block = chunk.get(x, y, z);
                    int cell = cellOf(x, y, z);
                    seen[cell] = block == null || block.isAir() ? null : seenByPlayers.apply(block);
                    open[cell] = seen[cell] == null || !seen[cell].isOccluding();
                    fluid[cell] = seen[cell] != null && holdsFluid(seen[cell]);
                    dimming[cell] = (byte) (fluid[cell] || (seen[cell] != null && materialOf(seen[cell].key()).endsWith(LEAVES_SUFFIX)) ? 1 : 0);
                }
            }
        }
    }

    /**
     * @param terrainY      the terrain's top in a column by its x and z from the chunk's corner, which reaches into the
     *                      chunks around it
     * @param fluidHeight   the height the sea fills to
     * @param seenByPlayers the state players see in place of a block, which decides what hides what
     * @param litBeside     cells along the chunk's sides, as {@link VisibleBlocks#positionOf} places them, that the open
     *                      air of the chunks beside it reaches, each with the light of the cell beside it there
     */
    static ChunkCells of(Hunk<PlatformBlockState> chunk, IntBinaryOperator terrainY, int fluidHeight, UnaryOperator<PlatformBlockState> seenByPlayers,
                         Int2ByteMap litBeside) {
        ChunkCells cells = new ChunkCells(chunk, terrainY, fluidHeight, seenByPlayers);
        cells.spreadSkyLight(litBeside);
        return cells;
    }

    int height() {
        return height;
    }

    Hunk<PlatformBlockState> chunk() {
        return chunk;
    }

    /** The block players see in a cell of the chunk, or null for air. */
    PlatformBlockState seenAt(int x, int y, int z) {
        return seen[cellOf(x, y, z)];
    }

    boolean isOpen(int x, int y, int z) {
        return open[cellOf(x, y, z)];
    }

    boolean holdsFluid(int x, int y, int z) {
        return fluid[cellOf(x, y, z)];
    }

    /** What lies beside the chunk, judged by the terrain's height there alone. */
    Outside outsideAt(int x, int y, int z) {
        if (y >= height) {
            return Outside.AIR;
        }
        if (y < 0 || y <= terrainY.applyAsInt(x, z)) {
            return Outside.SOLID;
        }
        return y <= fluidHeight ? Outside.FLUID : Outside.AIR;
    }

    /** The sky's light in a cell, 0 to {@link #FULL_LIGHT}, in the chunk or beside it. */
    int lightAt(int x, int y, int z) {
        if (isInside(x, y, z)) {
            return light[cellOf(x, y, z)];
        }
        return switch (outsideAt(x, y, z)) {
            case AIR -> FULL_LIGHT;
            case FLUID -> Math.max(0, FULL_LIGHT - (fluidHeight - y));
            case SOLID -> 0;
        };
    }

    /** The light a block shows on faces that stand inside it, such as a plant's: its own cell's, or the brightest beside a solid one. */
    int blockLightAt(int x, int y, int z) {
        if (open[cellOf(x, y, z)]) {
            return light[cellOf(x, y, z)];
        }
        int brightest = 0;
        for (int[] side : VisibleBlocks.SIDES) {
            brightest = Math.max(brightest, lightAt(x + side[0], y + side[1], z + side[2]));
        }
        return brightest;
    }

    /**
     * The shade of a block's face on a side: the light of the cell it faces, then for each of its corners, in
     * {@link #FACE_CORNERS}' order, how many of the three cells around the corner in front of the face are opaque, as
     * Minecraft counts them; two opaque sides hide the corner between them too.
     */
    short faceShade(int x, int y, int z, int side) {
        int[] normal = VisibleBlocks.SIDES[side];
        int frontX = x + normal[0];
        int frontY = y + normal[1];
        int frontZ = z + normal[2];
        int shade = lightAt(frontX, frontY, frontZ);
        for (int corner = 0; corner < CORNERS; corner++) {
            shade |= opaqueAround(frontX, frontY, frontZ, normal, FACE_CORNERS[side][corner]) << (LIGHT_BITS + corner * CORNER_BITS);
        }
        return (short) shade;
    }

    /** A face's shade lit by {@code light} with no corner darkened, for a face whose surroundings lie in another chunk. */
    static short plainShade(int light) {
        return (short) light;
    }

    static int lightOf(short shade) {
        return shade & LIGHT_MASK;
    }

    /** How many opaque cells the corners of a face shaded {@code shade} have together, 0 to 12. */
    static int opaqueAroundCorners(short shade) {
        int opaque = 0;
        for (int corner = 0; corner < CORNERS; corner++) {
            opaque += (shade >> (LIGHT_BITS + corner * CORNER_BITS)) & MOST_OPAQUE_AROUND_CORNER;
        }
        return opaque;
    }

    /** The shade of the top of the block at a cell. */
    short topShade(int x, int y, int z) {
        return faceShade(x, y, z, UP);
    }

    private int opaqueAround(int frontX, int frontY, int frontZ, int[] normal, int[] corner) {
        int[] first = new int[3];
        int[] second = new int[3];
        int found = 0;
        for (int axis = 0; axis < 3; axis++) {
            if (normal[axis] != 0) {
                continue;
            }
            int[] along = found++ == 0 ? first : second;
            along[axis] = corner[axis] == 1 ? 1 : -1;
        }
        boolean firstOpaque = isOpaque(frontX + first[0], frontY + first[1], frontZ + first[2]);
        boolean secondOpaque = isOpaque(frontX + second[0], frontY + second[1], frontZ + second[2]);
        if (firstOpaque && secondOpaque) {
            return MOST_OPAQUE_AROUND_CORNER;
        }
        boolean between = isOpaque(frontX + first[0] + second[0], frontY + first[1] + second[1], frontZ + first[2] + second[2]);
        return (firstOpaque ? 1 : 0) + (secondOpaque ? 1 : 0) + (between ? 1 : 0);
    }

    private boolean isOpaque(int x, int y, int z) {
        return isInside(x, y, z) ? !open[cellOf(x, y, z)] : outsideAt(x, y, z) == Outside.SOLID;
    }

    // Straight down from the sky in each column, then aside and on, a level less a step, as far as the light reaches.
    private void spreadSkyLight(Int2ByteMap litBeside) {
        IntArrayFIFOQueue lit = new IntArrayFIFOQueue();
        for (int z = 0; z < ACROSS; z++) {
            for (int x = 0; x < ACROSS; x++) {
                int level = FULL_LIGHT;
                for (int y = height - 1; y >= 0 && open[cellOf(x, y, z)]; y--) {
                    level -= dimming[cellOf(x, y, z)];
                    if (level <= 0) {
                        break;
                    }
                    light(cellOf(x, y, z), level, lit);
                }
            }
        }
        for (int y = 0; y < height; y++) {
            for (int z = 0; z < ACROSS; z++) {
                for (int x = 0; x < ACROSS; x++) {
                    if (isOnSide(x, z)) {
                        lightFromBeside(x, y, z, lit);
                    }
                }
            }
        }
        for (Int2ByteMap.Entry beside : litBeside.int2ByteEntrySet()) {
            int position = beside.getIntKey();
            int cell = cellOf(position & (ACROSS - 1), position >> VisibleBlocks.POSITION_Y_SHIFT, (position >> 4) & (ACROSS - 1));
            if (open[cell]) {
                light(cell, beside.getByteValue() - 1 - dimming[cell], lit);
            }
        }
        while (!lit.isEmpty()) {
            int cell = lit.dequeueInt();
            int x = cell % ACROSS;
            int z = (cell / ACROSS) % ACROSS;
            int y = cell / (ACROSS * ACROSS);
            for (int[] side : VisibleBlocks.SIDES) {
                int nx = x + side[0];
                int ny = y + side[1];
                int nz = z + side[2];
                if (isInside(nx, ny, nz) && open[cellOf(nx, ny, nz)]) {
                    int neighbour = cellOf(nx, ny, nz);
                    light(neighbour, light[cell] - 1 - dimming[neighbour], lit);
                }
            }
        }
    }

    private void lightFromBeside(int x, int y, int z, IntArrayFIFOQueue lit) {
        int cell = cellOf(x, y, z);
        if (!open[cell]) {
            return;
        }
        for (int side = 2; side < VisibleBlocks.SIDES.length; side++) {
            int[] step = VisibleBlocks.SIDES[side];
            if (!isInside(x + step[0], y, z + step[2])) {
                light(cell, lightAt(x + step[0], y, z + step[2]) - 1 - dimming[cell], lit);
            }
        }
    }

    private void light(int cell, int level, IntArrayFIFOQueue lit) {
        if (level > light[cell]) {
            light[cell] = (byte) level;
            lit.enqueue(cell);
        }
    }

    private static boolean isOnSide(int x, int z) {
        return x == 0 || z == 0 || x == ACROSS - 1 || z == ACROSS - 1;
    }

    // Water around a waterlogged block, or one that only grows in water, has no side against it.
    private static boolean holdsFluid(PlatformBlockState block) {
        return block.isFluid() || block.isWaterLogged() || ALWAYS_IN_WATER.contains(materialOf(block.key()));
    }

    private static String materialOf(String key) {
        int properties = key.indexOf('[');
        return properties < 0 ? key : key.substring(0, properties);
    }

    boolean isInside(int x, int y, int z) {
        return x >= 0 && x < ACROSS && z >= 0 && z < ACROSS && y >= 0 && y < height;
    }

    static int cellOf(int x, int y, int z) {
        return (y * ACROSS + z) * ACROSS + x;
    }

    /** What lies beside the chunk, judged by the terrain's height there alone. */
    enum Outside {
        SOLID,
        FLUID,
        AIR
    }
}
