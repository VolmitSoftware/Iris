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

package com.volmit.iris.util.noise;

import com.volmit.iris.Iris;
import com.volmit.iris.engine.object.IRare;
import com.volmit.iris.engine.object.NoiseStyle;
import com.volmit.iris.util.cache.FloatCache;
import com.volmit.iris.util.collection.KList;
import com.volmit.iris.util.format.Form;
import com.volmit.iris.util.function.NoiseInjector;
import com.volmit.iris.util.interpolation.IrisInterpolation;
import com.volmit.iris.util.math.RNG;
import com.volmit.iris.util.scheduling.PrecisionStopwatch;
import com.volmit.iris.util.stream.ProceduralStream;
import com.volmit.iris.util.stream.arithmetic.FittedStream;
import com.volmit.iris.util.stream.sources.CNGStream;
import lombok.Data;

import java.io.DataOutputStream;
import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
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
    public static long hits = 0;
    public static long creates = 0;
    private final double opacity;
    private double scale;
    private double bakedScale;
    private double fscale;
    private boolean trueFracturing = false;
    private KList<CNG> children;
    private CNG fracture;
    private FloatCache cache;
    private NoiseGenerator generator;
    private NoiseInjector injector;
    private RNG rng;
    private boolean noscale;
    private int oct;
    private double patch;
    private double up;
    private double down;
    private double power;
    private NoiseStyle leakStyle;
    private ProceduralStream<Double> customGenerator;

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
        this(random, type.create(random.nextParallelRNG((long) ((1113334944L * opacity) + 12922 + octaves)).lmax()), opacity, octaves);
    }

    public CNG(RNG random, NoiseGenerator generator, double opacity, int octaves) {
        customGenerator = null;
        creates++;
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

        if (generator instanceof OctaveNoise) {
            ((OctaveNoise) generator).setOctaves(octaves);
        }
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

        System.out.println(Form.duration(p.getMilliseconds(), 10) + " merged = " + r);
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
        if (size <= 0) {
            return this;
        }

        cache = null;

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

            try {
                f.getParentFile().mkdirs();
                FileOutputStream fos = new FileOutputStream(f);
                DataOutputStream dos = new DataOutputStream(fos);
                fbc.writeCache(dos);
                dos.close();
                Iris.info("Saved Noise Cache " + f.getName());
            } catch (IOException e) {
                throw new RuntimeException(e);
            }
        }

        cache = fbc;
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
        return this;
    }

    public CNG child(CNG c) {
        if (children == null) {
            children = new KList<>();
        }

        children.add(c);
        return this;
    }

    public RNG getRNG() {
        return rng;
    }

    public CNG fractureWith(CNG c, double scale) {
        fracture = c;
        fscale = scale;
        return this;
    }

    public CNG scale(double c) {
        scale = c;
        return this;
    }

    public CNG patch(double c) {
        patch = c;
        return this;
    }

    public CNG up(double c) {
        up = c;
        return this;
    }

    public CNG down(double c) {
        down = c;
        return this;
    }

    public CNG injectWith(NoiseInjector i) {
        injector = i;
        return this;
    }

    public <T extends IRare> T fitRarity(KList<T> b, double... dim) {
        if (b.size() == 0) {
            return null;
        }

        if (b.size() == 1) {
            return b.get(0);
        }

        KList<T> rarityMapped = new KList<>();
        boolean o = false;
        int max = 1;
        for (T i : b) {
            if (i.getRarity() > max) {
                max = i.getRarity();
            }
        }

        max++;

        for (T i : b) {
            for (int j = 0; j < max - i.getRarity(); j++) {
                //noinspection AssignmentUsedAsCondition
                if (o = !o) {
                    rarityMapped.add(i);
                } else {
                    rarityMapped.add(0, i);
                }
            }
        }

        if (rarityMapped.size() == 1) {
            return rarityMapped.get(0);
        }

        if (rarityMapped.isEmpty()) {
            throw new RuntimeException("BAD RARITY MAP! RELATED TO: " + b.toString(", or possibly "));
        }

        return fit(rarityMapped, dim);
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
            Iris.reportError(e);
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

    public int fit(double min, double max, double... dim) {
        if (min == max) {
            return (int) Math.round(min);
        }

        double noise = noise(dim);

        return (int) Math.round(IrisInterpolation.lerp(min, max, noise));
    }

    public double fitDouble(double min, double max, double... dim) {
        if (min == max) {
            return min;
        }

        double noise = noise(dim);

        return IrisInterpolation.lerp(min, max, noise);
    }

    private double getNoise(double... dim) {
        double scale = noscale ? 1 : this.bakedScale * this.scale;

        if (fracture == null || noscale) {
            return generator.noise(
                    (dim.length > 0 ? dim[0] : 0D) * scale,
                    (dim.length > 1 ? dim[1] : 0D) * scale,
                    (dim.length > 2 ? dim[2] : 0D) * scale) * opacity;
        }

        if (fracture.isTrueFracturing()) {
            double x = dim.length > 0 ? dim[0] + ((fracture.noise(dim) - 0.5) * fscale) : 0D;
            double y = dim.length > 1 ? dim[1] + ((fracture.noise(dim[1], dim[0]) - 0.5) * fscale) : 0D;
            double z = dim.length > 2 ? dim[2] + ((fracture.noise(dim[2], dim[0], dim[1]) - 0.5) * fscale) : 0D;
            return generator.noise(x * scale, y * scale, z * scale) * opacity;
        }

        double f = fracture.noise(dim) * fscale;
        double x = dim.length > 0 ? dim[0] + f : 0D;
        double y = dim.length > 1 ? dim[1] - f : 0D;
        double z = dim.length > 2 ? dim[2] - f : 0D;
        return generator.noise(x * scale, y * scale, z * scale) * opacity;
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

    public double noise(double... dim) {
        if (cache != null && dim.length == 2) {
            return cache.get((int) dim[0], (int) dim[1]);
        }

        double n = getNoise(dim);
        n = power != 1D ? (n < 0 ? -Math.pow(Math.abs(n), power) : Math.pow(n, power)) : n;
        double m = 1;
        hits += oct;
        if (children == null) {
            return (n - down + up) * patch;
        }

        for (CNG i : children) {
            double[] r = injector.combine(n, i.noise(dim));
            n = r[0];
            m += r[1];
        }

        return ((n / m) - down + up) * patch;
    }

    public CNG pow(double power) {
        this.power = power;
        return this;
    }

    public CNG oct(int octaves) {
        oct = octaves;
        return this;
    }

    public double getScale() {
        return scale;
    }

    public boolean isStatic() {
        return generator != null && generator.isStatic();
    }
}
