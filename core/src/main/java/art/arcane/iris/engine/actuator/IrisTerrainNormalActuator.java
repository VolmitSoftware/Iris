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

package art.arcane.iris.engine.actuator;

import art.arcane.iris.engine.framework.Engine;
import art.arcane.iris.engine.framework.EngineAssignedActuator;
import art.arcane.iris.core.loader.IrisData;
import art.arcane.iris.engine.IrisComplex;
import art.arcane.iris.engine.UpperDimensionContext;
import art.arcane.iris.engine.object.world.biome.IrisBiome;
import art.arcane.iris.engine.object.world.dimension.IrisDimension;
import art.arcane.iris.engine.object.terrain.deposit.IrisOreGenerator;
import art.arcane.iris.engine.object.terrain.deposit.IrisOreGeneratorBounds;
import art.arcane.iris.engine.object.world.region.IrisRegion;
import art.arcane.volmlib.util.collection.KList;
import art.arcane.iris.util.project.context.ChunkedDataCache;
import art.arcane.iris.util.project.context.ChunkContext;
import art.arcane.volmlib.util.documentation.BlockCoordinates;
import art.arcane.iris.util.project.hunk.Hunk;
import art.arcane.volmlib.util.math.RNG;
import art.arcane.volmlib.util.scheduling.PrecisionStopwatch;
import art.arcane.iris.spi.PlatformBlockState;
import art.arcane.iris.util.common.data.B;
import lombok.Getter;

public class IrisTerrainNormalActuator extends EngineAssignedActuator<PlatformBlockState> {
    private static final PlatformBlockState AIR = B.getState("AIR");
    private static final PlatformBlockState BEDROCK = B.getState("BEDROCK");
    private static final PlatformBlockState LAVA = B.getState("LAVA");
    private static final PlatformBlockState GLASS = B.getState("GLASS");
    private static final PlatformBlockState CAVE_AIR = B.getState("CAVE_AIR");
    @Getter
    private final RNG rng;
    @Getter
    private int lastBedrock = -1;

    public IrisTerrainNormalActuator(Engine engine) {
        super(engine, "Terrain");
        rng = new RNG(engine.getSeedManager().getTerrain());
    }

    @BlockCoordinates
    @Override
    public void onActuate(int x, int z, Hunk<PlatformBlockState> h, boolean multicore, ChunkContext context) {
        PrecisionStopwatch p = PrecisionStopwatch.start();

        for (int xf = 0; xf < h.getWidth(); xf++) {
            terrainSliver(x, z, xf, h, context);
        }

        getEngine().getMetrics().getTerrain().put(p.getMilliseconds());
    }

    /**
     * This is calling 1/16th of a chunk x/z slice. It is a plane from sky to bedrock 1 thick in the x direction.
     *
     * @param x  the chunk x in blocks
     * @param z  the chunk z in blocks
     * @param xf the current x slice
     * @param h  the blockdata
     */
    @BlockCoordinates
    public void terrainSliver(int x, int z, int xf, Hunk<PlatformBlockState> h, ChunkContext context) {
        terrainSliverOptimized(x, z, xf, h, context);
    }

