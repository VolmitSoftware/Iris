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
package art.arcane.iris.modules.terrain;

import art.arcane.iris.builtin.prepare.SlopeClip;
import art.arcane.iris.builtin.terrain.LayersSettings;
import art.arcane.iris.builtin.terrain.PaletteLayer;
import art.arcane.volmlib.util.cache.AtomicCache;
import art.arcane.volmlib.util.collection.KList;
import art.arcane.volmlib.util.math.RNG;
import com.volmit.ModuleLog;
import com.volmit.block.BlockState;
import com.volmit.config.FeatureSettings;
import com.volmit.noise.NoiseFactory;
import com.volmit.resource.ResourceId;
import com.volmit.world.PackWorld;

import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.Supplier;

/**
 * What the terrain module keeps for one world about the layers of its biomes: each layer made ready ({@link ResolvedLayer})
 * and the noise that sets the thickness of a biome's layers, and the blocks the layers of a biome stack up to at a column.
 * A biome's layers are its {@link LayersSettings}, {@link SeaLayersSettings} and {@link CaveCeilingLayersSettings}; a biome
 * that declares no layers has the one layer of grass the settings default to.
 *
 * <p>The noise of a biome's layers is built once, from the random of the first column asked for, as it always was.
 */
public final class BiomeLayers {
    private static final int SURFACE_HEIGHT_SEED = 7235;
    private static final int SEA_HEIGHT_SEED = 7735;
    private static final int LOCKED_LAYERS_REFERENCE_HEIGHT = 512;
    private static final LayersSettings DEFAULT_LAYERS = new LayersSettings();
    private static final PaletteLayer NO_LAYER = PaletteLayer.withoutBlocks();
    private static final BlockState BARRIER = new BlockState("minecraft:barrier");

    private final Supplier<LayerBlocks> layerBlocks;
    private final Supplier<NoiseFactory> noise;
    private final ModuleLog log;
    private final Map<PaletteLayer, ResolvedLayer> resolved = new ConcurrentHashMap<>();
    private final Map<LayersSettings, AtomicCache<KList<LayerNoise>>> surfaceHeights = new ConcurrentHashMap<>();
    private final Map<SeaLayersSettings, AtomicCache<KList<LayerNoise>>> seaHeights = new ConcurrentHashMap<>();
    private final Map<ResourceId, LayeredBiome> biomes = new ConcurrentHashMap<>();

    /** The layers of {@code world}. */
    public static BiomeLayers of(PackWorld world) {
        return world.state(BiomeLayers.class, generating -> new BiomeLayers(() -> LayerBlocks.of(generating), () -> generating.resources().noise(), generating.log()));
    }

    /** The layers of a world whose layer blocks {@code layerBlocks} and noise {@code noise} give, asked for when a layer first needs them. */
    public BiomeLayers(Supplier<LayerBlocks> layerBlocks, Supplier<NoiseFactory> noise, ModuleLog log) {
        this.layerBlocks = layerBlocks;
        this.noise = noise;
        this.log = log;
    }

    /** {@code layer} ready for this world; its block picks are the world's {@link LayerBlocks}. */
    public ResolvedLayer resolve(PaletteLayer layer) {
        return resolved.computeIfAbsent(layer, declared -> ResolvedLayer.of(declared, layerBlocks, noise));
    }

    /** What the biome {@code id} declares about its layers, read once from {@code settings}. */
    public LayeredBiome biome(ResourceId id, FeatureSettings settings) {
        LayeredBiome known = biomes.get(id);
        if (known != null) {
            return known;
        }
        return biomes.computeIfAbsent(id, ignored -> new LayeredBiome(
                settings.declaredBy(id, LayersSettings.class).orElse(null),
                settings.declaredBy(id, SeaLayersSettings.class).orElse(null),
                settings.declaredBy(id, CaveCeilingLayersSettings.class).orElse(null),
                settings.declaredBy(id, LockLayersSettings.class).orElse(null),
                settings.declaredBy(id, LockLayersMaxSettings.class).orElse(null),
                settings.declaredBy(id, WallSettings.class).orElse(null)));
    }

