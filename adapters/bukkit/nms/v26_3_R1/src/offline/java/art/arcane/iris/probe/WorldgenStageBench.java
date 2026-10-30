package art.arcane.iris.probe;

import io.papermc.paper.configuration.WorldConfiguration;
import net.minecraft.core.Holder;
import net.minecraft.core.QuartPos;
import net.minecraft.core.RegistryAccess;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.Identifier;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.LevelHeightAccessor;
import net.minecraft.world.level.biome.Biome;
import net.minecraft.world.level.biome.BiomeManager;
import net.minecraft.world.level.biome.BiomeResolver;
import net.minecraft.world.level.chunk.ChunkAccess;
import net.minecraft.world.level.chunk.PalettedContainerFactory;
import net.minecraft.world.level.chunk.ProtoChunk;
import net.minecraft.world.level.chunk.UpgradeData;
import net.minecraft.world.level.chunk.status.ChunkStatus;
import net.minecraft.world.level.dimension.DimensionType;
import net.minecraft.world.level.dimension.LevelStem;
import net.minecraft.world.level.levelgen.Aquifer;
import net.minecraft.world.level.levelgen.Heightmap;
import net.minecraft.world.level.levelgen.NoiseBasedChunkGenerator;
import net.minecraft.world.level.levelgen.NoiseChunk;
import net.minecraft.world.level.levelgen.NoiseGeneratorSettings;
import net.minecraft.world.level.levelgen.NoiseSettings;
import net.minecraft.world.level.levelgen.RandomState;
import net.minecraft.world.level.levelgen.WorldGenerationContext;
import net.minecraft.world.level.levelgen.blending.Blender;
import net.minecraft.world.level.levelgen.densityfunction.DensityVolume;
import net.minecraft.world.level.levelgen.material.rule.MaterialRule;
import org.spigotmc.SpigotWorldConfig;

import java.lang.management.ManagementFactory;
import java.lang.management.ThreadMXBean;
import java.lang.reflect.Constructor;
import java.lang.reflect.Field;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.util.Arrays;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.SplittableRandom;
import java.util.function.Supplier;

final class WorldgenStageBench {
    private static final int SAMPLE_RADIUS_BLOCKS = 32_768;
    private static final double NANOS_PER_MILLI = 1_000_000.0;
    private static final double NANOS_PER_MICRO = 1_000.0;
    private static final ThreadMXBean THREADS = ManagementFactory.getThreadMXBean();

    private final String label;
    private final NoiseBasedChunkGenerator generator;
    private final NoiseGeneratorSettings settings;
    private final RandomState randomState;
    private final LevelHeightAccessor heights;
    private final PalettedContainerFactory containers;
    private final long seed;
    private final Method fill;
    private final Aquifer.FluidPicker fluidPicker;
    private final BiomeResolver uncached;
    private final Level surfaceLevel;

    WorldgenStageBench(Options options) {
        label = options.label();
        if (!(options.stem().generator() instanceof NoiseBasedChunkGenerator noiseGenerator)) {
            throw new IllegalStateException(label + " uses " + options.stem().generator().getClass().getSimpleName() + ", not a noise generator");
        }
        generator = noiseGenerator;
        settings = noiseGenerator.generatorSettings().value();
        seed = options.seed();
        randomState = RandomState.create(options.registries().lookupOrThrow(Registries.NOISE), seed, settings);
        DimensionType type = options.stem().type().value();
        heights = LevelHeightAccessor.create(type.minY(), type.height());
        containers = PalettedContainerFactory.create(options.registries());
        fill = fillMethod();
        fluidPicker = fluidPicker(noiseGenerator);
        uncached = noiseGenerator.getBiomeSource().createUncachedResolver(randomState);
        surfaceLevel = paperConfiguredLevel();
    }

    String label() {
        return label;
    }

    NoiseGeneratorSettings settings() {
        return settings;
    }