    @BlockCoordinates
    private void terrainSliverOptimized(int x, int z, int xf, Hunk<PlatformBlockState> h, ChunkContext context) {
        int chunkHeight = h.getHeight();
        int chunkDepth = h.getDepth();
        IrisDimension dimension = getDimension();
        IrisData data = getData();
        IrisComplex complex = getComplex();
        RNG localRng = rng;
        int fluidHeight = dimension.getFluidHeight();
        int clampedFluidHeight = Math.min(chunkHeight, fluidHeight);
        boolean bedrockEnabled = dimension.isBedrock();
        boolean hideOres = dimension.isHideOresForHiddenOre();
        ChunkedDataCache<IrisBiome> biomeCache = context.getBiome();
        ChunkedDataCache<IrisRegion> regionCache = context.getRegion();
        ChunkedDataCache<PlatformBlockState> fluidCache = context.getFluid();
        ChunkedDataCache<PlatformBlockState> rockCache = context.getRock();
        int realX = xf + x;
        UpperDimensionContext upperContext = getEngine().getUpperContext();
        // Dimension-level ore lookups are chunk-invariant; resolving them per column paid
        // four accessor chains times 256 per chunk for constants.
        KList<IrisOreGenerator> dimensionSurfaceOres = hideOres ? null : dimension.getSurfaceOreGenerators();
        KList<IrisOreGenerator> dimensionUndergroundOres = hideOres ? null : dimension.getUndergroundOreGenerators();
        IrisOreGeneratorBounds dimensionSurfaceOreBounds = hideOres ? IrisOreGeneratorBounds.EMPTY : dimension.getSurfaceOreGeneratorBounds();
        IrisOreGeneratorBounds dimensionUndergroundOreBounds = hideOres ? IrisOreGeneratorBounds.EMPTY : dimension.getUndergroundOreGeneratorBounds();

        for (int zf = 0; zf < chunkDepth; zf++) {
            int realZ = zf + z;
            IrisBiome biome = biomeCache.get(xf, zf);
            IrisRegion region = regionCache.get(xf, zf);
            int he = Math.min(chunkHeight, context.getRoundedHeight(xf, zf));
            int hf = Math.max(clampedFluidHeight, he);
            if (hf < 0) {
                continue;
            }

            int topY = Math.min(hf, chunkHeight - 1);
            PlatformBlockState fluid = fluidCache.get(xf, zf);
            PlatformBlockState rock = rockCache.get(xf, zf);
            PlatformBlockState mappedSurfaceBlock = complex.getImageMapRuntime().sampleSurfaceBlock(realX, realZ);
            KList<IrisOreGenerator> biomeSurfaceOres = hideOres ? null : biome.getSurfaceOreGenerators();
            KList<IrisOreGenerator> regionSurfaceOres = hideOres ? null : region.getSurfaceOreGenerators();
            KList<IrisOreGenerator> biomeUndergroundOres = hideOres ? null : biome.getUndergroundOreGenerators();
            KList<IrisOreGenerator> regionUndergroundOres = hideOres ? null : region.getUndergroundOreGenerators();
            IrisOreGeneratorBounds biomeSurfaceOreBounds = hideOres ? IrisOreGeneratorBounds.EMPTY : biome.getSurfaceOreGeneratorBounds();
            IrisOreGeneratorBounds regionSurfaceOreBounds = hideOres ? IrisOreGeneratorBounds.EMPTY : region.getSurfaceOreGeneratorBounds();
            IrisOreGeneratorBounds biomeUndergroundOreBounds = hideOres ? IrisOreGeneratorBounds.EMPTY : biome.getUndergroundOreGeneratorBounds();
            IrisOreGeneratorBounds regionUndergroundOreBounds = hideOres ? IrisOreGeneratorBounds.EMPTY : region.getUndergroundOreGeneratorBounds();
            boolean hasSurfaceOres = biomeSurfaceOreBounds.hasOres() || regionSurfaceOreBounds.hasOres() || dimensionSurfaceOreBounds.hasOres();
            boolean hasUndergroundOres = biomeUndergroundOreBounds.hasOres() || regionUndergroundOreBounds.hasOres() || dimensionUndergroundOreBounds.hasOres();
            KList<PlatformBlockState> blocks = null;
            KList<PlatformBlockState> fblocks = null;

            for (int i = topY; i >= 0; i--) {
                if (i == 0 && bedrockEnabled) {
                    h.setRaw(xf, i, zf, BEDROCK);
                    lastBedrock = i;
                    continue;
                }

                PlatformBlockState ore = null;
                if (hasSurfaceOres) {
                    if (biomeSurfaceOreBounds.contains(i)) {
                        ore = generateOres(biomeSurfaceOres, realX, i, realZ, localRng, data);
                    }
                    if (ore == null && regionSurfaceOreBounds.contains(i)) {
                        ore = generateOres(regionSurfaceOres, realX, i, realZ, localRng, data);
                    }
                    if (ore == null && dimensionSurfaceOreBounds.contains(i)) {
                        ore = generateOres(dimensionSurfaceOres, realX, i, realZ, localRng, data);
                    }
                }
                if (ore != null) {
                    h.setRaw(xf, i, zf, ore);
                    continue;
                }

                if (i > he && i <= hf) {
                    int fdepth = hf - i;
                    if (fblocks == null) {
                        fblocks = biome.generateSeaLayers(realX, realZ, localRng, hf - he, data);
                    }

                    if (fblocks.hasIndex(fdepth)) {
                        h.setRaw(xf, i, zf, fblocks.get(fdepth));
                    } else {
                        h.setRaw(xf, i, zf, fluid);
                    }
                    continue;
                }

                if (i <= he) {
                    int depth = he - i;
                    if (depth == 0 && mappedSurfaceBlock != null) {
                        h.setRaw(xf, i, zf, mappedSurfaceBlock);
                        continue;
                    }
                    if (blocks == null) {
                        blocks = biome.generateLayers(dimension, realX, realZ, localRng, he, he, data, complex);
                    }

                    if (blocks.hasIndex(depth)) {
                        h.setRaw(xf, i, zf, blocks.get(depth));
                        continue;
                    }

                    if (hasUndergroundOres) {
                        if (biomeUndergroundOreBounds.contains(i)) {
                            ore = generateOres(biomeUndergroundOres, realX, i, realZ, localRng, data);
                        }
                        if (ore == null && regionUndergroundOreBounds.contains(i)) {
                            ore = generateOres(regionUndergroundOres, realX, i, realZ, localRng, data);
                        }
                        if (ore == null && dimensionUndergroundOreBounds.contains(i)) {
                            ore = generateOres(dimensionUndergroundOres, realX, i, realZ, localRng, data);
                        }
                    }

                    if (ore != null) {
                        h.setRaw(xf, i, zf, ore);
                    } else {
                        h.setRaw(xf, i, zf, rock);
                    }
                }
            }

            if (upperContext != null) {
                int rawUpperSurface = upperContext.getUpperSurfaceY(realX, realZ);
                int upperGap = dimension.getUpperDimensionGap();
                int upperSurfaceY = Math.max(rawUpperSurface, he + upperGap);

                if (upperSurfaceY < chunkHeight - 1) {
                    IrisBiome upperBiome = upperContext.getUpperBiome(realX, realZ);
                    PlatformBlockState upperRock = upperContext.getRockBlock(realX, realZ);
                    PlatformBlockState upperMappedSurface = upperContext.getSurfaceBlock(realX, realZ);
                    int upperThickness = chunkHeight - 1 - upperSurfaceY;
                    KList<PlatformBlockState> upperBlocks = upperBiome != null
                            ? upperBiome.generateLayers(upperContext.getDimension(),
                            realX, realZ, localRng, upperThickness, upperThickness,
                            upperContext.getData(), complex)
                            : null;

                    for (int y = chunkHeight - 1; y >= upperSurfaceY; y--) {
                        if (y == chunkHeight - 1 && bedrockEnabled) {
                            h.setRaw(xf, y, zf, BEDROCK);
                            continue;
                        }
                        if (y == upperSurfaceY && upperMappedSurface != null) {
                            h.setRaw(xf, y, zf, upperMappedSurface);
                            continue;
                        }
                        int depthFromFace = y - upperSurfaceY;
                        if (upperBlocks != null && upperBlocks.hasIndex(depthFromFace)) {
                            h.setRaw(xf, y, zf, upperBlocks.get(depthFromFace));
                        } else {
                            h.setRaw(xf, y, zf, upperRock);
                        }
                    }
                }
            }
        }
    }

    private PlatformBlockState generateOres(KList<IrisOreGenerator> oreGenerators, int x, int y, int z, RNG rng, IrisData data) {
        if (oreGenerators == null || oreGenerators.isEmpty()) {
            return null;
        }

        int oreCount = oreGenerators.size();
        for (int oreIndex = 0; oreIndex < oreCount; oreIndex++) {
            IrisOreGenerator oreGenerator = oreGenerators.get(oreIndex);
            PlatformBlockState ore = oreGenerator.generate(x, y, z, rng, data);
            if (ore != null) {
                return ore;
            }
        }

        return null;
    }
}
