package art.arcane.iris.engine.hydrology;

import art.arcane.iris.spi.IrisLogging;
import art.arcane.iris.util.common.parallel.MultiBurst;
import art.arcane.volmlib.util.cache.CacheKey;
import com.github.benmanes.caffeine.cache.Cache;
import com.github.benmanes.caffeine.cache.Caffeine;

import java.io.IOException;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.concurrent.CancellationException;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionException;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentLinkedQueue;
import java.util.concurrent.Executor;
import java.util.concurrent.ForkJoinPool;
import java.util.concurrent.ForkJoinWorkerThread;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicLong;
import java.util.function.BooleanSupplier;

public final class HydrologyTileCache implements AutoCloseable {
    private static final int DEFAULT_MAXIMUM_ENTRIES = 64;
    private static final int MAXIMUM_SHARED_ENTRIES = 8;
    private static final int MAXIMUM_COMPOSED_CHUNKS = 256;
    private static final int CHUNK_SIZE = 16;
    private static final int CHUNK_COLUMN_COUNT = CHUNK_SIZE * CHUNK_SIZE;
    private static final Cache<SharedTileKey, HydrologyTile> SHARED_TILES = Caffeine.newBuilder()
            .maximumSize(MAXIMUM_SHARED_ENTRIES)
            .expireAfterAccess(10, TimeUnit.MINUTES)
            .build();

    private final HydrologyPlanner planner;
    private final int maximumEntries;
    private final Cache<HydrologyTileKey, HydrologyTile> tiles;
    private final Cache<Long, ChunkColumns> composedChunks;
    private final AtomicLong cacheEpoch;
    private final AtomicBoolean closed;
    private final ThreadLocal<LocalChunkColumns> localChunkColumns;
    private final Executor prefetchExecutor;
    private final ConcurrentLinkedQueue<HydrologyTileKey> prefetchQueue;
    private final Set<HydrologyTileKey> queuedPrefetches;
    private final Set<CacheLoadKey<HydrologyTileKey>> demandedPlans;
    private final AtomicBoolean prefetchActive;
    private final ConcurrentHashMap<CacheLoadKey<HydrologyTileKey>, CompletableFuture<HydrologyTile>> planning;
    private final ConcurrentHashMap<CacheLoadKey<HydrologyTileKey>, PendingLoad<HydrologyTile>> loading;
    private final ConcurrentHashMap<CacheLoadKey<Long>, PendingLoad<ChunkColumns>> composing;
    private final Object publicationLock = new Object();
    private final BooleanSupplier waitingForbidden;
    private volatile SharedCacheScope sharedCacheScope;
    private volatile StudioHydrologyTileStore persistentStore;
    private volatile boolean neighbourPrefetchEnabled;
    private int demandBatches;

    public HydrologyTileCache(HydrologyPlanner planner) {
        this(planner, DEFAULT_MAXIMUM_ENTRIES, null, null, null);
    }

    public HydrologyTileCache(HydrologyPlanner planner, int maximumEntries) {
        this(planner, maximumEntries, null, null, null);
    }

    public HydrologyTileCache(HydrologyPlanner planner, int maximumEntries, Executor prefetchExecutor) {
        this(planner, maximumEntries, prefetchExecutor, null, null);
    }

    /**
     * @param prefetchExecutor runs neighbour tile planning ahead of generation, or null to plan
     *                         tiles only when a chunk needs them
     * @param waitingForbidden true on a thread that must never wait for a cold plan, so a sample
     *                         from it is answered as "no hydrology here" while the tiles it needs
     *                         are handed to the prefetch executor; null lets every caller wait
     */
    public HydrologyTileCache(
            HydrologyPlanner planner,
            int maximumEntries,
            Executor prefetchExecutor,
            BooleanSupplier waitingForbidden
    ) {
        this(planner, maximumEntries, prefetchExecutor, waitingForbidden, null);
    }

    public HydrologyTileCache(
            HydrologyPlanner planner,
            int maximumEntries,
            Executor prefetchExecutor,
            BooleanSupplier waitingForbidden,
            SharedCacheScope sharedCacheScope
    ) {
        this.planner = Objects.requireNonNull(planner, "planner");
        this.prefetchExecutor = prefetchExecutor;
        this.waitingForbidden = waitingForbidden;
        this.sharedCacheScope = sharedCacheScope;
        this.persistentStore = null;
        this.planning = new ConcurrentHashMap<>();
        this.loading = new ConcurrentHashMap<>();
        this.composing = new ConcurrentHashMap<>();
        if (maximumEntries < 1) {
            throw new IllegalArgumentException("maximumEntries must be positive.");
        }
        this.maximumEntries = maximumEntries;
        this.prefetchQueue = new ConcurrentLinkedQueue<>();
        this.queuedPrefetches = ConcurrentHashMap.newKeySet();
        this.demandedPlans = new HashSet<>();
        this.prefetchActive = new AtomicBoolean();
        this.tiles = Caffeine.newBuilder()
                .maximumSize(maximumEntries)
                .build();
        this.composedChunks = Caffeine.newBuilder()
                .maximumSize(MAXIMUM_COMPOSED_CHUNKS)
                .build();
        this.cacheEpoch = new AtomicLong();
        this.closed = new AtomicBoolean();
        this.localChunkColumns = new ThreadLocal<>();
        this.neighbourPrefetchEnabled = true;
    }

