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
import it.unimi.dsi.fastutil.ints.Int2ByteMap;
import it.unimi.dsi.fastutil.ints.Int2ByteMaps;
import it.unimi.dsi.fastutil.ints.IntArrayList;
import it.unimi.dsi.fastutil.shorts.ShortArrayList;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.function.IntBinaryOperator;
import java.util.function.UnaryOperator;

/**
 * The blocks of a chunk that can be seen from the open air above it, each with the sides that face that air and how
 * they are lit. The open air is every cell no opaque block fills that the sky reaches, through water and leaves too,
 * so the ground, cliffs, overhangs, the wood inside a tree and the floor of a lake are seen, and a cave closed to the
 * sky is not.
 *
 * @param positions each block's place in the chunk: x in the lowest 4 bits, z in the next 4, then its height above the
 *                  dimension's bottom
 * @param blocks    each block's key
 * @param faces     the sides of each block that face the open air, a bit each in {@link #SIDES}' order
 * @param lights    the sky's light each block shows on faces that stand inside it, such as a plant's, 0 to 15
 * @param shades    the shade of each face {@link #faces} names, block after block and side after side, as
 *                  {@link ChunkCells#faceShade} gives it
 * @param borders   the cells along the chunk's sides, for the faces it shows once the chunks beside it are known; two
 *                  visible blocks are equal by the faces they show without them
 */
