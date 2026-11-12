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

import art.arcane.iris.engine.object.terrain.carving.DeclaredByLevel;
import art.arcane.iris.modules.carve.Cave;
import art.arcane.iris.modules.carve.CaveList;
import art.arcane.iris.modules.carve.CaveResources;
import art.arcane.iris.modules.carve.field.CaveCarver;
import art.arcane.iris.modules.carve.field.ChunkLattice;
import art.arcane.volmlib.util.math.PowerOfTwoCoordinates;
import com.volmit.ModuleLog;
import com.volmit.mantle.CarvedVoid;
import com.volmit.mantle.MantleLayer;
import com.volmit.resource.PackResources;
import com.volmit.resource.ResourceId;
import com.volmit.world.BiomeSample;
import com.volmit.world.PackWorld;
import com.volmit.world.TerrainSampler;

import java.util.Arrays;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.TreeMap;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Plans one chunk's caves into the mantle for {@link art.arcane.iris.engine.modifier.CarveModule}: every cave the
 * dimension, the regions and the biomes around the chunk list under {@code caves}. The lists of a column add up, and a
 * cave narrows to nothing over a few blocks where the columns that list it end. One per world, keeping a carver per cave.
 */
public class CaveCarving {
    private static final int CHUNK_SIZE = 16;
    private static final int CHUNK_AREA = CHUNK_SIZE * CHUNK_SIZE;
    private static final int WATER_FIELD_PADDING = 3;
    private static final int WATER_FIELD_SIZE = CHUNK_SIZE + (WATER_FIELD_PADDING * 2);
    /** A column's cave biome is the one the world places this far below its surface (at least at height 1). */
    private static final int CAVE_SAMPLE_DEPTH_BELOW_SURFACE_BLOCKS = 56;
    /** Which caves a column lists is read every fourth block, one ring beyond the chunk, and smoothed between the readings. */
    private static final int LISTING_STEP_BLOCKS = ChunkLattice.CELL_BLOCKS;
    private static final int LISTINGS_PER_AXIS = ChunkLattice.CELLS_PER_CHUNK_AXIS + 3;
    private static final int LISTINGS = LISTINGS_PER_AXIS * LISTINGS_PER_AXIS;
    private static final int WEIGHTS_PER_AXIS = ChunkLattice.CELLS_PER_CHUNK_AXIS + 1;
    private static final int LISTINGS_PER_WEIGHT = 9;
    /** Loose rock is swept this far below the surface where a cave opens to it. */
    private static final int LOOSE_ROCK_SWEEP_DEPTH_BLOCKS = 16;
    private static final double[] LISTED_EVERYWHERE = filled(1D);
    private static final ThreadLocal<Scratch> SCRATCH = ThreadLocal.withInitial(Scratch::new);

    private final CarveWorld world;
    private final PackResources resources;
    private final DeclaredByLevel<CaveList> lists;
    private final FluidColumns fluidColumns;
    private final ModuleLog log;
    private final Map<ResourceId, Optional<ListedCave>> caves = new ConcurrentHashMap<>();
    private final Map<ListingKey, List<ResourceId>> listings = new ConcurrentHashMap<>();
    /** What every column lists when only the dimension lists caves, which spares reading the biomes around each chunk. */
    private final Map<ResourceId, double[]> dimensionCaves;

    /** Whether the dimension's fluid palette gives a column a fluid, which caves do not cut into. */
    @FunctionalInterface
    public interface FluidColumns {
        boolean hasFluid(int worldX, int worldZ);
    }

    public CaveCarving(PackWorld packWorld, CarveWorld world, FluidColumns fluidColumns, ModuleLog log) {
        this.world = world;
        this.resources = packWorld.resources();
        this.lists = DeclaredByLevel.of(packWorld, CaveList.class);
        this.fluidColumns = fluidColumns;
        this.log = log;
        this.dimensionCaves = new TreeMap<>();
        Set<ResourceId> listedByDimension = new LinkedHashSet<>();
        addListed(listedByDimension, lists.ofDimension());
        for (ResourceId cave : listedByDimension) {
            dimensionCaves.put(cave, LISTED_EVERYWHERE);
        }
    }

    /** Carves the caves of the chunk at chunk coordinates {@code x}, {@code z} into {@code voids}, reading the terrain around it from {@code terrain}. */
    public void carve(MantleLayer<CarvedVoid> voids, TerrainSampler terrain, int x, int z) {
        Scratch scratch = SCRATCH.get();
        int originX = PowerOfTwoCoordinates.chunkToBlock(x);
        int originZ = PowerOfTwoCoordinates.chunkToBlock(z);
        Map<ResourceId, double[]> listed = listedCaves(terrain, originX, originZ, scratch);
        if (listed.isEmpty()) {
            return;
        }
        readSurface(terrain, originX, originZ, scratch);
        boolean opensToSurface = false;
        boolean carved = false;
        for (Map.Entry<ResourceId, double[]> entry : listed.entrySet()) {
            ListedCave cave = caveOf(entry.getKey());
            if (cave == null) {
                continue;
            }
            opensToSurface |= cave.carver.opensToSurface();
            carved |= carveCave(cave, entry.getValue(), voids, originX, originZ, scratch);
        }
        if (carved) {
            CarveOrphanSweep.sweepChunk(voids, originX, originZ, scratch.surfaceY,
                    opensToSurface ? LOOSE_ROCK_SWEEP_DEPTH_BLOCKS : 0, world.height() - 1, scratch.waterStartY, world.fluidHeight());
        }
    }

