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

package art.arcane.iris.util.project.noise;

import art.arcane.iris.engine.object.IRare;
import art.arcane.iris.engine.object.NoiseStyle;
import art.arcane.iris.spi.IrisLogging;
import art.arcane.volmlib.util.cache.FloatCache;
import art.arcane.volmlib.util.collection.KList;
import art.arcane.volmlib.util.format.Form;
import art.arcane.volmlib.util.function.NoiseInjector;
import art.arcane.iris.util.project.interpolation.IrisInterpolation;
import art.arcane.volmlib.util.math.RNG;
import art.arcane.volmlib.util.scheduling.PrecisionStopwatch;
import art.arcane.iris.util.project.stream.ProceduralStream;
import art.arcane.iris.util.project.stream.arithmetic.FittedStream;
import art.arcane.iris.util.project.stream.sources.CNGStream;
import lombok.Data;

import java.io.DataOutputStream;
import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.StandardCopyOption;
import java.util.IdentityHashMap;
import java.util.List;

@Data
public class CNG {
    public static final NoiseInjector ADD = (s, v) -> new double[]{s + v, 1};
    public static final NoiseInjector SRC_SUBTRACT = (s, v) -> new double[]{s - v < 0 ? 0 : s - v, -1};
    public static final NoiseInjector DST_SUBTRACT = (s, v) -> new double[]{v - s < 0 ? 0 : s - v, -1};
    public static final NoiseInjector MULTIPLY = (s, v) -> new double[]{s * v, 0};
    public static final NoiseInjector MAX = (s, v) -> new double[]{Math.max(s, v), 0};
    public static final NoiseInjector MIN = (s, v) -> new double[]{Math.min(s, v), 0};
    public static final NoiseInjector SRC_MOD = (s, v) -> new double[]{s % v, 0};
    public static final NoiseInjector SRC_POW = (s, v) -> new double[]{Math.pow(s, v), 0};
    public static final NoiseInjector DST_MOD = (s, v) -> new double[]{v % s, 0};
    public static final NoiseInjector DST_POW = (s, v) -> new double[]{Math.pow(v, s), 0};
    private final double opacity;
    private double scale;
    private double bakedScale;
    private double fscale;
    private KList<CNG> children;
    private CNG fracture;
    private FloatCache cache;
    private NoiseGenerator generator;
    private NoiseInjector injector;
    private InjectorMode injectorMode;
    private RNG rng;
    private boolean noscale;
    private int oct;
    private double patch;
    private double up;
    private double down;
    private double power;
    private NoiseStyle leakStyle;
    private ProceduralStream<Double> customGenerator;
    private transient boolean identityPostFastPath;
    private transient boolean identitySignedFastPath;
    private transient boolean unityOpacity;
    private transient double effectiveScale;
    private transient double signedFractureScale;
    private transient boolean fastPathStateDirty = true;
    private transient int coordCacheSalt;
    private transient Object coordCacheIdentity = new Object();

    public CNG(RNG random) {
        this(random, 1);
    }

    public CNG(RNG random, int octaves) {
        this(random, 1D, octaves);
    }

    public CNG(RNG random, double opacity, int octaves) {
        this(random, NoiseType.SIMPLEX, opacity, octaves);
    }

    public CNG(RNG random, NoiseType type, double opacity, int octaves) {
        this(random, type.create(random.nextParallelRNG((long) ((1113334944L * opacity) + 12923)).lmax()), opacity, octaves);
    }

    public CNG(RNG random, NoiseGenerator generator, double opacity, int octaves) {
        customGenerator = null;
        noscale = generator.isNoScale();
        this.oct = octaves;
        this.rng = random;
        power = 1;
        scale = 1;
        patch = 1;
        bakedScale = 1;
        fscale = 1;
        down = 0;
        up = 0;
        fracture = null;
        this.generator = generator;
        this.opacity = opacity;
        this.injector = ADD;
        this.injectorMode = InjectorMode.ADD;
        coordCacheSalt = createCoordCacheSalt();

        if (generator instanceof OctaveNoise octaveNoise) {
            octaveNoise.setOctaves(octaves);
        }

        refreshFastPathState();
    }

    public static CNG signature(RNG rng) {
        return signature(rng, NoiseType.SIMPLEX);
    }

    public static CNG signatureHalf(RNG rng) {
        return signatureHalf(rng, NoiseType.SIMPLEX);
    }