    /**
     * The tile, planned on the prefetch executor when there is one so that a caller that gets
     * interrupted or cancelled (a map render, a command) never aborts a plan every other caller is
     * waiting for; without an executor the tile is planned on the caller.
     */
    public HydrologyTile get(HydrologyTileKey key) {
        Objects.requireNonNull(key, "key");
        requireOpen();
        if (prefetchExecutor == null) {
            return planInline(key, cacheEpoch.get());
        }
        if (plansInline()) {
            return planDemandInline(key);
        }
        return awaitPlan(planDemandAsync(key));
    }

    private boolean plansInline() {
        if (prefetchExecutor instanceof MultiBurst burst) {
            return burst.ownsCurrentThread();
        }
        return prefetchExecutor instanceof ForkJoinPool pool
                && Thread.currentThread() instanceof ForkJoinWorkerThread worker
                && worker.getPool() == pool;
    }

    private HydrologyTile planInline(HydrologyTileKey key, long epoch) {
        HydrologyTile present = tiles.getIfPresent(key);
        if (present != null) {
            return present;
        }
        CacheLoadKey<HydrologyTileKey> loadKey = new CacheLoadKey<>(epoch, key);
        PendingLoad<HydrologyTile> owned = new PendingLoad<>(Thread.currentThread(), new CompletableFuture<>());
        PendingLoad<HydrologyTile> existing;
        synchronized (publicationLock) {
            requireOpen();
            existing = loading.putIfAbsent(loadKey, owned);
        }
        if (existing != null) {
            return awaitLoad(existing);
        }
        try {
            present = tiles.getIfPresent(key);
            HydrologyTile tile = present == null ? planOrEmpty(key, epoch) : present;
            synchronized (publicationLock) {
                if (!closed.get() && cacheEpoch.get() == epoch) {
                    tiles.put(key, tile);
                }
            }
            owned.future().complete(tile);
            return tile;
        } catch (Throwable failure) {
            owned.future().completeExceptionally(failure);
            throw failure;
        } finally {
            loading.remove(loadKey, owned);
        }
    }

    /**
     * A tile whose planning throws is published without rivers instead of failing the chunks that
     * asked for it: one bad column must not take the chunk system down. The failure is logged in
     * full so the cause can be traced, and the empty tile is cached like any other so the session
     * stays consistent. A plan that was interrupted is not a bad tile: it is rethrown without being
     * cached so the next request plans it again.
     */
    private HydrologyTile planOrEmpty(HydrologyTileKey key, long planningEpoch) {
        SharedTileKey sharedKey = sharedCacheScope == null ? null : new SharedTileKey(sharedCacheScope, key);
        HydrologyTile shared = sharedKey == null ? null : SHARED_TILES.getIfPresent(sharedKey);
        if (shared != null && validSharedTile(shared, sharedKey)) {
            planner.reuseResolvedTile(shared);
            IrisLogging.debug("Reused shared Studio hydrology tile %d,%d", key.tileX(), key.tileZ());
            return shared;
        }
        if (shared != null) {
            SHARED_TILES.invalidate(sharedKey);
        }
        StudioHydrologyTileStore store = persistentStore;
        if (store != null && persistentStudioKey(key)) {
            HydrologyTile persisted = store.load(key).orElse(null);
            if (persisted != null) {
                SHARED_TILES.put(sharedKey, persisted);
                planner.reuseResolvedTile(persisted);
                IrisLogging.debug("Loaded persisted Studio hydrology tile %d,%d", key.tileX(), key.tileZ());
                return persisted;
            }
        }
        try {
            HydrologyTile planned = planner.plan(key);
            synchronized (publicationLock) {
                if (sharedKey != null && !closed.get() && planningEpoch == cacheEpoch.get()) {
                    HydrologyTile existing = SHARED_TILES.asMap().putIfAbsent(sharedKey, planned);
                    if (existing == null) {
                        IrisLogging.debug("Published shared Studio hydrology tile %d,%d", key.tileX(), key.tileZ());
                    }
                }
            }
            if (store != null && persistentStudioKey(key) && !closed.get() && planningEpoch == cacheEpoch.get()) {
                try {
                    store.save(planned);
                    IrisLogging.debug("Persisted Studio hydrology tile %d,%d", key.tileX(), key.tileZ());
                } catch (IOException failure) {
                    IrisLogging.reportError("Failed to persist Studio hydrology tile "
                            + key.tileX() + "," + key.tileZ() + ".", failure);
                }
            }
            return planned;
        } catch (RuntimeException failure) {
            if (interrupted(failure)) {
                throw failure;
            }
            IrisLogging.error("Hydrology tile " + key.tileX() + "," + key.tileZ()
                    + " failed to plan and generates without rivers: " + failure.getMessage());
            IrisLogging.reportError(failure);
            return planner.emptyTile(key);
        }
    }

