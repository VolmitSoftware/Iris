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

    protected CNG getGenerator(long superSeed, IrisData data) {
        Engine engine = data == null ? null : data.getEngine();
        long generatorSeed = superSeed + GENERATOR_SEED_SALT - seed;
        CachedGenerator recent = recentGenerator.get();
        if (recent != null && recent.key.matches(data, engine, generatorSeed)) {
            return recent.generator;
        }

        GeneratorKey key = new GeneratorKey(data, engine, generatorSeed);
        CNG generator = generators.computeIfAbsent(key,
                ignored -> style.createForLayer(new RNG(generatorSeed), data, octaves));
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

        double x = xv;
        double z = zv;
        int g = 33;

        for (IrisNoiseGenerator i : fracture) {
            if (i.isEnabled()) {
                double fractureOffset = i.getOpacity() / 2D;
                x += i.getNoise(superSeed + seed + g, xv, zv, data) - fractureOffset;
                z += i.getNoise(superSeed + seed + g, zv, xv, data) - fractureOffset;
            }
            g += 819;
        }

        CNG cng = getGenerator(superSeed, data);
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

    public KList<IrisNoiseGenerator> getAllComposites() {
        KList<IrisNoiseGenerator> g = new KList<>();

        g.add(this);

        for (IrisNoiseGenerator i : getFracture()) {
            g.addAll(i.getAllComposites());
        }

        return g;
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

    private static final class CachedGenerator {
        private final GeneratorKey key;
        private final CNG generator;

        private CachedGenerator(GeneratorKey key, CNG generator) {
            this.key = key;
            this.generator = generator;
        }
    }
}
