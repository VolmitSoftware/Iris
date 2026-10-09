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

package art.arcane.iris.engine.modifier;


import art.arcane.iris.engine.object.world.biome.custom.IrisBiomeCustom;
import art.arcane.iris.core.loader.IrisData;
import art.arcane.iris.engine.IrisComplex;
import art.arcane.iris.engine.decorator.FloatingDecorator;
import art.arcane.iris.engine.decorator.IrisSeaSurfaceDecorator;
import art.arcane.iris.engine.framework.Engine;
import art.arcane.iris.engine.framework.EngineDecorator;
import art.arcane.iris.engine.object.terrain.floating.FloatingBottomPaletteMode;
import art.arcane.iris.engine.object.terrain.floating.FloatingIslandBoundarySampler;
import art.arcane.iris.engine.object.terrain.floating.FloatingIslandSample;
import art.arcane.iris.engine.object.world.biome.IrisBiome;
import art.arcane.iris.engine.object.terrain.IrisBiomePaletteLayer;
import art.arcane.iris.builtin.decorant.DecorationPart;
import art.arcane.iris.engine.object.world.dimension.IrisDimension;
import art.arcane.iris.engine.object.terrain.floating.IrisFloatingChildBiomes;
import art.arcane.iris.engine.object.placement.IrisSlopeClip;
import art.arcane.iris.spi.IrisLogging;
import art.arcane.iris.util.common.data.B;
import art.arcane.iris.util.project.context.ChunkContext;
import art.arcane.iris.util.project.hunk.Hunk;
import art.arcane.iris.util.project.noise.CNG;
import art.arcane.volmlib.util.collection.KList;
import art.arcane.volmlib.util.math.RNG;
import art.arcane.volmlib.util.matter.MatterBiomeInject;
import art.arcane.volmlib.util.matter.slices.BiomeInjectMatter;
import art.arcane.volmlib.util.scheduling.PrecisionStopwatch;
import art.arcane.iris.spi.IrisPlatforms;
import art.arcane.iris.spi.PlatformBlockState;

import java.util.IdentityHashMap;

import static art.arcane.iris.engine.mantle.EngineMantle.AIR;

/**
 * Fills and decorates the floating islands of the dimension's floating child biomes for {@link FloatingModule}:
 * the islands' solids (top layers, bottom palette, sea surface) and, later, the decoration on their tops. One
 * per engine runtime, holding the islands' random.
 */
public class FloatingIslands {
    public static final long FLOATING_BASE_SEED_SALT = 0x5EED_F107_00F1B10CL;
    private static final Runnable NOOP_DECORATION_MISS = () -> {
    };
    private final Engine engine;
    private final RNG rng;
    private final EngineDecorator seaSurfaceDecorator;

    private static KList<PlatformBlockState> generateBottomPaletteLayers(IrisFloatingChildBiomes entry, IrisDimension dimension, double wx, double wz, RNG random, int paletteDepth, IrisData data, IrisComplex complex) {
        if (entry == null || entry.getBottomPaletteMode() != FloatingBottomPaletteMode.CUSTOM || entry.getBottomPalette() == null || entry.getBottomPalette().isEmpty()) {
            return null;
        }
        return generatePaletteLayers(dimension, entry.getBottomPalette(), wx, wz, random.nextParallelRNG(0xB0770B), paletteDepth, data, complex);
    }

