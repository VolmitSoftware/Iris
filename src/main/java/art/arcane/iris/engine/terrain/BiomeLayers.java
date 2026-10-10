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

package art.arcane.iris.engine.terrain;

import art.arcane.iris.builtin.prepare.SlopeClip;
import art.arcane.iris.builtin.terrain.CaveCeilingLayersSettings;
import art.arcane.iris.builtin.terrain.LayersSettings;
import art.arcane.iris.builtin.terrain.LockLayersMaxSettings;
import art.arcane.iris.builtin.terrain.LayerBlocks;
import art.arcane.iris.builtin.terrain.LockLayersSettings;
import art.arcane.iris.builtin.terrain.PaletteLayer;
import art.arcane.iris.builtin.terrain.SeaLayersSettings;
import art.arcane.iris.builtin.terrain.WallSettings;
import art.arcane.iris.core.loader.IrisData;
import art.arcane.iris.core.modules.IrisValues;
import art.arcane.iris.engine.IrisComplex;
import art.arcane.volmlib.util.cache.AtomicCache;
import art.arcane.iris.engine.framework.Engine;
import art.arcane.iris.engine.object.noise.NoiseStyle;
import art.arcane.iris.engine.object.world.biome.IrisBiome;
import art.arcane.iris.engine.object.world.dimension.IrisDimension;
import art.arcane.iris.spi.PlatformBlockState;
import art.arcane.iris.util.common.data.B;
import art.arcane.iris.util.project.noise.CNG;
import art.arcane.volmlib.util.collection.KList;
import art.arcane.volmlib.util.math.RNG;