    public static CNG signatureThick(RNG rng) {
        return signatureThick(rng, NoiseType.SIMPLEX);
    }

    public static CNG signatureDouble(RNG rng) {
        return signatureDouble(rng, NoiseType.SIMPLEX);
    }

    public static CNG signatureDouble(RNG rng, NoiseType t) {
        return signatureThick(rng, t).fractureWith(signature(rng.nextParallelRNG(4956)), 93);
    }

    public static CNG signatureDoubleFast(RNG rng, NoiseType t, NoiseType f) {
        return signatureThickFast(rng, t, f)
                .fractureWith(signatureFast(rng.nextParallelRNG(4956), t, f), 93);
    }

    public static CNG signature(RNG rng, NoiseType t) {
        // @NoArgsConstructor
        return new CNG(rng.nextParallelRNG(17), t, 1D, 1).fractureWith(new CNG(rng.nextParallelRNG(18), 1, 1).scale(0.9).fractureWith(new CNG(rng.nextParallelRNG(20), 1, 1).scale(0.21).fractureWith(new CNG(rng.nextParallelRNG(20), 1, 1).scale(0.9), 620), 145), 44).bake();
        // @done
    }

    public static CNG signaturePerlin(RNG rng) {
        return signaturePerlin(rng, NoiseType.PERLIN);
    }

    public static CNG signaturePerlin(RNG rng, NoiseType t) {
        // @NoArgsConstructor
        return new CNG(rng.nextParallelRNG(124996), t, 1D, 1)
                .fractureWith(new CNG(rng.nextParallelRNG(18), NoiseType.PERLIN, 1, 1).scale(1.25), 250)
                .bake();
        // @done
    }

    public static CNG signatureFast(RNG rng, NoiseType t, NoiseType f) {
        // @NoArgsConstructor
        return new CNG(rng.nextParallelRNG(17), t, 1D, 1)
                .fractureWith(new CNG(rng.nextParallelRNG(18), f, 1, 1)
                        .scale(0.9)
                        .fractureWith(new CNG(rng.nextParallelRNG(20), f, 1, 1)
                                .scale(0.21)
                                .fractureWith(new CNG(rng.nextParallelRNG(20), f, 1, 1).scale(0.9), 620), 145), 44)
                .bake();
        // @done
    }

    public static CNG signatureThick(RNG rng, NoiseType t) {
        // @NoArgsConstructor
        return new CNG(rng.nextParallelRNG(133), t, 1D, 1).fractureWith(new CNG(rng.nextParallelRNG(18), 1, 1).scale(0.5).fractureWith(new CNG(rng.nextParallelRNG(20), 1, 1).scale(0.11).fractureWith(new CNG(rng.nextParallelRNG(20), 1, 1).scale(0.4), 620), 145), 44).bake();
        // @done
    }

    public static CNG signatureThickFast(RNG rng, NoiseType t, NoiseType f) {
        // @NoArgsConstructor
        return new CNG(rng.nextParallelRNG(133), t, 1D, 1)
                .fractureWith(new CNG(rng.nextParallelRNG(18), f, 1, 1)
                        .scale(0.5).fractureWith(new CNG(rng.nextParallelRNG(20), f, 1, 1)
                                .scale(0.11).fractureWith(new CNG(rng.nextParallelRNG(20), f, 1, 1)
                                        .scale(0.4), 620), 145), 44).bake();
        // @done
    }

    public static CNG signatureHalf(RNG rng, NoiseType t) {
        // @NoArgsConstructor
        return new CNG(rng.nextParallelRNG(127), t, 1D, 1).fractureWith(new CNG(rng.nextParallelRNG(18), 1, 1).scale(0.9).fractureWith(new CNG(rng.nextParallelRNG(20), 1, 1).scale(0.21).fractureWith(new CNG(rng.nextParallelRNG(20), 1, 1).scale(0.9), 420), 99), 22).bake();
        // @done
    }

    public static CNG signatureHalfFast(RNG rng, NoiseType t, NoiseType f) {
        // @NoArgsConstructor
        return new CNG(rng.nextParallelRNG(127), t, 1D, 1)
                .fractureWith(new CNG(rng.nextParallelRNG(18), f, 1, 1).scale(0.9)
                        .fractureWith(new CNG(rng.nextParallelRNG(20), f, 1, 1).scale(0.21)
                                .fractureWith(new CNG(rng.nextParallelRNG(20), f, 1, 1).scale(0.9), 420), 99), 22).bake();
        // @done
    }