    private static boolean interrupted(Throwable failure) {
        if (Thread.currentThread().isInterrupted()) {
            return true;
        }
        for (Throwable cause = failure; cause != null; cause = cause.getCause()) {
            if (cause instanceof InterruptedException || cause instanceof java.io.InterruptedIOException) {
                return true;
            }
            if (cause.getCause() == cause) {
                break;
            }
        }
        return false;
    }

    public Optional<HydrologyColumnSample> columnAt(int blockX, int blockZ) {
        return chunkColumns(blockX, blockZ).columnAt(blockX, blockZ);
    }

    public void prepareChunkColumns(int blockX, int blockZ) {
        chunkColumns(blockX, blockZ);
    }

    /**
     * Whether every tile the column's chunk composes from is already planned. A caller that must not
     * wait (the server thread answering a height or biome query) uses this before sampling: when the
     * answer is false the missing tiles are handed to the prefetch executor so a later query finds
     * them, and nothing is planned on the caller.
     */
    public boolean isPlanned(int blockX, int blockZ) {
        int chunkX = Math.floorDiv(blockX, CHUNK_SIZE);
        int chunkZ = Math.floorDiv(blockZ, CHUNK_SIZE);
        if (composedChunks.getIfPresent(CacheKey.mix(RiverFootprint.pack(chunkX, chunkZ))) != null) {
            return true;
        }
        return !requestUnplanned(chunkX, chunkZ);
    }

    /**
     * Hands every tile the chunk needs and does not have to the prefetch executor and says whether
     * any was missing. Nothing is planned on the caller, so this is safe from a thread that must
     * never wait for a cold plan.
     */
    private boolean requestUnplanned(int chunkX, int chunkZ) {
        boolean missing = false;
        for (HydrologyTileKey key : relevantKeys(chunkX, chunkZ)) {
            if (tiles.getIfPresent(key) != null) {
                continue;
            }
            missing = true;
            if (prefetchExecutor != null) {
                prefetch(key);
            }
        }
        return missing;
    }

    /** Whether the calling thread must never wait for a cold hydrology plan. */
    private boolean waitsForbidden() {
        return waitingForbidden != null && waitingForbidden.getAsBoolean();
    }

    public HydrologyRenderSample renderAt(int blockX, int blockZ) {
        return columnAt(blockX, blockZ)
                .map(HydrologyColumnSample::renderSample)
                .orElseGet(() -> new HydrologyRenderSample(blockX, blockZ, List.of()));
    }

    public void invalidate(HydrologyTileKey key) {
        Objects.requireNonNull(key, "key");
        clear();
    }

    public void clear() {
        synchronized (prefetchQueue) {
            synchronized (publicationLock) {
                cacheEpoch.incrementAndGet();
                planner.clearOwnerDrafts();
                tiles.invalidateAll();
                composedChunks.invalidateAll();
            }
            prefetchQueue.clear();
            queuedPrefetches.clear();
            demandedPlans.clear();
            demandBatches = 0;
        }
        localChunkColumns.remove();
    }

    public int size() {
        tiles.cleanUp();
        return Math.toIntExact(tiles.estimatedSize());
    }

    public int maximumEntries() {
        return maximumEntries;
    }

    public void setNeighbourPrefetchEnabled(boolean enabled) {
        neighbourPrefetchEnabled = enabled;
    }

    public void preparePregeneration(int centerBlockX, int centerBlockZ) {
        int tileSize = planner.settings().routing().tileSize();
        int minimumBlockX = Math.subtractExact(centerBlockX, tileSize);
        int minimumBlockZ = Math.subtractExact(centerBlockZ, tileSize);
        int maximumBlockX = Math.addExact(centerBlockX, tileSize);
        int maximumBlockZ = Math.addExact(centerBlockZ, tileSize);
        enqueuePrefetchArea(prefetchAreaKeys(
                minimumBlockX,
                minimumBlockZ,
                maximumBlockX,
                maximumBlockZ,
                centerBlockX,
                centerBlockZ
        ), true);
    }

    public void enableSharedCache(SharedCacheScope scope, Path persistentRoot) {
        if (closed.get()) {
            throw new IllegalStateException("Hydrology tile cache is closed.");
        }
        sharedCacheScope = Objects.requireNonNull(scope, "scope");
        persistentStore = persistentRoot == null
                ? null
                : new StudioHydrologyTileStore(persistentRoot, scope, planner.settings().routing().tileSize());
    }

