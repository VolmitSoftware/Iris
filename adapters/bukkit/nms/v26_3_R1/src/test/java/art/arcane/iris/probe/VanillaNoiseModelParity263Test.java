package art.arcane.iris.probe;

import art.arcane.iris.pack.datapack.DataVersion;
import art.arcane.iris.pack.datapack.export.compile.noise.VanillaNoiseModel;
import net.minecraft.core.HolderGetter;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.Identifier;
import net.minecraft.resources.ResourceKey;
import net.minecraft.util.RandomSource;
import net.minecraft.world.level.levelgen.NoiseGeneratorSettings;
import net.minecraft.world.level.levelgen.PositionalRandomFactory;
import net.minecraft.world.level.levelgen.RandomState;
import net.minecraft.world.level.levelgen.XoroshiroRandomSource;
import net.minecraft.world.level.levelgen.synth.Noise;
import net.minecraft.world.level.levelgen.synth.NormalNoise;
import org.junit.AfterClass;
import org.junit.BeforeClass;
import org.junit.ClassRule;
import org.junit.Test;

import java.io.IOException;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.SplittableRandom;
import java.util.stream.Collectors;

import static org.junit.Assert.assertTrue;

public final class VanillaNoiseModelParity263Test {
    @ClassRule
    public static final PaperRegistryIsolation registryIsolation = new PaperRegistryIsolation();

    private static final int POINTS = 4096;
    private static final double TOLERANCE = 1.0E-5D;
    private static final double RUNTIME_TOLERANCE = 1.0E-4D;
    private static final List<Long> WORLD_SEEDS = List.of(0L, 8_675_309L, -4_611_686_018_427_387_904L);
    private static final List<ParityNoise> NOISES = List.of(
            ParityNoise.mapped("iris_export:parity/single", -7, List.of(1.0D)),
            ParityNoise.mapped("iris_export:parity/fbm", -9, List.of(1.0D, 1.0D, 0.5D, 0.25D)),
            ParityNoise.mapped("iris_export:parity/sparse", -10, List.of(1.5D, 0.0D, 1.0D, 0.0D, 0.0D, 0.0D)),
            ParityNoise.mapped("iris_export:parity/leading_zero", -6, List.of(0.0D, 0.0D, 1.0D, 2.0D)),
            ParityNoise.mapped("iris_export:parity/positive", 2, List.of(1.0D, 0.5D)),
            ParityNoise.profiled("iris_export:parity/enabled",
                    "{\"base_octave\":-10,\"octave_count\":6,\"amplitude_modifiers\":[1.5,0.0,1.0,0.0,0.0,0.0],"
                            + "\"base_amplitude\":1.2453007926713473}",
                    new VanillaNoiseModel.OctaveProfile(-10, 6, List.of(1.5D, 0.0D, 1.0D, 0.0D, 0.0D, 0.0D),
                            1.2453007926713473D, VanillaNoiseModel.Normalization.ENABLED)),
            ParityNoise.profiled("iris_export:parity/legacy",
                    "{\"base_octave\":-9,\"octave_count\":3,\"amplitude_modifiers\":[1.0,0.0,1.0],\"normalize\":\"legacy\"}",
                    new VanillaNoiseModel.OctaveProfile(-9, 3, List.of(1.0D, 0.0D, 1.0D), 1.0D,
                            VanillaNoiseModel.Normalization.LEGACY)),
            ParityNoise.profiled("iris_export:parity/defaults", "{\"base_octave\":-5}",
                    new VanillaNoiseModel.OctaveProfile(-5, 1, List.of(), 1.0D, VanillaNoiseModel.Normalization.ENABLED)),
            ParityNoise.profiled("iris_export:parity/scaled",
                    "{\"base_octave\":-6,\"octave_count\":2,\"normalize\":false,\"base_amplitude\":0.5}",
                    new VanillaNoiseModel.OctaveProfile(-6, 2, List.of(), 0.5D, VanillaNoiseModel.Normalization.DISABLED)));

    private static HeadlessNativeRegistries registries;

    @BeforeClass
    public static void loadNoiseDefinitions() throws IOException {
        Map<String, String> definitions = new LinkedHashMap<>();
        for (ParityNoise noise : NOISES) {
            definitions.put(resourcePath(noise.id()), noise.json());
        }
        registries = HeadlessNativeRegistries.load(definitions);
    }

    @AfterClass
    public static void closeRegistries() throws IOException {
        if (registries != null) {
            registries.close();
        }
    }

    @Test
    public void modelMatchesNoiseBuiltFromLoadedDefinitions() {
        List<String> report = new ArrayList<>();
        for (long seed : WORLD_SEEDS) {
            PositionalRandomFactory world = new VanillaPrecisionRandom(new XoroshiroRandomSource(seed)).forkPositional();
            for (ParityNoise noise : NOISES) {
                NormalNoise definition = registries.registries().lookupOrThrow(Registries.NOISE).getOrThrow(key(noise.id())).value();
                Noise real = definition.create(world.fromHashOf(Identifier.parse(noise.id())));
                report.add(noise.id() + "@" + seed + "=" + assertParity(noise, seed, real, TOLERANCE));
            }
        }
        System.out.println("26.3 vanilla-precision parity " + report);
    }

