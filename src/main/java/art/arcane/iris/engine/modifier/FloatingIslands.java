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


import art.arcane.iris.engine.object.terrain.floating.FloatingChildren;
import art.arcane.iris.engine.object.terrain.floating.ResolvedFloatingChild;
import art.arcane.iris.builtin.floating.FloatingBottomPaletteMode;
import art.arcane.iris.builtin.prepare.SlopeClip;
import art.arcane.iris.builtin.terrain.PaletteLayer;
import art.arcane.iris.engine.object.world.biome.custom.IrisBiomeCustom;
import art.arcane.iris.core.loader.IrisData;
import art.arcane.iris.engine.IrisComplex;
import art.arcane.iris.engine.decorator.FloatingDecorator;
import art.arcane.iris.engine.decorator.IrisSeaSurfaceDecorator;
import art.arcane.iris.engine.framework.Engine;
import art.arcane.iris.engine.framework.EngineDecorator;
import art.arcane.iris.engine.object.terrain.floating.FloatingIslandBoundarySampler;
import art.arcane.iris.engine.object.terrain.floating.FloatingIslandSample;
import art.arcane.iris.engine.object.world.biome.IrisBiome;
import art.arcane.iris.engine.terrain.BiomeLayers;
import art.arcane.iris.engine.terrain.ResolvedLayer;
import art.arcane.iris.builtin.decorant.DecorationPart;
import art.arcane.iris.engine.object.world.dimension.IrisDimension;
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

