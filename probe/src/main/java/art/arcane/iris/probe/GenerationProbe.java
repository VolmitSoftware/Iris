/*
 * Iris is a World Generator for Minecraft Servers
 * Copyright (c) 2026 Arcane Arts (Volmit Software)
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

package art.arcane.iris.probe;

import art.arcane.iris.generation.bake.BakePlane;
import art.arcane.iris.generation.bake.GeneratorEvaluations;
import art.arcane.iris.generation.mantle.IrisCaveCarver3D;
import art.arcane.iris.pack.datapack.DataVersion;
import art.arcane.iris.pack.loading.IrisData;
import art.arcane.iris.pack.PackValidationResult;
import art.arcane.iris.pack.PackValidator;
import art.arcane.iris.generation.runtime.IrisEngine;
import art.arcane.iris.generation.runtime.Engine;
import art.arcane.iris.generation.runtime.EngineAssignedComponent;
import art.arcane.iris.generation.runtime.EngineEffects;
import art.arcane.iris.generation.runtime.EngineEffectsProvider;
import art.arcane.iris.generation.runtime.EnginePlatformHooks;
import art.arcane.iris.generation.runtime.EngineTarget;
import art.arcane.iris.generation.runtime.EngineWorldManager;
import art.arcane.iris.generation.runtime.EngineWorldManagerProvider;
import art.arcane.iris.generation.runtime.MeteredCache;
import art.arcane.iris.generation.runtime.PreservationRegistry;
import art.arcane.iris.configuration.IrisSettings;
import art.arcane.iris.generation.concurrent.MultiBurst;
import art.arcane.iris.generation.hydrology.HydrologyTileCache;
import art.arcane.iris.generation.hydrology.runtime.IrisHydrologyRuntime;
import art.arcane.iris.generation.terrain.IrisDimension;
import art.arcane.iris.world.IrisWorld;
import art.arcane.iris.world.history.GenerationHistory;
import art.arcane.iris.world.history.GenerationHistoryRuntimeRouter;
import art.arcane.iris.world.history.IrisBoundarySignatureSampler;
import art.arcane.iris.world.pregen.PregenPerformanceProfile;
import art.arcane.iris.spi.IrisPlatforms;
import art.arcane.iris.spi.IrisServices;
import art.arcane.volmlib.nativelib.terrain.NativeBiome;
import art.arcane.volmlib.nativelib.terrain.NativeBlockState;
import art.arcane.volmlib.util.hunk.Hunk;
import art.arcane.volmlib.util.noise.FractalBillowSimplexNoise;

import java.io.File;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.HexFormat;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;

public final class GenerationProbe {
    private static final List<Throwable> REPORTED = Collections.synchronizedList(new ArrayList<>());

    private static final class InertPreservation implements PreservationRegistry {
        @Override
        public void register(Thread thread) {
        }

        @Override
        public void register(ExecutorService service) {
        }

        @Override
        public void registerCache(MeteredCache cache) {
        }

        @Override
        public void dereference() {
        }
    }

    private static final class InertWorldManager implements EngineWorldManager {
        @Override
        public void close() {
        }

        @Override
        public int getEntityCount() {
            return 0;
        }

        @Override
        public int getChunkCount() {
            return 0;
        }

        @Override
        public double getEntitySaturation() {
            return 0;
        }

        @Override
        public void onTick() {
        }

        @Override
        public void onSave() {
        }
    }

    private static final class InertEffects extends EngineAssignedComponent implements EngineEffects {
        private InertEffects(Engine engine) {
            super(engine, "FX");
        }

        @Override
        public void updatePlayerMap() {
        }

        @Override
        public void tickRandomPlayer() {
        }
    }

    private static final class InertPlatformHooks implements EnginePlatformHooks {
    }

    record ProbeConfiguration(File packSource, String dimensionKey, int warmupChunks, int measuredChunks,
                              int centerChunkX, int centerChunkZ, boolean multicore, boolean studio, long seed) {
        ProbeConfiguration {
            if (packSource == null) {
                throw new IllegalArgumentException("Pack folder is required.");
            }
            if (dimensionKey == null || dimensionKey.isBlank() || dimensionKey.chars().anyMatch(Character::isWhitespace)) {
                throw new IllegalArgumentException("Dimension key must be non-blank and contain no whitespace.");
            }
            if (warmupChunks < 1) {
                throw new IllegalArgumentException("Warmup chunk count must be at least 1.");
            }
            if (measuredChunks < 1) {
                throw new IllegalArgumentException("Measured chunk count must be at least 1.");
            }
        }

        static ProbeConfiguration parse(String[] args) {
            if (args.length != 9) {
                throw new IllegalArgumentException("Expected: <pack> <dimension> <warmupChunks> <measuredChunks> <centerChunkX> <centerChunkZ> <multicore> <studio> <seed>");
            }
            return new ProbeConfiguration(
                    new File(args[0]),
                    args[1],
                    Integer.parseInt(args[2]),
                    Integer.parseInt(args[3]),
                    Integer.parseInt(args[4]),
                    Integer.parseInt(args[5]),
                    Boolean.parseBoolean(args[6]),
                    Boolean.parseBoolean(args[7]),
                    Long.parseLong(args[8]));
        }
    }

    record ProbeOptions(int threads, Path dumpFile, boolean pregenProfile, boolean hydrologyPreplan,
                        boolean historyRouter, boolean productionBuffers, boolean counters) {
        ProbeOptions {
            if (threads < 1) {
                throw new IllegalArgumentException("Thread count must be at least 1.");
            }
            if (dumpFile != null && !dumpFile.isAbsolute()) {
                throw new IllegalArgumentException("Dump file must be an absolute path.");
            }
        }

        static ProbeOptions fromSystemProperties() {
            String dump = System.getProperty("iris.probe.dumpFile", "").trim();
            return new ProbeOptions(
                    Integer.parseInt(System.getProperty("iris.probe.threads", "1").trim()),
                    dump.isEmpty() ? null : Path.of(dump),
                    booleanProperty("iris.probe.pregenProfile"),
                    booleanProperty("iris.probe.hydrologyPreplan"),
                    booleanProperty("iris.probe.historyRouter"),
                    booleanProperty("iris.probe.productionBuffers"),
                    booleanProperty("iris.generation.counters"));
        }

        private static boolean booleanProperty(String key) {
            return Boolean.parseBoolean(System.getProperty(key, "false").trim());
        }

        boolean parallel() {
            return threads > 1;
        }
    }

    record ChunkCoordinate(int x, int z) {
    }

    record ChunkOutcome(int index, ChunkCoordinate coordinate, long elapsedNanos,
                        Hunk<NativeBlockState> blocks, Hunk<NativeBiome> biomes, List<Throwable> failures) {
    }

    record ThroughputSummary(int threads, boolean multicore, boolean pregenProfile, int burstParallelism,
                             int noiseCacheSize, int measuredChunks, long warmupWallNanos, long measuredWallNanos,
                             long latencySumNanos, TimingSummary latencies, int unattributedReports) {
        String machineLine() {
            double wallSeconds = measuredWallNanos / 1_000_000_000D;
            double chunksPerSecond = wallSeconds == 0D ? 0D : measuredChunks / wallSeconds;
            return String.format(Locale.ROOT,
                    "IRIS_GENPROBE_THROUGHPUT version=1 threads=%d multicore=%s pregen_profile=%s burst_parallelism=%d noise_cache_size=%d measured_chunks=%d warmup_wall_ms=%.3f measured_wall_ms=%.3f wall_cps=%.3f latency_sum_ms=%.3f latency_median_ms=%.3f latency_p95_ms=%.3f latency_max_ms=%.3f effective_concurrency=%.2f unattributed_reports=%d",
                    threads,
                    multicore,
                    pregenProfile,
                    burstParallelism,
                    noiseCacheSize,
                    measuredChunks,
                    nanosToMillis(warmupWallNanos),
                    nanosToMillis(measuredWallNanos),
                    chunksPerSecond,
                    nanosToMillis(latencySumNanos),
                    nanosToMillis(latencies.medianNanos()),
                    nanosToMillis(latencies.p95Nanos()),
                    nanosToMillis(latencies.maxNanos()),
                    measuredWallNanos == 0L ? 0D : latencySumNanos / (double) measuredWallNanos,
                    unattributedReports);
        }
    }

    record TimingSummary(long medianNanos, long p95Nanos, long maxNanos, long totalNanos) {
        static TimingSummary from(List<Long> samples) {
            if (samples.isEmpty()) {
                throw new IllegalArgumentException("At least one timing sample is required.");
            }
            List<Long> sorted = new ArrayList<>(samples);
            sorted.sort(Comparator.naturalOrder());
            int size = sorted.size();
            long median;
            if ((size & 1) == 0) {
                long lower = sorted.get((size / 2) - 1);
                long upper = sorted.get(size / 2);
                median = lower + ((upper - lower) / 2L);
            } else {
                median = sorted.get(size / 2);
            }
            int p95Index = Math.max(0, (int) Math.ceil(size * 0.95D) - 1);
            long total = 0L;
            for (long sample : samples) {
                total += sample;
            }
            return new TimingSummary(median, sorted.get(p95Index), sorted.get(size - 1), total);
        }

        TimingSummary withTotal(long totalNanos) {
            return new TimingSummary(medianNanos, p95Nanos, maxNanos, totalNanos);
        }
    }

    record GenerationResult(int successfulChunks, int failedChunks, long firstChunkNanos,
                            TimingSummary measuredTimings, String signature) {
    }

    record ProbeResult(String status, String dimensionKey, int warmupChunks, int measuredChunks, boolean multicore,
                       boolean studio,
                       int successfulChunks, int failedChunks, long engineReadyNanos, long firstChunkNanos,
                       TimingSummary measuredTimings, String signature) {
        String machineLine() {
            double measuredSeconds = measuredTimings.totalNanos() / 1_000_000_000D;
            double chunksPerSecond = measuredSeconds == 0D ? 0D : measuredChunks / measuredSeconds;
            return String.format(Locale.ROOT,
                    "IRIS_GENPROBE_RESULT version=1 status=%s dimension=%s warmup_chunks=%d measured_chunks=%d multicore=%s studio=%s successful_chunks=%d failed_chunks=%d engine_ready_ms=%.3f first_chunk_ms=%.3f measured_median_ms=%.3f measured_p95_ms=%.3f measured_max_ms=%.3f measured_total_ms=%.3f measured_cps=%.3f signature=%s",
                    status,
                    dimensionKey,
                    warmupChunks,
                    measuredChunks,
                    multicore,
                    studio,
                    successfulChunks,
                    failedChunks,
                    nanosToMillis(engineReadyNanos),
                    nanosToMillis(firstChunkNanos),
                    nanosToMillis(measuredTimings.medianNanos()),
                    nanosToMillis(measuredTimings.p95Nanos()),
                    nanosToMillis(measuredTimings.maxNanos()),
                    nanosToMillis(measuredTimings.totalNanos()),
                    chunksPerSecond,
                    signature);
        }
    }

    public static void main(String[] args) {
        ProbeConfiguration configuration;
        ProbeOptions options;
        try {
            configuration = ProbeConfiguration.parse(args);
            options = ProbeOptions.fromSystemProperties();
        } catch (Throwable e) {
            System.out.println("[genprobe] FAIL: " + e.getMessage());
            e.printStackTrace(System.out);
            System.exit(2);
            return;
        }

        int exitCode;
        try {
            ProbeResult result = run(configuration, options);
            System.out.println(result.machineLine());
            System.out.println(IrisCaveCarver3D.cellCounters().machineLine());
            exitCode = result.failedChunks() == 0 ? 0 : 1;
        } catch (Throwable e) {
            System.out.println("[genprobe] FAIL: probe execution failed");
            e.printStackTrace(System.out);
            TimingSummary unavailable = new TimingSummary(0L, 0L, 0L, 0L);
            ProbeResult result = new ProbeResult(
                    "FAIL",
                    configuration.dimensionKey(),
                    configuration.warmupChunks(),
                    configuration.measuredChunks(),
                    configuration.multicore(),
                    configuration.studio(),
                    0,
                    configuration.warmupChunks() + configuration.measuredChunks(),
                    0L,
                    0L,
                    unavailable,
                    "unavailable");
            System.out.println(result.machineLine());
            exitCode = 1;
        }
        System.exit(exitCode);
    }

    static List<ChunkCoordinate> scheduleCoordinates(int count, int centerChunkX, int centerChunkZ) {
        if (count < 1) {
            throw new IllegalArgumentException("Chunk count must be at least 1.");
        }
        int width = (int) Math.ceil(Math.sqrt(count));
        int height = (count + width - 1) / width;
        int startX = centerChunkX - (width / 2);
        int startZ = centerChunkZ - (height / 2);
        List<ChunkCoordinate> coordinates = new ArrayList<>(count);
        for (int index = 0; index < count; index++) {
            coordinates.add(new ChunkCoordinate(startX + (index % width), startZ + (index / width)));
        }
        return coordinates;
    }

    private static ProbeResult run(ProbeConfiguration configuration, ProbeOptions options) throws Throwable {
        if (!configuration.packSource().isDirectory()) {
            throw new IllegalArgumentException("Pack folder not found: " + configuration.packSource().getAbsolutePath());
        }

        Path temporaryRoot = Path.of("build", "generation-probe").toAbsolutePath();
        Files.createDirectories(temporaryRoot);
        File workRoot = Files.createTempDirectory(temporaryRoot, "iris-genprobe-").toFile();
        IrisData data = null;
        Engine engine = null;
        Throwable executionFailure = null;
        try {
            File pack = clonePack(configuration.packSource(), workRoot);
            configureProbeRuntime(workRoot, options.historyRouter());
            validatePack(pack);

            System.out.println("[genprobe] pack: " + configuration.packSource().getAbsolutePath());
            System.out.println("[genprobe] dimension: " + configuration.dimensionKey());
            System.out.println("[genprobe] seed: " + configuration.seed());
            System.out.println("[genprobe] warmup chunks: " + configuration.warmupChunks());
            System.out.println("[genprobe] measured chunks: " + configuration.measuredChunks());
            System.out.println("[genprobe] center chunk: " + configuration.centerChunkX() + "," + configuration.centerChunkZ());
            System.out.println("[genprobe] multicore: " + configuration.multicore());
            System.out.println("[genprobe] studio: " + configuration.studio());
            System.out.println("[genprobe] threads: " + options.threads());
            System.out.println("[genprobe] pregen profile: " + options.pregenProfile());
            System.out.println("[genprobe] hydrology preplan: " + options.hydrologyPreplan());
            System.out.println("[genprobe] history router: " + options.historyRouter());
            System.out.println("[genprobe] production buffers: " + options.productionBuffers());
            System.out.println("[genprobe] counters: " + options.counters());
            System.out.println("[genprobe] dump file: " + (options.dumpFile() == null ? "none" : options.dumpFile()));

            long engineStart = System.nanoTime();
            File worldFolder = new File(workRoot, "world");
            data = IrisData.get(pack);
            IrisDimension dimension = loadDimension(data, configuration.dimensionKey());
            GenerationHistory history = null;
            if (options.historyRouter()) {
                history = HeadlessGenerationHistorySession.create(new RealPackProbeSupport.HistoryRequest(
                        worldFolder.toPath(), data, dimension, configuration.seed()), DataVersion.getLatest().get());
                data.close();
                data = IrisData.openRuntime(history.activePackRoot().toFile());
                data.bindGenerationRegistryContract(history.activeEpoch().registryContract());
                dimension = loadDimension(data, configuration.dimensionKey());
            }
            IrisWorld world = IrisWorld.builder()
                    .platformIdentity("iris:probe")
                    .name("probe")
                    .seed(configuration.seed())
                    .worldFolder(worldFolder)
                    .minHeight(dimension.getMinHeight())
                    .maxHeight(dimension.getMaxHeight())
                    .build();
            EngineTarget target = new EngineTarget(world, dimension, data);
            IrisEngine.InitializationMode mode = configuration.studio()
                    ? IrisEngine.InitializationMode.STUDIO
                    : IrisEngine.InitializationMode.RUNTIME;
            GenerationHistoryRuntimeRouter router = null;
            if (history == null) {
                engine = new IrisEngine(target, mode);
            } else {
                IrisEngine historyEngine = new IrisEngine(target, mode,
                        history.paths().activationMantleRoot(history.activeActivation().activationId()),
                        history.activeEpoch().kernelVersion(), null);
                engine = historyEngine;
                router = GenerationHistoryRuntimeRouter.attach(historyEngine, history, IrisBoundarySignatureSampler.INSTANCE);
                router.preloadActiveRuntimes();
            }
            long engineReadyNanos = System.nanoTime() - engineStart;

            List<Throwable> initNoise = settleAndDrain();
            printDistinctCauses("engine-init reported errors (non-fatal, async)", initNoise);
            System.out.println("[genprobe] engine ready: dim=" + engine.getDimension().getLoadKey()
                    + " seed=" + engine.getSeedManager().getSeed()
                    + " minY=" + engine.getMinHeight() + " maxY=" + engine.getMaxHeight()
                    + " timeMs=" + String.format(Locale.ROOT, "%.3f", nanosToMillis(engineReadyNanos)));
            System.out.println("IRIS_GENPROBE_MANTLE version=1 radius=" + engine.getMantle().getRadius()
                    + " real_radius=" + engine.getMantle().getRealRadius());
            ProbeChunkGenerator generator = new ProbeChunkGenerator(engine, router, options.productionBuffers());
            long nativeSamplesBefore = FractalBillowSimplexNoise.nativeSampleCount();
            GenerationResult generation = generateWithProfile(generator, configuration, options);
            verifyBackend(nativeSamplesBefore);
            if (options.productionBuffers()) {
                System.out.println("IRIS_GENPROBE_BUFFERS version=1 mode=production biome_expanded_chunks="
                        + generator.expandedBiomeHunks());
            }
            String status = generation.failedChunks() == 0 ? "PASS" : "FAIL";
            printGenerationFailures(generation);
            return new ProbeResult(
                    status,
                    configuration.dimensionKey(),
                    configuration.warmupChunks(),
                    configuration.measuredChunks(),
                    configuration.multicore(),
                    configuration.studio(),
                    generation.successfulChunks(),
                    generation.failedChunks(),
                    engineReadyNanos,
                    generation.firstChunkNanos(),
                    generation.measuredTimings(),
                    generation.signature());
        } catch (Throwable e) {
            executionFailure = e;
            throw e;
        } finally {
            Throwable cleanupFailure = closeProbe(engine, data, workRoot);
            if (cleanupFailure != null) {
                if (executionFailure != null) {
                    executionFailure.addSuppressed(cleanupFailure);
                } else {
                    throw cleanupFailure;
                }
            }
        }
    }

    private static void verifyBackend(long nativeSamplesBefore) {
        String backend = FractalBillowSimplexNoise.nativeBackendStatus();
        long nativeSamples = FractalBillowSimplexNoise.nativeSampleCount() - nativeSamplesBefore;
        System.out.println("IRIS_GENPROBE_BACKEND status=" + backend + " native_samples=" + nativeSamples
                + " diagnostics=" + Boolean.getBoolean("volmlib.noise.nativeBillowDiagnostics"));
        String expected = System.getProperty("iris.probe.expectedBackend", "");
        if (!expected.isEmpty() && !expected.equals(backend)) {
            throw new IllegalStateException("Expected backend " + expected + ", received " + backend);
        }
        if (Boolean.getBoolean("iris.probe.requireNativeSamples") && nativeSamples <= 0L) {
            throw new IllegalStateException("Real chunk generation did not report native Billow samples.");
        }
    }

    private static IrisDimension loadDimension(IrisData data, String dimensionKey) {
        IrisDimension dimension = data.getDimensionLoader().load(dimensionKey);
        if (dimension == null) {
            throw new IllegalStateException("Dimension '" + dimensionKey + "' did not load from "
                    + data.getDataFolder().getAbsolutePath());
        }
        return dimension;
    }

    private static void configureProbeRuntime(File workRoot, boolean generationRegistry) {
        REPORTED.clear();
        StubPlatform.bindGenerationStateHandlers();
        StubPlatform.verbose(false);
        StubPlatform.errorSink(REPORTED::add);
        IrisServices.register(PreservationRegistry.class, new InertPreservation());
        IrisServices.register(EngineWorldManagerProvider.class,
                (EngineWorldManagerProvider) (Engine engine) -> new InertWorldManager());
        IrisServices.register(EngineEffectsProvider.class, (EngineEffectsProvider) InertEffects::new);
        IrisServices.register(EnginePlatformHooks.class, new InertPlatformHooks());
        IrisPlatforms.unbind();
        IrisPlatforms.bind(new StubPlatform(new File(workRoot, "platform-data"),
                generationRegistry ? ProbeGenerationRegistry.INSTANCE : null));
    }

    private static void validatePack(File pack) {
        PackValidationResult validation = PackValidator.validateForDatapackBootstrap(pack);
        for (String warning : validation.getWarnings()) {
            System.out.println("[genprobe] pack warning: " + warning);
        }
        if (!validation.isLoadable()) {
            for (String error : validation.getBlockingErrors()) {
                System.out.println("[genprobe] pack error: " + error);
            }
            throw new IllegalStateException("Pack validation blocked generation.");
        }
        System.out.println("[genprobe] offline pack validation: PASS");
    }

    private static GenerationResult generateWithProfile(ProbeChunkGenerator generator, ProbeConfiguration configuration,
                                                        ProbeOptions options) throws Exception {
        Engine engine = generator.engine();
        if (!options.pregenProfile()) {
            preplanHydrology(engine, configuration, options);
            return generate(generator, configuration, options);
        }
        PregenPerformanceProfile.apply(engine);
        IrisHydrologyRuntime hydrology = engine.getComplex().getHydrologyRuntime();
        try (HydrologyTileCache.PregenerationScope ignored = hydrology == null ? null
                : hydrology.preparePregeneration(pregenerationArea(configuration))) {
            preplanHydrology(engine, configuration, options);
            return generate(generator, configuration, options);
        } finally {
            PregenPerformanceProfile.restore();
        }
    }

    private static void preplanHydrology(Engine engine, ProbeConfiguration configuration, ProbeOptions options)
            throws Exception {
        if (!options.hydrologyPreplan()) {
            return;
        }
        Optional<ProbeHydrologyPreplan> preplan = ProbeHydrologyPreplan.run(engine, configuration);
        printDistinctCauses("hydrology preplan reported errors", drainReported());
        System.out.println(preplan.map(ProbeHydrologyPreplan::machineLine)
                .orElse("IRIS_GENPROBE_HYDROLOGY_PREPLAN version=1 tiles=0 skipped=no-hydrology"));
    }

    static HydrologyTileCache.PregenerationArea pregenerationArea(ProbeConfiguration configuration) {
        List<ChunkCoordinate> coordinates = scheduleCoordinates(
                configuration.warmupChunks() + configuration.measuredChunks(),
                configuration.centerChunkX(), configuration.centerChunkZ());
        int minimumX = Integer.MAX_VALUE;
        int minimumZ = Integer.MAX_VALUE;
        int maximumX = Integer.MIN_VALUE;
        int maximumZ = Integer.MIN_VALUE;
        for (ChunkCoordinate coordinate : coordinates) {
            minimumX = Math.min(minimumX, coordinate.x());
            minimumZ = Math.min(minimumZ, coordinate.z());
            maximumX = Math.max(maximumX, coordinate.x());
            maximumZ = Math.max(maximumZ, coordinate.z());
        }
        int centerBlockX = Math.toIntExact(Math.clamp(((long) configuration.centerChunkX() << 4) + 8L,
                (long) minimumX << 4, (((long) maximumX + 1L) << 4) - 1L));
        int centerBlockZ = Math.toIntExact(Math.clamp(((long) configuration.centerChunkZ() << 4) + 8L,
                (long) minimumZ << 4, (((long) maximumZ + 1L) << 4) - 1L));
        return new HydrologyTileCache.PregenerationArea(centerBlockX, centerBlockZ,
                (long) minimumX << 4, (long) minimumZ << 4,
                (((long) maximumX + 1L) << 4) - 1L, (((long) maximumZ + 1L) << 4) - 1L);
    }

    static List<Integer> spiralOrder(List<ChunkCoordinate> coordinates, int fromIndex, int centerChunkX,
                                     int centerChunkZ) {
        List<Integer> order = new ArrayList<>(coordinates.size() - fromIndex);
        for (int index = fromIndex; index < coordinates.size(); index++) {
            order.add(index);
        }
        order.sort(Comparator
                .comparingInt((Integer index) -> ringOf(coordinates.get(index), centerChunkX, centerChunkZ))
                .thenComparingDouble(index -> angleOf(coordinates.get(index), centerChunkX, centerChunkZ)));
        return order;
    }

    private static int ringOf(ChunkCoordinate coordinate, int centerChunkX, int centerChunkZ) {
        return Math.max(Math.abs(coordinate.x() - centerChunkX), Math.abs(coordinate.z() - centerChunkZ));
    }

    private static double angleOf(ChunkCoordinate coordinate, int centerChunkX, int centerChunkZ) {
        return Math.atan2(coordinate.z() - centerChunkZ, coordinate.x() - centerChunkX);
    }

    private static GenerationResult generate(ProbeChunkGenerator generator, ProbeConfiguration configuration,
                                             ProbeOptions options) throws Exception {
        Engine engine = generator.engine();
        int totalChunks = configuration.warmupChunks() + configuration.measuredChunks();
        List<ChunkCoordinate> coordinates = scheduleCoordinates(
                totalChunks, configuration.centerChunkX(), configuration.centerChunkZ());
        int height = engine.getTarget().getHeight();
        ChunkDump.Header header = new ChunkDump.Header(configuration.dimensionKey(), configuration.seed(),
                engine.getMinHeight(), height);
        try (OutcomeSink sink = new OutcomeSink(options.dumpFile(), header, configuration.measuredChunks())) {
            if (options.parallel()) {
                generateParallel(generator, configuration, options, coordinates, sink);
            } else {
                generateSequential(generator, configuration, options, coordinates, sink);
            }
            return sink.result(configuration);
        }
    }

    private static void generateSequential(ProbeChunkGenerator generator, ProbeConfiguration configuration,
                                           ProbeOptions options, List<ChunkCoordinate> coordinates,
                                           OutcomeSink sink) throws IOException {
        int totalChunks = coordinates.size();
        long measuredStartEpochMillis = 0L;
        ProbeWindowMetrics measuredStart = null;
        GeneratorEvaluations.Snapshot measuredStartEvaluations = GeneratorEvaluations.snapshot();
        for (int index = 0; index < totalChunks; index++) {
            if (index == configuration.warmupChunks()) {
                measuredStartEpochMillis = System.currentTimeMillis();
                measuredStartEvaluations = GeneratorEvaluations.snapshot();
                measuredStart = ProbeWindowMetrics.capture(generator.engine(), options.counters());
            }
            drainReported();
            ChunkOutcome outcome = generator.generate(index, coordinates.get(index), configuration.multicore());
            outcome.failures().addAll(drainReported());
            sink.accept(outcome, index >= configuration.warmupChunks());
            printProgress(configuration, index + 1, totalChunks);
        }
        printWindow(generator.engine(), measuredStartEpochMillis, System.currentTimeMillis(), measuredStartEvaluations);
        printWindowMetrics(measuredStart, ProbeWindowMetrics.capture(generator.engine(), options.counters()),
                configuration.measuredChunks(), options);
    }

    private static void printWindowMetrics(ProbeWindowMetrics start, ProbeWindowMetrics end, int chunks,
                                           ProbeOptions options) {
        System.out.println(ProbeWindowMetrics.stagesLine(start, end, chunks, options.threads()));
        if (options.counters()) {
            System.out.println(ProbeWindowMetrics.countersLine(start, end, chunks));
        }
    }

    private static void printWindow(Engine engine, long startEpochMillis, long endEpochMillis, GeneratorEvaluations.Snapshot startEvaluations) {
        System.out.println("IRIS_GENPROBE_WINDOW phase=measured start_epoch_ms=" + startEpochMillis
                + " end_epoch_ms=" + endEpochMillis);
        GeneratorEvaluations.Snapshot evaluations = GeneratorEvaluations.snapshot().minus(startEvaluations);
        System.out.printf(Locale.ROOT, "IRIS_GENPROBE_GENERATORS baked_chunks=%d baked_evaluations=%d evaluations_per_baked_chunk=%.3f batched_evaluations=%d scalar_evaluations=%d%n",
                evaluations.bakedChunks(), evaluations.bakedEvaluations(), evaluations.bakedEvaluationsPerChunk(),
                evaluations.batchedEvaluations(), evaluations.scalarEvaluations());
        long[] census = engine.getComplex().bakeCensus();
        StringBuilder planes = new StringBuilder("IRIS_GENPROBE_BAKE entries=").append(census[BakePlane.values().length]);
        for (BakePlane plane : BakePlane.values()) {
            planes.append(' ').append(plane.name().toLowerCase(Locale.ROOT)).append('=').append(census[plane.ordinal()]);
        }
        System.out.println(planes);
    }

    private static void generateParallel(ProbeChunkGenerator generator, ProbeConfiguration configuration,
                                         ProbeOptions options, List<ChunkCoordinate> coordinates,
                                         OutcomeSink sink) throws Exception {
        ChunkOutcome[] outcomes = new ChunkOutcome[coordinates.size()];
        ExecutorService executor = Executors.newFixedThreadPool(options.threads());
        long warmupWallNanos;
        long measuredWallNanos;
        List<Throwable> unattributed = new ArrayList<>();
        try {
            drainReported();
            List<Integer> warmup = new ArrayList<>(configuration.warmupChunks());
            for (int index = 0; index < configuration.warmupChunks(); index++) {
                warmup.add(index);
            }
            long warmupStarted = System.nanoTime();
            runPhase(executor, generator, configuration, coordinates, warmup, outcomes);
            warmupWallNanos = System.nanoTime() - warmupStarted;
            unattributed.addAll(drainReported());
            System.out.println("[genprobe] warmup complete: " + configuration.warmupChunks() + " chunks on "
                    + options.threads() + " threads");

            List<Integer> measured = spiralOrder(coordinates, configuration.warmupChunks(),
                    configuration.centerChunkX(), configuration.centerChunkZ());
            ProbeWindowMetrics measuredStart = ProbeWindowMetrics.capture(generator.engine(), options.counters());
            long measuredStartEpochMillis = System.currentTimeMillis();
            GeneratorEvaluations.Snapshot measuredStartEvaluations = GeneratorEvaluations.snapshot();
            long measuredStarted = System.nanoTime();
            runPhase(executor, generator, configuration, coordinates, measured, outcomes);
            measuredWallNanos = System.nanoTime() - measuredStarted;
            printWindow(generator.engine(), measuredStartEpochMillis, System.currentTimeMillis(), measuredStartEvaluations);
            printWindowMetrics(measuredStart, ProbeWindowMetrics.capture(generator.engine(), options.counters()),
                    configuration.measuredChunks(), options);
            unattributed.addAll(drainReported());
            System.out.println("[genprobe] measured progress: " + configuration.measuredChunks() + "/"
                    + configuration.measuredChunks());
        } finally {
            executor.shutdown();
            if (!executor.awaitTermination(30L, TimeUnit.SECONDS)) {
                executor.shutdownNow();
            }
        }

        for (int index = 0; index < outcomes.length; index++) {
            ChunkOutcome outcome = outcomes[index];
            outcomes[index] = null;
            sink.accept(outcome, index >= configuration.warmupChunks());
        }
        sink.reportUnattributed(unattributed);
        TimingSummary latencies = TimingSummary.from(sink.measuredTimings());
        ThroughputSummary throughput = new ThroughputSummary(
                options.threads(),
                configuration.multicore(),
                options.pregenProfile(),
                MultiBurst.burst.parallelism(),
                IrisSettings.get().getPerformance().getNoiseCacheSize(),
                configuration.measuredChunks(),
                warmupWallNanos,
                measuredWallNanos,
                latencies.totalNanos(),
                latencies,
                unattributed.size());
        sink.measuredWallNanos(measuredWallNanos);
        System.out.println(throughput.machineLine());
    }

    private static void runPhase(ExecutorService executor, ProbeChunkGenerator generator,
                                 ProbeConfiguration configuration, List<ChunkCoordinate> coordinates,
                                 List<Integer> order, ChunkOutcome[] outcomes) throws Exception {
        List<Future<ChunkOutcome>> futures = new ArrayList<>(order.size());
        for (int index : order) {
            ChunkCoordinate coordinate = coordinates.get(index);
            futures.add(executor.submit(() -> generator.generate(index, coordinate, configuration.multicore())));
        }
        for (Future<ChunkOutcome> future : futures) {
            ChunkOutcome outcome;
            try {
                outcome = future.get();
            } catch (ExecutionException failure) {
                Throwable cause = failure.getCause();
                if (cause instanceof Error error) {
                    throw error;
                }
                if (cause instanceof Exception exception) {
                    throw exception;
                }
                throw new IllegalStateException(cause);
            }
            outcomes[outcome.index()] = outcome;
        }
    }

    private static void printProgress(ProbeConfiguration configuration, int completed, int totalChunks) {
        if (completed == configuration.warmupChunks()) {
            System.out.println("[genprobe] warmup complete: " + configuration.warmupChunks() + " chunks");
        } else if (completed > configuration.warmupChunks()
                && (completed == totalChunks || (completed - configuration.warmupChunks()) % 128 == 0)) {
            System.out.println("[genprobe] measured progress: "
                    + (completed - configuration.warmupChunks()) + "/" + configuration.measuredChunks());
        }
    }

    private static void recordFailures(ChunkCoordinate coordinate, List<Throwable> failures,
                                       Map<String, Integer> distinctFailures,
                                       Map<String, String> firstFailureChunk) {
        String coordinateLabel = coordinate.x() + "," + coordinate.z();
        System.out.println("[genprobe] chunk " + coordinateLabel + " FAILED (" + failures.size() + " error(s))");
        for (Throwable failure : failures) {
            failure.printStackTrace(System.out);
            String key = causeKey(failure);
            distinctFailures.merge(key, 1, Integer::sum);
            firstFailureChunk.putIfAbsent(key, coordinateLabel);
        }
    }

    private static void printGenerationFailures(GenerationResult generation) {
        System.out.println("[genprobe] generated OK: " + generation.successfulChunks()
                + ", failed: " + generation.failedChunks());
        System.out.println("[genprobe] first chunk ms: "
                + String.format(Locale.ROOT, "%.3f", nanosToMillis(generation.firstChunkNanos())));
        System.out.println("[genprobe] measured median/p95/max ms: "
                + String.format(Locale.ROOT, "%.3f/%.3f/%.3f",
                nanosToMillis(generation.measuredTimings().medianNanos()),
                nanosToMillis(generation.measuredTimings().p95Nanos()),
                nanosToMillis(generation.measuredTimings().maxNanos())));
    }

    private static void updateSignature(MessageDigest digest, ChunkDump.ChunkSnapshot snapshot, int height) {
        MessageDigest blockDigest = sha256();
        MessageDigest biomeDigest = sha256();
        String coordinateLabel = snapshot.chunkX() + "," + snapshot.chunkZ();
        updateDigest(digest, coordinateLabel);
        updateDigest(blockDigest, coordinateLabel);
        updateDigest(biomeDigest, coordinateLabel);
        byte[][] blockKeys = terminatedKeys(snapshot.blockPalette());
        byte[][] biomeKeys = terminatedKeys(snapshot.biomePalette());
        char[] blocks = snapshot.blocks();
        char[] biomes = snapshot.biomes();
        int size = 256 * height;
        for (int index = 0; index < size; index++) {
            byte[] stateKey = blockKeys[blocks[index]];
            byte[] biomeKey = biomeKeys[biomes[index]];
            digest.update(stateKey);
            digest.update(biomeKey);
            blockDigest.update(stateKey);
            biomeDigest.update(biomeKey);
        }
        System.out.println("GENPROBE_CHUNK_HASH chunk=" + coordinateLabel
                + " blocks=" + HexFormat.of().formatHex(blockDigest.digest()).substring(0, 16)
                + " biomes=" + HexFormat.of().formatHex(biomeDigest.digest()).substring(0, 16));
    }

    private static byte[][] terminatedKeys(String[] palette) {
        byte[][] encoded = new byte[palette.length][];
        for (int index = 0; index < palette.length; index++) {
            byte[] key = palette[index].getBytes(StandardCharsets.UTF_8);
            byte[] terminated = new byte[key.length + 1];
            System.arraycopy(key, 0, terminated, 0, key.length);
            encoded[index] = terminated;
        }
        return encoded;
    }

    private static void updateDigest(MessageDigest digest, String value) {
        digest.update(value.getBytes(StandardCharsets.UTF_8));
        digest.update((byte) 0);
    }

    private static File clonePack(File source, File workRoot) throws Exception {
        File destination = new File(workRoot, "pack");
        Process clone = new ProcessBuilder("cp", "-Rc", source.getAbsolutePath(), destination.getAbsolutePath())
                .inheritIO()
                .start();
        if (clone.waitFor() != 0) {
            Process copy = new ProcessBuilder("cp", "-R", source.getAbsolutePath(), destination.getAbsolutePath())
                    .inheritIO()
                    .start();
            if (copy.waitFor() != 0) {
                throw new IllegalStateException("Failed to copy pack to " + destination.getAbsolutePath());
            }
        }
        return destination;
    }

    private static Throwable closeProbe(Engine engine, IrisData data, File workRoot) {
        Throwable failure = null;
        if (engine != null) {
            try {
                engine.close();
            } catch (Throwable e) {
                failure = e;
            }
        }
        if (data != null) {
            try {
                data.close();
            } catch (Throwable e) {
                failure = appendFailure(failure, e);
            }
        }
        try {
            ProbeWorkspaceCleanup.deleteRecursively(workRoot.toPath());
        } catch (Throwable e) {
            failure = appendFailure(failure, e);
        }
        return failure;
    }

    private static Throwable appendFailure(Throwable failure, Throwable additional) {
        if (failure == null) {
            return additional;
        }
        if (failure != additional) {
            failure.addSuppressed(additional);
        }
        return failure;
    }

    private static List<Throwable> settleAndDrain() throws InterruptedException {
        List<Throwable> drained = new ArrayList<>();
        long quietSince = System.currentTimeMillis();
        long start = quietSince;
        while (System.currentTimeMillis() - start < 15000L) {
            List<Throwable> batch = drainReported();
            if (batch.isEmpty()) {
                if (System.currentTimeMillis() - quietSince >= 1500L) {
                    break;
                }
            } else {
                drained.addAll(batch);
                quietSince = System.currentTimeMillis();
            }
            Thread.sleep(50L);
        }
        return drained;
    }

    private static List<Throwable> drainReported() {
        synchronized (REPORTED) {
            List<Throwable> drained = new ArrayList<>(REPORTED);
            REPORTED.clear();
            return drained;
        }
    }

    private static void printDistinctCauses(String label, List<Throwable> errors) {
        if (errors.isEmpty()) {
            return;
        }
        Map<String, Integer> distinct = new LinkedHashMap<>();
        for (Throwable error : errors) {
            distinct.merge(causeKey(error), 1, Integer::sum);
        }
        System.out.println("[genprobe] " + label + " (" + distinct.size() + " distinct):");
        for (Map.Entry<String, Integer> entry : distinct.entrySet()) {
            System.out.println("  x" + entry.getValue() + " " + entry.getKey());
        }
        for (Throwable error : errors) {
            error.printStackTrace(System.out);
        }
    }

    private static String causeKey(Throwable failure) {
        List<Throwable> chain = new ArrayList<>();
        Throwable cause = failure;
        while (cause != null && !chain.contains(cause)) {
            chain.add(cause);
            cause = cause.getCause();
        }
        Throwable root = chain.get(chain.size() - 1);
        return root.getClass().getName() + ": " + root.getMessage() + " @ " + siteOf(chain);
    }

    private static String siteOf(List<Throwable> chain) {
        for (int i = chain.size() - 1; i >= 0; i--) {
            for (StackTraceElement frame : chain.get(i).getStackTrace()) {
                String className = frame.getClassName();
                if (className.startsWith("art.arcane.") && !className.startsWith("art.arcane.iris.probe.")) {
                    return frame.toString();
                }
            }
        }
        StackTraceElement[] trace = chain.get(chain.size() - 1).getStackTrace();
        return trace.length > 0 ? trace[0].toString() : "<no frames>";
    }

    private static MessageDigest sha256() {
        try {
            return MessageDigest.getInstance("SHA-256");
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException(e);
        }
    }

    private static double nanosToMillis(long nanos) {
        return nanos / 1_000_000D;
    }

    private static final class OutcomeSink implements AutoCloseable {
        private final MessageDigest signature = sha256();
        private final List<Long> measuredTimings;
        private final Map<String, Integer> distinctFailures = new LinkedHashMap<>();
        private final Map<String, String> firstFailureChunk = new LinkedHashMap<>();
        private final ChunkDump.Writer dump;
        private final int height;
        private int successfulChunks;
        private int failedChunks;
        private int unattributedReports;
        private long firstChunkNanos;
        private long measuredWallNanos = -1L;

        private OutcomeSink(Path dumpFile, ChunkDump.Header header, int measuredChunks) throws IOException {
            this.measuredTimings = new ArrayList<>(measuredChunks);
            this.height = header.height();
            this.dump = dumpFile == null ? null : new ChunkDump.Writer(dumpFile, header);
        }

        void accept(ChunkOutcome outcome, boolean measured) throws IOException {
            if (outcome.index() == 0) {
                firstChunkNanos = outcome.elapsedNanos();
            }
            if (measured) {
                measuredTimings.add(outcome.elapsedNanos());
            }
            if (!outcome.failures().isEmpty()) {
                failedChunks++;
                recordFailures(outcome.coordinate(), outcome.failures(), distinctFailures, firstFailureChunk);
                return;
            }
            successfulChunks++;
            ChunkDump.ChunkSnapshot snapshot = ChunkDump.capture(outcome.coordinate().x(), outcome.coordinate().z(),
                    outcome.blocks(), outcome.biomes(), height);
            updateSignature(signature, snapshot, height);
            if (dump != null) {
                dump.write(snapshot);
            }
        }

        void reportUnattributed(List<Throwable> reports) {
            unattributedReports += reports.size();
            if (!reports.isEmpty()) {
                printDistinctCauses("parallel generation reported errors without a chunk owner", reports);
            }
        }

        void measuredWallNanos(long nanos) {
            measuredWallNanos = nanos;
        }

        List<Long> measuredTimings() {
            return measuredTimings;
        }

        GenerationResult result(ProbeConfiguration configuration) {
            if (!distinctFailures.isEmpty()) {
                System.out.println("[genprobe] DISTINCT ROOT CAUSES (" + distinctFailures.size() + "):");
                for (Map.Entry<String, Integer> entry : distinctFailures.entrySet()) {
                    System.out.println("  x" + entry.getValue() + " (first at chunk "
                            + firstFailureChunk.get(entry.getKey()) + ") " + entry.getKey());
                }
            }
            TimingSummary timings = TimingSummary.from(measuredTimings);
            if (measuredWallNanos >= 0L) {
                timings = timings.withTotal(measuredWallNanos);
            }
            int failures = failedChunks + (unattributedReports > 0 && failedChunks == 0 ? 1 : 0);
            return new GenerationResult(
                    successfulChunks,
                    failures,
                    firstChunkNanos,
                    timings,
                    HexFormat.of().formatHex(signature.digest()).substring(0, 16));
        }

        @Override
        public void close() throws IOException {
            if (dump != null) {
                dump.close();
            }
        }
    }
}
