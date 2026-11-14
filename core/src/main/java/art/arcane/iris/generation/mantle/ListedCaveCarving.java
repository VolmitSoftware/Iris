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

package art.arcane.iris.generation.mantle;

import art.arcane.iris.generation.biome.IrisBiome;
import art.arcane.iris.generation.cave.IrisCave;
import art.arcane.iris.generation.cave.field.CaveCarver.ChunkColumns;
import art.arcane.iris.generation.cave.field.ChunkLattice;
import art.arcane.iris.generation.cave.field.DepthRange;
import art.arcane.iris.generation.cave.field.HeightBand;
import art.arcane.iris.generation.cave.field.NoiseFactory;
import art.arcane.iris.generation.mantle.CaveListing.ListedColumns;
import art.arcane.iris.generation.mantle.CaveTerrain.CaveBand;
import art.arcane.iris.generation.mantle.CaveTerrain.CavePlace;
import art.arcane.volmlib.util.collection.KList;
import art.arcane.volmlib.util.math.PowerOfTwoCoordinates;
import art.arcane.volmlib.util.matter.MatterCavern;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.Function;
import java.util.function.Predicate;

/**
 * Plans one chunk's caves for {@link MantleCarvingComponent}: every cave the dimension, the regions and the biomes around
 * the chunk list under {@code caves}, and the caves of the cave biomes that carving bands put at some heights. The lists
 * of a column add up, and a cave narrows to nothing over a few blocks where the columns that list it end. One per
 * world, keeping a carver per cave.
 *
 * <p>A block that several caves carve takes its fluid and biome from the first of them in list order: the dimension's
 * caves, then the region's, the surface biome's and the cave biome's, then the carving bands'. Caves are carved last to
 * first, so the first listed writes last.
 */
final class ListedCaveCarving {
    private static final int CHUNK_SIZE = 16;
    private static final int CHUNK_AREA = CHUNK_SIZE * CHUNK_SIZE;
    /** Which caves a column lists is read every fourth block, one ring beyond the chunk, and smoothed between the readings. */
    private static final int LISTING_STEP_BLOCKS = ChunkLattice.CELL_BLOCKS;
    private static final int LISTINGS_PER_AXIS = ChunkLattice.CELLS_PER_CHUNK_AXIS + 3;
    private static final int LISTINGS = LISTINGS_PER_AXIS * LISTINGS_PER_AXIS;
    private static final int WEIGHTS_PER_AXIS = ChunkLattice.CELLS_PER_CHUNK_AXIS + 1;
    private static final int LISTINGS_PER_WEIGHT = 9;
    private static final double[] LISTED_EVERYWHERE = filled(1D);

    /** What listed cave carving reports to the pack's author. */
    interface Problems {
        void missingCave(String caveKey);

        void missingCaveBiome(String caveKey, String biomeKey);
    }

    private final List<String> dimensionCaves;
    private final boolean onlyDimensionLists;
    private final CaveWorld world;
    private final NoiseFactory noise;
    private final Function<String, IrisCave> caveOf;
    private final Predicate<String> biomeExists;
    private final Problems problems;
    private final Map<String, Optional<ListedCave>> caves = new ConcurrentHashMap<>();

    /**
     * @param dimensionCaves     the caves the dimension lists
     * @param onlyDimensionLists whether no region or biome lists a cave, which spares reading the biomes around each chunk
     * @param caveOf             the cave a key names in the pack, or null
     * @param biomeExists        whether the pack has the biome a key names
     */
    ListedCaveCarving(List<String> dimensionCaves, boolean onlyDimensionLists, CaveWorld world, NoiseFactory noise,
                      Function<String, IrisCave> caveOf, Predicate<String> biomeExists, Problems problems) {
        this.dimensionCaves = List.copyOf(dimensionCaves);
        this.onlyDimensionLists = onlyDimensionLists;
        this.world = world;
        this.noise = noise;
        this.caveOf = caveOf;
        this.biomeExists = biomeExists;
        this.problems = problems;
    }

    /** The caves that carve in the chunk at chunk coordinates {@code chunkX}, {@code chunkZ}, with how much each column lists them. */
    CaveListing list(CaveTerrain terrain, int chunkX, int chunkZ) {
        int originX = PowerOfTwoCoordinates.chunkToBlock(chunkX);
        int originZ = PowerOfTwoCoordinates.chunkToBlock(chunkZ);
        List<ListedColumns> listed = new ArrayList<>();
        boolean opensToSurface = false;
        for (Map.Entry<String, double[]> entry : columnCaves(terrain, originX, originZ).entrySet()) {
            ListedCave cave = caveOf(entry.getKey());
            if (cave == null) {
                continue;
            }
            opensToSurface |= cave.carver().opensToSurface();
            listed.add(new ListedColumns(cave, entry.getValue(), HeightBand.UNLIMITED));
        }
        for (ListedColumns inBand : bandCaves(terrain.bandsOf(chunkX, chunkZ))) {
            opensToSurface |= inBand.cave().carver().opensToSurface();
            listed.add(inBand);
        }
        return new CaveListing(List.copyOf(listed), opensToSurface);
    }