    /** The biome's wall, which places no block when the biome declares none. */
    public ResolvedLayer wall(LayeredBiome biome) {
        PaletteLayer layer = biome.wall() == null ? NO_LAYER : biome.wall().layer();
        return resolve(layer);
    }

    /** The blocks the biome's layers stack up to at the column, top first, at most {@code maxDepth} of them. */
    public KList<BlockState> generateLayers(LayeredBiome biome, PaletteExplosion explosion, double wx, double wz, RNG random, int maxDepth, int height,
                                            ColumnSlope slope) {
        if (isLocked(biome)) {
            return generateLockedLayers(biome, wx, wz, random, maxDepth, height, slope);
        }

        KList<BlockState> blocks = new KList<>();
        if (maxDepth <= 0) {
            return blocks;
        }

        LayersSettings settings = surfaceSettings(biome);
        List<PaletteLayer> layers = settings.layers();
        if (layers.isEmpty()) {
            return blocks;
        }

        KList<LayerNoise> heightNoises = surfaceHeightNoises(settings, random);
        for (int i = 0; i < layers.size(); i++) {
            ResolvedLayer layer = resolve(layers.get(i));
            double depth = layerDepth(layer, heightNoises.get(i), wx, wz, slope);
            if (depth <= 0) {
                continue;
            }

            stackLayer(blocks, layer, i, depth, wx, wz, random, maxDepth);
            if (blocks.size() >= maxDepth) {
                break;
            }

            separateLayers(blocks, explosion, maxDepth);
        }

        return blocks;
    }

    /** The blocks the biome's cave ceiling layers stack up to at the column, nearest the ceiling first, at most {@code maxDepth} of them. */
    public KList<BlockState> generateCeilingLayers(LayeredBiome biome, PaletteExplosion explosion, double wx, double wz, RNG random, int maxDepth) {
        KList<BlockState> blocks = new KList<>();
        if (maxDepth <= 0) {
            return blocks;
        }

        List<PaletteLayer> ceilingLayers = biome.ceilingLayers() == null ? List.of() : biome.ceilingLayers().layers();
        if (ceilingLayers.isEmpty()) {
            return blocks;
        }

        KList<LayerNoise> heightNoises = surfaceHeightNoises(surfaceSettings(biome), random);
        // Ceiling layers reuse the surface-layer height noise, so entries beyond the surface layers have none; skip them.
        int usableLayers = Math.min(ceilingLayers.size(), heightNoises.size());
        for (int i = 0; i < usableLayers; i++) {
            ResolvedLayer layer = resolve(ceilingLayers.get(i));
            double depth = layerThickness(layer, heightNoises.get(i), wx, wz);
            if (depth <= 0) {
                continue;
            }

            stackLayer(blocks, layer, i, depth, wx, wz, random, maxDepth);
            if (blocks.size() >= maxDepth) {
                break;
            }

            separateLayers(blocks, explosion, maxDepth);
        }

        return blocks;
    }

    /** The blocks of the biome's sea layers at the column, from the water surface down, at most {@code maxDepth} of them. */
    public KList<BlockState> generateSeaLayers(LayeredBiome biome, double wx, double wz, RNG random, int maxDepth) {
        KList<BlockState> blocks = new KList<>();
        SeaLayersSettings settings = biome.seaLayers();
        if (settings == null || settings.layers().isEmpty()) {
            return blocks;
        }

        List<PaletteLayer> layers = settings.layers();
        KList<LayerNoise> heightNoises = seaHeightNoises(settings, random);
        for (int i = 0; i < layers.size(); i++) {
            ResolvedLayer layer = resolve(layers.get(i));
            int depth = heightNoises.get(i).fit(layer.minHeight(), layer.maxHeight(), wx / layer.zoom(), wz / layer.zoom());
            if (depth < 0) {
                continue;
            }

            stackLayer(blocks, layer, i, depth, wx, wz, random, maxDepth);
            if (blocks.size() >= maxDepth) {
                break;
            }
        }

        return blocks;
    }

    /** The biome's surface layers, the one default layer when it declares none. */
    public List<PaletteLayer> surfaceLayers(LayeredBiome biome) {
        return surfaceSettings(biome).layers();
    }