    private boolean persistentStudioKey(HydrologyTileKey key) {
        int tileSize = planner.settings().routing().tileSize();
        int publicationRadius = planner.settings().publicationRadius();
        int minimumTile = Math.subtractExact(tileCoordinate(-(long) publicationRadius, tileSize), 1);
        int maximumTile = Math.addExact(tileCoordinate(CHUNK_SIZE - 1L + publicationRadius, tileSize), 1);
        return key.tileX() >= minimumTile && key.tileX() <= maximumTile
                && key.tileZ() >= minimumTile && key.tileZ() <= maximumTile;
    }

    @Override
    public void close() {
        ArrayList<CompletableFuture<?>> active = new ArrayList<>();
        ArrayList<CompletableFuture<HydrologyTile>> queued = new ArrayList<>();
        synchronized (publicationLock) {
            for (PendingLoad<HydrologyTile> load : loading.values()) {
                collectActiveLoad(active, load);
            }
            for (PendingLoad<ChunkColumns> load : composing.values()) {
                collectActiveLoad(active, load);
            }
            closed.set(true);
            planning.forEach((key, future) -> {
                if (!loading.containsKey(key)) {
                    queued.add(future);
                }
            });
        }
        for (CompletableFuture<HydrologyTile> future : queued) {
            future.completeExceptionally(new CancellationException("Hydrology tile cache is closed."));
        }
        CompletableFuture.allOf(active.toArray(CompletableFuture<?>[]::new))
                .handle((ignored, failure) -> null)
                .join();
        planning.clear();
        clear();
    }

    private static void collectActiveLoad(List<CompletableFuture<?>> active, PendingLoad<?> load) {
        if (load.owner() == Thread.currentThread()) {
            throw new IllegalStateException("Hydrology tile cache cannot close from an active load.");
        }
        active.add(load.future());
    }

    private void requireOpen() {
        if (closed.get()) {
            throw new CancellationException("Hydrology tile cache is closed.");
        }
    }

    private boolean validSharedTile(HydrologyTile tile, SharedTileKey sharedKey) {
        SharedCacheScope scope = sharedKey.scope();
        return tile.key().equals(sharedKey.tileKey())
                && tile.worldSeed() == scope.worldSeed()
                && tile.settingsFingerprint() == scope.settingsFingerprint()
                && tile.tileSize() == planner.settings().routing().tileSize();
    }

    private ChunkColumns chunkColumns(int blockX, int blockZ) {
        requireOpen();
        int chunkX = Math.floorDiv(blockX, CHUNK_SIZE);
        int chunkZ = Math.floorDiv(blockZ, CHUNK_SIZE);
        long epoch = cacheEpoch.get();
        LocalChunkColumns local = localChunkColumns.get();
        if (local != null && local.epoch() == epoch
                && local.columns().chunkX() == chunkX
                && local.columns().chunkZ() == chunkZ) {
            return local.columns();
        }
        long packedChunk = CacheKey.mix(RiverFootprint.pack(chunkX, chunkZ));
        if (composedChunks.getIfPresent(packedChunk) == null
                && waitsForbidden()
                && requestUnplanned(chunkX, chunkZ)) {
            // A thread that must never wait (the server thread answering a query) gets a riverless
            // answer while the tiles it would have waited for are planned on the prefetch executor.
            // The empty result is never cached, so the next query composes the real columns.
            return ChunkColumns.empty(chunkX, chunkZ);
        }
        ChunkColumns columns = loadChunkColumns(new CacheLoadKey<>(epoch, packedChunk), chunkX, chunkZ);
        localChunkColumns.set(new LocalChunkColumns(epoch, columns));
        return columns;
    }

    private ChunkColumns loadChunkColumns(CacheLoadKey<Long> loadKey, int chunkX, int chunkZ) {
        long epoch = loadKey.epoch();
        long packedChunk = loadKey.key();
        ChunkColumns present = composedChunks.getIfPresent(packedChunk);
        if (present != null) {
            return present;
        }
        PendingLoad<ChunkColumns> owned = new PendingLoad<>(Thread.currentThread(), new CompletableFuture<>());
        PendingLoad<ChunkColumns> existing;
        synchronized (publicationLock) {
            requireOpen();
            existing = composing.putIfAbsent(loadKey, owned);
        }
        if (existing != null) {
            return awaitLoad(existing);
        }
        try {
            present = composedChunks.getIfPresent(packedChunk);
            ChunkColumns columns = present == null ? composeChunkColumns(chunkX, chunkZ) : present;
            synchronized (publicationLock) {
                if (!closed.get() && cacheEpoch.get() == epoch) {
                    composedChunks.put(packedChunk, columns);
                }
            }
            owned.future().complete(columns);
            return columns;
        } catch (Throwable failure) {
            owned.future().completeExceptionally(failure);
            throw failure;
        } finally {
            composing.remove(loadKey, owned);
        }
    }