import com.volmit.ModuleLog;
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
    private final BiomeLayers biomeLayers;
    private final ModuleLog log;

    private KList<PlatformBlockState> generateBottomPaletteLayers(ResolvedFloatingChild entry, IrisDimension dimension, double wx, double wz, RNG random, int paletteDepth, IrisData data, IrisComplex complex) {
        if (entry == null || entry.settings().bottomPaletteMode() != FloatingBottomPaletteMode.CUSTOM || entry.settings().bottomPalette() == null || entry.settings().bottomPalette().isEmpty()) {
            return null;
        }
        return generatePaletteLayers(dimension, new KList<>(entry.settings().bottomPalette()), wx, wz, random.nextParallelRNG(0xB0770B), paletteDepth, data, complex);
    }

    private KList<PlatformBlockState> generatePaletteLayers(IrisDimension dimension, KList<PaletteLayer> layers, double wx, double wz, RNG random, int maxDepth, IrisData data, IrisComplex complex) {
        KList<PlatformBlockState> generated = new KList<>();
        if (layers == null || layers.isEmpty() || maxDepth <= 0) {
            return generated;
        }

        int generatorSeed = 7235;
        for (int i = 0; i < layers.size(); i++) {
            ResolvedLayer layer = biomeLayers.resolve(layers.get(i));
            CNG heightGenerator = layer.heightNoise(random.nextParallelRNG((generatorSeed++) * generatorSeed * generatorSeed * generatorSeed));
            if (heightGenerator == null) {
                continue;
            }
            double layerDepth = heightGenerator.fit(layer.minHeight(), layer.maxHeight(), wx / layer.zoom(), wz / layer.zoom());
            SlopeClip slopeClip = layer.slopeCondition();

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
                    generated.add(layer.get(random.nextParallelRNG(i + j), (wx + j) / layer.zoom(), j, (wz - j) / layer.zoom()));
                } catch (Throwable e) {
                    log.error("Failed to pick a block of a floating island's palette", e);
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

    private static boolean usesBottomPalette(ResolvedFloatingChild entry) {
        FloatingBottomPaletteMode mode = entry == null || entry.settings().bottomPaletteMode() == null ? FloatingBottomPaletteMode.DEPTH : entry.settings().bottomPaletteMode();
        return mode != FloatingBottomPaletteMode.DEPTH;
    }

    private static int[] bottomDepths(FloatingIslandSample sample, int chunkHeight) {
        int[] bottomDepths = new int[sample.solidMask.length];
        for (int i = 0; i < bottomDepths.length; i++) {
            bottomDepths[i] = -1;
        }

        IdentityHashMap<ResolvedFloatingChild, Integer> depthByEntry = new IdentityHashMap<>();
        int max = Math.min(sample.topIdx, sample.solidMask.length - 1);
        for (int k = 0; k <= max; k++) {
            if (!sample.solidMask[k]) {
                continue;
            }
            int y = sample.islandBaseY + k;
            if (y < 0 || y >= chunkHeight) {
                continue;
            }
            ResolvedFloatingChild entry = sample.entryAt(k);
            int depth = depthByEntry.getOrDefault(entry, 0);
            bottomDepths[k] = depth;
            depthByEntry.put(entry, depth + 1);
        }

        return bottomDepths;
    }

    private static PlatformBlockState selectPaletteBlock(ResolvedFloatingChild entry, KList<PlatformBlockState> topBlocks, KList<PlatformBlockState> bottomBlocks, int topDepth, int bottomDepth, PlatformBlockState fallbackSolid) {
        FloatingBottomPaletteMode mode = entry == null || entry.settings().bottomPaletteMode() == null ? FloatingBottomPaletteMode.DEPTH : entry.settings().bottomPaletteMode();
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

    private PaletteContext createPaletteContext(IrisBiome parent, ResolvedFloatingChild entry, IrisDimension dimension, int wx, int wz, long colSeed, int paletteDepth, IrisData data, IrisComplex complex) {
        IrisBiome target = entry == null ? parent : entry.getRealBiome(parent, data);
        int entrySeed = entry == null || entry.settings().biome() == null ? 0 : entry.settings().biome().hashCode();
        RNG layerRng = rng.nextParallelRNG((int) (colSeed ^ 0x7A4E ^ entrySeed));
        KList<PlatformBlockState> topBlocks = target == null ? null : biomeLayers.generateLayers(target, dimension, wx, wz, layerRng, paletteDepth, paletteDepth, data, complex);
        if (topBlocks == null || topBlocks.isEmpty()) {
            topBlocks = biomeLayers.generateLayers(parent, dimension, wx, wz, layerRng, paletteDepth, paletteDepth, data, complex);
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

    public FloatingIslands(Engine engine, ModuleLog log) {
        this.engine = engine;
        this.log = log;
        rng = new RNG(engine.getSeedManager().getTerrain() ^ 0x7EB0A73F1DCE514DL);
        seaSurfaceDecorator = new IrisSeaSurfaceDecorator(engine);
        biomeLayers = BiomeLayers.of(engine, log);
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
                if (parent == null || FloatingChildren.declaredBy(parent).isEmpty()) {
                    continue;
                }
                FloatingIslandSample sample = FloatingIslandSample.sampleMemoized(parent, wx, wz, chunkHeight, baseSeed, data, engine, boundarySampler, log);
                if (sample == null) {
                    continue;
                }

                long colSeed = FloatingIslandSample.columnSeed(baseSeed, wx, wz);
                int paletteDepth = Math.max(4, sample.solidCount + 4);
                IdentityHashMap<ResolvedFloatingChild, PaletteContext> paletteContexts = new IdentityHashMap<>();
                IdentityHashMap<ResolvedFloatingChild, Integer> topDepthByEntry = new IdentityHashMap<>();
                int[] bottomDepths = bottomDepths(sample, chunkHeight);
                for (int k = sample.topIdx; k >= 0; k--) {
                    if (!sample.solidMask[k]) {
                        continue;
                    }
                    int y = sample.islandBaseY + k;
                    if (y < 0 || y >= chunkHeight) {
                        continue;
                    }
                    ResolvedFloatingChild entry = sample.entryAt(k);
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

                ResolvedFloatingChild entry = sample.entry;
                int localFluidHeight = entry.settings().localFluidHeight().orElse(0);
                if (localFluidHeight > 0) {
                    PlatformBlockState fluid = B.getStateOrNull(entry.settings().fluidBlock());
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
                if (parent == null || FloatingChildren.declaredBy(parent).isEmpty()) {
                    continue;
                }
                FloatingIslandSample sample = FloatingIslandSample.sampleMemoized(parent, wx, wz, chunkHeight, baseSeed, data, engine, boundarySampler, log);
                if (sample == null) {
                    continue;
                }
                ResolvedFloatingChild entry = sample.entry;
                IrisBiome target = entry.getRealBiome(parent, data);

                if (!entry.settings().inheritDecorators() || target == null) {
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
                            log.error("Failed to decorate the top of a floating island", e);
                        }
                    }
                }

                int localFluidHeight = entry.settings().localFluidHeight().orElse(0);
                if (localFluidHeight > 0) {
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
                            log.error("Failed to decorate the sea surface of a floating island", e);
                        }
                    }
                }
            }
        }
    }

    private void writeIslandSkyBiomes(IrisBiome parent, int wx, int wz, FloatingIslandSample sample, int chunkHeight, IrisData data) {
        try {
            IdentityHashMap<ResolvedFloatingChild, MatterBiomeInject> matterByEntry = new IdentityHashMap<>();
            for (int k = 0; k <= sample.topIdx; k++) {
                if (!sample.solidMask[k]) {
                    continue;
                }
                int y = sample.islandBaseY + k;
                if (y < 0 || y >= chunkHeight) {
                    continue;
                }
                ResolvedFloatingChild entry = sample.entryAt(k);
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
            log.error("Failed to write the sky biomes of a floating island", e);
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