    public List<PaletteLayer> seaLayers(LayeredBiome biome) {
        return biome.seaLayers() == null ? List.of() : biome.seaLayers().layers();
    }

    /** The biome's cave ceiling layers that place blocks: those with a surface layer at their place to borrow its thickness from. */
    public List<PaletteLayer> ceilingLayers(LayeredBiome biome) {
        List<PaletteLayer> declared = biome.ceilingLayers() == null ? List.of() : biome.ceilingLayers().layers();
        return declared.subList(0, Math.min(declared.size(), surfaceLayers(biome).size()));
    }

    /**
     * Surface layer {@code index}'s block {@code depth} blocks below its top at the column, as {@link #generateLayers} picks
     * it, leaving its slope condition aside; null where the layer is thinner there.
     */
    public BlockState surfaceLayerBlock(LayeredBiome biome, int index, int depth, double wx, double wz, RNG random) {
        LayersSettings settings = surfaceSettings(biome);
        ResolvedLayer layer = resolve(settings.layers().get(index));
        double thickness = layerThickness(layer, surfaceHeightNoises(settings, random).get(index), wx, wz);
        return blockAtDepth(layer, index, depth, thickness, wx, wz, random);
    }

    /** Sea layer {@code index}'s block {@code depth} blocks below the water surface, as {@link #generateSeaLayers} picks it. */
    public BlockState seaLayerBlock(LayeredBiome biome, int index, int depth, double wx, double wz, RNG random) {
        ResolvedLayer layer = resolve(seaLayers(biome).get(index));
        LayerNoise heightNoise = seaHeightNoises(biome.seaLayers(), random).get(index);
        int thickness = heightNoise.fit(layer.minHeight(), layer.maxHeight(), wx / layer.zoom(), wz / layer.zoom());
        return blockAtDepth(layer, index, depth, thickness, wx, wz, random);
    }

    /** Cave ceiling layer {@code index}'s block {@code depth} blocks below the ceiling, as {@link #generateCeilingLayers} picks it. */
    public BlockState ceilingLayerBlock(LayeredBiome biome, int index, int depth, double wx, double wz, RNG random) {
        ResolvedLayer layer = resolve(ceilingLayers(biome).get(index));
        double thickness = layerThickness(layer, surfaceHeightNoises(surfaceSettings(biome), random).get(index), wx, wz);
        return blockAtDepth(layer, index, depth, thickness, wx, wz, random);
    }

    // The layer's blocks are stacked from its top, as generation stacks them, so the first pick seeds its noise as the world's did.
    private BlockState blockAtDepth(ResolvedLayer layer, int index, int depth, double thickness, double wx, double wz, RNG random) {
        if (depth >= thickness) {
            return null;
        }
        KList<BlockState> stacked = new KList<>();
        stackLayer(stacked, layer, index, depth + 1, wx, wz, random, depth + 1);
        return stacked.size() > depth ? stacked.get(depth) : null;
    }

    private KList<BlockState> generateLockedLayers(LayeredBiome biome, double wx, double wz, RNG random, int maxDepthLimit, int height, ColumnSlope slope) {
        KList<BlockState> stacked = new KList<>();
        KList<BlockState> locked = new KList<>();
        int maxDepth = Math.min(maxDepthLimit, lockedLayersMax(biome));
        if (maxDepth <= 0) {
            return stacked;
        }

        LayersSettings settings = surfaceSettings(biome);
        List<PaletteLayer> layers = settings.layers();
        if (!layers.isEmpty()) {
            KList<LayerNoise> heightNoises = surfaceHeightNoises(settings, random);
            for (int i = 0; i < layers.size(); i++) {
                ResolvedLayer layer = resolve(layers.get(i));
                double depth = layerDepth(layer, heightNoises.get(i), wx, wz, slope);
                if (depth <= 0) {
                    continue;
                }

                stackLayerUnlimited(stacked, layer, i, depth, wx, wz, random);
            }
        }

        if (stacked.isEmpty()) {
            return locked;
        }

        for (int i = 0; i < maxDepth; i++) {
            int offset = (LOCKED_LAYERS_REFERENCE_HEIGHT - height) - i;
            locked.add(stacked.get(Math.max(offset % stacked.size(), 0)));
        }

        return locked;
    }

