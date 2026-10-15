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

package art.arcane.iris.engine.modifier;

import art.arcane.iris.builtin.IrisSeed;
import art.arcane.iris.builtin.decorant.DecorantSettings;
import art.arcane.iris.builtin.decorant.DecorationPart;
import art.arcane.iris.builtin.floating.FloatingBottomPaletteMode;
import art.arcane.iris.builtin.prepare.SlopeClip;
import art.arcane.iris.builtin.terrain.BiomeLayers;
import art.arcane.iris.builtin.terrain.ColumnSlope;
import art.arcane.iris.builtin.terrain.LayerNoise;
import art.arcane.iris.builtin.terrain.PaletteExplosion;
import art.arcane.iris.builtin.terrain.PaletteLayer;
import art.arcane.iris.builtin.terrain.ResolvedLayer;
import art.arcane.iris.engine.decorator.BiomeDecorators;
import art.arcane.iris.engine.decorator.DecoratedBiome;
import art.arcane.iris.engine.decorator.DecorationScene;
import art.arcane.iris.engine.decorator.FloatingDecorator;
import art.arcane.iris.engine.decorator.IrisSeaSurfaceDecorator;
import art.arcane.iris.engine.object.terrain.floating.FloatingIslandBoundarySampler;
import art.arcane.iris.engine.object.terrain.floating.FloatingIslandSample;
import art.arcane.iris.engine.object.terrain.floating.FloatingWorld;
import art.arcane.iris.engine.object.terrain.floating.ResolvedFloatingChild;
import art.arcane.volmlib.util.collection.KList;
import art.arcane.volmlib.util.math.RNG;
import com.volmit.ModuleLog;
import com.volmit.block.BlockRegistry;
import com.volmit.block.BlockState;
import com.volmit.config.FeatureSettings;
import com.volmit.hunk.Hunk;
import com.volmit.resource.ResourceId;
import com.volmit.stage.ChunkStageContext;
import com.volmit.world.BiomeKind;
import com.volmit.world.PackWorld;
import com.volmit.world.TerrainSampler;

import java.util.ArrayList;
import java.util.IdentityHashMap;
import java.util.List;
import java.util.Map;

/**
 * Fills and decorates the floating islands of the dimension's floating child biomes for {@link FloatingModule}:
 * the islands' solids (top layers, bottom palette, sea surface) and, later, the decoration on their tops. One
 * per world, holding the islands' random.
 */
final class FloatingIslands {
    private static final long ISLAND_RANDOM_SALT = 0x7EB0A73F1DCE514DL;
    private static final int BOTTOM_PALETTE_SALT = 0xB0770B;
    private static final int LAYER_RANDOM_SALT = 0x7A4E;
    private static final int FIRST_LAYER_GENERATOR_SEED = 7235;
    private static final int MIN_PALETTE_DEPTH = 4;
    private static final int CHUNK_WIDTH = 16;
    private static final int CHUNK_CENTRE = 7;
    private static final BlockState BARRIER = new BlockState("minecraft:barrier");
    private static final BlockState FALLBACK_SOLID = new BlockState("minecraft:stone");
    private static final String DEFAULT_FLUID = "minecraft:water";
    private static final Runnable NOOP_DECORATION_MISS = () -> {
    };

    private final FloatingWorld world;
    private final RNG rng;
    private final BiomeLayers biomeLayers;
    private final ModuleLog log;

    FloatingIslands(PackWorld world, ModuleLog log) {
        this.world = FloatingWorld.of(world);
        this.rng = new RNG(IrisSeed.TERRAIN.of(world) ^ ISLAND_RANDOM_SALT);
        this.biomeLayers = BiomeLayers.of(world);
        this.log = log;
    }

    /** The islands of {@code world}. */
    static FloatingIslands of(PackWorld world, ModuleLog log) {
        return world.state(FloatingIslands.class, generating -> new FloatingIslands(generating, log));
    }

    /** Fills the islands' solids into the chunk of {@code context}. */
    void fillSolids(ChunkStageContext context) {
        Chunk chunk = new Chunk(context);
        FloatingIslandSample.clearChunkMemo();

        for (int xf = 0; xf < CHUNK_WIDTH; xf++) {
            for (int zf = 0; zf < CHUNK_WIDTH; zf++) {
                fillColumn(chunk, xf, zf);
            }
        }
    }