    private boolean carveCave(ListedCave cave, double[] columnWeight, MantleLayer<CarvedVoid> voids, int originX, int originZ, Scratch scratch) {
        int[] waterStartY = scratch.waterStartY;
        int fluidHeight = world.fluidHeight();
        boolean[] carved = scratch.carvedAnything;
        carved[0] = false;
        cave.carver.carve(originX, originZ, scratch.surfaceY, columnWeight, scratch.underWater, (localX, y, localZ) -> {
            if (SurfaceFluidBoundaryPlan.protects(waterStartY, PowerOfTwoCoordinates.packLocal16(localX, localZ), y, fluidHeight)) {
                return;
            }
            voids.set(originX + localX, y, originZ + localZ, cave.carvedVoid);
            carved[0] = true;
        });
        return carved[0];
    }

    /** The surface of the chunk's columns, and which of them lie under or next to water, where no cave may open the surface. */
    private void readSurface(TerrainSampler terrain, int originX, int originZ, Scratch scratch) {
        for (int localX = 0; localX < CHUNK_SIZE; localX++) {
            for (int localZ = 0; localZ < CHUNK_SIZE; localZ++) {
                scratch.surfaceY[PowerOfTwoCoordinates.packLocal16(localX, localZ)] = terrain.surfaceHeight(originX + localX, originZ + localZ);
            }
        }
        for (int fieldX = 0; fieldX < WATER_FIELD_SIZE; fieldX++) {
            int worldX = originX - WATER_FIELD_PADDING + fieldX;
            for (int fieldZ = 0; fieldZ < WATER_FIELD_SIZE; fieldZ++) {
                int worldZ = originZ - WATER_FIELD_PADDING + fieldZ;
                int fieldIndex = (fieldX * WATER_FIELD_SIZE) + fieldZ;
                scratch.fieldSurfaceY[fieldIndex] = terrain.surfaceY(worldX, worldZ);
                scratch.fieldHasFluid[fieldIndex] = fluidColumns.hasFluid(worldX, worldZ);
            }
        }
        SurfaceFluidBoundaryPlan.fill(scratch.surfaceY, scratch.fieldSurfaceY, scratch.fieldHasFluid, WATER_FIELD_SIZE,
                WATER_FIELD_PADDING, world.fluidHeight(), scratch.waterStartY);
        for (int column = 0; column < CHUNK_AREA; column++) {
            scratch.underWater[column] = scratch.waterStartY[column] != SurfaceFluidBoundaryPlan.NO_BOUNDARY;
        }
    }

    /**
     * The caves any column of the chunk lists, in the order of their ids, each with how much every column lists it:
     * 1 well inside the columns that do, 0 well outside, in between across their border.
     */
    private Map<ResourceId, double[]> listedCaves(TerrainSampler terrain, int originX, int originZ, Scratch scratch) {
        if (lists.onlyDimensionDeclares()) {
            return dimensionCaves;
        }
        Map<ResourceId, boolean[]> listedAt = new TreeMap<>();
        for (int listingX = 0; listingX < LISTINGS_PER_AXIS; listingX++) {
            int worldX = originX + ((listingX - 1) * LISTING_STEP_BLOCKS);
            for (int listingZ = 0; listingZ < LISTINGS_PER_AXIS; listingZ++) {
                int worldZ = originZ + ((listingZ - 1) * LISTING_STEP_BLOCKS);
                int listing = (listingX * LISTINGS_PER_AXIS) + listingZ;
                for (ResourceId cave : listingAt(terrain, worldX, worldZ)) {
                    listedAt.computeIfAbsent(cave, absent -> new boolean[LISTINGS])[listing] = true;
                }
            }
        }
        Map<ResourceId, double[]> weights = new TreeMap<>();
        for (Map.Entry<ResourceId, boolean[]> entry : listedAt.entrySet()) {
            weights.put(entry.getKey(), columnWeights(entry.getValue(), scratch));
        }
        return weights;
    }

    private List<ResourceId> listingAt(TerrainSampler terrain, int worldX, int worldZ) {
        int surfaceY = (int) Math.round(terrain.surfaceY(worldX, worldZ));
        BiomeSample sample = terrain.biome(worldX, Math.max(1, surfaceY - CAVE_SAMPLE_DEPTH_BELOW_SURFACE_BLOCKS), worldZ);
        ListingKey key = new ListingKey(sample.region(), sample.surfaceBiome(), sample.caveBiome().orElse(null));
        return listings.computeIfAbsent(key, this::listingOf);
    }

