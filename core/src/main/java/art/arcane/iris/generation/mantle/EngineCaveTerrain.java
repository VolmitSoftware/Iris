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
import art.arcane.iris.generation.runtime.Engine;
import art.arcane.iris.generation.runtime.IrisComplex;
import art.arcane.iris.generation.terrain.IrisDimensionCarvingEntry;
import art.arcane.iris.generation.terrain.IrisDimensionCarvingResolver;
import art.arcane.iris.generation.terrain.IrisRegion;
import art.arcane.volmlib.util.math.PowerOfTwoCoordinates;

import java.util.ArrayList;
import java.util.List;
import java.util.TreeSet;

/** The terrain an engine generates, as listed cave carving reads it: the streams of one {@link IrisComplex}. */
final class EngineCaveTerrain implements CaveTerrain {
    private static final int CHUNK_AREA = 256;
    /** A column's cave biome is the one the world places this far below its surface (at least at height 1). */
    private static final int CAVE_SAMPLE_DEPTH_BELOW_SURFACE_BLOCKS = 56;

    private final Engine engine;
    private final IrisComplex complex;
    private final IrisDimensionCarvingResolver.State resolverState;

    EngineCaveTerrain(Engine engine, IrisComplex complex, IrisDimensionCarvingResolver.State resolverState) {
        this.engine = engine;
        this.complex = complex;
        this.resolverState = resolverState;
    }

    @Override
    public CavePlace placeAt(int worldX, int worldZ) {
        double surfaceHeight = complex.getNaturalHeightStream().getDouble(worldX, worldZ);
        IrisRegion region = complex.getRegionStream().get(worldX, worldZ);
        IrisBiome surfaceBiome = complex.getNaturalTrueBiomeStream().get(worldX, worldZ);
        IrisBiome caveBiome = complex.getCaveBiomeStream().get(worldX, worldZ);
        return new CavePlace(region, surfaceBiome, caveBiomeOf(worldX, worldZ, surfaceHeight, surfaceBiome, caveBiome));
    }

    /**
     * The cave biome of a column: the one a carving entry puts at the height caves are read at, else the cave biome the
     * world places there, unless it asks to lie deeper than the cave is, in which case the surface biome stands in.
     */
    private IrisBiome caveBiomeOf(int worldX, int worldZ, double surfaceHeight, IrisBiome surfaceBiome, IrisBiome worldCaveBiome) {
        int sampleY = Math.max(1, (int) Math.round(surfaceHeight) - CAVE_SAMPLE_DEPTH_BELOW_SURFACE_BLOCKS);
        int worldY = sampleY + engine.getWorld().minHeight();
        IrisBiome carvingBiome = carvingBiomeAt(worldX, worldY, worldZ);
        if (carvingBiome != null) {
            return carvingBiome;
        }
        if (worldCaveBiome == null || surfaceBiome == null) {
            return worldCaveBiome;
        }
        int depthBelowSurface = (int) surfaceHeight - sampleY;
        boolean tooShallow = depthBelowSurface <= 0 || depthBelowSurface < Math.max(0, worldCaveBiome.getCaveMinDepthBelowSurface());
        return tooShallow ? surfaceBiome : worldCaveBiome;
    }

    private IrisBiome carvingBiomeAt(int worldX, int worldY, int worldZ) {
        IrisDimensionCarvingEntry root = IrisDimensionCarvingResolver.resolveRootEntry(engine, worldY, resolverState);
        if (root == null) {
            return null;
        }
        IrisDimensionCarvingEntry resolved = IrisDimensionCarvingResolver.resolveFromRoot(engine, root, worldX, worldY, worldZ, resolverState);
        return IrisDimensionCarvingResolver.resolveEntryBiome(engine, resolved, resolverState);
    }

    @Override
    public List<CaveBand> bandsOf(int chunkX, int chunkZ) {
        List<IrisDimensionCarvingEntry> entries = engine.getDimension().getCarving();
        List<CaveBand> bands = new ArrayList<>();
        if (entries == null || entries.isEmpty()) {
            return bands;
        }
        TreeSet<Integer> boundaries = boundariesOf(entries);
        for (IrisDimensionCarvingEntry entry : entries) {
            if (entry != null && entry.isEnabled()) {
                addBandsOf(entry, boundaries, chunkX, chunkZ, bands);
            }
        }
        return bands;
    }

    private TreeSet<Integer> boundariesOf(List<IrisDimensionCarvingEntry> entries) {
        int minimumY = engine.getWorld().minHeight();
        int maximumY = engine.getWorld().maxHeight();
        TreeSet<Integer> boundaries = new TreeSet<>();
        boundaries.add(minimumY);
        boundaries.add(maximumY);
        for (IrisDimensionCarvingEntry candidate : entries) {
            if (candidate != null && candidate.getWorldYRange() != null) {
                boundaries.add(Math.max(minimumY, Math.min(maximumY, (int) Math.ceil(candidate.getWorldYRange().getMin()))));
                boundaries.add(Math.max(minimumY, Math.min(maximumY, (int) Math.floor(candidate.getWorldYRange().getMax()) + 1)));
            }
        }
        return boundaries;
    }

    private void addBandsOf(IrisDimensionCarvingEntry entry, TreeSet<Integer> boundaries, int chunkX, int chunkZ, List<CaveBand> bands) {
        if (IrisDimensionCarvingResolver.resolveEntryBiome(engine, entry, resolverState) == null) {
            return;
        }
        Integer previous = null;
        for (int boundary : boundaries) {
            boolean bandStartsInEntry = previous != null && boundary > previous
                    && (entry.getWorldYRange() == null || entry.getWorldYRange().contains(previous));
            if (bandStartsInEntry) {
                addBand(entry, previous, boundary - 1, chunkX, chunkZ, bands);
            }
            previous = boundary;
        }
    }

    private void addBand(IrisDimensionCarvingEntry entry, int lowestWorldY, int highestWorldY, int chunkX, int chunkZ, List<CaveBand> bands) {
        int baseX = PowerOfTwoCoordinates.chunkToBlock(chunkX);
        int baseZ = PowerOfTwoCoordinates.chunkToBlock(chunkZ);
        IrisBiome[] biomeByColumn = new IrisBiome[CHUNK_AREA];
        boolean any = false;
        for (int column = 0; column < CHUNK_AREA; column++) {
            IrisDimensionCarvingEntry resolved = IrisDimensionCarvingResolver.resolveFromRoot(
                    engine, entry, baseX + (column >> 4), lowestWorldY, baseZ + (column & 15), resolverState);
            biomeByColumn[column] = IrisDimensionCarvingResolver.resolveEntryBiome(engine, resolved, resolverState);
            any |= biomeByColumn[column] != null;
        }
        if (any) {
            bands.add(new CaveBand(lowestWorldY, highestWorldY, biomeByColumn));
        }
    }
}