    private void fillColumn(Chunk chunk, int xf, int zf) {
        int wx = chunk.originX + xf;
        int wz = chunk.originZ + zf;
        ResourceId parent = chunk.boundarySampler.parent(wx, wz);
        if (parent == null || world.declaredBy(parent).isEmpty()) {
            return;
        }
        FloatingIslandSample sample = FloatingIslandSample.sampleMemoized(parent, wx, wz, chunk.height, world, chunk.boundarySampler, log);
        if (sample == null) {
            return;
        }

        long colSeed = FloatingIslandSample.columnSeed(world.baseSeed(), wx, wz);
        int paletteDepth = Math.max(MIN_PALETTE_DEPTH, sample.solidCount + MIN_PALETTE_DEPTH);
        Map<ResolvedFloatingChild, PaletteContext> paletteContexts = new IdentityHashMap<>();
        Map<ResolvedFloatingChild, Integer> topDepthByEntry = new IdentityHashMap<>();
        int[] bottomDepths = bottomDepths(sample, chunk.height);
        for (int k = sample.topIdx; k >= 0; k--) {
            if (!sample.solidMask[k]) {
                continue;
            }
            int y = sample.islandBaseY + k;
            if (y < 0 || y >= chunk.height) {
                continue;
            }
            ResolvedFloatingChild entry = sample.entryAt(k);
            PaletteContext paletteContext = paletteContexts.computeIfAbsent(entry,
                    ignored -> createPaletteContext(chunk, parent, entry, wx, wz, colSeed, paletteDepth));
            int depth = topDepthByEntry.getOrDefault(entry, 0);
            int bottomDepth = bottomDepths[k] < 0 ? depth : bottomDepths[k];
            BlockState block = selectPaletteBlock(entry, paletteContext.topBlocks(), paletteContext.bottomBlocks(), depth, bottomDepth);
            if (block != null) {
                chunk.output.set(xf, y, zf, block);
            }
            topDepthByEntry.put(entry, depth + 1);
        }

        fillLocalFluid(chunk, sample, xf, zf);
        paintSkyBiomes(parent, wx, wz, sample, chunk.height);
    }

    private void fillLocalFluid(Chunk chunk, FloatingIslandSample sample, int xf, int zf) {
        int localFluidHeight = sample.entry.settings().localFluidHeight().orElse(0);
        if (localFluidHeight <= 0) {
            return;
        }
        BlockState fluid = fluidOf(chunk.blocks, sample.entry.settings().fluidBlock());
        int fluidCap = Math.min(sample.thickness - 1, localFluidHeight);
        for (int k = 1; k <= fluidCap; k++) {
            if (sample.solidMask[k]) {
                continue;
            }
            int y = sample.islandBaseY + k;
            if (y < 0 || y >= chunk.height) {
                continue;
            }
            if (hasSolidBelow(sample, k)) {
                chunk.output.set(xf, y, zf, fluid);
            }
        }
    }

    private static BlockState fluidOf(BlockRegistry blocks, String key) {
        try {
            return blocks.resolve(key);
        } catch (IllegalArgumentException unknown) {
            return blocks.resolve(DEFAULT_FLUID);
        }
    }

    private static boolean hasSolidBelow(FloatingIslandSample sample, int k) {
        for (int kb = k - 1; kb >= 0; kb--) {
            if (sample.solidMask[kb]) {
                return true;
            }
        }
        return false;
    }

    /** Decorates the tops of the islands in the chunk of {@code context}. */
    void decorateColumns(ChunkStageContext context) {
        Chunk chunk = new Chunk(context);
        DecorationScene scene = DecorationScene.of(context);

        for (int xf = 0; xf < CHUNK_WIDTH; xf++) {
            for (int zf = 0; zf < CHUNK_WIDTH; zf++) {
                decorateColumn(chunk, scene, xf, zf);
            }
        }
    }

    private void decorateColumn(Chunk chunk, DecorationScene scene, int xf, int zf) {
        int wx = chunk.originX + xf;
        int wz = chunk.originZ + zf;
        ResourceId parent = chunk.boundarySampler.parent(wx, wz);
        if (parent == null || world.declaredBy(parent).isEmpty()) {
            return;
        }
        FloatingIslandSample sample = FloatingIslandSample.sampleMemoized(parent, wx, wz, chunk.height, world, chunk.boundarySampler, log);
        if (sample == null) {
            return;
        }
        ResolvedFloatingChild entry = sample.entry;
        ResourceId target = entry.realBiome(parent);
        if (!entry.settings().inheritDecorators() || target == null) {
            return;
        }

        DecoratedBiome decorated = decoratedBiome(chunk.settings, target);
        decorateTop(chunk, scene, sample, decorated, xf, zf, wx, wz);
        decorateLocalFluid(chunk, scene, sample, decorated, xf, zf, wx, wz);
    }