    @Test
    public void modelMatchesRuntimeRandomStateNoise() {
        List<String> report = new ArrayList<>();
        for (long seed : WORLD_SEEDS) {
            RandomState state = randomState(seed);
            for (ParityNoise noise : NOISES) {
                Noise real = state.getOrCreateNoise(key(noise.id()));
                report.add(noise.id() + "@" + seed + "=" + assertParity(noise, seed, real, RUNTIME_TOLERANCE));
            }
        }
        System.out.println("26.3 runtime parity " + report);
    }

    private static double assertParity(ParityNoise noise, long seed, Noise real, double tolerance) {
        VanillaNoiseModel model = noise.model(seed);
        SplittableRandom random = new SplittableRandom(seed ^ noise.id().hashCode());
        double worst = 0.0D;
        double peak = 0.0D;
        for (int index = 0; index < POINTS; index++) {
            double range = random.nextInt(4) == 0 ? 30_000_000.0D : 100_000.0D;
            double x = random.nextDouble(-range, range);
            double y = random.nextDouble(-2_048.0D, 2_048.0D);
            double z = random.nextDouble(-range, range);
            double expected = real.get(x, y, z);
            worst = Math.max(worst, Math.abs(expected - model.sample(x, y, z)));
            peak = Math.max(peak, Math.abs(expected));
        }
        assertTrue(noise.id() + " seed " + seed + " differs by " + worst, worst <= tolerance);
        assertTrue(noise.id() + " seed " + seed + " is flat", peak > 0.05D);
        return worst;
    }

    private static RandomState randomState(long seed) {
        HolderGetter<NormalNoise> noises = registries.registries().lookupOrThrow(Registries.NOISE);
        NoiseGeneratorSettings settings = registries.registries().lookupOrThrow(Registries.NOISE_SETTINGS)
                .getOrThrow(NoiseGeneratorSettings.OVERWORLD).value();
        return RandomState.create(noises, seed, settings);
    }

    private static ResourceKey<NormalNoise> key(String id) {
        return ResourceKey.create(Registries.NOISE, Identifier.parse(id));
    }

    private static String resourcePath(String id) {
        Identifier identifier = Identifier.parse(id);
        return "data/" + identifier.getNamespace() + "/worldgen/noise/" + identifier.getPath() + ".json";
    }

    private record ParityNoise(String id, String json, int firstOctave, List<Double> amplitudes,
                               VanillaNoiseModel.OctaveProfile profile) {
        private static ParityNoise mapped(String id, int firstOctave, List<Double> amplitudes) {
            String modifiers = amplitudes.stream().map(String::valueOf).collect(Collectors.joining(","));
            String json = "{\"base_octave\":" + firstOctave + ",\"octave_count\":" + amplitudes.size()
                    + ",\"amplitude_modifiers\":[" + modifiers + "],\"base_amplitude\":1.0,\"normalize\":false}";
            return new ParityNoise(id, json, firstOctave, amplitudes, null);
        }

        private static ParityNoise profiled(String id, String json, VanillaNoiseModel.OctaveProfile profile) {
            return new ParityNoise(id, json, 0, List.of(), profile);
        }

        private VanillaNoiseModel model(long seed) {
            if (profile == null) {
                return VanillaNoiseModel.create(id, firstOctave, amplitudes, seed, DataVersion.V26_3);
            }
            return VanillaNoiseModel.create(id, profile, seed);
        }
    }

    private static final class VanillaPrecisionRandom implements RandomSource {
        private static final double DOUBLE_UNIT = 1.1102230246251565E-16D;

        private final RandomSource source;

        private VanillaPrecisionRandom(RandomSource source) {
            this.source = source;
        }

        @Override
        public RandomSource fork() {
            return new VanillaPrecisionRandom(source.fork());
        }

        @Override
        public PositionalRandomFactory forkPositional() {
            return new VanillaPrecisionPositional(source.forkPositional());
        }

        @Override
        public void setSeed(long seed) {
            source.setSeed(seed);
        }

        @Override
        public int nextInt() {
            return source.nextInt();
        }

        @Override
        public int nextInt(int bound) {
            return source.nextInt(bound);
        }

        @Override
        public long nextLong() {
            return source.nextLong();
        }

        @Override
        public boolean nextBoolean() {
            return source.nextBoolean();
        }

        @Override
        public float nextFloat() {
            return source.nextFloat();
        }

        @Override
        public double nextDouble() {
            return (source.nextLong() >>> 11) * DOUBLE_UNIT;
        }

        @Override
        public double nextGaussian() {
            return source.nextGaussian();
        }
    }

    private record VanillaPrecisionPositional(PositionalRandomFactory factory) implements PositionalRandomFactory {
        @Override
        public RandomSource at(int x, int y, int z) {
            return new VanillaPrecisionRandom(factory.at(x, y, z));
        }

        @Override
        public RandomSource fromHashOf(String name) {
            return new VanillaPrecisionRandom(factory.fromHashOf(name));
        }

        @Override
        public RandomSource fromSeed(long seed) {
            return new VanillaPrecisionRandom(factory.fromSeed(seed));
        }

        @Override
        public void parityConfigString(StringBuilder builder) {
            factory.parityConfigString(builder);
        }
    }
}