    private static KList<PlatformBlockState> generatePaletteLayers(IrisDimension dimension, KList<IrisBiomePaletteLayer> layers, double wx, double wz, RNG random, int maxDepth, IrisData data, IrisComplex complex) {
        KList<PlatformBlockState> generated = new KList<>();
        if (layers == null || layers.isEmpty() || maxDepth <= 0) {
            return generated;
        }

        int generatorSeed = 7235;
        for (int i = 0; i < layers.size(); i++) {
            IrisBiomePaletteLayer layer = layers.get(i);
            CNG heightGenerator = layer.getHeightGenerator(random.nextParallelRNG((generatorSeed++) * generatorSeed * generatorSeed * generatorSeed), data);
            if (heightGenerator == null) {
                continue;
            }
            double layerDepth = heightGenerator.fit(layer.getMinHeight(), layer.getMaxHeight(), wx / layer.getZoom(), wz / layer.getZoom());
            IrisSlopeClip slopeClip = layer.getSlopeCondition();

            if (slopeClip != null && !slopeClip.isDefault() && complex != null && !slopeClip.isValid(complex.getSlopeStream().get(wx, wz))) {
                layerDepth = 0;
            }

            if (layerDepth <= 0) {
                continue;
            }

            for (int j = 0; j < layerDepth; j++) {
                if (generated.size() >= maxDepth) {
                    break;
                }

                try {
                    generated.add(layer.get(random.nextParallelRNG(i + j), (wx + j) / layer.getZoom(), j, (wz - j) / layer.getZoom(), data));
                } catch (Throwable e) {
                    IrisLogging.reportError(e);
                }
            }

            if (generated.size() >= maxDepth) {
                break;
            }

            if (dimension != null && dimension.isExplodeBiomePalettes()) {
                PlatformBlockState barrier = B.getState("minecraft:barrier");
                for (int j = 0; j < dimension.getExplodeBiomePaletteSize(); j++) {
                    generated.add(barrier);

                    if (generated.size() >= maxDepth) {
                        break;
                    }
                }
            }
        }

        return generated;
    }

    private static boolean usesBottomPalette(IrisFloatingChildBiomes entry) {
        FloatingBottomPaletteMode mode = entry == null || entry.getBottomPaletteMode() == null ? FloatingBottomPaletteMode.DEPTH : entry.getBottomPaletteMode();
        return mode != FloatingBottomPaletteMode.DEPTH;
    }

    private static int[] bottomDepths(FloatingIslandSample sample, int chunkHeight) {
        int[] bottomDepths = new int[sample.solidMask.length];
        for (int i = 0; i < bottomDepths.length; i++) {
            bottomDepths[i] = -1;
        }

        IdentityHashMap<IrisFloatingChildBiomes, Integer> depthByEntry = new IdentityHashMap<>();
        int max = Math.min(sample.topIdx, sample.solidMask.length - 1);
        for (int k = 0; k <= max; k++) {
            if (!sample.solidMask[k]) {
                continue;
            }
            int y = sample.islandBaseY + k;
            if (y < 0 || y >= chunkHeight) {
                continue;
            }
            IrisFloatingChildBiomes entry = sample.entryAt(k);
            int depth = depthByEntry.getOrDefault(entry, 0);
            bottomDepths[k] = depth;
            depthByEntry.put(entry, depth + 1);
        }

        return bottomDepths;
    }

    private static PlatformBlockState selectPaletteBlock(IrisFloatingChildBiomes entry, KList<PlatformBlockState> topBlocks, KList<PlatformBlockState> bottomBlocks, int topDepth, int bottomDepth, PlatformBlockState fallbackSolid) {
        FloatingBottomPaletteMode mode = entry == null || entry.getBottomPaletteMode() == null ? FloatingBottomPaletteMode.DEPTH : entry.getBottomPaletteMode();
        if (mode == FloatingBottomPaletteMode.MIRROR_TOP) {
            return paletteBlock(topBlocks, Math.min(topDepth, bottomDepth), fallbackSolid);
        }
        if (mode == FloatingBottomPaletteMode.CUSTOM && bottomDepth < topDepth) {
            if (bottomBlocks != null && !bottomBlocks.isEmpty()) {
                return paletteBlock(bottomBlocks, bottomDepth, fallbackSolid);
            }
            return paletteBlock(topBlocks, bottomDepth, fallbackSolid);
        }
        return paletteBlock(topBlocks, topDepth, fallbackSolid);
    }

