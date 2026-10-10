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

package art.arcane.iris.engine.actuator;

import art.arcane.iris.builtin.IrisStages;
import art.arcane.iris.builtin.terrain.CaveCeilingLayersSettings;
import art.arcane.iris.builtin.terrain.FluidPaletteSettings;
import art.arcane.iris.builtin.terrain.LayersSettings;
import art.arcane.iris.builtin.terrain.LockLayersMaxSettings;
import art.arcane.iris.builtin.terrain.LockLayersSettings;
import art.arcane.iris.builtin.terrain.RockPaletteSettings;
import art.arcane.iris.builtin.terrain.SeaLayersSettings;
import art.arcane.iris.builtin.terrain.SlabSettings;
import art.arcane.iris.builtin.terrain.WallSettings;
import art.arcane.iris.builtin.terrain.HideOresSettings;
import art.arcane.iris.builtin.terrain.OreSettings;
import art.arcane.iris.core.loader.IrisData;
import art.arcane.iris.core.modules.IrisChunk;
import art.arcane.iris.core.modules.IrisPackWorld;
import art.arcane.iris.engine.IrisComplex;
import art.arcane.iris.engine.UpperDimensionContext;
import art.arcane.iris.engine.actuator.ResolvedOres.YBand;
import art.arcane.iris.engine.framework.Engine;
import art.arcane.iris.engine.object.world.biome.IrisBiome;
import art.arcane.iris.engine.object.world.dimension.IrisDimension;
import art.arcane.iris.engine.object.world.region.IrisRegion;
import art.arcane.iris.engine.terrain.BiomeLayers;
import art.arcane.iris.engine.terrain.TerrainPalettes;
import art.arcane.iris.spi.PlatformBlockState;
import art.arcane.iris.util.common.data.B;
import art.arcane.iris.util.project.context.ChunkContext;
import art.arcane.iris.util.project.context.ChunkedDataCache;
import art.arcane.iris.util.project.hunk.Hunk;
import art.arcane.volmlib.util.collection.KList;
import art.arcane.volmlib.util.documentation.BlockCoordinates;
import art.arcane.volmlib.util.math.RNG;
import art.arcane.volmlib.util.scheduling.PrecisionStopwatch;
import com.volmit.FeatureRegistry;
import com.volmit.IrisModule;
import com.volmit.ModuleInfo;
import com.volmit.ModuleLifecycle;
import com.volmit.stage.ChunkStageContext;
import com.volmit.stage.StageGraph;
import com.volmit.world.PackWorld;

/**
 * Module {@code iris:terrain}: the chunk's ground, fluids and biome layers. Each column is filled from bedrock up
 * to its height and the fluid height with its biome's layers, with ore generators of the dimension, region and
 * biome replacing blocks on the way and the upper dimension's ground filled in from the top. The ore generators' noise
 * and the rock and fluid palettes' noise are built ahead of generation, in {@link #warmUp}.
 */
public final class TerrainModule implements IrisModule, ModuleLifecycle {
    private static final String VERSION = "built-in";

    @Override
    public ModuleInfo info() {
        return ModuleInfo.of("iris:terrain", "Terrain", VERSION);
    }

    @Override
    public void register(FeatureRegistry registry) {
        registry.registerSettings(LayersSettings.class);
        registry.registerSettings(SeaLayersSettings.class);
        registry.registerSettings(CaveCeilingLayersSettings.class);
        registry.registerSettings(SlabSettings.class);
        registry.registerSettings(WallSettings.class);
        registry.registerSettings(LockLayersSettings.class);
        registry.registerSettings(LockLayersMaxSettings.class);
        registry.registerSettings(RockPaletteSettings.class);
        registry.registerSettings(FluidPaletteSettings.class);
        registry.registerSettings(OreSettings.class);
        registry.registerSettings(HideOresSettings.class);
        registry.registerLifecycle(this);
    }

    @Override
    public void warmUp(PackWorld world) {
        Engine engine = IrisPackWorld.of(world).engine();
        TerrainPalettes.of(engine).warm();
        new OreWarmUp(engine).warm();
    }

    @Override
    public void install(StageGraph graph) {
        graph.define(IrisStages.Terrain.SHAPE).run(TerrainModule::shape);
    }

    private static void shape(ChunkStageContext context) {
        IrisChunk chunk = IrisChunk.current();
        chunk.state(TerrainShaper.class, TerrainShaper::new).shape(chunk.originX(), chunk.originZ(), chunk.blocks(), chunk.context());
    }

    /** What shaping terrain keeps for one engine runtime: the terrain stream's random. */
    static final class TerrainShaper {
        private static final PlatformBlockState BEDROCK = B.getState("BEDROCK");

        private final Engine engine;
        private final TerrainPalettes palettes;
        private final BiomeLayers layers;
        private final TerrainOres ores;
        private final RNG rng;

        TerrainShaper(Engine engine) {
            this(engine, TerrainPalettes.of(engine), BiomeLayers.of(engine), TerrainOres.of(engine));
        }

        TerrainShaper(Engine engine, TerrainPalettes palettes, BiomeLayers layers, TerrainOres ores) {
            this.engine = engine;
            this.palettes = palettes;
            this.layers = layers;
            this.ores = ores;
            this.rng = new RNG(engine.getSeedManager().getTerrain());
        }

