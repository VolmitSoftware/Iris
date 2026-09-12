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
import art.arcane.iris.generation.mantle.MantleComponent;
import art.arcane.iris.generation.mantle.MantleObjectComponent;
import art.arcane.iris.generation.terrain.IrisDimension;
import art.arcane.iris.world.IrisWorld;
import art.arcane.iris.spi.IrisPlatforms;
import art.arcane.iris.spi.IrisServices;
import art.arcane.iris.spi.PlatformBiome;
import art.arcane.iris.spi.PlatformBlockState;
import art.arcane.volmlib.util.hunk.Hunk;

import java.io.File;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ExecutorService;
import java.util.stream.Stream;

final class RealPackProbeSupport {
    private static final List<Throwable> REPORTED = Collections.synchronizedList(new ArrayList<>());

    private RealPackProbeSupport() {
    }

    static Workspace openWorkspace(File packSource, String dimensionKey, String logPrefix) throws Exception {
        if (packSource == null || !packSource.isDirectory()) {
            throw new IllegalArgumentException("Pack folder not found: "
                    + (packSource == null ? "null" : packSource.getAbsolutePath()));
        }
        if (dimensionKey == null || dimensionKey.isBlank()
                || dimensionKey.chars().anyMatch(Character::isWhitespace)) {
            throw new IllegalArgumentException("Dimension key must be non-blank and contain no whitespace.");
        }
        File workRoot = Files.createTempDirectory("iris-real-pack-probe-").toFile();
        try {
            File pack = clonePack(packSource, workRoot);
            configureRuntime(new File(workRoot, "platform-validation"), false);
            validatePack(pack, logPrefix);
            return new Workspace(workRoot, pack, dimensionKey, logPrefix, false);
        } catch (Throwable failure) {
            Throwable cleanupFailure = deleteWorkRoot(workRoot);
            if (cleanupFailure != null) {
                failure.addSuppressed(cleanupFailure);
            }
            throwAsException(failure);
            throw new IllegalStateException("Unreachable");
        }
    }

    static Workspace openPreparedWorkspace(
            File pack,
            String dimensionKey,
            String logPrefix,
            boolean hydrologyGeneratedVerification
    ) throws Exception {
        if (pack == null || !pack.isDirectory()) {
            throw new IllegalArgumentException("Prepared pack folder not found: "
                    + (pack == null ? "null" : pack.getAbsolutePath()));
        }
        File workRoot = Files.createTempDirectory("iris-generated-chunk-probe-").toFile();
        try {
            configureRuntime(new File(workRoot, "platform-validation"), hydrologyGeneratedVerification);
            return new Workspace(workRoot, pack, dimensionKey, logPrefix, hydrologyGeneratedVerification);
        } catch (Throwable failure) {
            Throwable cleanupFailure = deleteWorkRoot(workRoot);
            if (cleanupFailure != null) {
                failure.addSuppressed(cleanupFailure);
            }
            throwAsException(failure);
            throw new IllegalStateException("Unreachable");
        }
    }

    static List<Throwable> drainReported() {
        synchronized (REPORTED) {
            List<Throwable> drained = new ArrayList<>(REPORTED);
            REPORTED.clear();
            return drained;
        }
    }