    private static PlatformBlockState paletteBlock(KList<PlatformBlockState> blocks, int depth, PlatformBlockState fallbackSolid) {
        if (blocks == null || blocks.isEmpty()) {
            return fallbackSolid;
        }
        PlatformBlockState block = blocks.hasIndex(depth) ? blocks.get(depth) : blocks.getLast();
        return block == null ? fallbackSolid : block;
    }

    private PaletteContext createPaletteContext(IrisBiome parent, IrisFloatingChildBiomes entry, IrisDimension dimension, int wx, int wz, long colSeed, int paletteDepth, IrisData data, IrisComplex complex) {
        IrisBiome target = entry == null ? parent : entry.getRealBiome(parent, data);
        int entrySeed = entry == null || entry.getBiome() == null ? 0 : entry.getBiome().hashCode();
        RNG layerRng = rng.nextParallelRNG((int) (colSeed ^ 0x7A4E ^ entrySeed));
        KList<PlatformBlockState> topBlocks = target == null ? null : target.generateLayers(dimension, wx, wz, layerRng, paletteDepth, paletteDepth, data, complex);
        if (topBlocks == null || topBlocks.isEmpty()) {
            topBlocks = parent.generateLayers(dimension, wx, wz, layerRng, paletteDepth, paletteDepth, data, complex);
        }
        KList<PlatformBlockState> bottomBlocks = generateBottomPaletteLayers(entry, dimension, wx, wz, layerRng, paletteDepth, data, complex);
        return new PaletteContext(topBlocks, bottomBlocks, B.getState("minecraft:stone"));
    }

    private static final class PaletteContext {
        private final KList<PlatformBlockState> topBlocks;
        private final KList<PlatformBlockState> bottomBlocks;
        private final PlatformBlockState fallbackSolid;

        private PaletteContext(KList<PlatformBlockState> topBlocks, KList<PlatformBlockState> bottomBlocks, PlatformBlockState fallbackSolid) {
            this.topBlocks = topBlocks;
            this.bottomBlocks = bottomBlocks;
            this.fallbackSolid = fallbackSolid;
        }
    }

    public FloatingIslands(Engine engine) {
        this.engine = engine;
        rng = new RNG(engine.getSeedManager().getTerrain() ^ 0x7EB0A73F1DCE514DL);
        seaSurfaceDecorator = new IrisSeaSurfaceDecorator(engine);
    }

