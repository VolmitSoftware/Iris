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
import art.arcane.iris.generation.runtime.Engine;
import art.arcane.iris.generation.runtime.PackRuntime;
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
    private static final ConcurrentHashMap<String, String> ACTIVE_CACHE_KEYS = new ConcurrentHashMap<>();
    @Description("The base noise style. Used when neither expression nor imageMap is set; a failed expression also falls back to this style, except one that names an unknown engine stream, which fails the pack load.")
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

    public CNG createNoCache(RNG rng, PackRuntime runtime) {
        return createNoCache(rng, runtime, 1, 0, false);
    }

    public CNG createScaledGenerator(RNG rng, PackRuntime runtime, double scaleFactor) {
        return createNoCache(rng, runtime).bake().scale(scaleFactor).bake();
    }

    public CNG createForLayer(RNG rng, PackRuntime runtime, int octaveMultiplier) {
        return createNoCache(rng, runtime, Math.clamp(octaveMultiplier, 1, 16), 0, false);
    }

    public CNG createForPrebake(RNG rng, PackRuntime runtime, int fallbackCacheSize) {
        return createNoCache(rng, runtime, 1, Math.max(0, fallbackCacheSize), true);
    }


    private int imageMapHash() {
        if (imageMap == null) {
            return 0;
        }

        return imageMap.hashCode();
    }

    private int hash() {
        return Objects.hash(expression, imageMapHash(), multiplier, fracture != null ? fracture.hash() : 0, exponent, cacheSize, zoom, cellularZoom, cellularFrequency, style == null ? null : style.name());
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

    private CNG createNoCache(RNG rng, PackRuntime runtime, int octaveMultiplier, int fallbackCacheSize,
                              boolean quietCacheLog) {
        IrisData data = runtime.data();
        Engine engine = runtime.engine();
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
                cng = new CNG(rng, new ExpressionNoise(rng, runtime.expression(e)), 1D, 1).bake();
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
            cng.fractureWith(fracture.createNoCache(rng.nextParallelRNG(2934), runtime, 1,
                    fallbackCacheSize, quietCacheLog), fracture.getMultiplier());
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

    public double warp(RNG rng, PackRuntime runtime, double value, double... coords) {
        return create(rng, runtime).noise(coords) + value;
    }

    /** The generator this runtime shares for this style and seed. */
    public CNG create(RNG rng, PackRuntime runtime) {
        return create(rng.getSeed(), runtime);
    }

    public CNG create(long seed, PackRuntime runtime) {
        return runtime.styleGenerator(this, seed);
    }

    @SuppressWarnings("BooleanMethodIsAlwaysInverted")
    public boolean isFlat() {
        return expression == null && imageMap == null && (style == null || style.equals(NoiseStyle.FLAT));
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
}
