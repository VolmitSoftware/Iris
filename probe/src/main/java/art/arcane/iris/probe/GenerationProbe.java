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
import art.arcane.iris.generation.terrain.IrisDimension;
import art.arcane.iris.world.IrisWorld;
import art.arcane.iris.spi.IrisPlatforms;
import art.arcane.iris.spi.IrisServices;
import art.arcane.iris.spi.PlatformBiome;
import art.arcane.iris.spi.PlatformBlockState;
import art.arcane.volmlib.util.hunk.Hunk;

import java.io.File;
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
import java.util.concurrent.ExecutorService;
import java.util.stream.Stream;

public final class GenerationProbe {
    private static final long SEED = 1337L;
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
                              int centerChunkX, int centerChunkZ, boolean multicore, boolean studio) {
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
            if (args.length != 8) {
                throw new IllegalArgumentException("Expected: <pack> <dimension> <warmupChunks> <measuredChunks> <centerChunkX> <centerChunkZ> <multicore> <studio>");
            }
            return new ProbeConfiguration(
                    new File(args[0]),
                    args[1],
                    Integer.parseInt(args[2]),
                    Integer.parseInt(args[3]),
                    Integer.parseInt(args[4]),
                    Integer.parseInt(args[5]),
                    Boolean.parseBoolean(args[6]),
                    Boolean.parseBoolean(args[7]));
        }
    }

    record ChunkCoordinate(int x, int z) {
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
        try {
            configuration = ProbeConfiguration.parse(args);
        } catch (Throwable e) {
            System.out.println("[genprobe] FAIL: " + e.getMessage());
            e.printStackTrace(System.out);
            System.exit(2);
            return;
        }

        int exitCode;
        try {
            ProbeResult result = run(configuration);
            System.out.println(result.machineLine());
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

    private static ProbeResult run(ProbeConfiguration configuration) throws Throwable {
        if (!configuration.packSource().isDirectory()) {
            throw new IllegalArgumentException("Pack folder not found: " + configuration.packSource().getAbsolutePath());
        }

        File workRoot = Files.createTempDirectory("iris-genprobe-").toFile();
        IrisData data = null;
        Engine engine = null;
        Throwable executionFailure = null;
        try {
            File pack = clonePack(configuration.packSource(), workRoot);
            configureProbeRuntime(workRoot);
            validatePack(pack);

            System.out.println("[genprobe] pack: " + configuration.packSource().getAbsolutePath());
            System.out.println("[genprobe] dimension: " + configuration.dimensionKey());
            System.out.println("[genprobe] warmup chunks: " + configuration.warmupChunks());
            System.out.println("[genprobe] measured chunks: " + configuration.measuredChunks());
            System.out.println("[genprobe] center chunk: " + configuration.centerChunkX() + "," + configuration.centerChunkZ());
            System.out.println("[genprobe] multicore: " + configuration.multicore());
            System.out.println("[genprobe] studio: " + configuration.studio());

            long engineStart = System.nanoTime();
            data = IrisData.get(pack);
            IrisDimension dimension = data.getDimensionLoader().load(configuration.dimensionKey());
            if (dimension == null) {
                throw new IllegalStateException("Dimension '" + configuration.dimensionKey()
                        + "' did not load from " + pack.getAbsolutePath());
            }
            IrisWorld world = IrisWorld.builder()
                    .platformIdentity("iris:probe")
                    .name("probe")
                    .seed(SEED)
                    .worldFolder(new File(workRoot, "world"))
                    .minHeight(dimension.getMinHeight())
                    .maxHeight(dimension.getMaxHeight())
                    .build();
            EngineTarget target = new EngineTarget(world, dimension, data);
            engine = new IrisEngine(
                    target,
                    configuration.studio()
                            ? IrisEngine.InitializationMode.STUDIO
                            : IrisEngine.InitializationMode.RUNTIME);
            long engineReadyNanos = System.nanoTime() - engineStart;

            List<Throwable> initNoise = settleAndDrain();
            printDistinctCauses("engine-init reported errors (non-fatal, async)", initNoise);
            System.out.println("[genprobe] engine ready: dim=" + engine.getDimension().getLoadKey()
                    + " seed=" + engine.getSeedManager().getSeed()
                    + " minY=" + engine.getMinHeight() + " maxY=" + engine.getMaxHeight()
                    + " timeMs=" + String.format(Locale.ROOT, "%.3f", nanosToMillis(engineReadyNanos)));
            GenerationResult generation = generate(engine, configuration);
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

    private static void configureProbeRuntime(File workRoot) {
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
        IrisPlatforms.bind(new StubPlatform(new File(workRoot, "platform-data")));
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

    private static GenerationResult generate(Engine engine, ProbeConfiguration configuration) {
        int totalChunks = configuration.warmupChunks() + configuration.measuredChunks();
        List<ChunkCoordinate> coordinates = scheduleCoordinates(
                totalChunks, configuration.centerChunkX(), configuration.centerChunkZ());
        List<Long> measuredTimings = new ArrayList<>(configuration.measuredChunks());
        MessageDigest signature = sha256();
        int successfulChunks = 0;
        int failedChunks = 0;
        long firstChunkNanos = 0L;
        Map<String, Integer> distinctFailures = new LinkedHashMap<>();
        Map<String, String> firstFailureChunk = new LinkedHashMap<>();
        int height = engine.getTarget().getHeight();

        for (int index = 0; index < totalChunks; index++) {
            ChunkCoordinate coordinate = coordinates.get(index);
            drainReported();
            List<Throwable> failures = new ArrayList<>();
            Hunk<PlatformBlockState> blocks = Hunk.newArrayHunk(16, height, 16);
            Hunk<PlatformBiome> biomes = Hunk.newArrayHunk(16, height, 16);
            long started = System.nanoTime();
            try {
                engine.generate(
                        coordinate.x() << 4,
                        coordinate.z() << 4,
                        blocks,
                        biomes,
                        configuration.multicore());
            } catch (Throwable e) {
                failures.add(e);
            }
            long elapsed = System.nanoTime() - started;
            failures.addAll(drainReported());

            if (index == 0) {
                firstChunkNanos = elapsed;
            }
            if (index >= configuration.warmupChunks()) {
                measuredTimings.add(elapsed);
            }

            if (failures.isEmpty()) {
                successfulChunks++;
                updateSignature(signature, coordinate, blocks, biomes, height);
            } else {
                failedChunks++;
                recordFailures(coordinate, failures, distinctFailures, firstFailureChunk);
            }

            int completed = index + 1;
            if (completed == configuration.warmupChunks()) {
                System.out.println("[genprobe] warmup complete: " + configuration.warmupChunks() + " chunks");
            } else if (completed > configuration.warmupChunks()
                    && (completed == totalChunks || (completed - configuration.warmupChunks()) % 128 == 0)) {
                System.out.println("[genprobe] measured progress: "
                        + (completed - configuration.warmupChunks()) + "/" + configuration.measuredChunks());
            }
        }

        if (!distinctFailures.isEmpty()) {
            System.out.println("[genprobe] DISTINCT ROOT CAUSES (" + distinctFailures.size() + "):");
            for (Map.Entry<String, Integer> entry : distinctFailures.entrySet()) {
                System.out.println("  x" + entry.getValue() + " (first at chunk "
                        + firstFailureChunk.get(entry.getKey()) + ") " + entry.getKey());
            }
        }
        return new GenerationResult(
                successfulChunks,
                failedChunks,
                firstChunkNanos,
                TimingSummary.from(measuredTimings),
                HexFormat.of().formatHex(signature.digest()).substring(0, 16));
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

    private static void updateSignature(MessageDigest digest, ChunkCoordinate coordinate,
                                        Hunk<PlatformBlockState> blocks, Hunk<PlatformBiome> biomes, int height) {
        MessageDigest blockDigest = sha256();
        MessageDigest biomeDigest = sha256();
        updateDigest(digest, coordinate.x() + "," + coordinate.z());
        updateDigest(blockDigest, coordinate.x() + "," + coordinate.z());
        updateDigest(biomeDigest, coordinate.x() + "," + coordinate.z());
        for (int x = 0; x < 16; x++) {
            for (int z = 0; z < 16; z++) {
                for (int y = 0; y < height; y++) {
                    PlatformBlockState state = blocks.get(x, y, z);
                    PlatformBiome biome = biomes.get(x, y, z);
                    String stateKey = state == null ? "minecraft:air" : state.key();
                    String biomeKey = biome == null ? "null" : biome.key();
                    updateDigest(digest, stateKey);
                    updateDigest(digest, biomeKey);
                    updateDigest(blockDigest, stateKey);
                    updateDigest(biomeDigest, biomeKey);
                }
            }
        }
        System.out.println("GENPROBE_CHUNK_HASH chunk=" + coordinate.x() + "," + coordinate.z()
                + " blocks=" + HexFormat.of().formatHex(blockDigest.digest()).substring(0, 16)
                + " biomes=" + HexFormat.of().formatHex(biomeDigest.digest()).substring(0, 16));
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
            deleteRecursively(workRoot.toPath());
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

    private static void deleteRecursively(Path root) throws Exception {
        if (!Files.exists(root)) {
            return;
        }
        List<Path> paths;
        try (Stream<Path> walk = Files.walk(root)) {
            paths = walk.sorted(Comparator.reverseOrder()).toList();
        }
        for (Path path : paths) {
            Files.deleteIfExists(path);
        }
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
}