    /** The tiles a chunk's columns compose from: every tile within the publication radius of the chunk. */
    private ArrayList<HydrologyTileKey> relevantKeys(int chunkX, int chunkZ) {
        int minimumBlockX = Math.multiplyExact(chunkX, CHUNK_SIZE);
        int minimumBlockZ = Math.multiplyExact(chunkZ, CHUNK_SIZE);
        int tileSize = planner.settings().routing().tileSize();
        int publicationRadius = planner.settings().publicationRadius();
        int minimumTileX = tileCoordinate((long) minimumBlockX - publicationRadius, tileSize);
        int maximumTileX = tileCoordinate((long) minimumBlockX + CHUNK_SIZE - 1L + publicationRadius, tileSize);
        int minimumTileZ = tileCoordinate((long) minimumBlockZ - publicationRadius, tileSize);
        int maximumTileZ = tileCoordinate((long) minimumBlockZ + CHUNK_SIZE - 1L + publicationRadius, tileSize);
        ArrayList<HydrologyTileKey> relevantKeys = new ArrayList<>(
                Math.multiplyExact(maximumTileX - minimumTileX + 1, maximumTileZ - minimumTileZ + 1)
        );
        for (int tileZ = minimumTileZ; tileZ <= maximumTileZ; tileZ++) {
            for (int tileX = minimumTileX; tileX <= maximumTileX; tileX++) {
                relevantKeys.add(new HydrologyTileKey(tileX, tileZ));
            }
        }
        return relevantKeys;
    }

    private ChunkColumns composeChunkColumns(int chunkX, int chunkZ) {
        int minimumBlockX = Math.multiplyExact(chunkX, CHUNK_SIZE);
        int minimumBlockZ = Math.multiplyExact(chunkZ, CHUNK_SIZE);
        ArrayList<HydrologyTileKey> relevantKeys = relevantKeys(chunkX, chunkZ);
        int minimumTileX = relevantKeys.getFirst().tileX();
        int maximumTileX = relevantKeys.getLast().tileX();
        int minimumTileZ = relevantKeys.getFirst().tileZ();
        int maximumTileZ = relevantKeys.getLast().tileZ();
        List<HydrologyTile> relevantTiles = tiles(relevantKeys);
        prefetchNeighbours(minimumTileX, maximumTileX, minimumTileZ, maximumTileZ);
        HydrologyColumnSample[] columns = new HydrologyColumnSample[CHUNK_COLUMN_COUNT];
        for (int localZ = 0; localZ < CHUNK_SIZE; localZ++) {
            for (int localX = 0; localX < CHUNK_SIZE; localX++) {
                int blockX = minimumBlockX + localX;
                int blockZ = minimumBlockZ + localZ;
                columns[localZ * CHUNK_SIZE + localX] = composeColumn(
                        blockX,
                        blockZ,
                        relevantKeys,
                        relevantTiles
                )
                        .orElse(null);
            }
        }
        return new ChunkColumns(chunkX, chunkZ, columns);
    }

    /**
     * Plans the ring of tiles around the ones a chunk just needed, on the prefetch executor, so a
     * generation front walking outward finds its next tiles already planned instead of stalling on
     * a cold plan. Tiles already cached or already being prefetched are skipped; a tile a chunk
     * needs before its prefetch finishes simply joins that computation.
     */
    private void prefetchNeighbours(int minimumTileX, int maximumTileX, int minimumTileZ, int maximumTileZ) {
        if (prefetchExecutor == null || !neighbourPrefetchEnabled) {
            return;
        }
        for (int tileZ = minimumTileZ - 1; tileZ <= maximumTileZ + 1; tileZ++) {
            for (int tileX = minimumTileX - 1; tileX <= maximumTileX + 1; tileX++) {
                if (tileX >= minimumTileX && tileX <= maximumTileX && tileZ >= minimumTileZ && tileZ <= maximumTileZ) {
                    continue;
                }
                prefetch(new HydrologyTileKey(tileX, tileZ));
            }
        }
    }

    /** Plans tiles that touch the block area in nearest-first order without flooding the planner. */
    public void prefetchArea(int minimumBlockX, int minimumBlockZ, int maximumBlockX, int maximumBlockZ, int centreBlockX, int centreBlockZ) {
        if (prefetchExecutor == null) {
            return;
        }
        enqueuePrefetchArea(prefetchAreaKeys(minimumBlockX, minimumBlockZ, maximumBlockX, maximumBlockZ,
                centreBlockX, centreBlockZ), false);
    }

    private List<HydrologyTileKey> prefetchAreaKeys(int minimumBlockX, int minimumBlockZ, int maximumBlockX,
                                                  int maximumBlockZ, int centreBlockX, int centreBlockZ) {
        int tileSize = planner.settings().routing().tileSize();
        int publicationRadius = planner.settings().publicationRadius();
        int minimumTileX = tileCoordinate((long) minimumBlockX - publicationRadius, tileSize);
        int maximumTileX = tileCoordinate((long) maximumBlockX + publicationRadius, tileSize);
        int minimumTileZ = tileCoordinate((long) minimumBlockZ - publicationRadius, tileSize);
        int maximumTileZ = tileCoordinate((long) maximumBlockZ + publicationRadius, tileSize);
        int centreTileX = tileCoordinate(centreBlockX, tileSize);
        int centreTileZ = tileCoordinate(centreBlockZ, tileSize);
        ArrayList<HydrologyTileKey> keys = new ArrayList<>();
        for (int tileZ = minimumTileZ; tileZ <= maximumTileZ; tileZ++) {
            for (int tileX = minimumTileX; tileX <= maximumTileX; tileX++) {
                keys.add(new HydrologyTileKey(tileX, tileZ));
            }
        }
        keys.sort(Comparator.comparingInt((HydrologyTileKey key) -> Math.max(Math.abs(key.tileX() - centreTileX), Math.abs(key.tileZ() - centreTileZ)))
                .thenComparingInt(HydrologyTileKey::tileZ)
                .thenComparingInt(HydrologyTileKey::tileX));
        return keys;
    }

