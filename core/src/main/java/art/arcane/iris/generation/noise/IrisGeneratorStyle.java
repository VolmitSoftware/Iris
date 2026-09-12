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

package art.arcane.iris.generation.noise;

import art.arcane.iris.generation.image.IrisImageMap;

import art.arcane.iris.pack.loading.IrisData;
import art.arcane.iris.generation.cache.LazyBoundedCache;
import art.arcane.iris.generation.runtime.Engine;
import art.arcane.iris.world.history.GenerationKernelRegistry;
import art.arcane.volmlib.util.documentation.Description;
import art.arcane.iris.pack.schema.annotation.MaxNumber;
import art.arcane.iris.pack.schema.annotation.MinNumber;
import art.arcane.iris.pack.schema.annotation.RegistryListResource;
import art.arcane.iris.pack.schema.annotation.Snippet;
import art.arcane.iris.spi.IrisLogging;
import art.arcane.volmlib.util.math.RNG;
import art.arcane.volmlib.util.noise.CNG;
import lombok.AccessLevel;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import lombok.experimental.Accessors;

import java.io.File;
import java.io.IOException;
import java.util.Objects;
import java.util.concurrent.ConcurrentHashMap;

@Snippet("style")
@Accessors(chain = true)
@NoArgsConstructor
@AllArgsConstructor
@Description("A gen style")
@Data
public class IrisGeneratorStyle {
    private static final int GENERATOR_CACHE_SIZE = 8;
    private static final ConcurrentHashMap<String, String> ACTIVE_CACHE_KEYS = new ConcurrentHashMap<>();
    @Getter(AccessLevel.NONE)
    @Setter(AccessLevel.NONE)
    private final transient LazyBoundedCache<GeneratorCacheKey, CNG> generatorCache =
            new LazyBoundedCache<>(GENERATOR_CACHE_SIZE);
    @Description("The base noise style. Used when neither expression nor imageMap is set; a failed expression also falls back to this style.")
    private NoiseStyle style = NoiseStyle.FLAT;

    @Description("If set above 0, this style will be cellularized")
    private double cellularFrequency = 0;

    @Description("Cell zooms")
    private double cellularZoom = 1;
    @MinNumber(0.00001)
    @Description("Feature size multiplier. Built-in coherent noise uses a 64-block base scale; 2 doubles feature size.")
    private double zoom = 1;
    @Description("Instead of using the style property, use a custom expression to represent this style.")
    @RegistryListResource(IrisExpression.class)
    private String expression = null;
    @Description("Use an Image map instead of a generated value")
    @RegistryListResource(IrisImageMap.class)
    private String imageMap = null;
    @MinNumber(0.00001)
    @Description("The Output multiplier. Only used if parent is fracture.")
    private double multiplier = 1;
    @Description("Apply a generator to the coordinate field fed into this parent generator. I.e. Distort your generator with another generator.")
    private IrisGeneratorStyle fracture = null;
    @MinNumber(0.01562)
    @MaxNumber(64)
    @Description("The exponent")
    private double exponent = 1;
    @MinNumber(0)
    @MaxNumber(8192)
    @Description("If the cache size is set above 0, this generator will be cached")
    private int cacheSize = 0;

    public IrisGeneratorStyle(NoiseStyle s) {
        this.style = s;
    }

    public IrisGeneratorStyle zoomed(double z) {
        this.zoom = z;
        return this;
    }

    public CNG createNoCache(RNG rng, IrisData data) {
        return createNoCache(rng, data, 1, 0, false, resolveEngine(data));
    }

    public CNG createForLayer(RNG rng, IrisData data, int octaveMultiplier) {
        return createNoCache(rng, data, Math.clamp(octaveMultiplier, 1, 16), 0, false, resolveEngine(data));
    }

    public CNG createForPrebake(RNG rng, IrisData data, int fallbackCacheSize) {
        return createNoCache(rng, data, 1, Math.max(0, fallbackCacheSize), true, resolveEngine(data));
    }


    private int imageMapHash() {
        if (imageMap == null) {
            return 0;
        }

        return imageMap.hashCode();
    }

    private int hash() {
        return Objects.hash(expression, imageMapHash(), multiplier, fracture != null ? fracture.hash() : 0, exponent, cacheSize, zoom, cellularZoom, cellularFrequency, style);
    }

    public int prebakeSignature() {
        return hash();
    }

    private String cachePrefix(RNG rng, int effectiveCacheSize, long engineStamp, int octaveMultiplier) {
        return "noise-" + NoiseCacheIdentity.FINGERPRINT + "-style-" + Integer.toUnsignedString(hash())
                + "-seed-" + Long.toUnsignedString(rng.getSeed())
                + "-eng-" + Long.toUnsignedString(engineStamp)
                + "-oct-" + octaveMultiplier
                + "-sz-" + effectiveCacheSize
                + "-src-";
    }

    private String cacheKey(RNG rng, long sourceStamp, int effectiveCacheSize, long engineStamp, int octaveMultiplier) {
        return cachePrefix(rng, effectiveCacheSize, engineStamp, octaveMultiplier) + Long.toUnsignedString(sourceStamp);
    }

    private void clearStaleCacheEntries(IrisData data, String prefix, String key) {
        File cacheFolder = new File(data.getDataFolder(), ".cache");
        String cacheFolderKey = cacheFolder.getAbsolutePath() + File.separator + prefix;
        String previousKey = ACTIVE_CACHE_KEYS.put(cacheFolderKey, key);
        if (key.equals(previousKey)) {
            return;
        }
        File[] files = cacheFolder.listFiles((dir, name) -> name.endsWith(".cnm") && name.startsWith(prefix));
        if (files == null) {
            return;
        }

        String keepFile = key + ".cnm";
        for (File file : files) {
            if (keepFile.equals(file.getName())) {
                continue;
            }

            if (!file.delete()) {
                IrisLogging.debug("Unable to delete stale noise cache " + file.getName());
            }
        }
    }