    static List<Throwable> settleAndDrain() throws InterruptedException {
        List<Throwable> drained = new ArrayList<>();
        long quietSince = System.currentTimeMillis();
        long start = quietSince;
        while (System.currentTimeMillis() - start < 15_000L) {
            List<Throwable> batch = drainReported();
            if (batch.isEmpty()) {
                if (System.currentTimeMillis() - quietSince >= 1_500L) {
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

    static void printReports(String logPrefix, String label, List<Throwable> reports) {
        if (reports.isEmpty()) {
            return;
        }
        Map<String, Integer> distinct = new LinkedHashMap<>();
        for (Throwable report : reports) {
            distinct.merge(causeKey(report), 1, Integer::sum);
        }
        System.out.println(logPrefix + " " + label + " (" + distinct.size() + " distinct):");
        for (Map.Entry<String, Integer> entry : distinct.entrySet()) {
            System.out.println("  x" + entry.getValue() + " " + entry.getKey());
        }
        for (Throwable report : reports) {
            report.printStackTrace(System.out);
        }
    }

    static boolean parseBoolean(String value, String name) {
        if ("true".equalsIgnoreCase(value)) {
            return true;
        }
        if ("false".equalsIgnoreCase(value)) {
            return false;
        }
        throw new IllegalArgumentException(name + " must be true or false.");
    }

    static GeneratedChunk generateChunk(Engine engine, int chunkX, int chunkZ) {
        int height = engine.getTarget().getHeight();
        Hunk<PlatformBlockState> blocks = Hunk.newArrayHunk(16, height, 16);
        Hunk<PlatformBiome> biomes = Hunk.newArrayHunk(16, height, 16);
        try {
            engine.generate(chunkX << 4, chunkZ << 4, blocks, biomes, false);
        } catch (Exception failure) {
            throw new IllegalStateException("Failed to generate probe chunk " + chunkX + "," + chunkZ, failure);
        }
        return new GeneratedChunk(chunkX, chunkZ, height, blocks, biomes);
    }

    record GeneratedChunk(
            int chunkX,
            int chunkZ,
            int height,
            Hunk<PlatformBlockState> blocks,
            Hunk<PlatformBiome> biomes
    ) {
        GeneratedChunk {
            if (height < 1 || blocks == null || biomes == null) {
                throw new IllegalArgumentException("Generated chunk requires buffers and a positive height.");
            }
        }

        PlatformBlockState blockAt(int blockX, int y, int blockZ) {
            validatePosition(blockX, y, blockZ);
            return blocks.get(Math.floorMod(blockX, 16), y, Math.floorMod(blockZ, 16));
        }

        PlatformBiome biomeAt(int blockX, int y, int blockZ) {
            validatePosition(blockX, y, blockZ);
            return biomes.get(Math.floorMod(blockX, 16), y, Math.floorMod(blockZ, 16));
        }

        private void validatePosition(int blockX, int y, int blockZ) {
            if (Math.floorDiv(blockX, 16) != chunkX || Math.floorDiv(blockZ, 16) != chunkZ) {
                throw new IllegalArgumentException("Block coordinate is outside the generated chunk.");
            }
            if (y < 0 || y >= height) {
                throw new IllegalArgumentException("Block Y is outside the generated chunk height.");
            }
        }
    }

    static final class Workspace implements AutoCloseable {
        private final File workRoot;
        private final File pack;
        private final String dimensionKey;
        private final String logPrefix;
        private final boolean hydrologyGeneratedVerification;
        private int sessionSequence;
        private boolean sessionOpen;
        private boolean closed;

        private Workspace(
                File workRoot,
                File pack,
                String dimensionKey,
                String logPrefix,
                boolean hydrologyGeneratedVerification
        ) {
            this.workRoot = workRoot;
            this.pack = pack;
            this.dimensionKey = dimensionKey;
            this.logPrefix = logPrefix;
            this.hydrologyGeneratedVerification = hydrologyGeneratedVerification;
        }

        EngineSession openEngine(long seed, boolean studio, String runLabel) throws Exception {
            if (closed) {
                throw new IllegalStateException("Probe workspace is closed.");
            }
            if (sessionOpen) {
                throw new IllegalStateException("Only one real-pack engine session may be open at a time.");
            }
            sessionOpen = true;
            int sequence = sessionSequence++;
            File platformRoot = new File(workRoot, "platform-" + sequence);
            File worldRoot = new File(workRoot, "world-" + sequence);
            IrisData data = null;
            Engine engine = null;
            try {
                configureRuntime(platformRoot, hydrologyGeneratedVerification);
                data = IrisData.openRuntime(pack);
                IrisDimension dimension = data.getDimensionLoader().load(dimensionKey);
                if (dimension == null) {
                    throw new IllegalStateException("Dimension '" + dimensionKey
                            + "' did not load from " + pack.getAbsolutePath());
                }
                IrisWorld world = IrisWorld.builder()
                        .platformIdentity("iris:probe")
                        .name("probe")
                        .seed(seed)
                        .worldFolder(worldRoot)
                        .minHeight(dimension.getMinHeight())
                        .maxHeight(dimension.getMaxHeight())
                        .build();
                long started = System.nanoTime();
                engine = new IrisEngine(
                        new EngineTarget(world, dimension, data),
                        studio ? IrisEngine.InitializationMode.STUDIO : IrisEngine.InitializationMode.RUNTIME
                );
                long readyNanos = System.nanoTime() - started;
                List<Throwable> initializationReports = settleAndDrain();
                printReports(logPrefix, runLabel + " engine-init reports", initializationReports);
                return new EngineSession(this, data, engine, readyNanos);
            } catch (Throwable failure) {
                Throwable cleanupFailure = closeResources(engine, data);
                sessionOpen = false;
                if (cleanupFailure != null) {
                    failure.addSuppressed(cleanupFailure);
                }
                throwAsException(failure);
                throw new IllegalStateException("Unreachable");
            }
        }

        File pack() {
            return pack;
        }

        @Override
        public void close() throws Exception {
            if (closed) {
                return;
            }
            if (sessionOpen) {
                throw new IllegalStateException("Cannot close a probe workspace with an open engine session.");
            }
            closed = true;
            IrisPlatforms.unbind();
            StubPlatform.errorSink(null);
            Throwable failure = deleteWorkRoot(workRoot);
            if (failure != null) {
                throwAsException(failure);
            }
        }

        private void releaseSession() {
            sessionOpen = false;
        }
    }

    static final class EngineSession implements AutoCloseable {
        private final Workspace workspace;
        private final IrisData data;
        private final Engine engine;
        private final long readyNanos;
        private boolean closed;

        private EngineSession(Workspace workspace, IrisData data, Engine engine, long readyNanos) {
            this.workspace = workspace;
            this.data = data;
            this.engine = engine;
            this.readyNanos = readyNanos;
        }

        Engine engine() {
            return engine;
        }

        long readyNanos() {
            return readyNanos;
        }

        @Override
        public void close() throws Exception {
            if (closed) {
                return;
            }
            closed = true;
            Throwable failure = closeResources(engine, data);
            List<Throwable> reported = drainReported();
            if (!reported.isEmpty()) {
                IllegalStateException reportedFailure = new IllegalStateException(
                        "The real-pack engine reported " + reported.size() + " error(s) during shutdown.");
                for (Throwable report : reported) {
                    reportedFailure.addSuppressed(report);
                }
                failure = appendFailure(failure, reportedFailure);
            }
            workspace.releaseSession();
            if (failure != null) {
                throwAsException(failure);
            }
        }
    }

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
        private final boolean hydrologyGeneratedVerification;

        private InertPlatformHooks(boolean hydrologyGeneratedVerification) {
            this.hydrologyGeneratedVerification = hydrologyGeneratedVerification;
        }

        @Override
        public boolean shouldGenerateMantleComponent(Engine engine, MantleComponent component) {
            return !hydrologyGeneratedVerification || !(component instanceof MantleObjectComponent);
        }
    }

    private static void configureRuntime(File platformRoot, boolean hydrologyGeneratedVerification) {
        REPORTED.clear();
        StubPlatform.bindGenerationStateHandlers();
        StubPlatform.verbose(false);
        StubPlatform.errorSink(REPORTED::add);
        IrisServices.register(PreservationRegistry.class, new InertPreservation());
        IrisServices.register(EngineWorldManagerProvider.class,
                (EngineWorldManagerProvider) (Engine engine) -> new InertWorldManager());
        IrisServices.register(EngineEffectsProvider.class, (EngineEffectsProvider) InertEffects::new);
        IrisServices.register(EnginePlatformHooks.class, new InertPlatformHooks(hydrologyGeneratedVerification));
        IrisPlatforms.unbind();
        IrisPlatforms.bind(new StubPlatform(platformRoot));
    }

    private static void validatePack(File pack, String logPrefix) {
        PackValidationResult validation = PackValidator.validateForDatapackBootstrap(pack);
        for (String warning : validation.getWarnings()) {
            System.out.println(logPrefix + " pack warning: " + warning);
        }
        if (!validation.isLoadable()) {
            for (String error : validation.getBlockingErrors()) {
                System.out.println(logPrefix + " pack error: " + error);
            }
            throw new IllegalStateException("Pack validation blocked generation.");
        }
        System.out.println(logPrefix + " offline pack validation: PASS");
    }

    private static File clonePack(File source, File workRoot) throws Exception {
        File destination = new File(workRoot, "pack");
        Process clone = new ProcessBuilder("cp", "-Rc", source.getAbsolutePath(), destination.getAbsolutePath())
                .inheritIO()
                .start();
        if (clone.waitFor() != 0) {
            deleteRecursively(destination.toPath());
            Process copy = new ProcessBuilder("cp", "-R", source.getAbsolutePath(), destination.getAbsolutePath())
                    .inheritIO()
                    .start();
            if (copy.waitFor() != 0) {
                throw new IllegalStateException("Failed to copy pack to " + destination.getAbsolutePath());
            }
        }
        return destination;
    }

    private static Throwable closeResources(Engine engine, IrisData data) {
        Throwable failure = null;
        if (engine != null) {
            try {
                engine.close();
            } catch (Throwable engineFailure) {
                failure = engineFailure;
            }
        }
        if (data != null) {
            try {
                data.close();
            } catch (Throwable dataFailure) {
                failure = appendFailure(failure, dataFailure);
            }
        }
        return failure;
    }

    private static Throwable deleteWorkRoot(File workRoot) {
        try {
            deleteRecursively(workRoot.toPath());
            return null;
        } catch (Throwable failure) {
            return failure;
        }
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

    private static Throwable appendFailure(Throwable failure, Throwable additional) {
        if (failure == null) {
            return additional;
        }
        if (failure != additional) {
            failure.addSuppressed(additional);
        }
        return failure;
    }

    private static void throwAsException(Throwable failure) throws Exception {
        if (failure instanceof Error error) {
            throw error;
        }
        if (failure instanceof Exception exception) {
            throw exception;
        }
        throw new IllegalStateException(failure);
    }

    private static String causeKey(Throwable failure) {
        List<Throwable> chain = new ArrayList<>();
        Throwable cause = failure;
        while (cause != null && !chain.contains(cause)) {
            chain.add(cause);
            cause = cause.getCause();
        }
        Throwable root = chain.getLast();
        return root.getClass().getName() + ": " + root.getMessage();
    }
}