    private void decorateTop(Chunk chunk, DecorationScene scene, FloatingIslandSample sample, DecoratedBiome decorated,
                             int xf, int zf, int wx, int wz) {
        int topY = sample.topY();
        if (topY + 1 >= chunk.height) {
            return;
        }
        BlockState above = chunk.output.get(xf, topY + 1, zf);
        if (above != null && !scene.isAir(above)) {
            return;
        }
        int max = Math.max(1, chunk.height - topY);
        try {
            RNG colRng = rng.nextParallelRNG((int) FloatingIslandSample.columnSeed(world.baseSeed(), wx, wz));
            FloatingDecorator.decorateColumn(scene, decorated, DecorationPart.NONE, xf, zf, wx, wz, topY, max, colRng, NOOP_DECORATION_MISS);
        } catch (Throwable e) {
            log.error("Failed to decorate the top of a floating island", e);
        }
    }

    private void decorateLocalFluid(Chunk chunk, DecorationScene scene, FloatingIslandSample sample, DecoratedBiome decorated,
                                    int xf, int zf, int wx, int wz) {
        int localFluidHeight = sample.entry.settings().localFluidHeight().orElse(0);
        if (localFluidHeight <= 0) {
            return;
        }
        int fluidCap = Math.min(sample.thickness - 1, localFluidHeight);
        int fluidTopY = -1;
        for (int k = 1; k <= fluidCap; k++) {
            if (sample.solidMask[k]) {
                continue;
            }
            int y = sample.islandBaseY + k;
            if (y < 0 || y >= chunk.height) {
                continue;
            }
            if (hasSolidBelow(sample, k) && y > fluidTopY) {
                fluidTopY = y;
            }
        }
        if (fluidTopY <= 0 || fluidTopY + 1 >= chunk.height) {
            return;
        }
        BlockState above = chunk.output.get(xf, fluidTopY + 1, zf);
        if (above != null && !scene.isAir(above)) {
            return;
        }
        try {
            new IrisSeaSurfaceDecorator(scene).decorate(xf, zf, wx, wx + 1, wx - 1, wz, wz + 1, wz - 1, decorated, fluidTopY, chunk.height);
        } catch (Throwable e) {
            log.error("Failed to decorate the sea surface of a floating island", e);
        }
    }

    /** The decorators' view of the island's biome, which always decorates as land. */
    private static DecoratedBiome decoratedBiome(FeatureSettings settings, ResourceId biome) {
        return new DecoratedBiome(BiomeDecorators.biomeKey(biome), settings.declaredBy(biome, DecorantSettings.class).orElse(null), BiomeKind.LAND);
    }

    private PaletteContext createPaletteContext(Chunk chunk, ResourceId parent, ResolvedFloatingChild entry, int wx, int wz, long colSeed,
                                                int paletteDepth) {
        ResourceId target = entry == null ? parent : entry.realBiome(parent);
        int entrySeed = entry == null ? 0 : entry.settings().biome().hashCode();
        RNG layerRng = rng.nextParallelRNG((int) (colSeed ^ LAYER_RANDOM_SALT ^ entrySeed));
        KList<BlockState> topBlocks = target == null ? null : surfaceLayers(chunk, target, wx, wz, layerRng, paletteDepth);
        if (topBlocks == null || topBlocks.isEmpty()) {
            topBlocks = surfaceLayers(chunk, parent, wx, wz, layerRng, paletteDepth);
        }
        List<BlockState> bottomBlocks = generateBottomPaletteLayers(chunk, entry, wx, wz, layerRng, paletteDepth);
        return new PaletteContext(topBlocks, bottomBlocks);
    }

    private KList<BlockState> surfaceLayers(Chunk chunk, ResourceId biome, int wx, int wz, RNG layerRng, int paletteDepth) {
        return biomeLayers.generateLayers(biomeLayers.biome(biome, chunk.settings), chunk.explosion, wx, wz, layerRng, paletteDepth,
                paletteDepth, chunk.slope);
    }

    private List<BlockState> generateBottomPaletteLayers(Chunk chunk, ResolvedFloatingChild entry, double wx, double wz, RNG random,
                                                         int paletteDepth) {
        if (entry == null || entry.settings().bottomPaletteMode() != FloatingBottomPaletteMode.CUSTOM || entry.settings().bottomPalette().isEmpty()) {
            return null;
        }
        return generatePaletteLayers(chunk, entry.settings().bottomPalette(), wx, wz, random.nextParallelRNG(BOTTOM_PALETTE_SALT), paletteDepth);
    }

