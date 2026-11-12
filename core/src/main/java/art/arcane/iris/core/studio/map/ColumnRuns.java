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
 * Plants and water fill no run: plants count toward the look of the run they stand on, and water is drawn its own way.
 *
 * @param counts      how many runs each area has, the areas row by row from the north west
 * @param bottoms     the lowest height of each run, area after area
 * @param tops        the highest height of each run
 * @param blocks      the key of the block most of each run's top shows
 * @param covers      the key of the plant most often standing on each run's top, or null where none does
 * @param coverShares how much of each run's top its plants stand on, 0 for none to {@link #WHOLE_SHARE} for all of it
 * @param lights      the sky's light on each run's top, the mean of its blocks' tops in {@link #LIGHT_STEPS} of a level
 * @param occlusions  how many opaque cells stand around each corner of each run's top, the mean of its blocks' tops in
 *                    {@link #OCCLUSION_STEPS} of a cell, so far ground is as dark as the near ground it stands for
 */
record ColumnRuns(short[] counts, short[] bottoms, short[] tops, String[] blocks, String[] covers, byte[] coverShares, byte[] lights,
                  byte[] occlusions) {
    static final int MOST_LEVEL = 4;
    static final int WHOLE_SHARE = 255;
    static final int LIGHT_STEPS = 16;
    static final int OCCLUSION_STEPS = 64;
    private static final int CORNERS = 4;
    private static final int ACROSS = 16;
    private static final int SOLID_SHARE_DIVISOR = 4;

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
     * @param cells         the chunk's cells with their light, which shade the runs' tops
     */
    static ColumnRuns of(Hunk<PlatformBlockState> chunk, int level, IntBinaryOperator terrainY, UnaryOperator<PlatformBlockState> seenByPlayers,
                         ChunkCells cells) {
        int areaBlocks = 1 << level;
        int areasAcross = ACROSS / areaBlocks;
        int fewestSolid = Math.max(1, areaBlocks * areaBlocks / SOLID_SHARE_DIVISOR);
        short[] counts = new short[areasAcross * areasAcross];
        Runs runs = new Runs();
        for (int areaZ = 0; areaZ < areasAcross; areaZ++) {
            for (int areaX = 0; areaX < areasAcross; areaX++) {
                Area area = new Area(chunk, areaX * areaBlocks, areaZ * areaBlocks, areaBlocks, seenByPlayers, cells);
                int before = runs.found;
                area.addRuns(fewestSolid, area.lowestOf(terrainY), runs);
                counts[areaZ * areasAcross + areaX] = (short) (runs.found - before);
            }
        }
        return runs.toColumnRuns(counts);
    }

    @Override
    public boolean equals(Object other) {
        return other instanceof ColumnRuns runs && Arrays.equals(counts, runs.counts) && Arrays.equals(bottoms, runs.bottoms)
                && Arrays.equals(tops, runs.tops) && Arrays.equals(blocks, runs.blocks) && Arrays.equals(covers, runs.covers)
                && Arrays.equals(coverShares, runs.coverShares) && Arrays.equals(lights, runs.lights) && Arrays.equals(occlusions, runs.occlusions);
    }

    @Override
    public int hashCode() {
        return Arrays.hashCode(counts) * 31 + Arrays.hashCode(tops);
    }

    @Override
    public String toString() {
        return "ColumnRuns" + Arrays.toString(counts) + Arrays.toString(bottoms) + Arrays.toString(tops) + Arrays.toString(blocks)
                + Arrays.toString(covers) + Arrays.toString(coverShares);
    }

    /** The plant most often standing on a run's top, and how much of the top plants stand on. */
    private record Cover(String block, int share) {
        static final Cover NONE = new Cover(null, 0);
    }

    /** How a run's top is lit, in {@link #LIGHT_STEPS} and {@link #OCCLUSION_STEPS}. */
    private record Shade(int light, int occlusion) {
    }

    /** The runs of every area as they are found, area after area and each area's from the top down. */
    private static final class Runs {
        private static final int NONE = -1;
        private final ShortArrayList bottoms = new ShortArrayList();
        private final ShortArrayList tops = new ShortArrayList();
        private final List<String> blocks = new ArrayList<>();
        private final List<String> covers = new ArrayList<>();
        private final ByteArrayList coverShares = new ByteArrayList();
        private final ByteArrayList lights = new ByteArrayList();
        private final ByteArrayList occlusions = new ByteArrayList();
        private int top = NONE;
        private int bottom;
        private String block;
        private Cover cover;
        private Shade shade;
        private int found;

        boolean isOpen() {
            return top != NONE;
        }

        boolean continues(String shown) {
            return isOpen() && shown.equals(block);
        }

        void start(int y, String shown, Cover onTop, Shade topShade) {
            top = y;
            bottom = y;
            block = shown;
            cover = onTop;
            shade = topShade;
        }

        void extendTo(int y) {
            bottom = y;
        }

        void end() {
            if (isOpen()) {
                add(bottom);
            }
        }

        // The ground reaches down out of sight, so its run has no bottom to draw.
        void endAsGround() {
            if (isOpen()) {
                add(0);
            }
        }

        private void add(int runBottom) {
            bottoms.add((short) runBottom);
            tops.add((short) top);
            blocks.add(block);
            covers.add(cover.block());
            coverShares.add((byte) cover.share());
            lights.add((byte) shade.light());
            occlusions.add((byte) shade.occlusion());
            found++;
            top = NONE;
        }

        ColumnRuns toColumnRuns(short[] counts) {
            return new ColumnRuns(counts, bottoms.toShortArray(), tops.toShortArray(), blocks.toArray(String[]::new),
                    covers.toArray(String[]::new), coverShares.toByteArray(), lights.toByteArray(), occlusions.toByteArray());
        }
    }

    /** One square area of a chunk, read a height at a time. */
    private record Area(Hunk<PlatformBlockState> chunk, int minX, int minZ, int across, UnaryOperator<PlatformBlockState> seenByPlayers,
                        ChunkCells cells) {
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
        void addRuns(int fewestSolid, int groundY, Runs runs) {
            for (int y = chunk.getHeight() - 1; y >= 0; y--) {
                if (solidCountAt(y) < fewestSolid) {
                    runs.end();
                    continue;
                }
                String shown = mostShownAt(y);
                if (runs.continues(shown)) {
                    runs.extendTo(y);
                } else {
                    runs.end();
                    runs.start(y, shown, coverOn(y), shadeOfTopsAt(y));
                }
                if (y <= groundY) {
                    runs.endAsGround();
                    return;
                }
            }
            runs.endAsGround();
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
            return mostOf(shown);
        }

        // The plants standing on the area's blocks at the height, out of all the area's columns.
        private Cover coverOn(int y) {
            if (y + 1 >= chunk.getHeight()) {
                return Cover.NONE;
            }
            Map<String, Integer> plants = new HashMap<>();
            int standing = 0;
            for (int z = minZ; z < minZ + across; z++) {
                for (int x = minX; x < minX + across; x++) {
                    PlatformBlockState above = chunk.get(x, y + 1, z);
                    if (isSolid(chunk.get(x, y, z)) && isPlant(above)) {
                        plants.merge(above.key(), 1, Integer::sum);
                        standing++;
                    }
                }
            }
            return standing == 0 ? Cover.NONE : new Cover(mostOf(plants), standing * WHOLE_SHARE / (across * across));
        }

        // The mean shade of the tops of the area's blocks at the height that are open above, as the near ground shows
        // them; where none is, the light above the area, unshaded.
        private Shade shadeOfTopsAt(int y) {
            int light = 0;
            int occlusion = 0;
            int tops = 0;
            for (int z = minZ; z < minZ + across; z++) {
                for (int x = minX; x < minX + across; x++) {
                    if (isSolid(chunk.get(x, y, z)) && (y + 1 >= chunk.getHeight() || cells.isOpen(x, y + 1, z))) {
                        short shade = cells.topShade(x, y, z);
                        light += ChunkCells.lightOf(shade);
                        occlusion += ChunkCells.opaqueAroundCorners(shade);
                        tops++;
                    }
                }
            }
            if (tops == 0) {
                return new Shade(lightAbove(y) * LIGHT_STEPS, 0);
            }
            return new Shade(Math.round((float) light * LIGHT_STEPS / tops), Math.round((float) occlusion * OCCLUSION_STEPS / (tops * CORNERS)));
        }

        private int lightAbove(int y) {
            int brightest = 0;
            for (int z = minZ; z < minZ + across; z++) {
                for (int x = minX; x < minX + across; x++) {
                    brightest = Math.max(brightest, cells.lightAt(x, y + 1, z));
                }
            }
            return brightest;
        }

        private static String mostOf(Map<String, Integer> counts) {
            return counts.entrySet().stream().max(Map.Entry.comparingByValue()).map(Map.Entry::getKey).orElseThrow();
        }

        private boolean isSolid(PlatformBlockState block) {
            if (block == null || block.isAir()) {
                return false;
            }
            PlatformBlockState seen = seenByPlayers.apply(block);
            return !seen.isAir() && !seen.isFluid() && seen.isSolid();
        }

        private boolean isPlant(PlatformBlockState block) {
            if (block == null || block.isAir()) {
                return false;
            }
            PlatformBlockState seen = seenByPlayers.apply(block);
            return !seen.isAir() && !seen.isFluid() && !seen.isSolid();
        }
    }
}
