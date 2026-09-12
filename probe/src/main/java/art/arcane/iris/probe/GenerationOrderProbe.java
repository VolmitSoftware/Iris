package art.arcane.iris.probe;

import art.arcane.iris.generation.runtime.Engine;
import art.arcane.iris.spi.PlatformBiome;
import art.arcane.iris.spi.PlatformBlockState;
import art.arcane.volmlib.util.hunk.Hunk;

import java.io.File;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.HexFormat;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.Random;
import java.util.TreeMap;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;

public final class GenerationOrderProbe {
    private static final String LOG_PREFIX = "[generation-order-probe]";
    private static final int MAXIMUM_CHUNKS = 1_024;
    private static final int MAXIMUM_PARALLELISM = 32;
    private static final int MINIMUM_SAFE_CHUNK_COORDINATE = -134_217_728;
    private static final int MAXIMUM_SAFE_CHUNK_COORDINATE = 134_217_727;

    private GenerationOrderProbe() {
    }

    enum GenerationOrder {
        FORWARD,
        REVERSE,
        SHUFFLED,
        BOUNDED_PARALLEL
    }

    record ProbeConfiguration(
            File packSource,
            String dimensionKey,
            long seed,
            int minimumChunkX,
            int maximumChunkX,
            int minimumChunkZ,
            int maximumChunkZ,
            int parallelism,
            long shuffleSeed,
            boolean multicore,
            boolean studio
    ) {
        ProbeConfiguration {
            if (packSource == null) {
                throw new IllegalArgumentException("Pack folder is required.");
            }
            if (dimensionKey == null || dimensionKey.isBlank()
                    || dimensionKey.chars().anyMatch(Character::isWhitespace)) {
                throw new IllegalArgumentException("Dimension key must be non-blank and contain no whitespace.");
            }
            if (minimumChunkX > maximumChunkX || minimumChunkZ > maximumChunkZ) {
                throw new IllegalArgumentException("Minimum chunk bounds cannot exceed maximum chunk bounds.");
            }
            if (minimumChunkX < MINIMUM_SAFE_CHUNK_COORDINATE
                    || maximumChunkX > MAXIMUM_SAFE_CHUNK_COORDINATE
                    || minimumChunkZ < MINIMUM_SAFE_CHUNK_COORDINATE
                    || maximumChunkZ > MAXIMUM_SAFE_CHUNK_COORDINATE) {
                throw new IllegalArgumentException("Chunk bounds overflow their block origins.");
            }
            long chunks = boundedArea(minimumChunkX, maximumChunkX, minimumChunkZ, maximumChunkZ);
            if (chunks > MAXIMUM_CHUNKS) {
                throw new IllegalArgumentException("Generation-order probe exceeds "
                        + MAXIMUM_CHUNKS + " chunks.");
            }
            if (parallelism < 2 || parallelism > MAXIMUM_PARALLELISM) {
                throw new IllegalArgumentException("Parallelism must be between 2 and "
                        + MAXIMUM_PARALLELISM + ".");
            }
        }

        static ProbeConfiguration parse(String[] arguments) {
            if (arguments.length != 11) {
                throw new IllegalArgumentException(
                        "Expected: <pack> <dimension> <seed> <minimumChunkX> <maximumChunkX> "
                                + "<minimumChunkZ> <maximumChunkZ> <parallelism> <shuffleSeed> <multicore> <studio>");
            }
            return new ProbeConfiguration(
                    new File(arguments[0]),
                    arguments[1],
                    Long.parseLong(arguments[2]),
                    Integer.parseInt(arguments[3]),
                    Integer.parseInt(arguments[4]),
                    Integer.parseInt(arguments[5]),
                    Integer.parseInt(arguments[6]),
                    Integer.parseInt(arguments[7]),
                    Long.parseLong(arguments[8]),
                    RealPackProbeSupport.parseBoolean(arguments[9], "multicore"),
                    RealPackProbeSupport.parseBoolean(arguments[10], "studio")
            );
        }

        int chunkCount() {
            return Math.toIntExact(boundedArea(
                    minimumChunkX, maximumChunkX, minimumChunkZ, maximumChunkZ));
        }
    }