    void chunks(int originChunkX, int originChunkZ, int side, ChunkSamples samples) {
        int ring = side + 2;
        Map<Long, ProtoChunk> chunks = new HashMap<>(ring * ring * 2);
        for (int dz = -1; dz <= side; dz++) {
            for (int dx = -1; dx <= side; dx++) {
                ChunkPos position = new ChunkPos(originChunkX + dx, originChunkZ + dz);
                ProtoChunk chunk = new ProtoChunk(position, UpgradeData.EMPTY, heights, containers, null);
                long started = System.nanoTime();
                long startedCpu = THREADS.getCurrentThreadCpuTime();
                generator.createBiomes(randomState, Blender.empty(), null, chunk).join();
                samples.biomes.add(System.nanoTime() - started, THREADS.getCurrentThreadCpuTime() - startedCpu);
                chunk.setPersistedStatus(ChunkStatus.BIOMES);
                chunks.put(ChunkPos.pack(position.x(), position.z()), chunk);
            }
        }
        RegionBiomes region = new RegionBiomes(chunks, uncached);
        BiomeManager biomeManager = new BiomeManager(region, BiomeManager.obfuscateSeed(seed));
        MaterialRule materialRule = settings.materialRule().value();
        for (int dz = 0; dz < side; dz++) {
            for (int dx = 0; dx < side; dx++) {
                int chunkX = originChunkX + dx;
                int chunkZ = originChunkZ + dz;
                ProtoChunk chunk = chunks.get(ChunkPos.pack(chunkX, chunkZ));
                Set<Holder<Biome>> possible = possibleBiomes(chunks, chunkX, chunkZ);
                long started = System.nanoTime();
                long startedCpu = THREADS.getCurrentThreadCpuTime();
                try (NoiseChunk noiseChunk = new NoiseChunk(randomState, null, settings, fluidPicker, Blender.empty(), volume(chunk))) {
                    fill(noiseChunk, chunk);
                    long filled = System.nanoTime();
                    long filledCpu = THREADS.getCurrentThreadCpuTime();
                    randomState.surfaceSystem().buildSurface(randomState, biomeManager,
                            new WorldGenerationContext(generator, chunk.getHeightAccessorForGeneration(), surfaceLevel), chunk, noiseChunk, materialRule, possible);
                    samples.surface.add(System.nanoTime() - filled, THREADS.getCurrentThreadCpuTime() - filledCpu);
                    samples.noise.add(filled - started, filledCpu - startedCpu);
                }
                ChunkPos position = chunk.getPos();
                long carverStarted = System.nanoTime();
                long carverStartedCpu = THREADS.getCurrentThreadCpuTime();
                uncached.getNoiseBiome(QuartPos.fromBlock(position.getMinBlockX()), 0, QuartPos.fromBlock(position.getMinBlockZ()));
                samples.carverBiome.add(System.nanoTime() - carverStarted, THREADS.getCurrentThreadCpuTime() - carverStartedCpu);
                samples.chunks++;
            }
        }
        samples.biomeFallbacks += region.fallbacks();
    }

    void uncachedBiome(CallBudget budget, CallSamples samples) {
        SplittableRandom random = new SplittableRandom(seed ^ 0x5DEECE66DL ^ samples.count);
        int minQuart = QuartPos.fromBlock(heights.getMinY());
        int quartHeight = QuartPos.fromBlock(heights.getHeight());
        long deadline = System.nanoTime() + budget.limitNanos();
        long startedCpu = THREADS.getCurrentThreadCpuTime();
        int taken = 0;
        while (taken < budget.calls() && (taken < budget.minimumCalls() || System.nanoTime() < deadline)) {
            int quartX = QuartPos.fromBlock(random.nextInt(-SAMPLE_RADIUS_BLOCKS, SAMPLE_RADIUS_BLOCKS));
            int quartY = minQuart + random.nextInt(quartHeight);
            int quartZ = QuartPos.fromBlock(random.nextInt(-SAMPLE_RADIUS_BLOCKS, SAMPLE_RADIUS_BLOCKS));
            long started = System.nanoTime();
            uncached.getNoiseBiome(quartX, quartY, quartZ);
            samples.wall.add(System.nanoTime() - started);
            taken++;
        }
        samples.finish(taken, THREADS.getCurrentThreadCpuTime() - startedCpu);
    }

    void baseHeight(CallBudget budget, Heightmap.Types type, CallSamples samples) {
        SplittableRandom random = new SplittableRandom(seed ^ type.ordinal() ^ 0x2545F4914F6CDD1DL ^ samples.count);
        long deadline = System.nanoTime() + budget.limitNanos();
        long startedCpu = THREADS.getCurrentThreadCpuTime();
        int taken = 0;
        while (taken < budget.calls() && (taken < budget.minimumCalls() || System.nanoTime() < deadline)) {
            int x = random.nextInt(-SAMPLE_RADIUS_BLOCKS, SAMPLE_RADIUS_BLOCKS);
            int z = random.nextInt(-SAMPLE_RADIUS_BLOCKS, SAMPLE_RADIUS_BLOCKS);
            long started = System.nanoTime();
            generator.getBaseHeight(x, z, type, heights, randomState);
            samples.wall.add(System.nanoTime() - started);
            taken++;
        }
        samples.finish(taken, THREADS.getCurrentThreadCpuTime() - startedCpu);
    }