    public static void main(String[] a) {
        CNG cng = NoiseStyle.SIMPLEX.create(new RNG(1234));
        PrecisionStopwatch p = PrecisionStopwatch.start();
        double r = 0;

        for (int i = 0; i < 30000000 * 10; i++) {
            r += cng.fit(-1000, 1000, i, i);
        }

        IrisLogging.info(Form.duration(p.getMilliseconds(), 10) + " merged = " + r);
    }

    public CNG cellularize(RNG seed, double freq) {
        FastNoise cellularFilter = new FastNoise(seed.imax());
        cellularFilter.SetNoiseType(FastNoise.NoiseType.Cellular);
        cellularFilter.SetCellularReturnType(FastNoise.CellularReturnType.CellValue);
        cellularFilter.SetCellularDistanceFunction(FastNoise.CellularDistanceFunction.Manhattan);
        cellularFilter.SetFrequency((float) freq * 0.01f);

        ProceduralStream<Double> str = stream();

        return new CNG(seed, new NoiseGenerator() {
            @Override
            public double noise(double x) {
                return noise(x, 0);
            }

            @Override
            public double noise(double x, double z) {
                return (cellularFilter.GetCellular((float) x, (float) z, str, 1) * 0.5) + 0.5D;
            }

            @Override
            public double noise(double x, double y, double z) {
                return noise(x, y + z);
            }
        }, 1D, 1);
    }

    public CNG cached(int size, String key, File cacheFolder) {
        return cached(size, key, cacheFolder, false);
    }

    public CNG cached(int size, String key, File cacheFolder, boolean quiet) {
        if (size <= 0) {
            return this;
        }

        markFastPathStateDirty();

        File f = new File(new File(cacheFolder, ".cache"), key + ".cnm");
        FloatCache fbc;
        boolean cached = false;
        if (f.exists()) {
            try {
                fbc = new FloatCache(f);
                cached = true;
            } catch (IOException e) {
                fbc = new FloatCache(size, size);
            }
        } else {
            fbc = new FloatCache(size, size);
        }

        if (!cached) {
            for (int i = 0; i < size; i++) {
                for (int j = 0; j < size; j++) {
                    fbc.set(i, j, (float) noise(i, j));
                }
            }

            // Write to a unique temp name (excluded from the *.cnm stale-entry pruner) and
            // move atomically: a throw mid-write must never leak the descriptor or leave a
            // truncated .cnm that the read path would pick up by existence alone. The name is
            // per-writer unique so two engines caching the same style key cannot interleave
            // into one temp file.
            File tmp = new File(f.getParentFile(), f.getName() + "." + Long.toUnsignedString(System.nanoTime(), 36)
                    + "-" + Long.toUnsignedString(Thread.currentThread().threadId(), 36) + ".tmp");
            try {
                f.getParentFile().mkdirs();
                try (DataOutputStream dos = new DataOutputStream(new FileOutputStream(tmp))) {
                    fbc.writeCache(dos);
                }
                Files.move(tmp.toPath(), f.toPath(),
                        StandardCopyOption.REPLACE_EXISTING, StandardCopyOption.ATOMIC_MOVE);
                if (!quiet) {
                    IrisLogging.info("Saved Noise Cache " + f.getName());
                }
            } catch (IOException e) {
                try {
                    Files.deleteIfExists(tmp.toPath());
                } catch (IOException ignored) {
                }
                throw new RuntimeException(e);
            }
        }

        cache = fbc;
        coordCacheIdentity = new Object();
        return this;
    }

    public NoiseGenerator getGen() {
        return generator;
    }

    public ProceduralStream<Double> stream() {
        return new CNGStream(this);
    }

    public ProceduralStream<Double> stream(double min, double max) {
        return new FittedStream<>(stream(), min, max);
    }

    public CNG bake() {
        bakedScale *= scale;
        scale = 1;
        markFastPathStateDirty();
        return this;
    }

    public CNG child(CNG c) {
        if (children == null) {
            children = new KList<>();
        }

        children.add(c);
        markFastPathStateDirty();
        return this;
    }

    public RNG getRNG() {
        return rng;
    }

    public CNG fractureWith(CNG c, double scale) {
        fracture = c;
        fscale = scale;
        markFastPathStateDirty();
        return this;
    }