    record ChunkCoordinate(int x, int z) implements Comparable<ChunkCoordinate> {
        @Override
        public int compareTo(ChunkCoordinate other) {
            int zComparison = Integer.compare(z, other.z);
            return zComparison != 0 ? zComparison : Integer.compare(x, other.x);
        }
    }

    record ChunkHash(
            String blocks,
            String biomes,
            String combined,
            Map<String, Integer> blockCounts,
            Map<String, Integer> blockCountsByY
    ) {
        ChunkHash(String blocks, String biomes, String combined) {
            this(blocks, biomes, combined, Map.of(), Map.of());
        }

        ChunkHash {
            blockCounts = Map.copyOf(blockCounts);
            blockCountsByY = Map.copyOf(blockCountsByY);
        }
    }

    record AggregateSignature(String blocks, String biomes, String combined) {
    }

    record ChunkOutput(ChunkCoordinate coordinate, ChunkHash hash) {
    }

    record ModeResult(
            GenerationOrder order,
            Map<ChunkCoordinate, ChunkHash> chunks,
            AggregateSignature signature,
            long engineReadyNanos,
            long generationNanos
    ) {
    }

    record ProbeResult(
            String status,
            String dimensionKey,
            long seed,
            int chunks,
            int minimumChunkX,
            int maximumChunkX,
            int minimumChunkZ,
            int maximumChunkZ,
            int parallelism,
            long shuffleSeed,
            boolean multicore,
            boolean studio,
            AggregateSignature signature
    ) {
        String machineLine() {
            return String.format(
                    Locale.ROOT,
                    "IRIS_GENERATION_ORDER_RESULT version=1 status=%s dimension=%s seed=%d chunks=%d "
                            + "chunk_x=%d..%d chunk_z=%d..%d parallelism=%d shuffle_seed=%d "
                            + "multicore=%s studio=%s "
                            + "block_signature=%s biome_signature=%s combined_signature=%s",
                    status,
                    dimensionKey,
                    seed,
                    chunks,
                    minimumChunkX,
                    maximumChunkX,
                    minimumChunkZ,
                    maximumChunkZ,
                    parallelism,
                    shuffleSeed,
                    multicore,
                    studio,
                    signature.blocks(),
                    signature.biomes(),
                    signature.combined()
            );
        }
    }

    public static void main(String[] arguments) {
        ProbeConfiguration configuration;
        try {
            configuration = ProbeConfiguration.parse(arguments);
        } catch (Throwable failure) {
            System.out.println(LOG_PREFIX + " FAIL: " + failure.getMessage());
            failure.printStackTrace(System.out);
            System.exit(2);
            return;
        }

        try {
            ProbeResult result = run(configuration);
            System.out.println(result.machineLine());
            System.exit(0);
        } catch (Throwable failure) {
            System.out.println(LOG_PREFIX + " FAIL: probe execution failed");
            failure.printStackTrace(System.out);
            AggregateSignature unavailable = new AggregateSignature("unavailable", "unavailable", "unavailable");
            ProbeResult failed = result(configuration, "FAIL", unavailable);
            System.out.println(failed.machineLine());
            System.exit(1);
        }
    }

    static List<ChunkCoordinate> coordinates(ProbeConfiguration configuration) {
        ArrayList<ChunkCoordinate> coordinates = new ArrayList<>(configuration.chunkCount());
        for (long chunkZ = configuration.minimumChunkZ(); chunkZ <= configuration.maximumChunkZ(); chunkZ++) {
            for (long chunkX = configuration.minimumChunkX(); chunkX <= configuration.maximumChunkX(); chunkX++) {
                coordinates.add(new ChunkCoordinate((int) chunkX, (int) chunkZ));
            }
        }
        return List.copyOf(coordinates);
    }

