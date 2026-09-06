package art.arcane.iris.engine.history;

import art.arcane.iris.core.loader.IrisData;
import art.arcane.iris.engine.IrisEngine;
import art.arcane.iris.engine.framework.BiomeEnvironment;
import art.arcane.iris.engine.object.IrisBiome;
import art.arcane.iris.engine.object.IrisDimension;
import art.arcane.iris.engine.object.IrisRegion;
import art.arcane.iris.spi.IrisLogging;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.LinkOption;
import java.nio.file.Path;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.locks.ReentrantReadWriteLock;
import java.util.function.Function;

public final class SavedBiomeRuntime implements AutoCloseable {
    private static final int MAXIMUM_QUERIES = 128;
    private static final int MAXIMUM_PENDING_QUERIES = 256;
    private static final long MAXIMUM_QUERY_BYTES = SavedBiomeChunk.MAXIMUM_ESTIMATED_BYTES;

    private final IrisEngine engine;
    private final GenerationHistory history;
    private final SavedBiomeStore store;
    private final Map<String, Definitions> definitions = new ConcurrentHashMap<>();
    private final LinkedHashMap<Long, PreparedQuery> queries = new LinkedHashMap<>(32, 0.75F, true);
    private final Map<Long, PendingQuery> pending = new HashMap<>();
    private final ReentrantReadWriteLock consumption = new ReentrantReadWriteLock();
    private long queryBytes;
    private final ExecutorService reads = Executors.newFixedThreadPool(2, task -> {
        Thread thread = new Thread(task, "Iris Saved Biomes");
        thread.setDaemon(true);
        return thread;
    });
    private volatile boolean closed;

    SavedBiomeRuntime(IrisEngine engine, GenerationHistory history) throws IOException {
        this.engine = Objects.requireNonNull(engine, "engine");
        this.history = Objects.requireNonNull(history, "history");
        store = history.savedBiomes();
    }

    public Optional<BiomeEnvironment> resolve(int blockX, int worldY, int blockZ, boolean surface) {
        return resolve(blockX, worldY, blockZ, surface ? QueryKind.SURFACE : QueryKind.VOLUME);
    }

    public <T> T readSurfaceBiome(int blockX, int blockZ, Function<Optional<BiomeEnvironment>, T> reader)
            throws InterruptedException {
        Objects.requireNonNull(reader, "reader");
        PreparedQuery prepared;
        try {
            prepared = queryAsync(Math.floorDiv(blockX, 16), Math.floorDiv(blockZ, 16)).get();
        } catch (ExecutionException failure) {
            Throwable cause = failure.getCause();
            if (cause instanceof RuntimeException runtimeFailure) {
                throw runtimeFailure;
            }
            if (cause instanceof Error error) {
                throw error;
            }
            throw new IllegalStateException("Unable to prepare a saved biome preview.", cause);
        }
        consumption.readLock().lockInterruptibly();
        try {
            requireOpen();
            return reader.apply(resolve(prepared, blockX, 0, blockZ, QueryKind.SURFACE));
        } finally {
            consumption.readLock().unlock();
        }
    }

    public NativeBiomeSpawnSelection nativeSpawnSelection(int blockX, int worldY, int blockZ, String physicalBiomeKey) {
        PreparedQuery prepared = query(Math.floorDiv(blockX, 16), Math.floorDiv(blockZ, 16));
        if (prepared.failure() != null) {
            throw prepared.failure();
        }
        if (prepared.chunk().isEmpty()) {
            return new NativeBiomeSpawnSelection(NativeBiomeSpawnSelection.Mode.CURRENT, "");
        }
        SavedBiomeChunk chunk = prepared.chunk().get();
        SavedBiomeChunk.Cell cell = chunk.biomeAt(Math.floorMod(blockX, 16),
                Math.max(chunk.header().minimumY(), Math.min(worldY, chunk.header().maximumYExclusive() - 1)),
                Math.floorMod(blockZ, 16));
        GenerationActivation activation = history.manifest().activation(cell.activationId()).orElseThrow(() ->
                new SavedBiomeUnavailableException("Historical biome generation is missing: " + cell.activationId(), false));
        Definitions source = definitions.get(activation.epochId());
        String derivative = source == null ? null : source.nativeDerivatives().get(physicalBiomeKey);
        return derivative == null
                ? new NativeBiomeSpawnSelection(NativeBiomeSpawnSelection.Mode.NONE, "")
                : new NativeBiomeSpawnSelection(NativeBiomeSpawnSelection.Mode.RETAINED, derivative);
    }