    public CNG scale(double c) {
        scale = c;
        markFastPathStateDirty();
        return this;
    }

    public CNG zoom(double factor) {
        if (!Double.isFinite(factor) || factor <= 0D || !Double.isFinite(1D / factor)) {
            throw new IllegalArgumentException("Zoom must be positive and finite");
        }
        if (factor != 1D) {
            zoom(factor, new IdentityHashMap<>());
        }
        return this;
    }

    public CNG patch(double c) {
        patch = c;
        markFastPathStateDirty();
        return this;
    }

    public CNG up(double c) {
        up = c;
        markFastPathStateDirty();
        return this;
    }

    public CNG down(double c) {
        down = c;
        markFastPathStateDirty();
        return this;
    }

    public CNG injectWith(NoiseInjector i) {
        injector = i == null ? ADD : i;
        injectorMode = resolveInjectorMode(injector);
        markFastPathStateDirty();
        return this;
    }

    private InjectorMode resolveInjectorMode(NoiseInjector i) {
        if (i == ADD) {
            return InjectorMode.ADD;
        }

        if (i == SRC_SUBTRACT) {
            return InjectorMode.SRC_SUBTRACT;
        }

        if (i == DST_SUBTRACT) {
            return InjectorMode.DST_SUBTRACT;
        }

        if (i == MULTIPLY) {
            return InjectorMode.MULTIPLY;
        }

        if (i == MAX) {
            return InjectorMode.MAX;
        }

        if (i == MIN) {
            return InjectorMode.MIN;
        }

        if (i == SRC_MOD) {
            return InjectorMode.SRC_MOD;
        }

        if (i == SRC_POW) {
            return InjectorMode.SRC_POW;
        }

        if (i == DST_MOD) {
            return InjectorMode.DST_MOD;
        }

        if (i == DST_POW) {
            return InjectorMode.DST_POW;
        }

        return InjectorMode.CUSTOM;
    }

    public <T extends IRare> T fitRarity(KList<T> b, double... dim) {
        if (dim.length == 2) {
            return fitRarity2D(b, dim[0], dim[1]);
        }

        if (b.size() == 0) {
            return null;
        }

        if (b.size() == 1) {
            return b.get(0);
        }

        KList<T> rarityMapped = buildRarityMapped(b);

        if (rarityMapped.size() == 1) {
            return rarityMapped.get(0);
        }

        if (rarityMapped.isEmpty()) {
            throw new RuntimeException("BAD RARITY MAP! RELATED TO: " + b.toString(", or possibly "));
        }

        return fit(rarityMapped, dim);
    }

    public <T extends IRare> T fitRarity2D(KList<T> b, double x, double z) {
        if (b.size() == 0) {
            return null;
        }

        if (b.size() == 1) {
            return b.get(0);
        }

        KList<T> rarityMapped = buildRarityMapped(b);
        if (rarityMapped.size() == 1) {
            return rarityMapped.get(0);
        }

        if (rarityMapped.isEmpty()) {
            throw new RuntimeException("BAD RARITY MAP! RELATED TO: " + b.toString(", or possibly "));
        }

        return fit2D(rarityMapped, x, z);
    }

    private <T extends IRare> KList<T> buildRarityMapped(KList<T> values) {
        KList<T> rarityMapped = new KList<>();
        boolean flip = false;
        int max = 1;
        for (T value : values) {
            if (value.getRarity() > max) {
                max = value.getRarity();
            }
        }

        max++;
        for (T value : values) {
            int count = max - value.getRarity();
            for (int j = 0; j < count; j++) {
                flip = !flip;
                if (flip) {
                    rarityMapped.add(value);
                } else {
                    rarityMapped.add(0, value);
                }
            }
        }

        return rarityMapped;
    }

    public <T> T fit2D(T[] values, double x, double z) {
        if (values.length == 0) {
            return null;
        }

        if (values.length == 1) {
            return values[0];
        }

        return values[fit2D(0, values.length - 1, x, z)];
    }

    public <T> T fit2D(List<T> values, double x, double z) {
        if (values.isEmpty()) {
            return null;
        }

        if (values.size() == 1) {
            return values.get(0);
        }

        try {
            return values.get(fit2D(0, values.size() - 1, x, z));
        } catch (Throwable e) {
            IrisLogging.reportError(e);
        }

        return values.get(0);
    }