    static List<ChunkCoordinate> orderedCoordinates(
            List<ChunkCoordinate> coordinates,
            GenerationOrder order,
            long shuffleSeed
    ) {
        ArrayList<ChunkCoordinate> ordered = new ArrayList<>(coordinates);
        switch (order) {
            case FORWARD -> {
            }
            case REVERSE -> Collections.reverse(ordered);
            case SHUFFLED, BOUNDED_PARALLEL -> Collections.shuffle(ordered, new Random(shuffleSeed));
        }
        return List.copyOf(ordered);
    }

    static AggregateSignature aggregateSignature(Map<ChunkCoordinate, ChunkHash> chunks) {
        MessageDigest blocks = sha256();
        MessageDigest biomes = sha256();
        MessageDigest combined = sha256();
        TreeMap<ChunkCoordinate, ChunkHash> ordered = new TreeMap<>(chunks);
        for (Map.Entry<ChunkCoordinate, ChunkHash> entry : ordered.entrySet()) {
            ChunkCoordinate coordinate = entry.getKey();
            ChunkHash hash = entry.getValue();
            byte[] encodedCoordinate = (coordinate.x() + "," + coordinate.z()).getBytes(StandardCharsets.UTF_8);
            updateDigest(blocks, encodedCoordinate);
            updateDigest(blocks, hash.blocks());
            updateDigest(biomes, encodedCoordinate);
            updateDigest(biomes, hash.biomes());
            updateDigest(combined, encodedCoordinate);
            updateDigest(combined, hash.combined());
        }
        return new AggregateSignature(
                HexFormat.of().formatHex(blocks.digest()),
                HexFormat.of().formatHex(biomes.digest()),
                HexFormat.of().formatHex(combined.digest())
        );
    }

    static List<ChunkCoordinate> mismatchedChunks(
            Map<ChunkCoordinate, ChunkHash> expected,
            Map<ChunkCoordinate, ChunkHash> observed
    ) {
        TreeMap<ChunkCoordinate, ChunkHash> all = new TreeMap<>(expected);
        for (Map.Entry<ChunkCoordinate, ChunkHash> entry : observed.entrySet()) {
            all.putIfAbsent(entry.getKey(), entry.getValue());
        }
        ArrayList<ChunkCoordinate> mismatches = new ArrayList<>();
        for (ChunkCoordinate coordinate : all.keySet()) {
            if (!Objects.equals(expected.get(coordinate), observed.get(coordinate))) {
                mismatches.add(coordinate);
            }
        }
        return List.copyOf(mismatches);
    }

    static ChunkHash hashChunk(
            ChunkCoordinate coordinate,
            Hunk<PlatformBlockState> blocks,
            Hunk<PlatformBiome> biomes,
            int height
    ) {
        MessageDigest blockDigest = sha256();
        MessageDigest biomeDigest = sha256();
        MessageDigest combinedDigest = sha256();
        HashMap<String, byte[]> encodedKeys = new HashMap<>();
        HashMap<String, Integer> blockCounts = new HashMap<>();
        HashMap<String, Integer> blockCountsByY = new HashMap<>();
        updateDigest(blockDigest, coordinate.x() + "," + coordinate.z() + "," + height);
        updateDigest(biomeDigest, coordinate.x() + "," + coordinate.z() + "," + height);
        updateDigest(combinedDigest, coordinate.x() + "," + coordinate.z() + "," + height);
        for (int x = 0; x < 16; x++) {
            for (int z = 0; z < 16; z++) {
                for (int y = 0; y < height; y++) {
                    PlatformBlockState state = blocks.get(x, y, z);
                    PlatformBiome biome = biomes.get(x, y, z);
                    String blockKey = state == null ? "minecraft:air" : state.key();
                    String biomeKey = biome == null ? "<null>" : biome.key();
                    blockCounts.merge(blockKey, 1, Integer::sum);
                    blockCountsByY.merge(y + ":" + blockKey, 1, Integer::sum);
                    byte[] encodedBlock = encoded(encodedKeys, blockKey);
                    byte[] encodedBiome = encoded(encodedKeys, biomeKey);
                    updateDigest(blockDigest, encodedBlock);
                    updateDigest(biomeDigest, encodedBiome);
                    combinedDigest.update((byte) 'B');
                    updateDigest(combinedDigest, encodedBlock);
                    combinedDigest.update((byte) 'M');
                    updateDigest(combinedDigest, encodedBiome);
                }
            }
        }
        return new ChunkHash(
                HexFormat.of().formatHex(blockDigest.digest()),
                HexFormat.of().formatHex(biomeDigest.digest()),
                HexFormat.of().formatHex(combinedDigest.digest()),
                blockCounts,
                blockCountsByY
        );
    }

