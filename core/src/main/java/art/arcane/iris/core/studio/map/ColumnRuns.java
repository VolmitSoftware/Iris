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
import it.unimi.dsi.fastutil.shorts.ShortArrayList;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.function.IntBinaryOperator;
import java.util.function.UnaryOperator;

/**
 * A chunk seen from afar: its blocks in square areas {@code 2^level} blocks across, each area a column of runs of
 * solid blocks, highest first, as Distant Horizons draws far terrain. A height of an area counts as solid where at least
 * a quarter of its blocks are, so a tree is a canopy on a trunk with air beneath, not a pillar down to the ground. The
 * runs go down to the area's ground and no further; the last of each area is that ground, solid all the way down.
 * Plants and water fill no run, as they are drawn their own way.
 *
 * @param counts  how many runs each area has, the areas row by row from the north west
 * @param bottoms the lowest height of each run, area after area
 * @param tops    the highest height of each run
 * @param blocks  the key of the block most of each run's top shows
 */
record ColumnRuns(short[] counts, short[] bottoms, short[] tops, String[] blocks) {
    static final int MOST_LEVEL = 4;
    private static final int ACROSS = 16;
    private static final int SOLID_SHARE_DIVISOR = 4;

    int areasAcross() {
        return (int) Math.round(Math.sqrt(counts.length));
    }

    /** Where the runs of the area at {@code index} start in {@link #bottoms}. */
    int firstRunOf(int index) {
        int first = 0;
        for (int area = 0; area < index; area++) {
            first += counts[area];
        }
        return first;
    }

    /**
     * @param level         the areas are {@code 2^level} blocks across, 1 to {@link #MOST_LEVEL}
     * @param terrainY      the terrain's top in a column by its x and z in the chunk
     * @param seenByPlayers the state players see in place of a block, which decides whether it is solid
     */
    static ColumnRuns of(Hunk<PlatformBlockState> chunk, int level, IntBinaryOperator terrainY, UnaryOperator<PlatformBlockState> seenByPlayers) {
        int areaBlocks = 1 << level;
        int areasAcross = ACROSS / areaBlocks;
        int fewestSolid = Math.max(1, areaBlocks * areaBlocks / SOLID_SHARE_DIVISOR);
        short[] counts = new short[areasAcross * areasAcross];
        ShortArrayList bottoms = new ShortArrayList();
        ShortArrayList tops = new ShortArrayList();
        List<String> blocks = new ArrayList<>();
        for (int areaZ = 0; areaZ < areasAcross; areaZ++) {
            for (int areaX = 0; areaX < areasAcross; areaX++) {
                Area area = new Area(chunk, areaX * areaBlocks, areaZ * areaBlocks, areaBlocks, seenByPlayers);
                int groundY = area.lowestOf(terrainY);
                int found = area.addRuns(fewestSolid, groundY, bottoms, tops, blocks);
                counts[areaZ * areasAcross + areaX] = (short) found;
            }
        }
        return new ColumnRuns(counts, bottoms.toShortArray(), tops.toShortArray(), blocks.toArray(String[]::new));
    }

    @Override
    public boolean equals(Object other) {
        return other instanceof ColumnRuns runs && Arrays.equals(counts, runs.counts) && Arrays.equals(bottoms, runs.bottoms)
                && Arrays.equals(tops, runs.tops) && Arrays.equals(blocks, runs.blocks);
    }

    @Override
    public int hashCode() {
        return Arrays.hashCode(counts) * 31 + Arrays.hashCode(tops);
    }

    @Override
    public String toString() {
        return "ColumnRuns" + Arrays.toString(counts) + Arrays.toString(bottoms) + Arrays.toString(tops) + Arrays.toString(blocks);
    }

    /** The runs of one area as they are found from the top down. */
    private static final class Runs {
        private static final int NONE = -1;
        private final ShortArrayList bottoms;
        private final ShortArrayList tops;
        private final List<String> blocks;
        private int top = NONE;
        private int bottom;
        private String block;
        private int found;

        Runs(ShortArrayList bottoms, ShortArrayList tops, List<String> blocks) {
            this.bottoms = bottoms;
            this.tops = tops;
            this.blocks = blocks;
        }

        void extendTo(int y, String shown) {
            if (top != NONE && !shown.equals(block)) {
                end(bottom);
            }
            if (top == NONE) {
                top = y;
                block = shown;
            }
            bottom = y;
        }

        void endAbove(int y) {
            if (top != NONE) {
                end(y + 1);
            }
        }

        // The ground reaches down out of sight, so its run has no bottom to draw.
        void endAsGround() {
            if (top != NONE) {
                end(0);
            }
        }

        private void end(int runBottom) {
            bottoms.add((short) runBottom);
            tops.add((short) top);
            blocks.add(block);
            found++;
            top = NONE;
        }
    }

    /** One square area of a chunk, read a height at a time. */
    private record Area(Hunk<PlatformBlockState> chunk, int minX, int minZ, int across, UnaryOperator<PlatformBlockState> seenByPlayers) {
        int lowestOf(IntBinaryOperator terrainY) {
            int lowest = Integer.MAX_VALUE;
            for (int z = minZ; z < minZ + across; z++) {
                for (int x = minX; x < minX + across; x++) {
                    lowest = Math.min(lowest, terrainY.applyAsInt(x, z));
                }
            }
            return lowest;
        }

        // From the top down, a run holds the solid heights in a row that mostly show one block, so a canopy, the trunk
        // under it and the ground are runs of their own. Once a run reaches the ground, it is the ground and the last.
        int addRuns(int fewestSolid, int groundY, ShortArrayList bottoms, ShortArrayList tops, List<String> blocks) {
            Runs runs = new Runs(bottoms, tops, blocks);
            for (int y = chunk.getHeight() - 1; y >= 0; y--) {
                if (solidCountAt(y) < fewestSolid) {
                    runs.endAbove(y);
                    continue;
                }
                runs.extendTo(y, mostShownAt(y));
                if (y <= groundY) {
                    runs.endAsGround();
                    return runs.found;
                }
            }
            runs.endAsGround();
            return runs.found;
        }

        private int solidCountAt(int y) {
            int solid = 0;
            for (int z = minZ; z < minZ + across; z++) {
                for (int x = minX; x < minX + across; x++) {
                    if (isSolid(chunk.get(x, y, z))) {
                        solid++;
                    }
                }
            }
            return solid;
        }

        private String mostShownAt(int y) {
            Map<String, Integer> shown = new HashMap<>();
            for (int z = minZ; z < minZ + across; z++) {
                for (int x = minX; x < minX + across; x++) {
                    PlatformBlockState block = chunk.get(x, y, z);
                    if (isSolid(block)) {
                        shown.merge(block.key(), 1, Integer::sum);
                    }
                }
            }
            return shown.entrySet().stream().max(Map.Entry.comparingByValue()).map(Map.Entry::getKey).orElseThrow();
        }

        private boolean isSolid(PlatformBlockState block) {
            if (block == null || block.isAir()) {
                return false;
            }
            PlatformBlockState seen = seenByPlayers.apply(block);
            return !seen.isAir() && !seen.isFluid() && seen.isSolid();
        }
    }
}