    public int fit2D(int min, int max, double x, double z) {
        if (min == max) {
            return min;
        }

        double noise = noiseFast2D(x, z);
        return (int) Math.round(IrisInterpolation.lerp(min, max, noise));
    }

    public <T> T fit(T[] v, double... dim) {
        if (v.length == 0) {
            return null;
        }

        if (v.length == 1) {
            return v[0];
        }

        return v[fit(0, v.length - 1, dim)];
    }

    public <T> T fit(List<T> v, double... dim) {
        if (v.size() == 0) {
            return null;
        }

        if (v.size() == 1) {
            return v.get(0);
        }

        try {
            return v.get(fit(0, v.size() - 1, dim));
        } catch (Throwable e) {
            IrisLogging.reportError(e);
        }

        return v.get(0);
    }

    public int fit(int min, int max, double... dim) {
        if (min == max) {
            return min;
        }

        double noise = noise(dim);

        return (int) Math.round(IrisInterpolation.lerp(min, max, noise));
    }

    public int fit(int min, int max, double x, double z) {
        return fit2D(min, max, x, z);
    }

    public int fit(int min, int max, double x, double y, double z) {
        if (min == max) {
            return min;
        }

        double noise = noise(x, y, z);

        return (int) Math.round(IrisInterpolation.lerp(min, max, noise));
    }

    public int fit(double min, double max, double... dim) {
        if (min == max) {
            return (int) Math.round(min);
        }

        double noise = noise(dim);

        return (int) Math.round(IrisInterpolation.lerp(min, max, noise));
    }

    public int fit(double min, double max, double x, double z) {
        if (min == max) {
            return (int) Math.round(min);
        }

        double noise = noiseFast2D(x, z);

        return (int) Math.round(IrisInterpolation.lerp(min, max, noise));
    }

    public int fit(double min, double max, double x, double y, double z) {
        if (min == max) {
            return (int) Math.round(min);
        }

        double noise = noise(x, y, z);

        return (int) Math.round(IrisInterpolation.lerp(min, max, noise));
    }

    public double fitDouble(double min, double max, double... dim) {
        if (min == max) {
            return min;
        }

        double noise = noise(dim);

        return IrisInterpolation.lerp(min, max, noise);
    }

    public double fitDouble(double min, double max, double x, double z) {
        if (min == max) {
            return min;
        }

        double noise = noise(x, z);

        return IrisInterpolation.lerp(min, max, noise);
    }

    public double fitDouble(double min, double max, double x, double y, double z) {
        if (min == max) {
            return min;
        }

        double noise = noise(x, y, z);

        return IrisInterpolation.lerp(min, max, noise);
    }

    private double getNoise(double x) {
        ensureFastPathState();
        double scl = effectiveScale;

        if (fracture == null || noscale) {
            return generator.noise(x * scl) * opacity;
        }

        double fx = x + ((fracture.noiseFast1D(x) - 0.5D) * fscale);
        return generator.noise(fx * scl) * opacity;
    }

    private double getNoise(double x, double z) {
        ensureFastPathState();
        double scl = effectiveScale;

        if (fracture == null || noscale) {
            return generator.noise(x * scl, z * scl) * opacity;
        }

        double fx = x + ((fracture.noiseFast2D(x, z) - 0.5D) * fscale);
        double fz = z + ((fracture.noiseFast2D(z, x) - 0.5D) * fscale);
        return generator.noise(fx * scl, fz * scl) * opacity;
    }

    private double getNoise(double x, double y, double z) {
        ensureFastPathState();
        double scl = effectiveScale;

        if (fracture == null || noscale) {
            return generator.noise(x * scl, y * scl, z * scl) * opacity;
        }

        double fx = x + ((fracture.noiseFast3D(x, y, z) - 0.5D) * fscale);
        double fy = y + ((fracture.noiseFast2D(y, x) - 0.5D) * fscale);
        double fz = z + ((fracture.noiseFast3D(z, x, y) - 0.5D) * fscale);
        return generator.noise(fx * scl, fy * scl, fz * scl) * opacity;
    }

    public double invertNoise(double... dim) {
        if (dim.length == 1) {
            return noise(-dim[0]);
        } else if (dim.length == 2) {
            return noise(dim[1], dim[0]);
        } else if (dim.length == 3) {
            return noise(dim[1], dim[2], dim[0]);
        }

        return noise(dim);
    }

    public double noiseSym(double... dim) {
        return (noise(dim) * 2) - 1;
    }