    private static ProbeResult run(ProbeConfiguration configuration) throws Exception {
        System.out.println(LOG_PREFIX + " pack: " + configuration.packSource().getAbsolutePath());
        System.out.println(LOG_PREFIX + " dimension: " + configuration.dimensionKey());
        System.out.println(LOG_PREFIX + " seed: " + configuration.seed());
        System.out.println(LOG_PREFIX + " chunk bounds: " + configuration.minimumChunkX() + ".."
                + configuration.maximumChunkX() + "," + configuration.minimumChunkZ() + ".."
                + configuration.maximumChunkZ());
        System.out.println(LOG_PREFIX + " chunks: " + configuration.chunkCount());
        System.out.println(LOG_PREFIX + " parallelism: " + configuration.parallelism());
        System.out.println(LOG_PREFIX + " shuffle seed: " + configuration.shuffleSeed());
        System.out.println(LOG_PREFIX + " multicore: " + configuration.multicore());
        System.out.println(LOG_PREFIX + " studio: " + configuration.studio());

        List<ChunkCoordinate> coordinates = coordinates(configuration);
        LinkedHashMap<GenerationOrder, ModeResult> results = new LinkedHashMap<>();
        try (RealPackProbeSupport.Workspace workspace = RealPackProbeSupport.openWorkspace(
                configuration.packSource(), configuration.dimensionKey(), LOG_PREFIX)) {
            for (GenerationOrder order : GenerationOrder.values()) {
                ModeResult result = generateMode(configuration, workspace, coordinates, order);
                results.put(order, result);
                printMode(result);
            }
        }

        ModeResult baseline = results.get(GenerationOrder.FORWARD);
        for (GenerationOrder order : GenerationOrder.values()) {
            if (order == GenerationOrder.FORWARD) {
                continue;
            }
            ModeResult observed = results.get(order);
            List<ChunkCoordinate> mismatches = mismatchedChunks(baseline.chunks(), observed.chunks());
            if (mismatches.isEmpty()) {
                continue;
            }
            int reportCount = Math.min(16, mismatches.size());
            for (int index = 0; index < reportCount; index++) {
                ChunkCoordinate coordinate = mismatches.get(index);
                ChunkHash expected = baseline.chunks().get(coordinate);
                ChunkHash actual = observed.chunks().get(coordinate);
                System.out.println("IRIS_GENERATION_ORDER_MISMATCH mode=" + order.name()
                        + " chunk_x=" + coordinate.x() + " chunk_z=" + coordinate.z()
                        + " forward_blocks=" + expected.blocks()
                        + " observed_blocks=" + actual.blocks()
                        + " forward_biomes=" + expected.biomes()
                        + " observed_biomes=" + actual.biomes()
                        + " block_count_delta=" + blockCountDelta(expected, actual)
                        + " block_y_count_delta=" + blockYCountDelta(expected, actual));
            }
            throw new IllegalStateException(order + " generation differed from forward generation in "
                    + mismatches.size() + " chunk(s).");
        }

        return result(configuration, "PASS", baseline.signature());
    }

