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

package art.arcane.iris.engine.object.noise;

import art.arcane.iris.engine.object.image.IrisImageMap;
import art.arcane.iris.engine.object.noise.expression.IrisExpression;
import art.arcane.iris.core.loader.IrisData;
import art.arcane.volmlib.util.cache.LazyBoundedCache;
import art.arcane.iris.engine.framework.Engine;
import art.arcane.iris.engine.object.annotations.Desc;
import art.arcane.iris.engine.object.annotations.MaxNumber;
import art.arcane.iris.engine.object.annotations.MinNumber;
import art.arcane.iris.engine.object.annotations.RegistryListResource;
import art.arcane.iris.engine.object.annotations.Snippet;
import art.arcane.iris.spi.IrisLogging;
import art.arcane.volmlib.util.math.RNG;
import art.arcane.iris.util.project.noise.CNG;
import art.arcane.iris.util.project.noise.ExpressionNoise;
import art.arcane.iris.util.project.noise.ImageNoise;
import lombok.AccessLevel;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import lombok.experimental.Accessors;

import java.io.File;
import java.util.Objects;
import java.util.concurrent.ConcurrentHashMap;

@Snippet("style")
@Accessors(chain = true)
@NoArgsConstructor
@AllArgsConstructor
@Desc("A gen style")
@Data
public class IrisGeneratorStyle {
    private static final int GENERATOR_CACHE_SIZE = 8;
    private static final ConcurrentHashMap<String, String> ACTIVE_CACHE_KEYS = new ConcurrentHashMap<>();
    @Getter(AccessLevel.NONE)
    @Setter(AccessLevel.NONE)
    private final transient LazyBoundedCache<GeneratorCacheKey, CNG> generatorCache =
            new LazyBoundedCache<>(GENERATOR_CACHE_SIZE);
    @Desc("The base noise style. Used when neither expression nor imageMap is set; a failed expression also falls back to this style.")
    private NoiseStyle style = NoiseStyle.FLAT;

    @Desc("If set above 0, this style will be cellularized")
    private double cellularFrequency = 0;

    @Desc("Cell zooms")
    private double cellularZoom = 1;
    @MinNumber(0.00001)
    @Desc("The zoom of this style")
    private double zoom = 1;
    @Desc("Instead of using the style property, use a custom expression to represent this style.")
    @RegistryListResource(IrisExpression.class)
    private String expression = null;
    @Desc("Use an Image map instead of a generated value")
    @RegistryListResource(IrisImageMap.class)
    private String imageMap = null;
    @MinNumber(0.00001)
    @Desc("The Output multiplier. Only used if parent is fracture.")
    private double multiplier = 1;
    @Desc("Apply a generator to the coordinate field fed into this parent generator. I.e. Distort your generator with another generator.")
    private IrisGeneratorStyle fracture = null;
    @MinNumber(0.01562)
    @MaxNumber(64)
    @Desc("The exponent")
    private double exponent = 1;
    @MinNumber(0)
    @MaxNumber(8192)
    @Desc("If the cache size is set above 0, this generator will be cached")
    private int cacheSize = 0;
    @Desc("Rolls this noise from the world seed and this number, so every style with the same seed shares one noise. Child biomes, palettes and decorator placement read a seeded style flat in blocks: zoom is its only scale, so a layer's zoom, childShrinkFactor and the decorator's extra zoom don't apply.")
    private Long seed = null;

    public IrisGeneratorStyle(NoiseStyle s) {
        this.style = s;
    }

    public IrisGeneratorStyle zoomed(double z) {
        this.zoom = z;
        return this;
    }

    public CNG createNoCache(RNG rng, IrisData data) {
        return createNoCache(rng, data, false, 0, false, resolveEngine(data));
    }

    public CNG createScaledGenerator(RNG rng, IrisData data, double scaleFactor) {
        return createNoCache(rng, data).bake().scale(scaleFactor).bake();
    }

    public CNG createForPrebake(RNG rng, IrisData data, int fallbackCacheSize) {
        return createNoCache(rng, data, false, Math.max(0, fallbackCacheSize), true, resolveEngine(data));
    }


    private int imageMapHash() {
        if (imageMap == null) {
            return 0;
        }

        return imageMap.hashCode();
    }

    private int hash() {
        return Objects.hash(expression, imageMapHash(), multiplier, fracture != null ? fracture.hash() : 0, exponent, cacheSize, zoom, cellularZoom, cellularFrequency, style, seed);
    }

    public boolean isSeeded() {
        return seed != null;
    }

    /** The random a style is built from: the caller's, or for a seeded style one rolled from its seed and the world seed. */
    public RNG rollFor(RNG callerRandom, Engine engine) {
        if (!isSeeded()) {
            return callerRandom;
        }

        long worldSeed = engine == null ? 0L : engine.getSeedManager().getSeed();
        return new RNG(splitMix(worldSeed ^ splitMix(seed)));
    }

    // Spreads nearby seed numbers far apart; Iris's own seed forking only adds.
    private static long splitMix(long value) {
        long mixed = value + 0x9E3779B97F4A7C15L;
        mixed = (mixed ^ (mixed >>> 30)) * 0xBF58476D1CE4E5B9L;
        mixed = (mixed ^ (mixed >>> 27)) * 0x94D049BB133111EBL;
        return mixed ^ (mixed >>> 31);
    }

    public int prebakeSignature() {
        return hash();
    }

    private String cachePrefix(RNG rng, int effectiveCacheSize, long engineStamp) {
        return "style-" + Integer.toUnsignedString(hash())
                + "-seed-" + Long.toUnsignedString(rng.getSeed())
                + "-eng-" + Long.toUnsignedString(engineStamp)
                + "-sz-" + effectiveCacheSize
                + "-src-";
    }

    private String cacheKey(RNG rng, long sourceStamp, int effectiveCacheSize, long engineStamp) {
        return cachePrefix(rng, effectiveCacheSize, engineStamp) + Long.toUnsignedString(sourceStamp);
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

    public CNG createNoCache(RNG rng, IrisData data, boolean actuallyCached) {
        return createNoCache(rng, data, actuallyCached, 0, false, resolveEngine(data));
    }

    private CNG createNoCache(RNG callerRandom, IrisData data, boolean actuallyCached, int fallbackCacheSize,
                              boolean quietCacheLog, Engine engine) {
        RNG rng = rollFor(callerRandom, engine);
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

        cng = cng.scale(1D / zoom).pow(exponent).bake();

        if (fracture != null) {
            cng.fractureWith(fracture.createNoCache(rng.nextParallelRNG(2934), data, false,
                    fallbackCacheSize, quietCacheLog, engine), fracture.getMultiplier());
        }

        if (cellularFrequency > 0) {
            cng = cng.cellularize(rng.nextParallelRNG(884466), cellularFrequency).scale(1D / cellularZoom).bake();
        }

        int effectiveCacheSize = cacheSize > 0 ? cacheSize : Math.max(0, fallbackCacheSize);
        if (effectiveCacheSize > 0) {
            long engineStamp = engineStamp(engine);
            String key = cacheKey(rng, sourceStamp, effectiveCacheSize, engineStamp);
            clearStaleCacheEntries(data, cachePrefix(rng, effectiveCacheSize, engineStamp), key);
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

    public CNG create(RNG callerRandom, IrisData data, Engine engine) {
        RNG rng = rollFor(callerRandom, engine);
        GeneratorCacheKey key = new GeneratorCacheKey(data, engine, rng.getSeed());
        return generatorCache.computeIfAbsent(key,
                ignored -> createNoCache(rng, data, true, 0, false, engine));
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