    /** Fills the islands' solids into the chunk whose first block is {@code x}, {@code z}. */
    public void fillSolids(int x, int z, Hunk<PlatformBlockState> output, boolean multicore, ChunkContext context) {
        PrecisionStopwatch p = PrecisionStopwatch.start();
        int chunkHeight = output.getHeight();
        IrisData data = engine.getData();
        IrisDimension dimension = engine.getDimension();
        IrisComplex complex = engine.getComplex();
        long baseSeed = engine.getSeedManager().getTerrain() ^ FLOATING_BASE_SEED_SALT;
        FloatingIslandBoundarySampler boundarySampler = context.getFloatingIslandBoundarySampler();
        FloatingIslandSample.clearChunkMemo();

        for (int xf = 0; xf < 16; xf++) {
            for (int zf = 0; zf < 16; zf++) {
                int wx = x + xf;
                int wz = z + zf;
                IrisBiome parent = boundarySampler.parent(wx, wz);
                if (parent == null || parent.getFloatingChildBiomes() == null || parent.getFloatingChildBiomes().isEmpty()) {
                    continue;
                }
                FloatingIslandSample sample = FloatingIslandSample.sampleMemoized(parent, wx, wz, chunkHeight, baseSeed, data, engine, boundarySampler);
                if (sample == null) {
                    continue;
                }

                long colSeed = FloatingIslandSample.columnSeed(baseSeed, wx, wz);
                int paletteDepth = Math.max(4, sample.solidCount + 4);
                IdentityHashMap<IrisFloatingChildBiomes, PaletteContext> paletteContexts = new IdentityHashMap<>();
                IdentityHashMap<IrisFloatingChildBiomes, Integer> topDepthByEntry = new IdentityHashMap<>();
                int[] bottomDepths = bottomDepths(sample, chunkHeight);
                for (int k = sample.topIdx; k >= 0; k--) {
                    if (!sample.solidMask[k]) {
                        continue;
                    }
                    int y = sample.islandBaseY + k;
                    if (y < 0 || y >= chunkHeight) {
                        continue;
                    }
                    IrisFloatingChildBiomes entry = sample.entryAt(k);
                    PaletteContext paletteContext = paletteContexts.get(entry);
                    if (paletteContext == null) {
                        paletteContext = createPaletteContext(parent, entry, dimension, wx, wz, colSeed, paletteDepth, data, complex);
                        paletteContexts.put(entry, paletteContext);
                    }
                    int depth = topDepthByEntry.getOrDefault(entry, 0);
                    int bottomDepth = bottomDepths == null || bottomDepths[k] < 0 ? depth : bottomDepths[k];
                    PlatformBlockState block = selectPaletteBlock(entry, paletteContext.topBlocks, paletteContext.bottomBlocks, depth, bottomDepth, paletteContext.fallbackSolid);
                    if (block != null) {
                        output.set(xf, y, zf, block);
                    }
                    topDepthByEntry.put(entry, depth + 1);
                }

                IrisFloatingChildBiomes entry = sample.entry;
                Integer localFluidHeight = entry.getLocalFluidHeight();
                if (localFluidHeight != null && localFluidHeight > 0) {
                    PlatformBlockState fluid = B.getStateOrNull(entry.getFluidBlock());
                    if (fluid == null) {
                        fluid = B.getState("minecraft:water");
                    }
                    int fluidCap = Math.min(sample.thickness - 1, localFluidHeight);
                    for (int k = 1; k <= fluidCap; k++) {
                        if (sample.solidMask[k]) {
                            continue;
                        }
                        int y = sample.islandBaseY + k;
                        if (y < 0 || y >= chunkHeight) {
                            continue;
                        }
                        boolean hasSolidBelow = false;
                        for (int kb = k - 1; kb >= 0; kb--) {
                            if (sample.solidMask[kb]) {
                                hasSolidBelow = true;
                                break;
                            }
                        }
                        if (hasSolidBelow) {
                            output.set(xf, y, zf, fluid);
                        }
                    }
                }

                writeIslandSkyBiomes(parent, wx, wz, sample, chunkHeight, data);
            }
        }

        engine.getMetrics().getTerrain().put(p.getMilliseconds());
    }