    /** Carves the listed caves of the chunk into {@code sink}, under the surface and the fluids and ceilings that surround it. */
    void carve(CaveListing listing, int chunkX, int chunkZ, ChunkSurface surface, CaveCellSink sink, CaveFluidSupportPlan fluidSupport) {
        boolean[] sealed = sealedColumns(surface.fluidBoundaries());
        for (int index = listing.caves().size() - 1; index >= 0; index--) {
            ListedColumns listed = listing.caves().get(index);
            ChunkColumns columns = new ChunkColumns(surface.surfaceY(), listed.weight(), sealed, surface.ceilings(), DepthRange.OWN);
            carveCave(listed.cave(), columns, listed.band(), surface, chunkX, chunkZ, sink, fluidSupport);
        }
    }

    /**
     * Carves the listed caves into the terrain of an upper dimension, which hangs from the top of the world down to
     * {@code lowestUpperY}: through all heights of the dimension instead of the ones each cave writes, closed under the
     * top of the world.
     */
    void carveBeneathUpperTerrain(CaveListing listing, int chunkX, int chunkZ, int lowestUpperY, CaveCellSink sink, CaveFluidSupportPlan fluidSupport) {
        int[] worldTop = new int[CHUNK_AREA];
        Arrays.fill(worldTop, world.height() - 1);
        boolean[] sealed = new boolean[CHUNK_AREA];
        Arrays.fill(sealed, true);
        ChunkSurface surface = new ChunkSurface(worldTop, null, null);
        for (int index = listing.caves().size() - 1; index >= 0; index--) {
            ListedColumns listed = listing.caves().get(index);
            HeightBand band = new HeightBand(Math.max(listed.band().lowestY(), lowestUpperY), Math.min(listed.band().highestY(), world.height() - 1));
            if (band.lowestY() > band.highestY()) {
                continue;
            }
            ChunkColumns columns = new ChunkColumns(worldTop, listed.weight(), sealed, null, DepthRange.WHOLE_DIMENSION);
            carveCave(listed.cave(), columns, band, surface, chunkX, chunkZ, sink, fluidSupport);
        }
    }

    private void carveCave(ListedCave cave, ChunkColumns columns, HeightBand band, ChunkSurface surface, int chunkX, int chunkZ,
                           CaveCellSink sink, CaveFluidSupportPlan fluidSupport) {
        int originX = PowerOfTwoCoordinates.chunkToBlock(chunkX);
        int originZ = PowerOfTwoCoordinates.chunkToBlock(chunkZ);
        int[] fluidTopY = new int[CHUNK_AREA];
        cave.fillFluidTops(surface.surfaceY(), fluidTopY);
        MatterCavern fluid = cave.fluid();
        cave.carver().carve(originX, originZ, columns, band, (localX, y, localZ) -> {
            int column = PowerOfTwoCoordinates.packLocal16(localX, localZ);
            if (SurfaceFluidBoundaryPlan.protects(surface.fluidBoundaries(), column, y)) {
                return;
            }
            MatterCavern cell = cave.cellAt(originX + localX, y, originZ + localZ, fluidTopY[column]);
            sink.set(localX, y, localZ, cell);
            if (cell == fluid && cave.fluidNeedsFloor()) {
                fluidSupport.add(localX, y, localZ, fluid, cave.air());
            }
        });
    }

    /** Columns whose surface lies under or next to a fluid, where no cave may open the surface. */
    private static boolean[] sealedColumns(long[] fluidBoundaries) {
        boolean[] sealed = new boolean[CHUNK_AREA];
        if (fluidBoundaries == null) {
            return sealed;
        }
        for (int column = 0; column < CHUNK_AREA; column++) {
            sealed[column] = SurfaceFluidBoundaryPlan.startY(fluidBoundaries[column]) != SurfaceFluidBoundaryPlan.NO_BOUNDARY;
        }
        return sealed;
    }