    /** Every cave the dimension, the region, the surface biome and the cave biome of a column list between them. */
    private List<ResourceId> listingOf(ListingKey key) {
        Set<ResourceId> listed = new LinkedHashSet<>();
        addListed(listed, lists.ofDimension());
        addListed(listed, lists.ofRegion(key.region));
        addListed(listed, lists.ofBiome(key.surfaceBiome));
        if (key.caveBiome != null) {
            addListed(listed, lists.ofBiome(key.caveBiome));
        }
        return List.copyOf(listed);
    }

    private void addListed(Set<ResourceId> listed, CaveList list) {
        if (list == null) {
            return;
        }
        for (String key : list.caves()) {
            if (key != null && !key.isBlank()) {
                listed.add(resources.idOf(key));
            }
        }
    }

    private static double[] columnWeights(boolean[] listedAt, Scratch scratch) {
        if (isAllTrue(listedAt)) {
            return LISTED_EVERYWHERE;
        }
        double[] latticeWeight = scratch.latticeWeight;
        for (int weightX = 0; weightX < WEIGHTS_PER_AXIS; weightX++) {
            for (int weightZ = 0; weightZ < WEIGHTS_PER_AXIS; weightZ++) {
                latticeWeight[(weightX * WEIGHTS_PER_AXIS) + weightZ] = listingsAround(listedAt, weightX, weightZ) / (double) LISTINGS_PER_WEIGHT;
            }
        }
        double[] columnWeight = new double[CHUNK_AREA];
        for (int localX = 0; localX < CHUNK_SIZE; localX++) {
            int weightX = localX / LISTING_STEP_BLOCKS;
            double shareX = (localX % LISTING_STEP_BLOCKS) / (double) LISTING_STEP_BLOCKS;
            for (int localZ = 0; localZ < CHUNK_SIZE; localZ++) {
                int weightZ = localZ / LISTING_STEP_BLOCKS;
                double shareZ = (localZ % LISTING_STEP_BLOCKS) / (double) LISTING_STEP_BLOCKS;
                double west = between(latticeWeight[(weightX * WEIGHTS_PER_AXIS) + weightZ], latticeWeight[(weightX * WEIGHTS_PER_AXIS) + weightZ + 1], shareZ);
                double east = between(latticeWeight[((weightX + 1) * WEIGHTS_PER_AXIS) + weightZ], latticeWeight[((weightX + 1) * WEIGHTS_PER_AXIS) + weightZ + 1], shareZ);
                columnWeight[PowerOfTwoCoordinates.packLocal16(localX, localZ)] = between(west, east, shareX);
            }
        }
        return columnWeight;
    }

    /** How many of the nine readings on and around a lattice point list the cave. The point's own reading is one ring in. */
    private static int listingsAround(boolean[] listedAt, int weightX, int weightZ) {
        int count = 0;
        for (int listingX = weightX; listingX <= weightX + 2; listingX++) {
            for (int listingZ = weightZ; listingZ <= weightZ + 2; listingZ++) {
                if (listedAt[(listingX * LISTINGS_PER_AXIS) + listingZ]) {
                    count++;
                }
            }
        }
        return count;
    }

    private static double between(double from, double to, double share) {
        return from + ((to - from) * share);
    }

    private static boolean isAllTrue(boolean[] values) {
        for (boolean value : values) {
            if (!value) {
                return false;
            }
        }
        return true;
    }

    private static double[] filled(double value) {
        double[] values = new double[CHUNK_AREA];
        Arrays.fill(values, value);
        return values;
    }

    /** The cave with this id, ready to carve; null when the pack has no such cave, which is reported once. */
    private ListedCave caveOf(ResourceId id) {
        return caves.computeIfAbsent(id, this::loadCave).orElse(null);
    }

    private Optional<ListedCave> loadCave(ResourceId id) {
        Cave cave = CaveResources.cave(resources, id);
        if (cave == null) {
            log.warnOnce("missing-cave:" + id, "Cave '" + id.path() + "' is listed under caves but the pack has no readable caves/" + id.path() + ".json");
            return Optional.empty();
        }
        CarvedVoid carvedVoid = cave.biome().isBlank() ? CarvedVoid.OPEN : CarvedVoid.OPEN.withBiome(resources.idOf(cave.biome()));
        long seed = world.carveSeed() + (31L * id.toString().hashCode());
        return Optional.of(new ListedCave(new CaveCarver(cave, world.noise(), seed, world.minHeight(), world.height()), carvedVoid));
    }

    private record ListedCave(CaveCarver carver, CarvedVoid carvedVoid) {
    }

    private record ListingKey(ResourceId region, ResourceId surfaceBiome, ResourceId caveBiome) {
    }

    private static final class Scratch {
        private final int[] surfaceY = new int[CHUNK_AREA];
        private final int[] waterStartY = new int[CHUNK_AREA];
        private final boolean[] underWater = new boolean[CHUNK_AREA];
        private final double[] fieldSurfaceY = new double[WATER_FIELD_SIZE * WATER_FIELD_SIZE];
        private final boolean[] fieldHasFluid = new boolean[WATER_FIELD_SIZE * WATER_FIELD_SIZE];
        private final double[] latticeWeight = new double[WEIGHTS_PER_AXIS * WEIGHTS_PER_AXIS];
        private final boolean[] carvedAnything = new boolean[1];
    }
}