    private boolean isCachedCoordinate(double x, double z) {
        return cache != null
                && x >= 0D && x < cache.getWidth()
                && z >= 0D && z < cache.getHeight()
                && x == (int) x && z == (int) z;
    }

    private double getCachedNoise(double x, double z) {
        return cache.get((int) z * cache.getWidth() + (int) x);
    }

    private double applyPost(double n, double x) {
        n = power != 1D ? (n < 0 ? -Math.pow(Math.abs(n), power) : Math.pow(n, power)) : n;
        double m = 1;

        if (children != null) {
            for (CNG i : children) {
                double source = n;
                double value = i.noise(x);
                switch (injectorMode) {
                    case ADD -> {
                        n = source + value;
                        m += 1D;
                    }
                    case SRC_SUBTRACT -> {
                        n = source - value < 0D ? 0D : source - value;
                        m -= 1D;
                    }
                    case DST_SUBTRACT -> {
                        n = value - source < 0D ? 0D : source - value;
                        m -= 1D;
                    }
                    case MULTIPLY -> n = source * value;
                    case MAX -> n = Math.max(source, value);
                    case MIN -> n = Math.min(source, value);
                    case SRC_MOD -> n = source % value;
                    case SRC_POW -> n = Math.pow(source, value);
                    case DST_MOD -> n = value % source;
                    case DST_POW -> n = Math.pow(value, source);
                    case CUSTOM -> {
                        double[] combined = injector.combine(source, value);
                        n = combined[0];
                        m += combined[1];
                    }
                }
            }
        }

        return ((n / m) - down + up) * patch;
    }

    private double applyPost(double n, double x, double z) {
        n = power != 1D ? (n < 0 ? -Math.pow(Math.abs(n), power) : Math.pow(n, power)) : n;
        double m = 1;

        if (children != null) {
            for (CNG i : children) {
                double source = n;
                double value = i.noise(x, z);
                switch (injectorMode) {
                    case ADD -> {
                        n = source + value;
                        m += 1D;
                    }
                    case SRC_SUBTRACT -> {
                        n = source - value < 0D ? 0D : source - value;
                        m -= 1D;
                    }
                    case DST_SUBTRACT -> {
                        n = value - source < 0D ? 0D : source - value;
                        m -= 1D;
                    }
                    case MULTIPLY -> n = source * value;
                    case MAX -> n = Math.max(source, value);
                    case MIN -> n = Math.min(source, value);
                    case SRC_MOD -> n = source % value;
                    case SRC_POW -> n = Math.pow(source, value);
                    case DST_MOD -> n = value % source;
                    case DST_POW -> n = Math.pow(value, source);
                    case CUSTOM -> {
                        double[] combined = injector.combine(source, value);
                        n = combined[0];
                        m += combined[1];
                    }
                }
            }
        }

        return ((n / m) - down + up) * patch;
    }

    private double applyPost(double n, double x, double y, double z) {
        n = power != 1D ? (n < 0 ? -Math.pow(Math.abs(n), power) : Math.pow(n, power)) : n;
        double m = 1;

        if (children != null) {
            for (CNG i : children) {
                double source = n;
                double value = i.noise(x, y, z);
                switch (injectorMode) {
                    case ADD -> {
                        n = source + value;
                        m += 1D;
                    }
                    case SRC_SUBTRACT -> {
                        n = source - value < 0D ? 0D : source - value;
                        m -= 1D;
                    }
                    case DST_SUBTRACT -> {
                        n = value - source < 0D ? 0D : source - value;
                        m -= 1D;
                    }
                    case MULTIPLY -> n = source * value;
                    case MAX -> n = Math.max(source, value);
                    case MIN -> n = Math.min(source, value);
                    case SRC_MOD -> n = source % value;
                    case SRC_POW -> n = Math.pow(source, value);
                    case DST_MOD -> n = value % source;
                    case DST_POW -> n = Math.pow(value, source);
                    case CUSTOM -> {
                        double[] combined = injector.combine(source, value);
                        n = combined[0];
                        m += combined[1];
                    }
                }
            }
        }

        return ((n / m) - down + up) * patch;
    }

    public double noise(double... dim) {
        return switch (dim.length) {
            case 1 -> noise(dim[0]);
            case 2 -> noise(dim[0], dim[1]);
            case 3 -> noise(dim[0], dim[1], dim[2]);
            default -> throw new IllegalArgumentException("Noise requires one, two, or three coordinates");
        };
    }