    void baseColumn(CallBudget budget, CallSamples samples) {
        SplittableRandom random = new SplittableRandom(seed ^ 0x9E3779B97F4A7C15L ^ samples.count);
        long deadline = System.nanoTime() + budget.limitNanos();
        long startedCpu = THREADS.getCurrentThreadCpuTime();
        int taken = 0;
        while (taken < budget.calls() && (taken < budget.minimumCalls() || System.nanoTime() < deadline)) {
            int x = random.nextInt(-SAMPLE_RADIUS_BLOCKS, SAMPLE_RADIUS_BLOCKS);
            int z = random.nextInt(-SAMPLE_RADIUS_BLOCKS, SAMPLE_RADIUS_BLOCKS);
            long started = System.nanoTime();
            generator.getBaseColumn(x, z, heights, randomState);
            samples.wall.add(System.nanoTime() - started);
            taken++;
        }
        samples.finish(taken, THREADS.getCurrentThreadCpuTime() - startedCpu);
    }

    private void fill(NoiseChunk noiseChunk, ChunkAccess chunk) {
        try {
            fill.invoke(generator, noiseChunk, chunk);
        } catch (IllegalAccessException failure) {
            throw new IllegalStateException("Cannot invoke NoiseBasedChunkGenerator.doFill for " + label, failure);
        } catch (InvocationTargetException failure) {
            if (failure.getCause() instanceof RuntimeException runtime) {
                throw runtime;
            }
            throw new IllegalStateException("Noise fill failed for " + label, failure.getCause());
        }
    }

    private DensityVolume volume(ChunkAccess chunk) {
        NoiseSettings noise = settings.noiseSettings().clampToHeightAccessor(chunk.getHeightAccessorForGeneration());
        ChunkPos position = chunk.getPos();
        return new DensityVolume(16, noise.height(), 16, position.getMinBlockX(), noise.minY(), position.getMinBlockZ());
    }

    private static Set<Holder<Biome>> possibleBiomes(Map<Long, ProtoChunk> chunks, int chunkX, int chunkZ) {
        Set<Holder<Biome>> possible = new HashSet<>();
        for (int dz = -1; dz <= 1; dz++) {
            for (int dx = -1; dx <= 1; dx++) {
                chunks.get(ChunkPos.pack(chunkX + dx, chunkZ + dz)).collectBiomesInPalette(possible);
            }
        }
        return possible;
    }

    private static Method fillMethod() {
        try {
            Method method = NoiseBasedChunkGenerator.class.getDeclaredMethod("doFill", NoiseChunk.class, ChunkAccess.class);
            method.setAccessible(true);
            return method;
        } catch (NoSuchMethodException failure) {
            throw new IllegalStateException("NoiseBasedChunkGenerator has no doFill(NoiseChunk, ChunkAccess)", failure);
        }
    }

    @SuppressWarnings("unchecked")
    private static Aquifer.FluidPicker fluidPicker(NoiseBasedChunkGenerator generator) {
        try {
            Field field = NoiseBasedChunkGenerator.class.getDeclaredField("globalFluidPicker");
            field.setAccessible(true);
            return ((Supplier<Aquifer.FluidPicker>) field.get(generator)).get();
        } catch (ReflectiveOperationException failure) {
            throw new IllegalStateException("NoiseBasedChunkGenerator has no globalFluidPicker supplier", failure);
        }
    }

    private static Level paperConfiguredLevel() {
        try {
            Constructor<WorldConfiguration> constructor = WorldConfiguration.class.getDeclaredConstructor(SpigotWorldConfig.class, Identifier.class);
            constructor.setAccessible(true);
            WorldConfiguration configuration = constructor.newInstance(null, Identifier.withDefaultNamespace("overworld"));
            configuration.environment = configuration.new Environment();
            Class<?> unsafeType = Class.forName("sun.misc.Unsafe");
            Field unsafeField = unsafeType.getDeclaredField("theUnsafe");
            unsafeField.setAccessible(true);
            Method allocate = unsafeType.getMethod("allocateInstance", Class.class);
            Level level = (Level) allocate.invoke(unsafeField.get(null), ServerLevel.class);
            Field paperConfig = Level.class.getDeclaredField("paperConfig");
            paperConfig.setAccessible(true);
            paperConfig.set(level, configuration);
            return level;
        } catch (ReflectiveOperationException failure) {
            throw new IllegalStateException("Cannot build the Paper world configuration that surface rules read", failure);
        }
    }