    private static ProbeResult result(ProbeConfiguration configuration, String status, AggregateSignature signature) {
        return new ProbeResult(
                status,
                configuration.dimensionKey(),
                configuration.seed(),
                configuration.chunkCount(),
                configuration.minimumChunkX(),
                configuration.maximumChunkX(),
                configuration.minimumChunkZ(),
                configuration.maximumChunkZ(),
                configuration.parallelism(),
                configuration.shuffleSeed(),
                configuration.multicore(),
                configuration.studio(),
                signature
        );
    }

    private static ModeResult generateMode(
            ProbeConfiguration configuration,
            RealPackProbeSupport.Workspace workspace,
            List<ChunkCoordinate> coordinates,
            GenerationOrder order
    ) throws Exception {
        List<ChunkCoordinate> scheduled = orderedCoordinates(coordinates, order, configuration.shuffleSeed());
        try (RealPackProbeSupport.EngineSession session = workspace.openEngine(
                configuration.seed(), configuration.studio(), order.name().toLowerCase(Locale.ROOT))) {
            RealPackProbeSupport.drainReported();
            long started = System.nanoTime();
            Map<ChunkCoordinate, ChunkHash> chunks = order == GenerationOrder.BOUNDED_PARALLEL
                    ? generateParallel(session.engine(), scheduled, configuration)
                    : generateSequential(session.engine(), scheduled, configuration);
            long generationNanos = System.nanoTime() - started;
            List<Throwable> reports = RealPackProbeSupport.settleAndDrain();
            if (!reports.isEmpty()) {
                RealPackProbeSupport.printReports(LOG_PREFIX, order + " generation reports", reports);
                throw reportedFailure(order + " generation", reports);
            }
            TreeMap<ChunkCoordinate, ChunkHash> ordered = new TreeMap<>(chunks);
            return new ModeResult(
                    order,
                    Collections.unmodifiableMap(ordered),
                    aggregateSignature(ordered),
                    session.readyNanos(),
                    generationNanos
            );
        }
    }

    private static Map<ChunkCoordinate, ChunkHash> generateSequential(
            Engine engine,
            List<ChunkCoordinate> coordinates,
            ProbeConfiguration configuration
    ) throws Exception {
        LinkedHashMap<ChunkCoordinate, ChunkHash> chunks = new LinkedHashMap<>(coordinates.size());
        int completed = 0;
        for (ChunkCoordinate coordinate : coordinates) {
            ChunkOutput output = generateChunk(engine, coordinate, configuration.multicore());
            chunks.put(output.coordinate(), output.hash());
            completed++;
            printProgress(completed, coordinates.size());
        }
        return chunks;
    }