        /** Fills the 16 x 16 columns of the chunk whose first block is {@code originX}, {@code originZ}, one slice along x at a time. */
        @BlockCoordinates
        void shape(int originX, int originZ, Hunk<PlatformBlockState> blocks, ChunkContext context) {
            PrecisionStopwatch stopwatch = PrecisionStopwatch.start();
            for (int sliceX = 0; sliceX < blocks.getWidth(); sliceX++) {
                shapeSliver(originX, originZ, sliceX, blocks, context);
            }
            engine.getMetrics().getTerrain().put(stopwatch.getMilliseconds());
        }

        /** One plane from the sky to bedrock, one block thick along x. */
        @BlockCoordinates
        private void shapeSliver(int x, int z, int xf, Hunk<PlatformBlockState> h, ChunkContext context) {
            int chunkHeight = h.getHeight();
            int chunkDepth = h.getDepth();
            IrisDimension dimension = engine.getDimension();
            IrisData data = engine.getData();
            IrisComplex complex = engine.getComplex();
            RNG localRng = rng;
            int fluidHeight = dimension.getFluidHeight();
            int clampedFluidHeight = Math.min(chunkHeight, fluidHeight);
            boolean bedrockEnabled = dimension.isBedrock();
            boolean hideOres = HiddenOres.in(dimension);
            ChunkedDataCache<IrisBiome> biomeCache = context.getBiome();
            ChunkedDataCache<IrisRegion> regionCache = context.getRegion();
            int realX = xf + x;
            UpperDimensionContext upperContext = engine.getUpperContext();
            // Dimension-level ore lookups are chunk-invariant; resolving them per column paid
            // the settings lookup times 256 per chunk for a constant.
            ResolvedOres dimensionOres = hideOres ? ResolvedOres.NONE : ores.of(dimension);
            YBand dimensionSurfaceBand = dimensionOres.surfaceBand();
            YBand dimensionUndergroundBand = dimensionOres.undergroundBand();

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
                PlatformBlockState fluid = palettes.fluidAt(realX, realZ);
                PlatformBlockState rock = palettes.rockAt(realX, realZ);
                PlatformBlockState mappedSurfaceBlock = complex.getImageMapRuntime().sampleSurfaceBlock(realX, realZ);
                ResolvedOres biomeOres = hideOres ? ResolvedOres.NONE : ores.of(biome);
                ResolvedOres regionOres = hideOres ? ResolvedOres.NONE : ores.of(region);
                YBand biomeSurfaceBand = biomeOres.surfaceBand();
                YBand regionSurfaceBand = regionOres.surfaceBand();
                YBand biomeUndergroundBand = biomeOres.undergroundBand();
                YBand regionUndergroundBand = regionOres.undergroundBand();
                boolean hasSurfaceOres = biomeSurfaceBand.hasOres() || regionSurfaceBand.hasOres() || dimensionSurfaceBand.hasOres();
                boolean hasUndergroundOres = biomeUndergroundBand.hasOres() || regionUndergroundBand.hasOres() || dimensionUndergroundBand.hasOres();
                KList<PlatformBlockState> blocks = null;
                KList<PlatformBlockState> fblocks = null;

                for (int i = topY; i >= 0; i--) {
                    if (i == 0 && bedrockEnabled) {
                        h.setRaw(xf, i, zf, BEDROCK);
                        continue;
                    }

                    PlatformBlockState ore = null;
                    if (hasSurfaceOres) {
                        if (biomeSurfaceBand.contains(i)) {
                            ore = biomeOres.generateSurface(realX, i, realZ);
                        }
                        if (ore == null && regionSurfaceBand.contains(i)) {
                            ore = regionOres.generateSurface(realX, i, realZ);
                        }
                        if (ore == null && dimensionSurfaceBand.contains(i)) {
                            ore = dimensionOres.generateSurface(realX, i, realZ);
                        }
                    }
                    if (ore != null) {
                        h.setRaw(xf, i, zf, ore);
                        continue;
                    }

                    if (i > he && i <= hf) {
                        int fdepth = hf - i;
                        if (fblocks == null) {
                            fblocks = layers.generateSeaLayers(biome, realX, realZ, localRng, hf - he, data);
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
                            blocks = layers.generateLayers(biome, dimension, realX, realZ, localRng, he, he, data, complex);
                        }

                        if (blocks.hasIndex(depth)) {
                            h.setRaw(xf, i, zf, blocks.get(depth));
                            continue;
                        }

                        if (hasUndergroundOres) {
                            if (biomeUndergroundBand.contains(i)) {
                                ore = biomeOres.generateUnderground(realX, i, realZ);
                            }
                            if (ore == null && regionUndergroundBand.contains(i)) {
                                ore = regionOres.generateUnderground(realX, i, realZ);
                            }
                            if (ore == null && dimensionUndergroundBand.contains(i)) {
                                ore = dimensionOres.generateUnderground(realX, i, realZ);
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
                        PlatformBlockState upperRock = palettes.upperRockAt(upperContext, realX, realZ);
                        PlatformBlockState upperMappedSurface = upperContext.getSurfaceBlock(realX, realZ);
                        int upperThickness = chunkHeight - 1 - upperSurfaceY;
                        KList<PlatformBlockState> upperBlocks = upperBiome != null
                                ? layers.generateLayers(upperBiome, upperContext.getDimension(),
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
    }
}
