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

package art.arcane.iris.generation.biome;

import art.arcane.iris.generation.block.IrisBlockData;
import art.arcane.iris.generation.noise.IrisGeneratorStyle;
import art.arcane.iris.generation.noise.NoiseStyle;
import art.arcane.iris.generation.terrain.IrisSlopeClip;

import art.arcane.iris.pack.loading.IrisData;
import art.arcane.iris.generation.cache.AtomicCache;
import art.arcane.iris.generation.cache.LazyBoundedCache;
import art.arcane.iris.generation.runtime.Engine;
import art.arcane.iris.pack.schema.annotation.ArrayType;
import art.arcane.iris.pack.schema.annotation.DependsOn;
import art.arcane.volmlib.util.documentation.Description;
import art.arcane.iris.pack.schema.annotation.MaxNumber;
import art.arcane.iris.pack.schema.annotation.MinNumber;
import art.arcane.iris.pack.schema.annotation.Required;
import art.arcane.iris.pack.schema.annotation.Snippet;
import art.arcane.volmlib.util.collection.KList;
import art.arcane.volmlib.util.math.RNG;
import art.arcane.volmlib.util.noise.CNG;
import lombok.AccessLevel;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.Getter;
import lombok.NoArgsConstructor;
import art.arcane.iris.spi.PlatformBlockState;
import lombok.Setter;
import lombok.experimental.Accessors;

import java.util.concurrent.atomic.AtomicReference;

@Snippet("biome-palette")
@Accessors(chain = true)
@NoArgsConstructor
@AllArgsConstructor
@Description("A layer of surface / subsurface material in biomes")
@Data
public class IrisBiomePaletteLayer {
    private static final int LAYER_GENERATOR_CACHE_SIZE = 32;

    private final transient AtomicCache<KList<PlatformBlockState>> blockData = new AtomicCache<>();
    @Getter(AccessLevel.NONE)
    @Setter(AccessLevel.NONE)
    private final transient LazyBoundedCache<LayerGeneratorKey, CNG> layerGenerators =
            new LazyBoundedCache<>(LAYER_GENERATOR_CACHE_SIZE);
    @Getter(AccessLevel.NONE)
    @Setter(AccessLevel.NONE)
    private final transient AtomicReference<CachedLayerGenerator> recentLayerGenerator = new AtomicReference<>();
    private final transient AtomicCache<CNG> heightGenerator = new AtomicCache<>();
    @Description("The style of noise")
    private IrisGeneratorStyle style = NoiseStyle.STATIC.style();
    @DependsOn({"minHeight", "maxHeight"})
    @MinNumber(0)
    @MaxNumber(2032)

    @Description("The min thickness of this layer")
    private int minHeight = 1;
    @DependsOn({"minHeight", "maxHeight"})
    @MinNumber(1)
    @MaxNumber(2032)

    @Description("The max thickness of this layer")
    private int maxHeight = 1;
    @Description("If set, this layer only shows where the terrain slope is within these bounds; outside them the layer is skipped entirely. Thickness is not scaled by slope.")
    private IrisSlopeClip slopeCondition = new IrisSlopeClip();
    @MinNumber(0.0001)
    @Description("The terrain zoom mostly for zooming in on a wispy palette")
    private double zoom = 5;
    @Required
    @ArrayType(min = 1, type = IrisBlockData.class)
    @Description("The palette of blocks to be used in this layer")
    private KList<IrisBlockData> palette = new KList<IrisBlockData>().qadd(new IrisBlockData("GRASS_BLOCK"));

    public CNG getHeightGenerator(RNG rng, IrisData data) {
        CNG cached = heightGenerator.getIfPresent();

        if (cached != null) {
            return cached;
        }

        return heightGenerator.aquire(() -> CNG.signature(rng.nextParallelRNG(minHeight * maxHeight + getBlockData(data).size())));
    }

    public PlatformBlockState get(RNG rng, double x, double y, double z, IrisData data) {
        return get(rng, 0, x, y, z, data);
    }

    /**
     * Resolves a block for this layer without allocating a child RNG per call. The child RNG is
     * only built inside the lazy layer generator initializer, using the exact same seed derivation
     * as {@code parent.nextParallelRNG(signature)} followed by the generator signature.
     */
    public PlatformBlockState get(RNG parent, int signature, double x, double y, double z, IrisData data) {
        KList<PlatformBlockState> localBlockData = getBlockData(data);

        if (localBlockData.isEmpty()) {
            return null;
        }

        if (localBlockData.size() == 1) {
            return localBlockData.get(0);
        }

        double localZoom = zoom;
        double scaledX = x / localZoom;
        double scaledY = y / localZoom;
        double scaledZ = z / localZoom;
        return getLayerGenerator(parent, signature, data).fit(localBlockData, scaledX, scaledY, scaledZ);
    }

    public CNG getLayerGenerator(RNG rng, IrisData data) {
        return getLayerGenerator(rng, 0, data);
    }

    public CNG getLayerGenerator(RNG parent, int signature, IrisData data) {
        Engine engine = data == null ? null : data.getEngine();
        long generatorSeed = parent.getSeed() + signature + minHeight + maxHeight + getBlockData(data).size();
        CachedLayerGenerator recent = recentLayerGenerator.get();
        if (recent != null && recent.key.matches(data, engine, generatorSeed)) {
            return recent.generator;
        }

        LayerGeneratorKey key = new LayerGeneratorKey(data, engine, generatorSeed);
        CNG generator = layerGenerators.computeIfAbsent(key,
                ignored -> style.create(new RNG(generatorSeed), data, engine));
        if (generator != null) {
            recentLayerGenerator.set(new CachedLayerGenerator(key, generator));
        }
        return generator;
    }

    public KList<IrisBlockData> add(String b) {
        palette.add(new IrisBlockData(b));

        return palette;
    }

    public KList<PlatformBlockState> getBlockData(IrisData data) {
        KList<PlatformBlockState> cached = blockData.getIfPresent();

        if (cached != null) {
            return cached;
        }

        return blockData.aquire(() ->
        {
            KList<PlatformBlockState> blockData = new KList<>();
            for (IrisBlockData ix : palette) {
                PlatformBlockState bx = ix.getBlockData(data);
                if (bx != null) {
                    for (int i = 0; i < ix.getWeight(); i++) {
                        blockData.add(bx);
                    }
                }
            }

            return blockData;
        });
    }

    public IrisBiomePaletteLayer zero() {
        palette.clear();
        return this;
    }

    private static final class LayerGeneratorKey {
        private final IrisData data;
        private final Engine engine;
        private final long seed;

        private LayerGeneratorKey(IrisData data, Engine engine, long seed) {
            this.data = data;
            this.engine = engine;
            this.seed = seed;
        }

        private boolean matches(IrisData data, Engine engine, long seed) {
            return this.data == data && this.engine == engine && this.seed == seed;
        }

        @Override
        public boolean equals(Object object) {
            if (this == object) {
                return true;
            }
            if (!(object instanceof LayerGeneratorKey other)) {
                return false;
            }
            return data == other.data && engine == other.engine && seed == other.seed;
        }

        @Override
        public int hashCode() {
            int result = System.identityHashCode(data);
            result = 31 * result + System.identityHashCode(engine);
            result = 31 * result + Long.hashCode(seed);
            return result;
        }
    }

    private static final class CachedLayerGenerator {
        private final LayerGeneratorKey key;
        private final CNG generator;

        private CachedLayerGenerator(LayerGeneratorKey key, CNG generator) {
            this.key = key;
            this.generator = generator;
        }
    }
}