    /** Decorates the tops of the islands in the chunk whose first block is {@code x}, {@code z}. */
    public void decorateColumns(int x, int z, Hunk<PlatformBlockState> output, boolean multicore, ChunkContext context) {
        int chunkHeight = output.getHeight();
        IrisData data = engine.getData();
        IrisComplex complex = engine.getComplex();
        long baseSeed = engine.getSeedManager().getTerrain() ^ FLOATING_BASE_SEED_SALT;
        FloatingIslandBoundarySampler boundarySampler = context.getFloatingIslandBoundarySampler();

        for (int xf = 0; xf < 16; xf++) {
            for (int zf = 0; zf < 16; zf++) {
                int wx = x + xf;
                int wz = z + zf;
                IrisBiome parent = boundarySampler.parent(wx, wz);
                if (parent == null || parent.getFloatingChildBiomes() == null || parent.getFloatingChildBiomes().isEmpty()) {
                    continue;
                }
                FloatingIslandSample sample = FloatingIslandSample.sampleMemoized(parent, wx, wz, chunkHeight, baseSeed, data, engine, boundarySampler);
                if (sample == null) {
                    continue;
                }
                IrisFloatingChildBiomes entry = sample.entry;
                IrisBiome target = entry.getRealBiome(parent, data);

                if (!entry.isInheritDecorators() || target == null) {
                    continue;
                }

                int topY = sample.topY();
                int max = Math.max(1, chunkHeight - topY);
                if (topY + 1 < chunkHeight) {
                    PlatformBlockState above = output.get(xf, topY + 1, zf);
                    if (above == null || above.isAir()) {
                        try {
                            RNG colRng = rng.nextParallelRNG((int) FloatingIslandSample.columnSeed(baseSeed, wx, wz));
                            FloatingDecorator.decorateColumn(engine, target, DecorationPart.NONE, xf, zf, wx, wz, topY, max, output, colRng, NOOP_DECORATION_MISS);
                        } catch (Throwable e) {
                            IrisLogging.reportError(e);
                        }
                    }
                }

                Integer localFluidHeight = entry.getLocalFluidHeight();
                if (localFluidHeight != null && localFluidHeight > 0) {
                    int fluidCap = Math.min(sample.thickness - 1, localFluidHeight);
                    int fluidTopY = -1;
                    for (int k = 1; k <= fluidCap; k++) {
                        if (sample.solidMask[k]) {
                            continue;
                        }
                        int y = sample.islandBaseY + k;
                        if (y < 0 || y >= chunkHeight) {
                            continue;
                        }
                        boolean hasSolidBelow = false;
                        for (int kb = k - 1; kb >= 0; kb--) {
                            if (sample.solidMask[kb]) {
                                hasSolidBelow = true;
                                break;
                            }
                        }
                        if (hasSolidBelow && y > fluidTopY) {
                            fluidTopY = y;
                        }
                    }
                    if (fluidTopY > 0 && fluidTopY + 1 < chunkHeight && B.isAir(output.get(xf, fluidTopY + 1, zf))) {
                        try {
                            seaSurfaceDecorator.decorate(xf, zf, wx, wx + 1, wx - 1, wz, wz + 1, wz - 1, output, target, fluidTopY, chunkHeight);
                        } catch (Throwable e) {
                            IrisLogging.reportError(e);
                        }
                    }
                }
            }
        }
    }

    private void writeIslandSkyBiomes(IrisBiome parent, int wx, int wz, FloatingIslandSample sample, int chunkHeight, IrisData data) {
        try {
            IdentityHashMap<IrisFloatingChildBiomes, MatterBiomeInject> matterByEntry = new IdentityHashMap<>();
            for (int k = 0; k <= sample.topIdx; k++) {
                if (!sample.solidMask[k]) {
                    continue;
                }
                int y = sample.islandBaseY + k;
                if (y < 0 || y >= chunkHeight) {
                    continue;
                }
                IrisFloatingChildBiomes entry = sample.entryAt(k);
                MatterBiomeInject matter = matterByEntry.get(entry);
                if (matter == null) {
                    IrisBiome target = entry == null ? parent : entry.getRealBiome(parent, data);
                    if (target == null) {
                        continue;
                    }
                    matter = createSkyBiomeMatter(target, wx, wz);
                    matterByEntry.put(entry, matter);
                }
                engine.getMantle().getMantle().set(wx, y, wz, matter);
            }
        } catch (Throwable e) {
            IrisLogging.reportError(e);
        }
    }

    private MatterBiomeInject createSkyBiomeMatter(IrisBiome target, int wx, int wz) {
        if (target.isCustom()) {
            IrisBiomeCustom custom = target.getCustomBiome(rng, engine, wx, 0, wz);
            return BiomeInjectMatter.get(IrisPlatforms.get().biomeWriter().biomeIdFor(engine.getDimension().getLoadKey() + ":" + custom.getId()));
        }

        return BiomeInjectMatter.get(IrisPlatforms.get().biomeWriter().biomeIdFor(target.getSkyBiomeKey(rng, engine, wx, 0, wz)));
    }
}