    private void enqueuePrefetchArea(List<HydrologyTileKey> keys, boolean discardOutsideArea) {
        Set<HydrologyTileKey> area = discardOutsideArea ? new HashSet<>(keys) : Set.of();
        tiles.cleanUp();
        synchronized (prefetchQueue) {
            if (discardOutsideArea) {
                prefetchQueue.removeIf(key -> {
                    if (!area.contains(key)) {
                        queuedPrefetches.remove(key);
                        return true;
                    }
                    return false;
                });
                neighbourPrefetchEnabled = true;
            }
            if (prefetchExecutor == null) {
                return;
            }
            for (HydrologyTileKey key : keys) {
                if (!enqueuePrefetch(key) && speculativeEntryCount() >= maximumEntries) {
                    break;
                }
            }
        }
        pumpPrefetch();
    }

    private boolean prefetch(HydrologyTileKey key) {
        if (closed.get()
                || tiles.getIfPresent(key) != null
                || planning.containsKey(new CacheLoadKey<>(cacheEpoch.get(), key))
                || queuedPrefetches.contains(key)) {
            return false;
        }
        tiles.cleanUp();
        synchronized (prefetchQueue) {
            if (!enqueuePrefetch(key)) {
                return false;
            }
        }
        pumpPrefetch();
        return true;
    }

    private boolean enqueuePrefetch(HydrologyTileKey key) {
        if (closed.get()
                || tiles.getIfPresent(key) != null
                || planning.containsKey(new CacheLoadKey<>(cacheEpoch.get(), key))
                || queuedPrefetches.contains(key)
                || speculativeEntryCount() >= maximumEntries) {
            return false;
        }
        queuedPrefetches.add(key);
        prefetchQueue.add(key);
        return true;
    }

    private int speculativeEntryCount() {
        return Math.toIntExact(Math.min(
                maximumEntries,
                tiles.estimatedSize() + planning.size() + queuedPrefetches.size()
        ));
    }

    private void pumpPrefetch() {
        synchronized (prefetchQueue) {
            if (prefetchExecutor == null || closed.get() || demandBatches != 0 || !demandedPlans.isEmpty()
                    || !prefetchActive.compareAndSet(false, true)) {
                return;
            }
        }
        scheduleNextPrefetch();
    }

    private void scheduleNextPrefetch() {
        CacheLoadKey<HydrologyTileKey> loadKey;
        synchronized (prefetchQueue) {
            if (closed.get() || demandBatches != 0 || !demandedPlans.isEmpty()) {
                prefetchActive.set(false);
                return;
            }
            HydrologyTileKey key = pollPrefetch();
            if (key == null) {
                prefetchActive.set(false);
                return;
            }
            loadKey = new CacheLoadKey<>(cacheEpoch.get(), key);
        }
        planAsync(loadKey).whenComplete((tile, failure) -> scheduleNextPrefetch());
    }

    private HydrologyTileKey pollPrefetch() {
        HydrologyTileKey key;
        while ((key = prefetchQueue.poll()) != null) {
            if (!queuedPrefetches.remove(key)) {
                continue;
            }
            if (tiles.getIfPresent(key) == null) {
                return key;
            }
        }
        return null;
    }

    /**
     * Plans every tile that is not cached yet on the prefetch executor and returns all of them in key
     * order. At most half the cache bound is in flight at once so a wide request cannot evict tiles
     * before the caller has read them; a tile another caller is already planning is joined rather than
     * planned twice. Without a prefetch executor the tiles are planned inline on the caller.
     */
    public List<HydrologyTile> tiles(List<HydrologyTileKey> keys) {
        Objects.requireNonNull(keys, "keys");
        requireOpen();
        HydrologyTile[] loaded = new HydrologyTile[keys.size()];
        boolean missing = false;
        for (int index = 0; index < loaded.length; index++) {
            loaded[index] = tiles.getIfPresent(keys.get(index));
            missing |= loaded[index] == null;
        }
        if (!missing) {
            return List.of(loaded);
        }
        if (prefetchExecutor == null) {
            for (int index = 0; index < loaded.length; index++) {
                if (loaded[index] == null) {
                    loaded[index] = get(keys.get(index));
                }
            }
            return List.of(loaded);
        }
        long demandEpoch = beginDemandBatch();
        try {
            if (plansInline()) {
                for (int index = 0; index < loaded.length; index++) {
                    if (loaded[index] == null) {
                        loaded[index] = get(keys.get(index));
                    }
                }
                return List.of(loaded);
            }
            int batch = Math.max(1, maximumEntries / 2);
            ArrayList<CompletableFuture<HydrologyTile>> futures = new ArrayList<>(Math.min(batch, loaded.length));
            for (int start = 0; start < loaded.length; start += batch) {
                int end = Math.min(loaded.length, start + batch);
                futures.clear();
                for (int index = start; index < end; index++) {
                    futures.add(planDemandAsync(keys.get(index)));
                }
                for (int index = start; index < end; index++) {
                    loaded[index] = awaitPlan(futures.get(index - start));
                }
            }
            return List.of(loaded);
        } finally {
            finishDemandBatch(demandEpoch);
        }
    }