    private List<BlockState> generatePaletteLayers(Chunk chunk, List<PaletteLayer> layers, double wx, double wz, RNG random, int maxDepth) {
        List<BlockState> generated = new ArrayList<>();
        if (layers.isEmpty() || maxDepth <= 0) {
            return generated;
        }

        int generatorSeed = FIRST_LAYER_GENERATOR_SEED;
        for (int i = 0; i < layers.size(); i++) {
            ResolvedLayer layer = biomeLayers.resolve(layers.get(i));
            LayerNoise heightGenerator = layer.heightNoise(random.nextParallelRNG((generatorSeed++) * generatorSeed * generatorSeed * generatorSeed));
            if (heightGenerator == null) {
                continue;
            }
            double layerDepth = heightGenerator.fit(layer.minHeight(), layer.maxHeight(), wx / layer.zoom(), wz / layer.zoom());
            SlopeClip slopeClip = layer.slopeCondition();

            if (slopeClip != null && !slopeClip.isDefault() && !slopeClip.isValid(chunk.slope.at(wx, wz))) {
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

            if (chunk.explosion.exploded()) {
                for (int j = 0; j < chunk.explosion.size(); j++) {
                    generated.add(BARRIER);

                    if (generated.size() >= maxDepth) {
                        break;
                    }
                }
            }
        }

        return generated;
    }

    private static int[] bottomDepths(FloatingIslandSample sample, int chunkHeight) {
        int[] bottomDepths = new int[sample.solidMask.length];
        java.util.Arrays.fill(bottomDepths, -1);

        Map<ResolvedFloatingChild, Integer> depthByEntry = new IdentityHashMap<>();
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

    private static BlockState selectPaletteBlock(ResolvedFloatingChild entry, List<BlockState> topBlocks, List<BlockState> bottomBlocks,
                                                 int topDepth, int bottomDepth) {
        FloatingBottomPaletteMode mode = entry == null ? FloatingBottomPaletteMode.DEPTH : entry.settings().bottomPaletteMode();
        if (mode == FloatingBottomPaletteMode.MIRROR_TOP) {
            return paletteBlock(topBlocks, Math.min(topDepth, bottomDepth));
        }
        if (mode == FloatingBottomPaletteMode.CUSTOM && bottomDepth < topDepth) {
            if (bottomBlocks != null && !bottomBlocks.isEmpty()) {
                return paletteBlock(bottomBlocks, bottomDepth);
            }
            return paletteBlock(topBlocks, bottomDepth);
        }
        return paletteBlock(topBlocks, topDepth);
    }

    private static BlockState paletteBlock(List<BlockState> blocks, int depth) {
        if (blocks == null || blocks.isEmpty()) {
            return FALLBACK_SOLID;
        }
        BlockState block = depth >= 0 && depth < blocks.size() ? blocks.get(depth) : blocks.getLast();
        return block == null ? FALLBACK_SOLID : block;
    }

    /** Paints, for each child the column's island is made of, its biome's sky biome into the child's solid blocks. */
    private void paintSkyBiomes(ResourceId parent, int wx, int wz, FloatingIslandSample sample, int chunkHeight) {
        try {
            Map<ResolvedFloatingChild, List<Integer>> solidYsByEntry = new IdentityHashMap<>();
            for (int k = 0; k <= sample.topIdx; k++) {
                if (!sample.solidMask[k]) {
                    continue;
                }
                int y = sample.islandBaseY + k;
                if (y < 0 || y >= chunkHeight) {
                    continue;
                }
                solidYsByEntry.computeIfAbsent(sample.entryAt(k), ignored -> new ArrayList<>()).add(y);
            }
            for (Map.Entry<ResolvedFloatingChild, List<Integer>> solid : solidYsByEntry.entrySet()) {
                ResolvedFloatingChild entry = solid.getKey();
                ResourceId target = entry == null ? parent : entry.realBiome(parent);
                if (target == null) {
                    continue;
                }
                int[] ys = solid.getValue().stream().mapToInt(Integer::intValue).toArray();
                world.host().paintSkyBiome(target, wx, wz, ys, rng);
            }
        } catch (Throwable e) {
            log.error("Failed to write the sky biomes of a floating island", e);
        }
    }

    private record PaletteContext(List<BlockState> topBlocks, List<BlockState> bottomBlocks) {
    }

    /** What one chunk's columns share: its views of the blocks, terrain and settings, and its island fields. */
    private final class Chunk {
        final int originX;
        final int originZ;
        final Hunk<BlockState> output;
        final int height;
        final BlockRegistry blocks;
        final FeatureSettings settings;
        final PaletteExplosion explosion;
        final ColumnSlope slope;
        final FloatingIslandBoundarySampler boundarySampler;

        Chunk(ChunkStageContext context) {
            this.originX = context.originX();
            this.originZ = context.originZ();
            this.output = context.hunk(BlockState.class);
            this.height = output.getHeight();
            this.blocks = context.resources().blocks();
            this.settings = context.settings();
            this.explosion = PaletteExplosion.of(settings, originX + CHUNK_CENTRE, originZ + CHUNK_CENTRE);
            TerrainSampler terrain = context.terrain();
            this.slope = (x, z) -> terrain.column((int) x, (int) z).slope();
            this.boundarySampler = new FloatingIslandBoundarySampler(world.host()::surfaceBiome);
        }
    }
}
