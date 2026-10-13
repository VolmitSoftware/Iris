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

import art.arcane.iris.pack.loading.IrisData;
import art.arcane.iris.generation.cache.LazyBoundedCache;
import art.arcane.iris.generation.runtime.Engine;
import art.arcane.iris.pack.schema.annotation.ArrayType;
import art.arcane.volmlib.util.documentation.Description;
import art.arcane.iris.pack.schema.annotation.MaxNumber;
import art.arcane.iris.pack.schema.annotation.MinNumber;
import art.arcane.iris.pack.schema.annotation.Required;
import art.arcane.iris.pack.schema.annotation.Snippet;
import art.arcane.volmlib.util.collection.KList;
import art.arcane.volmlib.util.math.RNG;
import art.arcane.volmlib.util.interpolation.IrisInterpolation;
import art.arcane.volmlib.util.noise.CNG;
import art.arcane.volmlib.util.noise.FlatNoise;
import art.arcane.volmlib.util.noise.FractalBillowPerlinNoise;
import art.arcane.volmlib.util.noise.FractalBillowSimplexNoise;
import art.arcane.volmlib.util.noise.FractalFBMSimplexNoise;
import art.arcane.volmlib.util.noise.NoiseGenerator;
import art.arcane.volmlib.util.noise.OffsetNoiseGenerator;
import art.arcane.volmlib.util.noise.PerlinNoise;
import art.arcane.volmlib.util.noise.SimplexNoise;
import lombok.AccessLevel;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import lombok.experimental.Accessors;

import java.util.concurrent.atomic.AtomicReference;

@Snippet("generator")
@Accessors(chain = true)
@NoArgsConstructor
@AllArgsConstructor
@Description("A noise generator")
@Data
public class IrisNoiseGenerator {
    private static final int GENERATOR_CACHE_SIZE = 32;
    private static final long GENERATOR_SEED_SALT = 33_955_677L;
    private static final int MINIMUM_FRACTURE_DEPTH = 4;
    private static final int MAXIMUM_FRACTURE_NODES = 32;
    private static final ThreadLocal<FractureSamples> FRACTURE_SAMPLES = ThreadLocal.withInitial(FractureSamples::new);

    @Getter(AccessLevel.NONE)
    @Setter(AccessLevel.NONE)
    private final transient LazyBoundedCache<GeneratorKey, CNG> generators =
            new LazyBoundedCache<>(GENERATOR_CACHE_SIZE);
    @Getter(AccessLevel.NONE)
    @Setter(AccessLevel.NONE)
    private final transient AtomicReference<CachedGenerator> recentGenerator = new AtomicReference<>();
    @MinNumber(0.0001)
    @Description("The coordinate input zoom")
    private double zoom = 1;
    @Description("Reverse the output. So that noise = -noise + opacity")
    private boolean negative = false;
    @MinNumber(0)
    @MaxNumber(1)
    @Description("The output multiplier")
    private double opacity = 1;
    @Description("Coordinate offset x")
    private double offsetX = 0;
    @Description("Height output offset y. Avoid using with terrain generation.")
    private double offsetY = 0;
    @Description("Coordinate offset z")
    private double offsetZ = 0;
    @Required
    @Description("The seed")
    private long seed = 0;
    @Description("Apply a parametric curve on the output")
    private boolean parametric = false;
    @Description("Apply a bezier curve on the output")
    private boolean bezier = false;
    @Description("Apply a sin-center curve on the output (0, and 1 = 0 and 0.5 = 1.0 using a sinoid shape.)")
    private boolean sinCentered = false;
    @Description("The exponent noise^EXPONENT")
    private double exponent = 1;
    @Description("Enable / disable. Outputs offsetY if disabled")
    private boolean enabled = true;
    @Required
    @Description("The Noise Style")
    private IrisGeneratorStyle style = NoiseStyle.IRIS.style();
    @MinNumber(1)
    @MaxNumber(16)
    @Description("Multiplies the style's octave count, capped at 16. Adds finer detail while preserving the base feature scale.")
    private int octaves = 1;
    @ArrayType(min = 1, type = IrisNoiseGenerator.class)
    @Description("Apply a child noise generator to fracture the input coordinates of this generator")
    private KList<IrisNoiseGenerator> fracture = new KList<>();

    public IrisNoiseGenerator(boolean enabled) {
        this();
        this.enabled = enabled;
    }

    CNG getGenerator(long superSeed, IrisData data, Engine engine) {
        long generatorSeed = superSeed + GENERATOR_SEED_SALT - seed;
        CachedGenerator recent = recentGenerator.get();
        if (recent != null && recent.key.matches(data, engine, generatorSeed)) {
            return recent.generator;
        }

        GeneratorKey key = new GeneratorKey(data, engine, generatorSeed);
        CNG generator = generators.computeIfAbsent(key,
                ignored -> style.createForLayer(new RNG(generatorSeed), data, octaves, engine));
        if (generator != null) {
            recentGenerator.set(new CachedGenerator(key, generator));
        }
        return generator;
    }

    public double getMax() {
        return getOffsetY() + opacity;
    }

    public double getNoise(long superSeed, double xv, double zv, IrisData data) {
        if (!enabled) {
            return offsetY;
        }

        return getNoise(superSeed, xv, zv, data, data == null ? null : data.getEngine());
    }