import com.volmit.ModuleLog;
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

    private static final class States {
        private static final PlatformBlockState BARRIER = B.getState("BARRIER");
    }

    private final Supplier<LayerBlocks> layerBlocks;
    private final ModuleLog log;
    private final Map<PaletteLayer, ResolvedLayer> resolved = new ConcurrentHashMap<>();
    private final Map<LayersSettings, AtomicCache<KList<CNG>>> surfaceHeights = new ConcurrentHashMap<>();
    private final Map<SeaLayersSettings, AtomicCache<KList<CNG>>> seaHeights = new ConcurrentHashMap<>();
    private volatile Double busiestBiomeLayerCount;

    /** The layers of the world {@code engine} generates now, reporting failures to {@code log}. */
    public static BiomeLayers of(Engine engine, ModuleLog log) {
        return engine.getGeneration().state(BiomeLayers.class,
                generating -> new BiomeLayers(() -> LayerBlocks.of(generating.getGeneration().world()), log));
    }

    /** The layers of a world whose layer blocks {@code layerBlocks} gives, asked for when a layer first picks a block. */
    public BiomeLayers(Supplier<LayerBlocks> layerBlocks, ModuleLog log) {
        this.layerBlocks = layerBlocks;
        this.log = log;
    }

    /** {@code layer} ready for this world; its block picks are the world's {@link LayerBlocks}. */
    public ResolvedLayer resolve(PaletteLayer layer) {
        return resolved.computeIfAbsent(layer, declared -> ResolvedLayer.of(declared, layerBlocks));
    }

    /** The biome's wall, which places no block when the biome declares none. */
    public ResolvedLayer wall(IrisBiome biome) {
        PaletteLayer layer = biome.getModuleSettings().find(WallSettings.class).map(WallSettings::layer).orElse(NO_LAYER);
        return resolve(layer);
    }

    /** The layers of the biome's surface, top first. */
    public static List<PaletteLayer> layersOf(IrisBiome biome) {
        return surfaceSettings(biome).layers();
    }

    /** The noise style of each layer of the biome's surface, top first, as Iris's generators know them. */
    public static List<NoiseStyle> layerStyles(IrisBiome biome) {
        return layersOf(biome).stream().map(layer -> IrisValues.generatorStyle(layer.style()).getStyle()).toList();
    }

    /** The most layers any biome the dimension reaches has, which is what studio's layer load view compares a biome with. */
    public double busiestLayerCount(Engine engine) {
        Double cached = busiestBiomeLayerCount;
        if (cached != null) {
            return cached;
        }

        double busiest = 0D;
        for (IrisBiome biome : engine.getDimension().getReachableBiomes(engine)) {
            busiest = Math.max(busiest, layersOf(biome).size());
        }
        busiestBiomeLayerCount = busiest;
        return busiest;
    }

    /** The blocks the biome's layers stack up to at the column, top first, at most {@code maxDepth} of them. */
    public KList<PlatformBlockState> generateLayers(IrisBiome biome, IrisDimension dimension, double wx, double wz, RNG random, int maxDepth, int height, IrisData data, IrisComplex complex) {
        if (isLocked(biome)) {
            return generateLockedLayers(biome, wx, wz, random, maxDepth, height, complex);
        }

        KList<PlatformBlockState> blocks = new KList<>();
        if (maxDepth <= 0) {
            return blocks;
        }

        LayersSettings settings = surfaceSettings(biome);
        List<PaletteLayer> layers = settings.layers();
        if (layers.isEmpty()) {
            return blocks;
        }

        KList<CNG> heightNoises = surfaceHeightNoises(settings, random);
        for (int i = 0; i < layers.size(); i++) {
            ResolvedLayer layer = resolve(layers.get(i));
            double depth = layerDepth(layer, heightNoises.get(i), wx, wz, complex);
            if (depth <= 0) {
                continue;
            }

            stackLayer(blocks, layer, i, depth, wx, wz, random, maxDepth);
            if (blocks.size() >= maxDepth) {
                break;
            }

            separateLayers(blocks, dimension, maxDepth);
        }

        return blocks;
    }

    /** The blocks the biome's cave ceiling layers stack up to at the column, nearest the ceiling first, at most {@code maxDepth} of them. */
    public KList<PlatformBlockState> generateCeilingLayers(IrisBiome biome, IrisDimension dimension, double wx, double wz, RNG random, int maxDepth, int height, IrisData data, IrisComplex complex) {
        KList<PlatformBlockState> blocks = new KList<>();
        if (maxDepth <= 0) {
            return blocks;
        }

        List<PaletteLayer> ceilingLayers = biome.getModuleSettings().find(CaveCeilingLayersSettings.class)
                .map(CaveCeilingLayersSettings::layers).orElse(List.of());
        if (ceilingLayers.isEmpty()) {
            return blocks;
        }

        KList<CNG> heightNoises = surfaceHeightNoises(surfaceSettings(biome), random);
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

            separateLayers(blocks, dimension, maxDepth);
        }

        return blocks;
    }

    /** The blocks of the biome's sea layers at the column, from the water surface down, at most {@code maxDepth} of them. */
    public KList<PlatformBlockState> generateSeaLayers(IrisBiome biome, double wx, double wz, RNG random, int maxDepth, IrisData data) {
        KList<PlatformBlockState> blocks = new KList<>();
        SeaLayersSettings settings = biome.getModuleSettings().find(SeaLayersSettings.class).orElse(null);
        if (settings == null || settings.layers().isEmpty()) {
            return blocks;
        }

        List<PaletteLayer> layers = settings.layers();
        KList<CNG> heightNoises = seaHeightNoises(settings, random);
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

    private KList<PlatformBlockState> generateLockedLayers(IrisBiome biome, double wx, double wz, RNG random, int maxDepthLimit, int height, IrisComplex complex) {
        KList<PlatformBlockState> stacked = new KList<>();
        KList<PlatformBlockState> locked = new KList<>();
        int maxDepth = Math.min(maxDepthLimit, lockedLayersMax(biome));
        if (maxDepth <= 0) {
            return stacked;
        }

        LayersSettings settings = surfaceSettings(biome);
        List<PaletteLayer> layers = settings.layers();
        if (!layers.isEmpty()) {
            KList<CNG> heightNoises = surfaceHeightNoises(settings, random);
            for (int i = 0; i < layers.size(); i++) {
                ResolvedLayer layer = resolve(layers.get(i));
                double depth = layerDepth(layer, heightNoises.get(i), wx, wz, complex);
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

    private void stackLayer(KList<PlatformBlockState> blocks, ResolvedLayer layer, int index, double depth, double wx, double wz, RNG random, int maxDepth) {
        for (int j = 0; j < depth; j++) {
            if (blocks.size() >= maxDepth) {
                break;
            }

            addBlock(blocks, layer, index, j, wx, wz, random);
        }
    }

    private void stackLayerUnlimited(KList<PlatformBlockState> blocks, ResolvedLayer layer, int index, double depth, double wx, double wz, RNG random) {
        for (int j = 0; j < depth; j++) {
            addBlock(blocks, layer, index, j, wx, wz, random);
        }
    }

    /** Adds the layer's block {@code j} blocks down; a layer that fails to pick adds nothing. */
    private void addBlock(KList<PlatformBlockState> blocks, ResolvedLayer layer, int index, int j, double wx, double wz, RNG random) {
        double zoom = layer.zoom();
        try {
            blocks.add(layer.get(random, index + j, (wx + j) / zoom, j, (wz - j) / zoom));
        } catch (Throwable e) {
            log.error("A biome layer failed to pick a block", e);
        }
    }

    /** The barrier blocks a dimension puts between layers to show them apart. */
    private void separateLayers(KList<PlatformBlockState> blocks, IrisDimension dimension, int maxDepth) {
        if (!dimension.isExplodeBiomePalettes()) {
            return;
        }

        for (int j = 0; j < dimension.getExplodeBiomePaletteSize(); j++) {
            blocks.add(States.BARRIER);
            if (blocks.size() >= maxDepth) {
                break;
            }
        }
    }

    /** How thick the layer is at the column: nothing where the slope is outside the layer's limits. */
    private double layerDepth(ResolvedLayer layer, CNG heightNoise, double wx, double wz, IrisComplex complex) {
        double depth = layerThickness(layer, heightNoise, wx, wz);
        SlopeClip slope = layer.slopeCondition();
        if (!slope.isDefault() && !slope.isValid(complex.getSlopeStream().get(wx, wz))) {
            return 0;
        }

        return depth;
    }

    private double layerThickness(ResolvedLayer layer, CNG heightNoise, double wx, double wz) {
        double zoom = layer.zoom();
        return heightNoise.fit(layer.minHeight(), layer.maxHeight(), wx / zoom, wz / zoom);
    }

    private KList<CNG> surfaceHeightNoises(LayersSettings settings, RNG rng) {
        AtomicCache<KList<CNG>> cache = surfaceHeights.computeIfAbsent(settings, declared -> new AtomicCache<>());
        return heightNoises(cache, settings.layers(), SURFACE_HEIGHT_SEED, rng);
    }

    private KList<CNG> seaHeightNoises(SeaLayersSettings settings, RNG rng) {
        AtomicCache<KList<CNG>> cache = seaHeights.computeIfAbsent(settings, declared -> new AtomicCache<>());
        return heightNoises(cache, settings.layers(), SEA_HEIGHT_SEED, rng);
    }

    private KList<CNG> heightNoises(AtomicCache<KList<CNG>> cache, List<PaletteLayer> layers, int firstSeed, RNG rng) {
        KList<CNG> cached = cache.getIfPresent();
        if (cached != null) {
            return cached;
        }

        return cache.aquire(() -> {
            KList<CNG> noises = new KList<>();
            int seed = firstSeed;
            for (PaletteLayer layer : layers) {
                noises.add(resolve(layer).heightNoise(rng.nextParallelRNG((seed++) * seed * seed * seed)));
            }
            return noises;
        });
    }

    private static LayersSettings surfaceSettings(IrisBiome biome) {
        return biome.getModuleSettings().find(LayersSettings.class).orElse(DEFAULT_LAYERS);
    }

    private static boolean isLocked(IrisBiome biome) {
        return biome.getModuleSettings().find(LockLayersSettings.class).map(LockLayersSettings::locked).orElse(false);
    }

    private static int lockedLayersMax(IrisBiome biome) {
        return biome.getModuleSettings().find(LockLayersMaxSettings.class).map(LockLayersMaxSettings::layers).orElse(LockLayersMaxSettings.DEFAULT_LAYERS);
    }
}
