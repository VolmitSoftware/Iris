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

package art.arcane.iris.engine.mantle.components;

import art.arcane.volmlib.util.mantle.runtime.MantleChunk;
import art.arcane.volmlib.util.math.PowerOfTwoCoordinates;
import art.arcane.volmlib.util.matter.Matter;
import art.arcane.volmlib.util.matter.MatterCavern;
import art.arcane.volmlib.util.matter.MatterSlice;

import java.util.Arrays;

public final class CarveOrphanSweep {
    private static final int CHUNK_SIZE = 16;
    private static final int CHUNK_AREA = CHUNK_SIZE * CHUNK_SIZE;
    private static final int BAND_FLOOR_MARGIN = 4;
    private static final int MAX_ORPHAN_CELLS = 16;
    private static final MatterCavern ORPHAN_CAVERN = new MatterCavern(true, "", (byte) 0);
    private static final ThreadLocal<SweepScratch> SCRATCH = ThreadLocal.withInitial(SweepScratch::new);

    public interface CarveAccess {
        boolean isCarved(int localX, int y, int localZ);

        void markCarved(int localX, int y, int localZ);

        default boolean isProtected(int localX, int y, int localZ) {
            return false;
        }

        /**
         * Cheap pre-check: may any block in [minY, maxY] be carved at all? Default true keeps
         * every implementor correct; the mantle-backed access answers from section slice
         * presence so an uncarved chunk skips the full band fill + scan.
         */
        default boolean mayContainCarvedCells(int minY, int maxY) {
            return true;
        }
    }

    private CarveOrphanSweep() {
    }

    static int sweepChunk(
            MantleChunk<Matter> chunk,
            int[] surfaceHeights,
            int maxSurfaceBreakDepth,
            int worldCeilingY,
            int[] surfaceFluidBoundaryStartY,
            int fluidHeight
    ) {
        if (chunk == null) {
            return 0;
        }

        return sweep(surfaceHeights, maxSurfaceBreakDepth, 0, worldCeilingY,
                new MantleCarveAccess(chunk, surfaceFluidBoundaryStartY, fluidHeight));
    }

    public static int sweep(int[] surfaceHeights, int maxSurfaceBreakDepth, int worldFloorY, int worldCeilingY, CarveAccess access) {
        if (surfaceHeights == null || surfaceHeights.length < CHUNK_AREA || access == null) {
            return 0;
        }

        int minSurfaceY = Integer.MAX_VALUE;
        int maxSurfaceY = Integer.MIN_VALUE;
        for (int columnIndex = 0; columnIndex < CHUNK_AREA; columnIndex++) {
            int surfaceY = surfaceHeights[columnIndex];
            if (surfaceY < minSurfaceY) {
                minSurfaceY = surfaceY;
            }
            if (surfaceY > maxSurfaceY) {
                maxSurfaceY = surfaceY;
            }
        }

        int bandTop = Math.min(worldCeilingY, maxSurfaceY);
        int bandFloor = Math.max(worldFloorY + 1, minSurfaceY - Math.max(0, maxSurfaceBreakDepth) - BAND_FLOOR_MARGIN);
        if (bandTop < bandFloor) {
            return 0;
        }

        if (!access.mayContainCarvedCells(bandFloor, bandTop)) {
            // Same early return the carvedPresent check reaches, without zero-filling and
            // scanning the whole band first.
            return 0;
        }

        int bandHeight = bandTop - bandFloor + 1;
        int cellCount = bandHeight * CHUNK_AREA;
        SweepScratch scratch = SCRATCH.get();
        scratch.prepare(cellCount);
        long[] solid = scratch.solid;
        long[] visited = scratch.visited;
        int[] stack = scratch.stack;
        int[] component = scratch.component;

        boolean carvedPresent = false;
        for (int y = bandFloor; y <= bandTop; y++) {
            int layer = (y - bandFloor) * CHUNK_AREA;
            for (int localX = 0; localX < CHUNK_SIZE; localX++) {
                for (int localZ = 0; localZ < CHUNK_SIZE; localZ++) {
                    int columnIndex = PowerOfTwoCoordinates.packLocal16(localX, localZ);
                    if (y > surfaceHeights[columnIndex]) {
                        continue;
                    }
                    if (access.isCarved(localX, y, localZ)) {
                        carvedPresent = true;
                        continue;
                    }

                    setBit(solid, layer + columnIndex);
                }
            }
        }

        if (!carvedPresent) {
            return 0;
        }

        int marked = 0;
        for (int rootIndex = 0; rootIndex < cellCount; rootIndex++) {
            if (!getBit(solid, rootIndex) || getBit(visited, rootIndex)) {
                continue;
            }

            setBit(visited, rootIndex);
            stack[0] = rootIndex;
            int stackSize = 1;
            int componentSize = 0;
            boolean anchored = false;

            while (stackSize > 0) {
                int current = stack[--stackSize];
                int columnIndex = current & (CHUNK_AREA - 1);
                int localX = columnIndex >>> 4;
                int localZ = columnIndex & (CHUNK_SIZE - 1);
                int y = bandFloor + (current / CHUNK_AREA);

                if (!anchored) {
                    if (access.isProtected(localX, y, localZ)) {
                        anchored = true;
                    } else if (localX == 0 || localX == CHUNK_SIZE - 1 || localZ == 0 || localZ == CHUNK_SIZE - 1) {
                        anchored = true;
                    } else if (y == bandFloor && isSolidBelowBand(access, surfaceHeights, columnIndex, localX, localZ, bandFloor - 1, worldFloorY)) {
                        anchored = true;
                    } else if (componentSize >= MAX_ORPHAN_CELLS) {
                        anchored = true;
                    } else {
                        component[componentSize++] = current;
                    }
                }

                if (y > bandFloor) {
                    stackSize = push(solid, visited, stack, stackSize, current - CHUNK_AREA);
                }
                if (y < bandTop) {
                    stackSize = push(solid, visited, stack, stackSize, current + CHUNK_AREA);
                }
                if (localX > 0) {
                    stackSize = push(solid, visited, stack, stackSize, current - CHUNK_SIZE);
                }
                if (localX < CHUNK_SIZE - 1) {
                    stackSize = push(solid, visited, stack, stackSize, current + CHUNK_SIZE);
                }
                if (localZ > 0) {
                    stackSize = push(solid, visited, stack, stackSize, current - 1);
                }
                if (localZ < CHUNK_SIZE - 1) {
                    stackSize = push(solid, visited, stack, stackSize, current + 1);
                }
            }

            if (anchored) {
                continue;
            }

            for (int index = 0; index < componentSize; index++) {
                int cell = component[index];
                int columnIndex = cell & (CHUNK_AREA - 1);
                access.markCarved(columnIndex >>> 4, bandFloor + (cell / CHUNK_AREA), columnIndex & (CHUNK_SIZE - 1));
                marked++;
            }
        }

        return marked;
    }