    public double noise(double x) {
        return applyPost(getNoise(x), x);
    }

    public double noiseFast1D(double x) {
        return applyPost(getNoise(x), x);
    }

    public double noise(double x, double z) {
        if (isCachedCoordinate(x, z)) {
            return getCachedNoise(x, z);
        }

        return applyPost(getNoise(x, z), x, z);
    }

    public double noiseFast2D(double x, double z) {
        if (isCachedCoordinate(x, z)) {
            return getCachedNoise(x, z);
        }

        if (isIdentityPostFastPath()) {
            return getNoise(x, z);
        }

        return applyPost(getNoise(x, z), x, z);
    }

    private static final int COORD_CACHE_SIZE = 1 << 16;
    private static final int COORD_CACHE_MASK = COORD_CACHE_SIZE - 1;

    private static final class CoordCache {
        private final Object[] owner = new Object[COORD_CACHE_SIZE];
        private final long[] kx = new long[COORD_CACHE_SIZE];
        private final long[] kz = new long[COORD_CACHE_SIZE];
        private final double[] value = new double[COORD_CACHE_SIZE];
    }

    private static final ThreadLocal<CoordCache> COORD_CACHE_2D = ThreadLocal.withInitial(CoordCache::new);

    private static int coordSlot(long a, long b, int salt) {
        long h = (a * 0x9E3779B97F4A7C15L) ^ (b * 0xC2B2AE3D27D4EB4FL) ^ (salt * 0x165667B19E3779F9L);
        h ^= (h >>> 32);
        return (int) (h & COORD_CACHE_MASK);
    }

    public double noiseFastSigned2D(double x, double z) {
        long kx = Double.doubleToRawLongBits(x);
        long kz = Double.doubleToRawLongBits(z);
        CoordCache cc = COORD_CACHE_2D.get();
        Object identity = coordCacheIdentity();
        int slot = coordSlot(kx, kz, coordCacheSalt());
        if (cc.owner[slot] == identity && cc.kx[slot] == kx && cc.kz[slot] == kz) {
            return cc.value[slot];
        }
        double computed = computeNoiseFastSigned2D(x, z);
        cc.owner[slot] = identity;
        cc.kx[slot] = kx;
        cc.kz[slot] = kz;
        cc.value[slot] = computed;
        return computed;
    }

    private double computeNoiseFastSigned2D(double x, double z) {
        if (isCachedCoordinate(x, z)) {
            return (getCachedNoise(x, z) * 2D) - 1D;
        }

        if (hasIdentitySignedFastPath()) {
            return getSignedNoise(x, z);
        }

        return (noiseFast2D(x, z) * 2D) - 1D;
    }

    public double noise(double x, double y, double z) {
        return applyPost(getNoise(x, y, z), x, y, z);
    }

    public double noiseFast3D(double x, double y, double z) {
        if (isIdentityPostFastPath()) {
            return getNoise(x, y, z);
        }

        return applyPost(getNoise(x, y, z), x, y, z);
    }

    private static final class CoordCache3D {
        private final Object[] owner = new Object[COORD_CACHE_SIZE];
        private final long[] kx = new long[COORD_CACHE_SIZE];
        private final long[] ky = new long[COORD_CACHE_SIZE];
        private final long[] kz = new long[COORD_CACHE_SIZE];
        private final double[] value = new double[COORD_CACHE_SIZE];
    }

    private static final ThreadLocal<CoordCache3D> COORD_CACHE_3D = ThreadLocal.withInitial(CoordCache3D::new);

    public double noiseFastSigned3D(double x, double y, double z) {
        long kx = Double.doubleToRawLongBits(x);
        long ky = Double.doubleToRawLongBits(y);
        long kz = Double.doubleToRawLongBits(z);
        CoordCache3D cc = COORD_CACHE_3D.get();
        Object identity = coordCacheIdentity();
        int slot = coordSlot(kx ^ Long.rotateLeft(ky, 21), kz, coordCacheSalt());
        if (cc.owner[slot] == identity && cc.kx[slot] == kx && cc.ky[slot] == ky && cc.kz[slot] == kz) {
            return cc.value[slot];
        }
        double computed = hasIdentitySignedFastPath() ? getSignedNoise(x, y, z) : (noiseFast3D(x, y, z) * 2D) - 1D;
        cc.owner[slot] = identity;
        cc.kx[slot] = kx;
        cc.ky[slot] = ky;
        cc.kz[slot] = kz;
        cc.value[slot] = computed;
        return computed;
    }

