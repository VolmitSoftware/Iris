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

package art.arcane.iris.engine.mantle.components;

import art.arcane.volmlib.util.math.PowerOfTwoCoordinates;
import com.volmit.geometry.BlockBox;
import com.volmit.geometry.BlockPosition;
import com.volmit.mantle.CarvedVoid;
import com.volmit.mantle.MantleLayer;
import com.volmit.placement.PlacedMarker;
import com.volmit.placement.PlacementTerrain;
import com.volmit.world.TerrainSampler;

import java.util.BitSet;

/**
 * Marks the floors of the caves {@link CaveCarving} planned in a chunk, which the host's cave spawning reads: the lowest
 * block of each vertical run of at least three carved blocks, one floor in sixteen. The runs are those the chunk stage
 * cuts, minus the blocks it leaves alone: under a dimension stacked above that carving does not go through, and open voids
 * the terrain holds fluid in. The roll is seed-derived and keyed by position alone, never random, because the markers
 * persist in the mantle: the same seed marks the same floors on every run.
 */
public final class CaveFloorMarking {
    static final String CAVE_FLOOR_MARKER = "cave_floor";
    private static final int CHUNK_SIZE = 16;
    private static final int CHUNK_AREA = CHUNK_SIZE * CHUNK_SIZE;
    private static final int MIN_RUN_LENGTH = 3;
    private static final long FLOOR_MARKER_SALT = 0xC2B2AE3D27D4EB4FL;
    private static final long ROLL_MASK = 15L;
    private static final ThreadLocal<BitSet[]> COLUMNS = ThreadLocal.withInitial(() -> {
        BitSet[] columns = new BitSet[CHUNK_AREA];
        for (int index = 0; index < CHUNK_AREA; index++) {
            columns[index] = new BitSet();
        }
        return columns;
    });

    private final CarveWorld world;
    private final boolean upperDimensionCarving;

    public CaveFloorMarking(CarveWorld world, boolean upperDimensionCarving) {
        this.world = world;
        this.upperDimensionCarving = upperDimensionCarving;
    }

    /** Marks the floors of the chunk at chunk coordinates {@code chunkX}, {@code chunkZ} in {@code placement}. */
    public void mark(MantleLayer<CarvedVoid> voids, TerrainSampler terrain, PlacementTerrain placement, int chunkX, int chunkZ) {
        int originX = PowerOfTwoCoordinates.chunkToBlock(chunkX);
        int originZ = PowerOfTwoCoordinates.chunkToBlock(chunkZ);
        BitSet[] carvedColumns = COLUMNS.get();
        fillCarvedColumns(carvedColumns, voids, terrain, originX, originZ);
        for (int columnIndex = 0; columnIndex < CHUNK_AREA; columnIndex++) {
            markColumn(carvedColumns[columnIndex], placement, originX + PowerOfTwoCoordinates.unpackLocal16X(columnIndex),
                    originZ + (columnIndex & (CHUNK_SIZE - 1)));
        }
    }

    private void fillCarvedColumns(BitSet[] carvedColumns, MantleLayer<CarvedVoid> voids, TerrainSampler terrain, int originX, int originZ) {
        int[] surfaceHeights = new int[CHUNK_AREA];
        int[] upperSurfaceHeights = upperDimensionCarving || terrain.upperDimensionY(originX, originZ).isEmpty() ? null : new int[CHUNK_AREA];
        for (int columnIndex = 0; columnIndex < CHUNK_AREA; columnIndex++) {
            carvedColumns[columnIndex].clear();
            int worldX = originX + PowerOfTwoCoordinates.unpackLocal16X(columnIndex);
            int worldZ = originZ + (columnIndex & (CHUNK_SIZE - 1));
            surfaceHeights[columnIndex] = terrain.surfaceHeight(worldX, worldZ);
            if (upperSurfaceHeights != null) {
                upperSurfaceHeights[columnIndex] = terrain.upperDimensionY(worldX, worldZ).getAsInt();
            }
        }

        int fluidHeight = terrain.fluidHeight();
        voids.visit(new BlockBox(originX, 0, originZ, originX + CHUNK_SIZE, world.height(), originZ + CHUNK_SIZE), (x, y, z, carvedVoid) -> {
            int columnIndex = PowerOfTwoCoordinates.packLocal16(x - originX, z - originZ);
            if (y <= 0 || upperSurfaceHeights != null && y >= upperSurfaceHeights[columnIndex]) {
                return;
            }
            boolean terrainHoldsFluid = y > surfaceHeights[columnIndex] && y <= fluidHeight;
            if (carvedVoid.fill() == CarvedVoid.Fill.OPEN && terrainHoldsFluid) {
                return;
            }
            carvedColumns[columnIndex].set(y);
        });
    }

    private void markColumn(BitSet carvedColumn, PlacementTerrain placement, int worldX, int worldZ) {
        int floor = carvedColumn.nextSetBit(0);
        while (floor >= 0) {
            int ceiling = carvedColumn.nextClearBit(floor) - 1;
            if (ceiling - floor + 1 >= MIN_RUN_LENGTH && floorRoll(worldX, floor, worldZ)) {
                placement.mark(new PlacedMarker.PackMarker(new BlockPosition(worldX, floor, worldZ), CAVE_FLOOR_MARKER));
            }
            floor = carvedColumn.nextSetBit(ceiling + 1);
        }
    }

    /** One in sixteen, from a SplitMix64 finalizer over the carve seed, the position and a salt. */
    private boolean floorRoll(int x, int y, int z) {
        long hash = (world.carveSeed() + FLOOR_MARKER_SALT) ^ art.arcane.volmlib.util.math.BlockPosition.toLong(x, y, z);
        hash = (hash ^ (hash >>> 30)) * 0xBF58476D1CE4E5B9L;
        hash = (hash ^ (hash >>> 27)) * 0x94D049BB133111EBL;
        hash ^= hash >>> 31;
        return (hash & ROLL_MASK) == 0L;
    }
}