    private void stackLayer(KList<BlockState> blocks, ResolvedLayer layer, int index, double depth, double wx, double wz, RNG random, int maxDepth) {
        for (int j = 0; j < depth; j++) {
            if (blocks.size() >= maxDepth) {
                break;
            }

            addBlock(blocks, layer, index, j, wx, wz, random);
        }
    }

    private void stackLayerUnlimited(KList<BlockState> blocks, ResolvedLayer layer, int index, double depth, double wx, double wz, RNG random) {
        for (int j = 0; j < depth; j++) {
            addBlock(blocks, layer, index, j, wx, wz, random);
        }
    }

    /** Adds the layer's block {@code j} blocks down; a layer that fails to pick adds nothing. */
    private void addBlock(KList<BlockState> blocks, ResolvedLayer layer, int index, int j, double wx, double wz, RNG random) {
        double blocksPerSample = layer.blocksPerSample();
        try {
            blocks.add(layer.get(random, index + j, (wx + j) / blocksPerSample, j, (wz - j) / blocksPerSample));
        } catch (Throwable e) {
            log.error("A biome layer failed to pick a block", e);
        }
    }

    /** The barrier blocks a dimension puts between layers to show them apart. */
    private void separateLayers(KList<BlockState> blocks, PaletteExplosion explosion, int maxDepth) {
        if (!explosion.exploded()) {
            return;
        }

        for (int j = 0; j < explosion.size(); j++) {
            blocks.add(BARRIER);
            if (blocks.size() >= maxDepth) {
                break;
            }
        }
    }

    /** How thick the layer is at the column: nothing where the slope is outside the layer's limits. */
    private double layerDepth(ResolvedLayer layer, LayerNoise heightNoise, double wx, double wz, ColumnSlope slope) {
        double depth = layerThickness(layer, heightNoise, wx, wz);
        SlopeClip clip = layer.slopeCondition();
        if (!clip.isDefault() && !clip.isValid(slope.at(wx, wz))) {
            return 0;
        }

        return depth;
    }

    private double layerThickness(ResolvedLayer layer, LayerNoise heightNoise, double wx, double wz) {
        double zoom = layer.zoom();
        return heightNoise.fit(layer.minHeight(), layer.maxHeight(), wx / zoom, wz / zoom);
    }

    private KList<LayerNoise> surfaceHeightNoises(LayersSettings settings, RNG rng) {
        AtomicCache<KList<LayerNoise>> cache = surfaceHeights.computeIfAbsent(settings, declared -> new AtomicCache<>());
        return heightNoises(cache, settings.layers(), SURFACE_HEIGHT_SEED, rng);
    }

    private KList<LayerNoise> seaHeightNoises(SeaLayersSettings settings, RNG rng) {
        AtomicCache<KList<LayerNoise>> cache = seaHeights.computeIfAbsent(settings, declared -> new AtomicCache<>());
        return heightNoises(cache, settings.layers(), SEA_HEIGHT_SEED, rng);
    }

    private KList<LayerNoise> heightNoises(AtomicCache<KList<LayerNoise>> cache, List<PaletteLayer> layers, int firstSeed, RNG rng) {
        KList<LayerNoise> cached = cache.getIfPresent();
        if (cached != null) {
            return cached;
        }

        return cache.aquire(() -> {
            KList<LayerNoise> noises = new KList<>();
            int seed = firstSeed;
            for (PaletteLayer layer : layers) {
                noises.add(resolve(layer).heightNoise(rng.nextParallelRNG((seed++) * seed * seed * seed)));
            }
            return noises;
        });
    }

    private static LayersSettings surfaceSettings(LayeredBiome biome) {
        return biome.layers() == null ? DEFAULT_LAYERS : biome.layers();
    }

    private static boolean isLocked(LayeredBiome biome) {
        return biome.lock() != null && biome.lock().locked();
    }

    private static int lockedLayersMax(LayeredBiome biome) {
        return biome.lockMax() == null ? LockLayersMaxSettings.DEFAULT_LAYERS : biome.lockMax().layers();
    }
}
