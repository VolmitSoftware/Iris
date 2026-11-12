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

import it.unimi.dsi.fastutil.booleans.BooleanArrayList;
import it.unimi.dsi.fastutil.ints.IntArrayList;
import it.unimi.dsi.fastutil.ints.IntList;
import it.unimi.dsi.fastutil.shorts.ShortArrayList;

import java.util.ArrayList;
import java.util.List;

/**
 * The cells along a chunk's four sides. A chunk judges what lies beside it by the terrain's height there, which knows
 * nothing dug out of the ground, such as a structure set into a hill or a cave mouth across the chunk's edge, so it
 * leaves out the faces it has against such open air. Once the chunk beside it is known, the faces it left out against
 * that chunk's open air are shown after all.
 *
 * @param walls each side's cells, in the order of {@link VisibleBlocks#SIDES} from north on
 */
record ChunkBorders(Wall[] walls) {
    /** The first horizontal side in {@link VisibleBlocks#SIDES}; north, south, west and east follow in that order. */
    static final int NORTH = 2;
    static final int SOUTH = 3;
    static final int WEST = 4;
    static final int EAST = 5;
    private static final int ACROSS = 16;
    private static final int LAST = ACROSS - 1;

    /** Takes a face a chunk shows on {@code side}, at its x, y and z in the chunk. */
    interface FaceSink {
        void add(int x, int y, int z, String block, int side);
    }

    /**
     * One side's cells, each a run of cells one above the other.
     *
     * @param open   the runs the sky's open air fills; {@link Runs#fluid} tells which hold water
     * @param sealed the runs of cells as open, which the sky was not found to reach from within the chunk
     * @param hidden the runs of blocks that left out their face toward this side
     */
    record Wall(Runs open, Runs sealed, Runs hidden) {
        int bytes() {
            return open.bytes() + sealed.bytes() + hidden.bytes();
        }
    }

    /**
     * Runs of cells, those of each place along the side together, from the west or the north.
     *
     * @param firstOfPlace where each place's runs start, with one more for where the last place's end
     * @param bottoms      each run's lowest cell
     * @param tops         the cell above each run's highest
     * @param blocks       each run's block, or null for runs of open air
     * @param fluid        whether each run holds water
     */
    record Runs(int[] firstOfPlace, short[] bottoms, short[] tops, String[] blocks, boolean[] fluid) {
        // A bottom, a top, a shared key and the fluid flag.
        private static final int RUN_BYTES = Short.BYTES + Short.BYTES + Integer.BYTES + 1;

        int bytes() {
            return firstOfPlace.length * Integer.BYTES + tops.length * RUN_BYTES;
        }
    }

    int bytes() {
        int bytes = 0;
        for (Wall wall : walls) {
            bytes += wall.bytes();
        }
        return bytes;
    }

    /**
     * Each face this chunk left out on {@code side} that faces open air in {@code beside}, the chunk on that side. Water
     * shows no face against water, as within a chunk.
     */
    void facesAgainst(ChunkBorders beside, int side, FaceSink faces) {
        Runs hidden = walls[side - NORTH].hidden();
        Runs open = beside.walls[opposite(side) - NORTH].open();
        for (int place = 0; place < ACROSS; place++) {
            for (int run = hidden.firstOfPlace()[place]; run < hidden.firstOfPlace()[place + 1]; run++) {
                for (int air = open.firstOfPlace()[place]; air < open.firstOfPlace()[place + 1]; air++) {
                    if (hidden.fluid()[run] && open.fluid()[air]) {
                        continue;
                    }
                    int top = Math.min(hidden.tops()[run], open.tops()[air]);
                    for (int y = Math.max(hidden.bottoms()[run], open.bottoms()[air]); y < top; y++) {
                        faces.add(xOf(side, place), y, zOf(side, place), hidden.blocks()[run], side);
                    }
                }
            }
        }
    }

    /**
     * The cells along {@code side} the chunk took for closed off from the sky that touch the open air of {@code beside},
     * the chunk on that side: a hollow whose only way out is across the edge, such as a pit dug under an overhang. Each
     * is a place in the chunk as {@link VisibleBlocks#positions()} gives it.
     */
    IntList reachedFrom(ChunkBorders beside, int side) {
        Runs sealed = walls[side - NORTH].sealed();
        Runs open = beside.walls[opposite(side) - NORTH].open();
        IntList reached = new IntArrayList();
        for (int place = 0; place < ACROSS; place++) {
            for (int run = sealed.firstOfPlace()[place]; run < sealed.firstOfPlace()[place + 1]; run++) {
                for (int air = open.firstOfPlace()[place]; air < open.firstOfPlace()[place + 1]; air++) {
                    int top = Math.min(sealed.tops()[run], open.tops()[air]);
                    for (int y = Math.max(sealed.bottoms()[run], open.bottoms()[air]); y < top; y++) {
                        reached.add(VisibleBlocks.positionOf(xOf(side, place), y, zOf(side, place)));
                    }
                }
            }
        }
        return reached;
    }

    static int opposite(int side) {
        return side ^ 1;
    }

    static int xOf(int side, int place) {
        return switch (side) {
            case WEST -> 0;
            case EAST -> LAST;
            default -> place;
        };
    }

    static int zOf(int side, int place) {
        return switch (side) {
            case NORTH -> 0;
            case SOUTH -> LAST;
            default -> place;
        };
    }

    /** Gathers a side's runs place by place, from the bottom of each place up. */
    static final class RunsBuilder {
        private final int[] firstOfPlace = new int[ACROSS + 1];
        private final ShortArrayList bottoms = new ShortArrayList();
        private final ShortArrayList tops = new ShortArrayList();
        private final List<String> blocks = new ArrayList<>();
        private final BooleanArrayList fluid = new BooleanArrayList();
        private int place;

        /** Starts the runs of the next place along the side. */
        void nextPlace() {
            firstOfPlace[++place] = tops.size();
        }

        /** Adds the cell at {@code y} of the current place, joining the run below where it is of the same. */
        void add(int y, String block, boolean holdsFluid) {
            int last = tops.size() - 1;
            boolean joins = last >= firstOfPlace[place] && tops.getShort(last) == y && fluid.getBoolean(last) == holdsFluid
                    && (block == null ? blocks.get(last) == null : block.equals(blocks.get(last)));
            if (joins) {
                tops.set(last, (short) (y + 1));
                return;
            }
            bottoms.add((short) y);
            tops.add((short) (y + 1));
            blocks.add(block);
            fluid.add(holdsFluid);
        }

        Runs build() {
            firstOfPlace[ACROSS] = tops.size();
            return new Runs(firstOfPlace, bottoms.toShortArray(), tops.toShortArray(), blocks.toArray(String[]::new), fluid.toBooleanArray());
        }
    }
}