record VisibleBlocks(int[] positions, String[] blocks, byte[] faces, byte[] lights, short[] shades, ChunkBorders borders) {
    /** The order of the sides in {@link #faces}: down, up, north, south, west, east. */
    static final int[][] SIDES = {{0, -1, 0}, {0, 1, 0}, {0, 0, -1}, {0, 0, 1}, {-1, 0, 0}, {1, 0, 0}};
    static final int POSITION_Y_SHIFT = 8;
    private static final int ACROSS = 16;
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
        return of(ChunkCells.of(chunk, terrainY, fluidHeight, seenByPlayers, Int2ByteMaps.EMPTY_MAP), Int2ByteMaps.EMPTY_MAP);
    }

    /**
     * @param reachedBeside cells along the chunk's sides, as {@link #positionOf} places them, that the open air of the
     *                      chunks beside it reaches, which the terrain's height there does not tell
     */
    static VisibleBlocks of(ChunkCells cells, Int2ByteMap reachedBeside) {
        return new Finder(cells, reachedBeside).find();
    }

    /** A block's place in its chunk, as {@link #positions} holds it. */
    static int positionOf(int x, int y, int z) {
        return x | z << Z_SHIFT | y << POSITION_Y_SHIFT;
    }

    @Override
    public boolean equals(Object other) {
        return other instanceof VisibleBlocks visible && Arrays.equals(positions, visible.positions) && Arrays.equals(blocks, visible.blocks)
                && Arrays.equals(faces, visible.faces) && Arrays.equals(lights, visible.lights) && Arrays.equals(shades, visible.shades);
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
        private final ChunkCells cells;
        private final Hunk<PlatformBlockState> chunk;
        private final int height;
        private final boolean[] skyReaches;
        private final Int2ByteMap reachedBeside;

        Finder(ChunkCells cells, Int2ByteMap reachedBeside) {
            this.cells = cells;
            this.chunk = cells.chunk();
            this.height = cells.height();
            this.reachedBeside = reachedBeside;
            this.skyReaches = new boolean[ACROSS * height * ACROSS];
        }

        VisibleBlocks find() {
            spreadTheSky();
            IntArrayList positions = new IntArrayList();
            List<String> blocks = new ArrayList<>();
            ByteArrayList faces = new ByteArrayList();
            ByteArrayList lights = new ByteArrayList();
            ShortArrayList shades = new ShortArrayList();
            for (int y = 0; y < height; y++) {
                for (int z = 0; z < ACROSS; z++) {
                    for (int x = 0; x < ACROSS; x++) {
                        if (cells.seenAt(x, y, z) == null) {
                            continue;
                        }
                        int sides = sidesFacingTheSky(x, y, z);
                        // A plant or a leaf the sky reaches shows whatever it touches; its model may have faces no side hides.
                        if (sides == 0 && !(cells.isOpen(x, y, z) && skyReaches[ChunkCells.cellOf(x, y, z)])) {
                            continue;
                        }
                        positions.add(positionOf(x, y, z));
                        blocks.add(chunk.get(x, y, z).key());
                        faces.add((byte) sides);
                        lights.add((byte) cells.blockLightAt(x, y, z));
                        for (int side = 0; side < SIDES.length; side++) {
                            if ((sides & 1 << side) != 0) {
                                shades.add(cells.faceShade(x, y, z, side));
                            }
                        }
                    }
                }
            }
            return new VisibleBlocks(positions.toIntArray(), blocks.toArray(String[]::new), faces.toByteArray(), lights.toByteArray(),
                    shades.toShortArray(), borders());
        }

        private ChunkBorders borders() {
            ChunkBorders.Wall[] walls = new ChunkBorders.Wall[ChunkBorders.EAST - ChunkBorders.NORTH + 1];
            for (int side = ChunkBorders.NORTH; side <= ChunkBorders.EAST; side++) {
                walls[side - ChunkBorders.NORTH] = wallOn(side);
            }
            return new ChunkBorders(walls);
        }

        private ChunkBorders.Wall wallOn(int side) {
            ChunkBorders.RunsBuilder reached = new ChunkBorders.RunsBuilder();
            ChunkBorders.RunsBuilder sealed = new ChunkBorders.RunsBuilder();
            ChunkBorders.RunsBuilder hidden = new ChunkBorders.RunsBuilder();
            for (int place = 0; place < ACROSS; place++) {
                int x = ChunkBorders.xOf(side, place);
                int z = ChunkBorders.zOf(side, place);
                for (int y = 0; y < height; y++) {
                    boolean holdsFluid = cells.holdsFluid(x, y, z);
                    if (skyReaches[ChunkCells.cellOf(x, y, z)]) {
                        reached.add(y, null, holdsFluid, cells.lightAt(x, y, z));
                    } else if (cells.isOpen(x, y, z)) {
                        sealed.add(y, null, holdsFluid, 0);
                    }
                    if (cells.seenAt(x, y, z) != null && !facesOutside(x, y, z, side)) {
                        hidden.add(y, chunk.get(x, y, z).key(), holdsFluid, 0);
                    }
                }
                reached.nextPlace();
                sealed.nextPlace();
                hidden.nextPlace();
            }
            return new ChunkBorders.Wall(reached.build(), sealed.build(), hidden.build());
        }

        // Fills every open cell the sky reaches, from the top of the chunk and from beside it where the ground around
        // lies lower or the chunk beside it is known to be open.
        private void spreadTheSky() {
            int[] queue = new int[skyReaches.length];
            int queued = 0;
            for (int y = 0; y < height; y++) {
                for (int z = 0; z < ACROSS; z++) {
                    for (int x = 0; x < ACROSS; x++) {
                        if (cells.isOpen(x, y, z) && (touchesOpenOutside(x, y, z) || reachedBeside.containsKey(positionOf(x, y, z)))) {
                            int cell = ChunkCells.cellOf(x, y, z);
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
                    if (!cells.isInside(nx, ny, nz)) {
                        continue;
                    }
                    int neighbour = ChunkCells.cellOf(nx, ny, nz);
                    if (cells.isOpen(nx, ny, nz) && !skyReaches[neighbour]) {
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
                if (!cells.isInside(nx, ny, nz) && cells.outsideAt(nx, ny, nz) != ChunkCells.Outside.SOLID) {
                    return true;
                }
            }
            return false;
        }

        // A side faces the sky where the cell beside it is open air the sky reaches; water hides the sides of water.
        private int sidesFacingTheSky(int x, int y, int z) {
            boolean isFluid = cells.holdsFluid(x, y, z);
            int sides = 0;
            for (int side = 0; side < SIDES.length; side++) {
                int nx = x + SIDES[side][0];
                int ny = y + SIDES[side][1];
                int nz = z + SIDES[side][2];
                boolean faces;
                if (cells.isInside(nx, ny, nz)) {
                    faces = skyReaches[ChunkCells.cellOf(nx, ny, nz)] && !(isFluid && cells.holdsFluid(nx, ny, nz));
                } else {
                    faces = facesOutside(x, y, z, side);
                }
                if (faces) {
                    sides |= 1 << side;
                }
            }
            return sides;
        }

        // Whether a side facing out of the chunk faces open air there, by the terrain's height beside the chunk.
        private boolean facesOutside(int x, int y, int z, int side) {
            ChunkCells.Outside outside = cells.outsideAt(x + SIDES[side][0], y + SIDES[side][1], z + SIDES[side][2]);
            return outside == ChunkCells.Outside.AIR || (outside == ChunkCells.Outside.FLUID && !cells.holdsFluid(x, y, z));
        }
    }
}