    double getNoise(long superSeed, double xv, double zv, IrisData data, Engine engine) {
        if (!enabled) {
            return offsetY;
        }

        if (hasDeepFracture()) {
            FractureSamples samples = FRACTURE_SAMPLES.get();
            if (!samples.active) {
                samples.active = true;
                try {
                    if (samples.prepare(this, superSeed, data, engine)) {
                        return samples.sample(xv, zv);
                    }
                } finally {
                    samples.clear();
                }
            }
        }

        double x = xv;
        double z = zv;
        int g = 33;

        for (IrisNoiseGenerator i : fracture) {
            if (i.isEnabled()) {
                double fractureOffset = i.getOpacity() / 2D;
                x += i.getNoise(superSeed + seed + g, xv, zv, data, engine) - fractureOffset;
                z += i.getNoise(superSeed + seed + g, zv, xv, data, engine) - fractureOffset;
            }
            g += 819;
        }

        return sample(getGenerator(superSeed, data, engine), x, z);
    }

    public KList<IrisNoiseGenerator> getAllComposites() {
        KList<IrisNoiseGenerator> g = new KList<>();

        g.add(this);

        for (IrisNoiseGenerator i : getFracture()) {
            g.addAll(i.getAllComposites());
        }

        return g;
    }

    private double sample(CNG cng, double x, double z) {
        double sampleX = (x / zoom) + offsetX;
        double sampleZ = (z / zoom) + offsetZ;
        double n = cng.noiseFast2D(sampleX, sampleZ) * opacity;
        n = negative ? (-n + opacity) : n;
        n = (exponent != 1 ? n < 0 ? -Math.pow(-n, exponent) : Math.pow(n, exponent) : n) + offsetY;
        n = parametric ? IrisInterpolation.parametric(n, 2D) : n;
        n = bezier ? IrisInterpolation.bezier(n) : n;
        n = sinCentered ? IrisInterpolation.sinCenter(n) : n;

        return n;
    }

    private boolean hasDeepFracture() {
        IrisNoiseGenerator node = this;
        for (int depth = 0; depth < MINIMUM_FRACTURE_DEPTH; depth++) {
            if (node.getClass() != IrisNoiseGenerator.class || !node.enabled || node.fracture.size() != 1) {
                return false;
            }
            node = node.fracture.get(0);
        }
        return true;
    }

    private boolean hasPureStyle() {
        return style != null && style.getClass() == IrisGeneratorStyle.class
                && style.getExpression() == null && style.getImageMap() == null
                && style.getFracture() == null && style.getCellularFrequency() == 0D
                && style.getCacheSize() == 0;
    }

    private static boolean isPureGenerator(CNG cng) {
        for (int depth = 0; depth < MAXIMUM_FRACTURE_NODES; depth++) {
            if (cng.getClass() != CNG.class || cng.getChildren() != null
                    || cng.getCustomGenerator() != null || cng.getInjector() != CNG.ADD
                    || !isPureBase(cng.getGenerator())) {
                return false;
            }
            cng = cng.getFracture();
            if (cng == null) {
                return true;
            }
        }
        return false;
    }

    private static boolean isPureBase(NoiseGenerator generator) {
        for (int depth = 0; depth < MAXIMUM_FRACTURE_NODES && generator != null; depth++) {
            Class<?> type = generator.getClass();
            if (type != OffsetNoiseGenerator.class) {
                return type == SimplexNoise.class || type == PerlinNoise.class || type == FlatNoise.class
                        || type == FractalBillowPerlinNoise.class || type == FractalBillowSimplexNoise.class
                        || type == FractalFBMSimplexNoise.class;
            }
            generator = ((OffsetNoiseGenerator) generator).getBase();
        }
        return false;
    }

    private static final class GeneratorKey {
        private final IrisData data;
        private final Engine engine;
        private final long seed;

        private GeneratorKey(IrisData data, Engine engine, long seed) {
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
            if (!(object instanceof GeneratorKey other)) {
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

    private static final class FractureSamples {
        private final IrisNoiseGenerator[] nodes = new IrisNoiseGenerator[MAXIMUM_FRACTURE_NODES];
        private final CNG[] generators = new CNG[MAXIMUM_FRACTURE_NODES];
        private int size;
        private boolean active;

        private boolean prepare(IrisNoiseGenerator node, long superSeed, IrisData data, Engine engine) {
            while (size < MAXIMUM_FRACTURE_NODES) {
                if (node.getClass() != IrisNoiseGenerator.class || !node.enabled || !node.hasPureStyle()
                        || node.fracture.size() > 1) {
                    return false;
                }
                CNG generator = node.getGenerator(superSeed, data, engine);
                if (!isPureGenerator(generator)) {
                    return false;
                }
                nodes[size] = node;
                generators[size++] = generator;
                if (node.fracture.isEmpty()) {
                    return true;
                }
                superSeed += node.seed + 33;
                node = node.fracture.get(0);
            }
            return false;
        }

        private double sample(double x, double z) {
            int leaf = size - 1;
            double forward = nodes[leaf].sample(generators[leaf], x, z);
            double reverse = nodes[leaf].sample(generators[leaf], z, x);
            for (int index = leaf - 1; index >= 0; index--) {
                double offset = nodes[index + 1].opacity / 2D;
                double forwardX = x + (forward - offset);
                double forwardZ = z + (reverse - offset);
                forward = nodes[index].sample(generators[index], forwardX, forwardZ);
                if (index > 0) {
                    reverse = nodes[index].sample(generators[index], forwardZ, forwardX);
                }
            }
            return forward;
        }

        private void clear() {
            for (int index = 0; index < size; index++) {
                nodes[index] = null;
                generators[index] = null;
            }
            size = 0;
            active = false;
        }
    }

    private static final class CachedGenerator {
        private final GeneratorKey key;
        private final CNG generator;

        private CachedGenerator(GeneratorKey key, CNG generator) {
            this.key = key;
            this.generator = generator;
        }
    }
}