    public void prepareChunk(int chunkX, int chunkZ) {
        PreparedQuery prepared = query(chunkX, chunkZ);
        if (prepared.failure() != null) {
            throw prepared.failure();
        }
    }

    public Optional<BiomeEnvironment> resolveCaveBase(int blockX, int blockZ) {
        return resolve(blockX, 0, blockZ, QueryKind.CAVE_BASE);
    }

    private Optional<BiomeEnvironment> resolve(int blockX, int worldY, int blockZ, QueryKind kind) {
        int chunkX = Math.floorDiv(blockX, 16);
        int chunkZ = Math.floorDiv(blockZ, 16);
        return resolve(query(chunkX, chunkZ), blockX, worldY, blockZ, kind);
    }

    private Optional<BiomeEnvironment> resolve(PreparedQuery query, int blockX, int worldY, int blockZ, QueryKind kind) {
        if (query.failure() != null) {
            throw query.failure();
        }
        if (query.chunk().isEmpty()) {
            return Optional.empty();
        }
        SavedBiomeChunk chunk = query.chunk().get();
        int localX = Math.floorMod(blockX, 16);
        int localZ = Math.floorMod(blockZ, 16);
        SavedBiomeChunk.Cell cell = switch (kind) {
            case SURFACE -> chunk.surfaceAt(localX, localZ);
            case CAVE_BASE -> chunk.caveBaseAt(localX, localZ);
            case VOLUME -> chunk.biomeAt(localX, Math.max(chunk.header().minimumY(),
                    Math.min(worldY, chunk.header().maximumYExclusive() - 1)), localZ);
        };
        if (!cell.isResolved()) {
            throw new SavedBiomeUnavailableException("This position has no recorded Iris biome identity for generation "
                    + cell.activationId() + ". The saved terrain and Minecraft biome remain available, but Iris biome rules cannot run.", false);
        }
        SavedBiomeUnavailableException unavailable = query.cellFailures().get(cell);
        if (unavailable != null) {
            throw unavailable;
        }
        BiomeEnvironment environment = query.environments().get(cell);
        if (environment == null) {
            throw new SavedBiomeUnavailableException("Historical biome definitions are unavailable for generation " + cell.activationId() + ".", false);
        }
        return Optional.of(environment);
    }

    public Optional<SavedBiomeChunk> snapshot(int chunkX, int chunkZ) throws IOException {
        requireOpen();
        Optional<SavedBiomeChunk> saved = store.get(chunkX, chunkZ);
        if (saved.isPresent()) {
            return saved;
        }
        if (history.isActiveUnowned(chunkX, chunkZ) && history.semantics(chunkX, chunkZ).isEmpty()) {
            return Optional.empty();
        }
        long activationId = history.resolveActivation(chunkX, chunkZ).activationId();
        Definitions source = definitions(activationId);
        Optional<SavedBiomeChunk> recovered = SavedBiomeRecovery.recover(new SavedBiomeRecovery.Input(
                history, chunkX, chunkZ, source.data(), source.dimension()));
        if (recovered.isPresent()) {
            store.claimAndPersist(recovered.get());
            return recovered;
        }
        throw new SavedBiomeUnavailableException("This chunk has no exact saved Iris biome assignment for generation "
                + activationId + ". Its terrain is preserved; historical biome rules cannot be recovered safely.", false);
    }

    public long activationAt(int chunkX, int chunkZ) {
        return history.resolveActivation(chunkX, chunkZ).activationId();
    }