    public CNG pow(double power) {
        this.power = power;
        markFastPathStateDirty();
        return this;
    }

    public CNG oct(int octaves) {
        if (oct == octaves) {
            return this;
        }

        oct = octaves;
        if (generator instanceof OctaveNoise octaveNoise) {
            octaveNoise.setOctaves(octaves);
        }
        markFastPathStateDirty();
        return this;
    }

    public double getScale() {
        return scale;
    }

    public boolean isStatic() {
        return generator != null && generator.isStatic();
    }

    private boolean isIdentityPostFastPath() {
        ensureFastPathState();
        return identityPostFastPath;
    }

    private boolean hasIdentitySignedFastPath() {
        ensureFastPathState();
        return identitySignedFastPath;
    }

    private void zoom(double factor, IdentityHashMap<CNG, Boolean> visited) {
        if (visited.put(this, Boolean.TRUE) != null) {
            return;
        }
        bakedScale /= factor;
        fscale *= factor;
        if (fracture != null) {
            fracture.zoom(factor, visited);
        }
        if (children != null) {
            for (CNG child : children) {
                child.zoom(factor, visited);
            }
        }
        markFastPathStateDirty();
    }

    private void ensureFastPathState() {
        if (fastPathStateDirty) {
            refreshFastPathState();
        }
    }

    private void markFastPathStateDirty() {
        fastPathStateDirty = true;
        cache = null;
        coordCacheIdentity = new Object();
    }

    private Object coordCacheIdentity() {
        if (coordCacheIdentity == null) {
            coordCacheIdentity = new Object();
        }

        return coordCacheIdentity;
    }

    private int coordCacheSalt() {
        if (coordCacheSalt == 0) {
            coordCacheSalt = createCoordCacheSalt();
        }

        return coordCacheSalt;
    }

    private int createCoordCacheSalt() {
        int salt = System.identityHashCode(this);
        return salt == 0 ? 1 : salt;
    }

    private void refreshFastPathState() {
        effectiveScale = noscale ? 1D : bakedScale * scale;
        identityPostFastPath = power == 1D
                && children == null
                && fracture == null
                && down == 0D
                && up == 0D
                && patch == 1D;
        identitySignedFastPath = power == 1D
                && children == null
                && down == 0D
                && up == 0D
                && patch == 1D;
        unityOpacity = opacity == 1D;
        signedFractureScale = 0.5D * fscale;
        fastPathStateDirty = false;
    }

    private double signedWithOpacity(double signedNoise) {
        if (unityOpacity) {
            return signedNoise;
        }

        return ((signedNoise + 1D) * opacity) - 1D;
    }

    private double getSignedNoise(double x, double z) {
        double scl = effectiveScale;
        NoiseGenerator localGenerator = generator;
        CNG localFracture = fracture;

        if (localFracture == null || noscale) {
            return signedWithOpacity(localGenerator.noiseSigned(x * scl, z * scl));
        }

        double fx = x + (localFracture.noiseFastSigned2D(x, z) * signedFractureScale);
        double fz = z + (localFracture.noiseFastSigned2D(z, x) * signedFractureScale);
        return signedWithOpacity(localGenerator.noiseSigned(fx * scl, fz * scl));
    }

    private double getSignedNoise(double x, double y, double z) {
        double scl = effectiveScale;
        NoiseGenerator localGenerator = generator;
        CNG localFracture = fracture;

        if (localFracture == null || noscale) {
            return signedWithOpacity(localGenerator.noiseSigned(x * scl, y * scl, z * scl));
        }

        double fx = x + (localFracture.noiseFastSigned3D(x, y, z) * signedFractureScale);
        double fy = y + (localFracture.noiseFastSigned2D(y, x) * signedFractureScale);
        double fz = z + (localFracture.noiseFastSigned3D(z, x, y) * signedFractureScale);
        return signedWithOpacity(localGenerator.noiseSigned(fx * scl, fy * scl, fz * scl));
    }

    private enum InjectorMode {
        ADD,
        SRC_SUBTRACT,
        DST_SUBTRACT,
        MULTIPLY,
        MAX,
        MIN,
        SRC_MOD,
        SRC_POW,
        DST_MOD,
        DST_POW,
        CUSTOM
    }
}