    /**
     * The caves any column of the chunk lists, in the order they were first listed, each with how much every column
     * lists it: 1 well inside the columns that do, 0 well outside, in between across their border.
     */
    private Map<String, double[]> columnCaves(CaveTerrain terrain, int originX, int originZ) {
        Map<String, double[]> weights = new LinkedHashMap<>();
        if (onlyDimensionLists) {
            dimensionCaves.stream().filter(ListedCaveCarving::isKey).forEach(key -> weights.put(key, LISTED_EVERYWHERE));
            return weights;
        }
        Map<String, boolean[]> listedAt = new LinkedHashMap<>();
        for (int listingX = 0; listingX < LISTINGS_PER_AXIS; listingX++) {
            int worldX = originX + ((listingX - 1) * LISTING_STEP_BLOCKS);
            for (int listingZ = 0; listingZ < LISTINGS_PER_AXIS; listingZ++) {
                int worldZ = originZ + ((listingZ - 1) * LISTING_STEP_BLOCKS);
                int listing = (listingX * LISTINGS_PER_AXIS) + listingZ;
                for (String key : listingAt(terrain.placeAt(worldX, worldZ))) {
                    listedAt.computeIfAbsent(key, absent -> new boolean[LISTINGS])[listing] = true;
                }
            }
        }
        listedAt.forEach((key, listed) -> weights.put(key, columnWeights(listed)));
        return weights;
    }

    /** Every cave the dimension, the region, the surface biome and the cave biome of a column list between them, once each. */
    private List<String> listingAt(CavePlace place) {
        List<String> listed = new ArrayList<>(dimensionCaves);
        addListed(listed, place.region() == null ? null : place.region().getCaves());
        addListed(listed, place.surfaceBiome() == null ? null : place.surfaceBiome().getCaves());
        addListed(listed, place.caveBiome() == null ? null : place.caveBiome().getCaves());
        return listed.stream().filter(ListedCaveCarving::isKey).distinct().toList();
    }

    private static void addListed(List<String> listed, KList<String> keys) {
        if (keys != null) {
            listed.addAll(keys);
        }
    }

    private static boolean isKey(String key) {
        return key != null && !key.isBlank();
    }

    private List<ListedColumns> bandCaves(List<CaveBand> bands) {
        List<ListedColumns> listed = new ArrayList<>();
        for (CaveBand band : bands) {
            HeightBand heights = new HeightBand(band.lowestWorldY() - world.minHeight(), band.highestWorldY() - world.minHeight());
            for (Map.Entry<String, double[]> entry : bandWeights(band).entrySet()) {
                ListedCave cave = caveOf(entry.getKey());
                if (cave != null) {
                    listed.add(new ListedColumns(cave, entry.getValue(), heights));
                }
            }
        }
        return listed;
    }

    private static Map<String, double[]> bandWeights(CaveBand band) {
        Map<String, double[]> weights = new LinkedHashMap<>();
        for (int column = 0; column < CHUNK_AREA; column++) {
            IrisBiome biome = band.biomeByColumn()[column];
            if (biome == null || biome.getCaves() == null) {
                continue;
            }
            for (String key : biome.getCaves()) {
                if (isKey(key)) {
                    weights.computeIfAbsent(key, absent -> new double[CHUNK_AREA])[column] = 1D;
                }
            }
        }
        return weights;
    }

    private static double[] columnWeights(boolean[] listedAt) {
        if (isAllTrue(listedAt)) {
            return LISTED_EVERYWHERE;
        }
        double[] latticeWeight = new double[WEIGHTS_PER_AXIS * WEIGHTS_PER_AXIS];
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

    /** The cave with this key, ready to carve; null when the pack has no such cave, which is reported once. */
    private ListedCave caveOf(String key) {
        return caves.computeIfAbsent(key, this::loadCave).orElse(null);
    }

    private Optional<ListedCave> loadCave(String key) {
        IrisCave cave = caveOf.apply(key);
        if (cave == null) {
            problems.missingCave(key);
            return Optional.empty();
        }
        return Optional.of(new ListedCave(key, cave, wallBiomeKey(key, cave), world, noise));
    }

    private String wallBiomeKey(String caveKey, IrisCave cave) {
        String biomeKey = cave.biome();
        if (biomeKey.isBlank() || biomeExists.test(biomeKey)) {
            return biomeKey;
        }
        problems.missingCaveBiome(caveKey, biomeKey);
        return "";
    }

    /**
     * What a chunk's carving is held back by.
     *
     * @param surfaceY        the height of the surface in each column, indexed as {@code (localX << 4) | localZ}
     * @param fluidBoundaries per column, the heights over which a surface fluid keeps the carving out; null for none
     * @param ceilings        per column, the highest height caves may carve at, where hydrology keeps cave pools below it; null for none
     */
    record ChunkSurface(int[] surfaceY, long[] fluidBoundaries, int[] ceilings) {
    }
}