    private long beginDemandBatch() {
        synchronized (prefetchQueue) {
            demandBatches++;
            return cacheEpoch.get();
        }
    }

    private void finishDemandBatch(long epoch) {
        synchronized (prefetchQueue) {
            if (epoch == cacheEpoch.get()) {
                demandBatches--;
            }
        }
        pumpPrefetch();
    }

    /**
     * The tile, planned on the prefetch executor unless it is cached or already being planned, in which
     * case the caller shares that plan instead of occupying a second executor thread with it.
     */
    private CompletableFuture<HydrologyTile> planAsync(CacheLoadKey<HydrologyTileKey> loadKey) {
        HydrologyTileKey key = loadKey.key();
        HydrologyTile present = tiles.getIfPresent(key);
        if (present != null) {
            return CompletableFuture.completedFuture(present);
        }
        CompletableFuture<HydrologyTile> future = new CompletableFuture<>();
        CompletableFuture<HydrologyTile> existing;
        synchronized (publicationLock) {
            if (closed.get()) {
                return CompletableFuture.failedFuture(new CancellationException("Hydrology tile cache is closed."));
            }
            existing = planning.putIfAbsent(loadKey, future);
        }
        if (existing != null) {
            return existing;
        }
        try {
            prefetchExecutor.execute(() -> {
                if (future.isDone()) {
                    planning.remove(loadKey, future);
                    return;
                }
                try {
                    future.complete(planInline(key, loadKey.epoch()));
                } catch (Throwable failure) {
                    future.completeExceptionally(failure);
                    if (!(failure instanceof CancellationException) || !closed.get()) {
                        IrisLogging.reportError(failure);
                    }
                } finally {
                    planning.remove(loadKey, future);
                }
            });
        } catch (RuntimeException rejected) {
            planning.remove(loadKey, future);
            future.completeExceptionally(rejected);
        }
        return future;
    }

    private CompletableFuture<HydrologyTile> planDemandAsync(HydrologyTileKey key) {
        HydrologyTile present = tiles.getIfPresent(key);
        if (present != null) {
            return CompletableFuture.completedFuture(present);
        }
        CacheLoadKey<HydrologyTileKey> loadKey = new CacheLoadKey<>(cacheEpoch.get(), key);
        boolean tracked = beginDemand(loadKey);
        CompletableFuture<HydrologyTile> future = planAsync(loadKey);
        if (tracked) {
            future.whenComplete((tile, failure) -> finishDemand(loadKey));
        }
        return future;
    }

    private HydrologyTile planDemandInline(HydrologyTileKey key) {
        HydrologyTile present = tiles.getIfPresent(key);
        if (present != null) {
            return present;
        }
        CacheLoadKey<HydrologyTileKey> loadKey = new CacheLoadKey<>(cacheEpoch.get(), key);
        boolean tracked = beginDemand(loadKey);
        try {
            return planInline(key, loadKey.epoch());
        } finally {
            if (tracked) {
                finishDemand(loadKey);
            }
        }
    }

    private boolean beginDemand(CacheLoadKey<HydrologyTileKey> loadKey) {
        synchronized (prefetchQueue) {
            if (loadKey.epoch() != cacheEpoch.get()) {
                return false;
            }
            queuedPrefetches.remove(loadKey.key());
            return demandedPlans.add(loadKey);
        }
    }

    private void finishDemand(CacheLoadKey<HydrologyTileKey> loadKey) {
        synchronized (prefetchQueue) {
            demandedPlans.remove(loadKey);
        }
        pumpPrefetch();
    }

    private static <T> T awaitLoad(PendingLoad<T> load) {
        if (load.owner() == Thread.currentThread()) {
            throw new IllegalStateException("Recursive hydrology cache load.");
        }
        return awaitPlan(load.future());
    }

    private static <T> T awaitPlan(CompletableFuture<T> future) {
        try {
            return future.join();
        } catch (CompletionException failure) {
            Throwable cause = failure.getCause();
            if (cause instanceof RuntimeException runtimeException) {
                throw runtimeException;
            }
            if (cause instanceof Error error) {
                throw error;
            }
            throw new IllegalStateException("Hydrology tile planning failed.", cause);
        }
    }