    private CNG createNoCache(RNG rng, IrisData data, int octaveMultiplier, int fallbackCacheSize,
                              boolean quietCacheLog, Engine engine) {
        requirePositiveFinite("zoom", zoom);
        requirePositiveFinite("exponent", exponent);
        if (!Double.isFinite(cellularFrequency) || cellularFrequency < 0D) {
            throw new IllegalArgumentException("cellularFrequency must be finite and nonnegative");
        }
        if (cellularFrequency > 0D) {
            requirePositiveFinite("cellularZoom", cellularZoom);
        }
        if (fracture != null) {
            requirePositiveFinite("fracture.multiplier", fracture.getMultiplier());
        }
        CNG cng = null;
        long sourceStamp = 0L;
        if (getExpression() != null) {
            IrisExpression e = data.getExpressionLoader().load(getExpression());
            if (e != null) {
                cng = new CNG(rng, new ExpressionNoise(rng, e), 1D, 1).bake();
                sourceStamp = Integer.toUnsignedLong(Objects.hash(getExpression(),
                        e.getLoadFile() == null ? 0L : e.getLoadFile().lastModified()));
            }
        } else if (getImageMap() != null) {
            ImageNoise imageNoise = new ImageNoise(data, getImageMap());
            cng = new CNG(rng, imageNoise, 1D, 1).bake();
            sourceStamp = Integer.toUnsignedLong(imageNoise.getContentHash().hashCode());
        }

        if (cng == null) {
            cng = (style != null ? style : NoiseStyle.FLAT).create(rng).bake();
        }

        cng = cng.oct(Math.clamp((long) cng.getOct() * octaveMultiplier, 1, 16))
                .pow(cng.getPower() * exponent);

        if (fracture != null) {
            cng.fractureWith(fracture.createNoCache(rng.nextParallelRNG(2934), data, 1,
                    fallbackCacheSize, quietCacheLog, engine), fracture.getMultiplier());
        }

        if (cellularFrequency > 0) {
            cng = cng.cellularize(rng.nextParallelRNG(884466), cellularFrequency).scale(1D / cellularZoom).bake();
        }

        cng.zoom(zoom);

        int effectiveCacheSize = cacheSize > 0 ? cacheSize : Math.max(0, fallbackCacheSize);
        if (effectiveCacheSize > 0) {
            long engineStamp = engineStamp(engine);
            String key = cacheKey(rng, sourceStamp, effectiveCacheSize, engineStamp, octaveMultiplier);
            clearStaleCacheEntries(data, cachePrefix(rng, effectiveCacheSize, engineStamp, octaveMultiplier), key);
            cng = cng.cached(effectiveCacheSize, key, data.getDataFolder(), quietCacheLog);
        }

        return cng;
    }

    public double warp(RNG rng, IrisData data, double value, double... coords) {
        return create(rng, data).noise(coords) + value;
    }

    public CNG create(RNG rng, IrisData data) {
        return create(rng, data, resolveEngine(data));
    }

    public CNG create(RNG rng, IrisData data, Engine engine) {
        GeneratorCacheKey key = new GeneratorCacheKey(data, engine, rng.getSeed());
        return generatorCache.computeIfAbsent(key,
                ignored -> createNoCache(rng, data, 1, 0, false, engine));
    }

    @SuppressWarnings("BooleanMethodIsAlwaysInverted")
    public boolean isFlat() {
        return style == null || style.equals(NoiseStyle.FLAT);
    }

    public double getMaxFractureDistance() {
        return multiplier;
    }

    private long engineStamp(Engine engine) {
        if (engine == null) {
            return 0L;
        }
        return Integer.toUnsignedLong(Objects.hash(
                engine.getSeedManager().getSeed(),
                engine.getMinHeight(),
                engine.getMaxHeight(),
                engine.getDimension().getLoadKey(),
                engine.getDimension().getFluidHeight()
        ));
    }

    private Engine resolveEngine(IrisData data) {
        return data == null ? null : data.getEngine();
    }

    private static void requirePositiveFinite(String field, double value) {
        if (!Double.isFinite(value) || value <= 0D || !Double.isFinite(1D / value)) {
            throw new IllegalArgumentException(field + " must be finite and positive");
        }
    }

    private static final class NoiseCacheIdentity {
        private static final String FINGERPRINT = fingerprint();

        private static String fingerprint() {
            GenerationKernelRegistry registry = GenerationKernelRegistry.standard();
            try {
                return registry.requireSupported(registry.current()).implementationFingerprint();
            } catch (IOException failure) {
                throw new IllegalStateException("Cannot identify the noise cache implementation", failure);
            }
        }
    }

    private static final class GeneratorCacheKey {
        private final IrisData data;
        private final Engine engine;
        private final long seed;

        private GeneratorCacheKey(IrisData data, Engine engine, long seed) {
            this.data = data;
            this.engine = engine;
            this.seed = seed;
        }

        @Override
        public boolean equals(Object other) {
            if (this == other) {
                return true;
            }
            if (!(other instanceof GeneratorCacheKey key)) {
                return false;
            }
            return data == key.data && engine == key.engine && seed == key.seed;
        }

        @Override
        public int hashCode() {
            int result = System.identityHashCode(data);
            result = 31 * result + System.identityHashCode(engine);
            return 31 * result + Long.hashCode(seed);
        }
    }
}