    private static Map<ChunkCoordinate, ChunkHash> generateParallel(
            Engine engine,
            List<ChunkCoordinate> coordinates,
            ProbeConfiguration configuration
    ) throws Exception {
        ExecutorService executor = Executors.newFixedThreadPool(configuration.parallelism());
        try {
            ArrayList<Future<ChunkOutput>> futures = new ArrayList<>(coordinates.size());
            for (ChunkCoordinate coordinate : coordinates) {
                futures.add(executor.submit(() -> generateChunk(engine, coordinate, configuration.multicore())));
            }
            LinkedHashMap<ChunkCoordinate, ChunkHash> chunks = new LinkedHashMap<>(coordinates.size());
            int completed = 0;
            for (Future<ChunkOutput> future : futures) {
                ChunkOutput output;
                try {
                    output = future.get();
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
                chunks.put(output.coordinate(), output.hash());
                completed++;
                printProgress(completed, coordinates.size());
            }
            return chunks;
        } finally {
            executor.shutdown();
            if (!executor.awaitTermination(30L, TimeUnit.SECONDS)) {
                executor.shutdownNow();
            }
        }
    }

    private static ChunkOutput generateChunk(
            Engine engine,
            ChunkCoordinate coordinate,
            boolean multicore
    ) throws Exception {
        int height = engine.getTarget().getHeight();
        Hunk<PlatformBlockState> blocks = Hunk.newArrayHunk(16, height, 16);
        Hunk<PlatformBiome> biomes = Hunk.newArrayHunk(16, height, 16);
        engine.generate(coordinate.x() << 4, coordinate.z() << 4, blocks, biomes, multicore);
        return new ChunkOutput(coordinate, hashChunk(coordinate, blocks, biomes, height));
    }

    private static void printProgress(int completed, int total) {
        if (completed == total || completed % 32 == 0) {
            System.out.println(LOG_PREFIX + " generation progress: " + completed + "/" + total);
        }
    }

    private static void printMode(ModeResult result) {
        System.out.printf(
                Locale.ROOT,
                "IRIS_GENERATION_ORDER_MODE mode=%s chunks=%d engine_ready_ms=%.3f generation_ms=%.3f "
                        + "block_signature=%s biome_signature=%s combined_signature=%s%n",
                result.order().name(),
                result.chunks().size(),
                result.engineReadyNanos() / 1_000_000D,
                result.generationNanos() / 1_000_000D,
                result.signature().blocks(),
                result.signature().biomes(),
                result.signature().combined()
        );
    }

    private static IllegalStateException reportedFailure(String operation, List<Throwable> reports) {
        IllegalStateException failure = new IllegalStateException(
                operation + " reported " + reports.size() + " error(s).");
        for (Throwable report : reports) {
            failure.addSuppressed(report);
        }
        return failure;
    }

    static Map<String, Integer> blockCountDelta(ChunkHash expected, ChunkHash observed) {
        return countDelta(expected.blockCounts(), observed.blockCounts());
    }

    static Map<String, Integer> blockYCountDelta(ChunkHash expected, ChunkHash observed) {
        return countDelta(expected.blockCountsByY(), observed.blockCountsByY());
    }

    private static Map<String, Integer> countDelta(
            Map<String, Integer> expected,
            Map<String, Integer> observed
    ) {
        TreeMap<String, Integer> deltas = new TreeMap<>();
        for (Map.Entry<String, Integer> entry : expected.entrySet()) {
            deltas.put(entry.getKey(), -entry.getValue());
        }
        for (Map.Entry<String, Integer> entry : observed.entrySet()) {
            deltas.merge(entry.getKey(), entry.getValue(), Integer::sum);
        }
        deltas.entrySet().removeIf(entry -> entry.getValue() == 0);
        return Collections.unmodifiableMap(deltas);
    }

    private static long boundedArea(int minimumX, int maximumX, int minimumZ, int maximumZ) {
        long width = (long) maximumX - minimumX + 1L;
        long depth = (long) maximumZ - minimumZ + 1L;
        try {
            return Math.multiplyExact(width, depth);
        } catch (ArithmeticException failure) {
            throw new IllegalArgumentException("Chunk bounds overflow their bounded area.", failure);
        }
    }

    private static void updateDigest(MessageDigest digest, String value) {
        updateDigest(digest, value.getBytes(StandardCharsets.UTF_8));
    }

    private static void updateDigest(MessageDigest digest, byte[] value) {
        digest.update(value);
        digest.update((byte) 0);
    }

    private static byte[] encoded(Map<String, byte[]> cache, String value) {
        byte[] encoded = cache.get(value);
        if (encoded != null) {
            return encoded;
        }
        byte[] created = value.getBytes(StandardCharsets.UTF_8);
        cache.put(value, created);
        return created;
    }

    private static MessageDigest sha256() {
        try {
            return MessageDigest.getInstance("SHA-256");
        } catch (NoSuchAlgorithmException failure) {
            throw new IllegalStateException(failure);
        }
    }
}