    public void capture(SavedBiomeChunk chunk) throws IOException {
        requireOpen();
        store.claimAndPersist(chunk);
        synchronized (queries) {
            long key = key(chunk.chunkX(), chunk.chunkZ());
            PreparedQuery removed = queries.remove(key);
            if (removed != null) {
                queryBytes -= removed.estimatedBytes();
            }
            PendingQuery loading = pending.get(key);
            if (loading != null) {
                loading.invalidated = true;
            }
        }
    }

    @Override
    public synchronized void close() {
        synchronized (queries) {
            closed = true;
            reads.shutdown();
        }
        boolean interrupted = false;
        Throwable failure = null;
        try {
            while (true) {
                try {
                    if (reads.awaitTermination(1L, TimeUnit.DAYS)) {
                        break;
                    }
                } catch (InterruptedException interruption) {
                    interrupted = true;
                }
            }
            synchronized (queries) {
                queries.clear();
                pending.clear();
                queryBytes = 0L;
            }
            consumption.writeLock().lock();
            try {
                for (Definitions source : definitions.values()) {
                    try {
                        try {
                            source.data().unregisterEngine(engine);
                        } finally {
                            source.data().close();
                        }
                    } catch (Throwable closeFailure) {
                        if (failure == null) {
                            failure = closeFailure;
                        } else {
                            failure.addSuppressed(closeFailure);
                        }
                    }
                }
                definitions.clear();
            } finally {
                consumption.writeLock().unlock();
            }
            if (failure != null) {
                throw new IllegalStateException("Unable to close saved biome definitions", failure);
            }
        } finally {
            if (interrupted) {
                Thread.currentThread().interrupt();
            }
        }
    }

    int cachedQueryCount() {
        synchronized (queries) {
            return queries.size();
        }
    }

    long cachedQueryBytes() {
        synchronized (queries) {
            return queryBytes;
        }
    }

    int pendingQueryCount() {
        synchronized (queries) {
            return pending.size();
        }
    }

    private PreparedQuery query(int chunkX, int chunkZ) {
        synchronized (queries) {
            requireOpen();
            PreparedQuery cached = queries.get(key(chunkX, chunkZ));
            if (cached != null) {
                return cached;
            }
            queryAsync(chunkX, chunkZ);
        }
        throw loading(chunkX, chunkZ);
    }

    private CompletableFuture<PreparedQuery> queryAsync(int chunkX, int chunkZ) {
        long key = key(chunkX, chunkZ);
        synchronized (queries) {
            requireOpen();
            PreparedQuery cached = queries.get(key);
            if (cached != null) {
                return CompletableFuture.completedFuture(cached);
            }
            PendingQuery loading = pending.get(key);
            if (loading != null) {
                return loading.result;
            }
            if (pending.size() >= MAXIMUM_PENDING_QUERIES) {
                return CompletableFuture.failedFuture(loading(chunkX, chunkZ));
            }
            PendingQuery requested = new PendingQuery();
            pending.put(key, requested);
            reads.execute(() -> readQuery(chunkX, chunkZ, requested));
            return requested.result;
        }
    }

    private static SavedBiomeUnavailableException loading(int chunkX, int chunkZ) {
        return new SavedBiomeUnavailableException("Saved biome information is loading at chunk "
                + chunkX + "," + chunkZ + ". Try again shortly.", true);
    }