    private Optional<HydrologyColumnSample> composeColumn(
            int blockX,
            int blockZ,
            List<HydrologyTileKey> relevantKeys,
            List<HydrologyTile> relevantTiles
    ) {
        ArrayList<HydrologyColumnLayer> layers = new ArrayList<>();
        HydrologyColumnSample template = null;
        int tileSize = planner.settings().routing().tileSize();
        int publicationRadius = planner.settings().publicationRadius();
        int minimumTileX = tileCoordinate((long) blockX - publicationRadius, tileSize);
        int maximumTileX = tileCoordinate((long) blockX + publicationRadius, tileSize);
        int minimumTileZ = tileCoordinate((long) blockZ - publicationRadius, tileSize);
        int maximumTileZ = tileCoordinate((long) blockZ + publicationRadius, tileSize);
        for (int tileIndex = 0; tileIndex < relevantTiles.size(); tileIndex++) {
            HydrologyTileKey key = relevantKeys.get(tileIndex);
            if (key.tileX() < minimumTileX || key.tileX() > maximumTileX
                    || key.tileZ() < minimumTileZ || key.tileZ() > maximumTileZ) {
                continue;
            }
            HydrologyTile tile = relevantTiles.get(tileIndex);
            Optional<HydrologyColumnSample> candidate = tile.columnAt(blockX, blockZ);
            if (candidate.isEmpty()) {
                continue;
            }
            HydrologyColumnSample sample = candidate.get();
            if (template == null) {
                template = sample;
            } else {
                requireMatchingTerrain(template, sample);
            }
            layers.addAll(sample.layers());
        }
        if (template == null) {
            return Optional.empty();
        }
        LinkedHashMap<Long, HydrologyColumnLayer> unique = new LinkedHashMap<>();
        for (HydrologyColumnLayer layer : layers) {
            HydrologyColumnLayer existing = unique.putIfAbsent(layer.feature().id(), layer);
            if (existing != null && !existing.equals(layer)) {
                throw new IllegalStateException("Hydrology feature id collision at " + blockX + "," + blockZ + ".");
            }
        }
        return Optional.of(new HydrologyColumnSample(
                blockX,
                blockZ,
                template.naturalHeight(),
                template.seaLevel(),
                template.ocean(),
                template.parentBiomeKey(),
                new ArrayList<>(unique.values())
        ));
    }

    private static int tileCoordinate(long blockCoordinate, int tileSize) {
        return Math.toIntExact(Math.floorDiv(blockCoordinate, tileSize));
    }

    private static void requireMatchingTerrain(
            HydrologyColumnSample expected,
            HydrologyColumnSample actual
    ) {
        if (expected.naturalHeight() != actual.naturalHeight()
                || expected.seaLevel() != actual.seaLevel()
                || expected.ocean() != actual.ocean()
                || !expected.parentBiomeKey().equals(actual.parentBiomeKey())) {
            throw new IllegalStateException(
                    "Hydrology plans disagree on terrain metadata at " + expected.x() + "," + expected.z() + "."
            );
        }
    }

    private record ChunkColumns(int chunkX, int chunkZ, HydrologyColumnSample[] columns) {
        /** A chunk with no hydrology at all, the answer for a caller that may not wait for its plan. */
        private static ChunkColumns empty(int chunkX, int chunkZ) {
            return new ChunkColumns(chunkX, chunkZ, new HydrologyColumnSample[CHUNK_COLUMN_COUNT]);
        }

        private Optional<HydrologyColumnSample> columnAt(int blockX, int blockZ) {
            int localX = Math.floorMod(blockX, CHUNK_SIZE);
            int localZ = Math.floorMod(blockZ, CHUNK_SIZE);
            return Optional.ofNullable(columns[localZ * CHUNK_SIZE + localX]);
        }
    }

    private record LocalChunkColumns(long epoch, ChunkColumns columns) {
    }

    private record CacheLoadKey<K>(long epoch, K key) {
    }

    private record PendingLoad<T>(Thread owner, CompletableFuture<T> future) {
    }

    public record SharedCacheScope(
            String runtimeIdentity,
            long worldSeed,
            int worldHeight,
            String dimensionKey,
            long settingsFingerprint
    ) {
        public SharedCacheScope {
            if (runtimeIdentity == null || runtimeIdentity.isBlank()) {
                throw new IllegalArgumentException("Hydrology shared cache runtime identity is required.");
            }
            if (worldHeight < 3) {
                throw new IllegalArgumentException("Hydrology shared cache world height must be at least three.");
            }
            if (dimensionKey == null || dimensionKey.isBlank()) {
                throw new IllegalArgumentException("Hydrology shared cache dimension key is required.");
            }
        }
    }

    private record SharedTileKey(SharedCacheScope scope, HydrologyTileKey tileKey) {
    }
}