    record Options(String label, LevelStem stem, RegistryAccess registries, long seed) {
        Options {
            Objects.requireNonNull(label, "label");
            Objects.requireNonNull(stem, "stem");
            Objects.requireNonNull(registries, "registries");
        }
    }

    record CallBudget(int calls, int minimumCalls, long limitNanos) {
    }

    record ChunkTimings(int chunks, StageTiming biomes, StageTiming noise, StageTiming surface, StageTiming carverBiome,
                        long biomeFallbacks) {
        double totalMean() {
            return biomes.wall().mean() + noise.wall().mean() + surface.wall().mean() + carverBiome.wall().mean();
        }

        double totalCpuMean() {
            return biomes.cpu().mean() + noise.cpu().mean() + surface.cpu().mean() + carverBiome.cpu().mean();
        }
    }

    record StageTiming(Summary wall, Summary cpu) {
    }

    record CallTiming(Summary wall, int calls, double cpuMean) {
    }

    record Summary(int count, double mean, double median, double p90, double min, double max) {
        private static Summary of(long[] nanos, double unit) {
            if (nanos.length == 0) {
                return new Summary(0, Double.NaN, Double.NaN, Double.NaN, Double.NaN, Double.NaN);
            }
            long[] sorted = nanos.clone();
            Arrays.sort(sorted);
            double total = 0.0;
            for (long value : sorted) {
                total += value;
            }
            return new Summary(sorted.length, total / sorted.length / unit, percentile(sorted, 0.5) / unit,
                    percentile(sorted, 0.9) / unit, sorted[0] / unit, sorted[sorted.length - 1] / unit);
        }

        private static double percentile(long[] sorted, double fraction) {
            int index = (int) Math.min(sorted.length - 1, Math.round(fraction * (sorted.length - 1)));
            return sorted[index];
        }
    }

    static final class ChunkSamples {
        private final StageSamples biomes = new StageSamples();
        private final StageSamples noise = new StageSamples();
        private final StageSamples surface = new StageSamples();
        private final StageSamples carverBiome = new StageSamples();
        private int chunks;
        private long biomeFallbacks;

        ChunkTimings timings() {
            return new ChunkTimings(chunks, biomes.timing(), noise.timing(), surface.timing(), carverBiome.timing(), biomeFallbacks);
        }
    }

    static final class CallSamples {
        private final Samples wall = new Samples();
        private int count;
        private long cpuNanos;

        CallTiming timing() {
            return new CallTiming(Summary.of(wall.values(), NANOS_PER_MICRO), count,
                    count == 0 ? Double.NaN : cpuNanos / (double) count / NANOS_PER_MICRO);
        }

        private void finish(int calls, long cpu) {
            count += calls;
            cpuNanos += cpu;
        }
    }

    private static final class StageSamples {
        private final Samples wall = new Samples();
        private final Samples cpu = new Samples();

        private void add(long wallNanos, long cpuNanos) {
            wall.add(wallNanos);
            cpu.add(cpuNanos);
        }

        private StageTiming timing() {
            return new StageTiming(Summary.of(wall.values(), NANOS_PER_MILLI), Summary.of(cpu.values(), NANOS_PER_MILLI));
        }
    }

    private static final class Samples {
        private long[] values = new long[64];
        private int size;

        private void add(long value) {
            if (size == values.length) {
                values = Arrays.copyOf(values, size * 2);
            }
            values[size++] = value;
        }

        private long[] values() {
            return Arrays.copyOf(values, size);
        }
    }

    private static final class RegionBiomes implements BiomeResolver {
        private final Map<Long, ProtoChunk> chunks;
        private final BiomeResolver fallback;
        private long fallbacks;

        private RegionBiomes(Map<Long, ProtoChunk> chunks, BiomeResolver fallback) {
            this.chunks = chunks;
            this.fallback = fallback;
        }

        @Override
        public Holder<Biome> getNoiseBiome(int quartX, int quartY, int quartZ) {
            ProtoChunk chunk = chunks.get(ChunkPos.pack(QuartPos.toSection(quartX), QuartPos.toSection(quartZ)));
            if (chunk == null) {
                fallbacks++;
                return fallback.getNoiseBiome(quartX, quartY, quartZ);
            }
            return chunk.getNoiseBiome(quartX, quartY, quartZ);
        }

        private long fallbacks() {
            return fallbacks;
        }
    }
}