    private static int push(long[] solid, long[] visited, int[] stack, int stackSize, int neighbor) {
        if (!getBit(solid, neighbor) || getBit(visited, neighbor)) {
            return stackSize;
        }

        setBit(visited, neighbor);
        stack[stackSize] = neighbor;
        return stackSize + 1;
    }

    private static boolean isSolidBelowBand(CarveAccess access, int[] surfaceHeights, int columnIndex, int localX, int localZ, int belowY, int worldFloorY) {
        if (belowY < worldFloorY || belowY > surfaceHeights[columnIndex]) {
            return false;
        }

        return !access.isCarved(localX, belowY, localZ);
    }

    private static boolean getBit(long[] bits, int index) {
        return (bits[index >>> 6] & (1L << (index & 63))) != 0L;
    }

    private static void setBit(long[] bits, int index) {
        bits[index >>> 6] |= 1L << (index & 63);
    }

    private static final class MantleCarveAccess implements CarveAccess {
        private final MantleChunk<Matter> chunk;
        private final int[] surfaceFluidBoundaryStartY;
        private final int fluidHeight;
        private MatterSlice<MatterCavern> cachedSlice;
        private int cachedSectionIndex = -1;

        private MantleCarveAccess(MantleChunk<Matter> chunk, int[] surfaceFluidBoundaryStartY, int fluidHeight) {
            this.chunk = chunk;
            this.surfaceFluidBoundaryStartY = surfaceFluidBoundaryStartY;
            this.fluidHeight = fluidHeight;
        }

        @Override
        public boolean isCarved(int localX, int y, int localZ) {
            MatterSlice<MatterCavern> cavernSlice = resolveSlice(y >> 4);
            if (cavernSlice == null) {
                return false;
            }

            MatterCavern cavern = cavernSlice.get(localX, y & 15, localZ);
            return cavern != null && cavern.isCavern();
        }

        @Override
        public void markCarved(int localX, int y, int localZ) {
            chunk.getOrCreate(y >> 4).slice(MatterCavern.class).set(localX, y & 15, localZ, ORPHAN_CAVERN);
            cachedSectionIndex = -1;
            cachedSlice = null;
        }

        @Override
        public boolean isProtected(int localX, int y, int localZ) {
            int columnIndex = PowerOfTwoCoordinates.packLocal16(localX, localZ);
            return SurfaceFluidBoundaryPlan.protects(surfaceFluidBoundaryStartY, columnIndex, y, fluidHeight);
        }

        @Override
        public boolean mayContainCarvedCells(int minY, int maxY) {
            int minSection = Math.max(0, minY >> 4);
            int maxSection = Math.min(chunk.sectionCount() - 1, maxY >> 4);
            for (int sectionIndex = minSection; sectionIndex <= maxSection; sectionIndex++) {
                Matter section = chunk.get(sectionIndex);
                if (section != null && section.getSlice(MatterCavern.class) != null) {
                    return true;
                }
            }
            return false;
        }

        private MatterSlice<MatterCavern> resolveSlice(int sectionIndex) {
            if (sectionIndex == cachedSectionIndex) {
                return cachedSlice;
            }

            Matter section = sectionIndex >= 0 && sectionIndex < chunk.sectionCount() ? chunk.get(sectionIndex) : null;
            cachedSlice = section == null ? null : section.getSlice(MatterCavern.class);
            cachedSectionIndex = sectionIndex;
            return cachedSlice;
        }
    }

    private static final class SweepScratch {
        private final int[] component = new int[MAX_ORPHAN_CELLS];
        private long[] solid = new long[0];
        private long[] visited = new long[0];
        private int[] stack = new int[0];

        private void prepare(int cellCount) {
            int words = (cellCount + 63) >>> 6;
            if (solid.length < words) {
                solid = new long[words];
                visited = new long[words];
            } else {
                Arrays.fill(solid, 0, words, 0L);
                Arrays.fill(visited, 0, words, 0L);
            }

            if (stack.length < cellCount) {
                stack = new int[cellCount];
            }
        }
    }
}