    private void readQuery(int chunkX, int chunkZ, PendingQuery loading) {
        long key = key(chunkX, chunkZ);
        PreparedQuery prepared = null;
        try {
            if (!closed) {
                Optional<SavedBiomeChunk> saved = snapshot(chunkX, chunkZ);
                prepared = saved.isEmpty()
                        ? new PreparedQuery(Optional.empty(), Map.of(), Map.of(), null, 256L)
                        : prepareDefinitions(saved.get());
            }
        } catch (SavedBiomeUnavailableException unavailable) {
            if (!closed) {
                IrisLogging.warnOnce("saved-biome:" + history.paths().dimensionRoot() + ":" + unavailable.getMessage(),
                        unavailable.getMessage());
            }
            prepared = new PreparedQuery(Optional.empty(), Map.of(), Map.of(), unavailable, 512L);
        } catch (IOException | RuntimeException failure) {
            if (!closed && IrisLogging.warnOnce("saved-biome:" + history.paths().dimensionRoot() + ":" + failure.getMessage(),
                    "Failed to read saved biome information for chunk " + chunkX + "," + chunkZ + ".")) {
                IrisLogging.reportError(failure);
            }
            prepared = new PreparedQuery(Optional.empty(), Map.of(), Map.of(), new SavedBiomeUnavailableException(
                    "Unable to read the saved biome at chunk " + chunkX + "," + chunkZ + ".", failure), 512L);
        } catch (Error failure) {
            loading.result.completeExceptionally(failure);
            throw failure;
        } finally {
            boolean refresh;
            synchronized (queries) {
                refresh = !closed && loading.invalidated;
                if (refresh) {
                    loading.invalidated = false;
                    reads.execute(() -> readQuery(chunkX, chunkZ, loading));
                } else {
                    pending.remove(key, loading);
                    if (!closed && prepared != null) {
                        cacheQuery(key, prepared);
                    }
                }
            }
            if (!refresh) {
                if (closed || prepared == null) {
                    loading.result.completeExceptionally(new IllegalStateException("Saved biome runtime is closed."));
                } else {
                    loading.result.complete(prepared);
                }
            }
        }
    }

    private void cacheQuery(long key, PreparedQuery prepared) {
        PreparedQuery previous = queries.put(key, prepared);
        if (previous != null) {
            queryBytes -= previous.estimatedBytes();
        }
        queryBytes += prepared.estimatedBytes();
        Iterator<Map.Entry<Long, PreparedQuery>> entries = queries.entrySet().iterator();
        while (queries.size() > MAXIMUM_QUERIES || queryBytes > MAXIMUM_QUERY_BYTES) {
            queryBytes -= entries.next().getValue().estimatedBytes();
            entries.remove();
        }
    }

    private PreparedQuery prepareDefinitions(SavedBiomeChunk chunk) throws IOException {
        if (chunk.estimatedBytes() + 256L > MAXIMUM_QUERY_BYTES) {
            throw new SavedBiomeUnavailableException("Saved biome definitions exceed the query memory limit.", false);
        }
        Map<SavedBiomeChunk.Cell, BiomeEnvironment> environments = new HashMap<>();
        Map<SavedBiomeChunk.Cell, SavedBiomeUnavailableException> failures = new HashMap<>();
        Set<Long> nativeOwners = new HashSet<>();
        for (int x = 0; x < 16; x++) {
            for (int z = 0; z < 16; z++) {
                SavedBiomeChunk.Column column = chunk.column(x, z);
                prepareEnvironment(chunk, column.surface(), environments, failures);
                prepareEnvironment(chunk, column.caveBase(), environments, failures);
                for (SavedBiomeChunk.Span span : column.vertical()) {
                    if (nativeOwners.add(span.cell().activationId())) {
                        prepareNativeDefinitions(span.cell().activationId());
                    }
                    prepareEnvironment(chunk, span.cell(), environments, failures);
                }
            }
        }
        return new PreparedQuery(Optional.of(chunk), Map.copyOf(environments), Map.copyOf(failures), null,
                chunk.estimatedBytes() + environments.size() * 256L + failures.size() * 512L + 256L);
    }

    private void prepareNativeDefinitions(long activationId) throws IOException {
        try {
            definitions(activationId);
        } catch (SavedBiomeUnavailableException unavailable) {
            IrisLogging.warnOnce("saved-biome:" + history.paths().dimensionRoot() + ":" + unavailable.getMessage(),
                    unavailable.getMessage());
        }
    }

