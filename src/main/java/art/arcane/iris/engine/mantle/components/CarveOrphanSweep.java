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

import art.arcane.volmlib.util.math.PowerOfTwoCoordinates;
import com.volmit.mantle.CarvedVoid;
import com.volmit.mantle.MantleLayer;

import java.util.Arrays;

public final class CarveOrphanSweep {
    private static final int CHUNK_SIZE = 16;
    private static final int CHUNK_AREA = CHUNK_SIZE * CHUNK_SIZE;
    private static final int BAND_FLOOR_MARGIN = 4;
    private static final int MAX_ORPHAN_CELLS = 16;
    private static final ThreadLocal<SweepScratch> SCRATCH = ThreadLocal.withInitial(SweepScratch::new);

    public interface CarveAccess {
        boolean isCarved(int localX, int y, int localZ);

        void markCarved(int localX, int y, int localZ);

        default boolean isProtected(int localX, int y, int localZ) {
            return false;
        }
    }

    private CarveOrphanSweep() {
    }

    /** Sweeps the chunk whose first block is {@code originX}, {@code originZ}. */
    static int sweepChunk(
            MantleLayer<CarvedVoid> voids,
            int originX,
            int originZ,
            int[] surfaceHeights,
            int maxSurfaceBreakDepth,
            int worldCeilingY,
            int[] surfaceFluidBoundaryStartY,
            int fluidHeight
    ) {
        return sweep(surfaceHeights, maxSurfaceBreakDepth, 0, worldCeilingY,
                new MantleCarveAccess(voids, originX, originZ, surfaceFluidBoundaryStartY, fluidHeight));
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
        private final MantleLayer<CarvedVoid> voids;
        private final int originX;
        private final int originZ;
        private final int[] surfaceFluidBoundaryStartY;
        private final int fluidHeight;

        private MantleCarveAccess(MantleLayer<CarvedVoid> voids, int originX, int originZ, int[] surfaceFluidBoundaryStartY, int fluidHeight) {
            this.voids = voids;
            this.originX = originX;
            this.originZ = originZ;
            this.surfaceFluidBoundaryStartY = surfaceFluidBoundaryStartY;
            this.fluidHeight = fluidHeight;
        }

        @Override
        public boolean isCarved(int localX, int y, int localZ) {
            return voids.get(originX + localX, y, originZ + localZ) != null;
        }

        @Override
        public void markCarved(int localX, int y, int localZ) {
            voids.set(originX + localX, y, originZ + localZ, CarvedVoid.OPEN);
        }

        @Override
        public boolean isProtected(int localX, int y, int localZ) {
            int columnIndex = PowerOfTwoCoordinates.packLocal16(localX, localZ);
            return SurfaceFluidBoundaryPlan.protects(surfaceFluidBoundaryStartY, columnIndex, y, fluidHeight);
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