    private void prepareEnvironment(SavedBiomeChunk chunk, SavedBiomeChunk.Cell cell,
                                    Map<SavedBiomeChunk.Cell, BiomeEnvironment> environments,
                                    Map<SavedBiomeChunk.Cell, SavedBiomeUnavailableException> failures) throws IOException {
        if (!cell.isResolved() || environments.containsKey(cell) || failures.containsKey(cell)) {
            return;
        }
        if (chunk.estimatedBytes() + environments.size() * 256L + (failures.size() + 1L) * 512L + 256L > MAXIMUM_QUERY_BYTES) {
            throw new SavedBiomeUnavailableException("Saved biome definitions exceed the query memory limit.", false);
        }
        try {
            environments.put(cell, definitions(cell.activationId()).environment(cell));
        } catch (SavedBiomeUnavailableException unavailable) {
            failures.put(cell, unavailable);
            IrisLogging.warnOnce("saved-biome:" + history.paths().dimensionRoot() + ":" + unavailable.getMessage(),
                    unavailable.getMessage());
        }
    }

    private Definitions definitions(long activationId) throws IOException {
        GenerationActivation activation = history.manifest().activation(activationId).orElseThrow(() ->
                new IOException("Historical biome generation is missing: " + activationId));
        String epochId = activation.epochId();
        Definitions cached = definitions.get(epochId);
        if (cached != null) {
            return cached;
        }
        synchronized (definitions) {
            cached = definitions.get(epochId);
            if (cached != null) {
                return cached;
            }
            GenerationEpoch epoch = history.manifest().epoch(activation.epochId()).orElseThrow(() ->
                    new IOException("Historical biome epoch is missing: " + activation.epochId()));
            Path pack = history.paths().packRoot(epoch.epochId());
            if (!Files.exists(pack, LinkOption.NOFOLLOW_LINKS)) {
                throw new SavedBiomeUnavailableException("The saved pack definitions for generation " + activationId
                        + " are missing. Its terrain remains saved, but its Iris biome rules cannot run.", false);
            }
            IrisData data = IrisData.openRuntime(history.packRoot(activationId).toFile());
            try {
                data.bindGenerationRegistryContract(epoch.registryContract());
                data.registerEngine(engine);
                IrisDimension dimension = data.getDimensionLoader().load(epoch.dimensionContract().dimensionKey());
                if (dimension == null) {
                    throw new IOException("Historical dimension is no longer supported: " + epoch.dimensionContract().dimensionKey());
                }
                Definitions loaded = new Definitions(data, dimension, NativeBiomeSpawnSelection.retainedDerivatives(data));
                definitions.put(epochId, loaded);
                return loaded;
            } catch (Throwable failure) {
                data.unregisterEngine(engine);
                data.close();
                throw failure;
            }
        }
    }

    private void requireOpen() {
        if (closed) {
            throw new IllegalStateException("Saved biome runtime is closed.");
        }
    }

    private static long key(int chunkX, int chunkZ) {
        return ((long) chunkX << 32) | (chunkZ & 0xFFFFFFFFL);
    }

    private record PreparedQuery(Optional<SavedBiomeChunk> chunk,
                                 Map<SavedBiomeChunk.Cell, BiomeEnvironment> environments,
                                 Map<SavedBiomeChunk.Cell, SavedBiomeUnavailableException> cellFailures,
                                 SavedBiomeUnavailableException failure, long estimatedBytes) {
    }

    private static final class PendingQuery {
        private final CompletableFuture<PreparedQuery> result = new CompletableFuture<>();
        private boolean invalidated;
    }

    private enum QueryKind {
        SURFACE, CAVE_BASE, VOLUME
    }

    private record Definitions(IrisData data, IrisDimension dimension, Map<String, String> nativeDerivatives) {
        private BiomeEnvironment environment(SavedBiomeChunk.Cell cell) {
            IrisBiome biome = data.getBiomeLoader().load(cell.biomeKey());
            IrisRegion region = data.getRegionLoader().load(cell.regionKey());
            if (biome == null || region == null) {
                throw new SavedBiomeUnavailableException("Historical biome " + cell.biomeKey() + " in region "
                        + cell.regionKey() + " is no longer supported for generation " + cell.activationId()
                        + ". Its terrain remains saved, but its Iris biome rules cannot run.", false);
            }
            return new BiomeEnvironment(cell.activationId(), biome, region, dimension, data);
        }
    }
}
