package art.arcane.iris.structure.nativegen;

import art.arcane.volmlib.nativelib.terrain.structure.StructureOwnershipRecordView;

import art.arcane.volmlib.nativelib.terrain.NativeBuildFutures;
import art.arcane.volmlib.nativelib.terrain.structure.NativeStructureVolume;

import art.arcane.iris.generation.runtime.IrisEngine;
import art.arcane.iris.generation.runtime.Engine;
import art.arcane.iris.world.history.GenerationHistoryRuntimeRouter;
import art.arcane.volmlib.util.collection.KList;

import java.io.IOException;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Objects;
import java.util.WeakHashMap;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.IntConsumer;
import java.util.function.Supplier;

/**
 * Resolves the world-space piece bounds of every native structure that will generate around a query rect.
 *
 * <p>The answer is derived from the seed, the registries and this pack's structure policy only: candidate origins
 * come from the placement grid and from vanilla structure placements, and each candidate is assembled through the
 * same native structure factory path chunk generation uses. Nothing here reads chunk state, ownership records
 * or generation progress, so the same query answers identically no matter which chunks already exist.
 */
public final class NativeStructureVolumeIndex {
    private static final int ORIGIN_REACH_CHUNKS = StructureOwnershipRecordView.MAX_REFERENCE_DISTANCE_CHUNKS;
    private static final int MAX_CACHED_ORIGIN_CHUNKS = 16_384;
    private static final int MAX_CACHED_QUERY_CHUNKS = 4_096;
    private static final Map<Engine, NativeStructureVolumeIndex> INDEXES =
            Collections.synchronizedMap(new WeakHashMap<>());

    private final OriginResolver origins;
    private final Map<RuntimeChunkKey, KList<NativeStructureVolume>> originCache = lru(MAX_CACHED_ORIGIN_CHUNKS);
    private final Map<RuntimeChunkKey, KList<NativeStructureVolume>> queryCache = lru(MAX_CACHED_QUERY_CHUNKS);
    private final ConcurrentHashMap<RuntimeChunkKey, CompletableFuture<KList<NativeStructureVolume>>> originBuilds =
            new ConcurrentHashMap<>();
    private final ConcurrentHashMap<RuntimeChunkKey, CompletableFuture<KList<NativeStructureVolume>>> queryBuilds =
            new ConcurrentHashMap<>();
    private final IntConsumer retirementListener = this::evictRuntime;

    private NativeStructureVolumeIndex(OriginResolver origins) {
        this.origins = Objects.requireNonNull(origins, "Native structure origin resolver must not be null");
    }

    public static void install(Engine engine, OriginResolver origins) {
        Engine requiredEngine = Objects.requireNonNull(engine, "Native structure volume index requires an engine");
        NativeStructureVolumeIndex installed = new NativeStructureVolumeIndex(origins);
        synchronized (INDEXES) {
            NativeStructureVolumeIndex previous = INDEXES.put(requiredEngine, installed);
            if (previous != null) {
                previous.detachRetirementListener(requiredEngine);
            }
            installed.attachRetirementListener(requiredEngine);
        }
    }

    public static void uninstall(Engine engine) {
        if (engine != null) {
            synchronized (INDEXES) {
                NativeStructureVolumeIndex removed = INDEXES.remove(engine);
                if (removed != null) {
                    removed.detachRetirementListener(engine);
                }
            }
        }
    }

    public static void invalidate(Engine engine) {
        if (engine == null) {
            return;
        }
        synchronized (INDEXES) {
            NativeStructureVolumeIndex current = INDEXES.get(engine);
            if (current != null) {
                NativeStructureVolumeIndex replacement = current.fresh();
                current.detachRetirementListener(engine);
                INDEXES.put(engine, replacement);
                replacement.attachRetirementListener(engine);
            }
        }
    }

    public static KList<NativeStructureVolume> volumes(Engine engine, int minX, int minZ, int maxX, int maxZ) {
        NativeStructureVolumeIndex index = engine == null ? null : INDEXES.get(engine);
        return index == null ? NativeStructureVolume.NONE : index.resolve(engine, minX, minZ, maxX, maxZ);
    }

    static NativeStructureVolumeIndex forTesting(OriginResolver origins) {
        return new NativeStructureVolumeIndex(origins);
    }

    static int originReachChunks() {
        return ORIGIN_REACH_CHUNKS;
    }

    private NativeStructureVolumeIndex fresh() {
        return new NativeStructureVolumeIndex(origins);
    }

    KList<NativeStructureVolume> resolve(Engine engine, int minX, int minZ, int maxX, int maxZ) {
        int runtimeId = runtimeId(engine);
        int fromChunkX = Math.min(minX, maxX) >> 4;
        int toChunkX = Math.max(minX, maxX) >> 4;
        int fromChunkZ = Math.min(minZ, maxZ) >> 4;
        int toChunkZ = Math.max(minZ, maxZ) >> 4;
        KList<NativeStructureVolume> matches = null;
        for (int chunkX = fromChunkX; chunkX <= toChunkX; chunkX++) {
            for (int chunkZ = fromChunkZ; chunkZ <= toChunkZ; chunkZ++) {
                for (NativeStructureVolume volume : chunkVolumes(engine, runtimeId, chunkX, chunkZ)) {
                    if (!volume.intersectsRect(minX, minZ, maxX, maxZ)) {
                        continue;
                    }
                    if (matches == null) {
                        matches = new KList<>();
                    }
                    if (!matches.contains(volume)) {
                        matches.add(volume);
                    }
                }
            }
        }
        return matches == null ? NativeStructureVolume.NONE : matches;
    }

    private KList<NativeStructureVolume> chunkVolumes(
            Engine engine,
            int runtimeId,
            int chunkX,
            int chunkZ
    ) {
        return cached(queryCache, queryBuilds, new RuntimeChunkKey(runtimeId, chunkKey(chunkX, chunkZ)),
                () -> buildChunkVolumes(engine, chunkX, chunkZ));
    }

    private KList<NativeStructureVolume> buildChunkVolumes(Engine engine, int chunkX, int chunkZ) {
        int minX = chunkX << 4;
        int minZ = chunkZ << 4;
        int maxX = minX + 15;
        int maxZ = minZ + 15;
        Integer pinnedRuntimeId = pinnedRuntimeId(engine);
        KList<NativeStructureVolume> volumes = null;
        for (int originX = chunkX - ORIGIN_REACH_CHUNKS; originX <= chunkX + ORIGIN_REACH_CHUNKS; originX++) {
            for (int originZ = chunkZ - ORIGIN_REACH_CHUNKS; originZ <= chunkZ + ORIGIN_REACH_CHUNKS; originZ++) {
                for (NativeStructureVolume volume : scopedOriginVolumes(engine, originX, originZ, pinnedRuntimeId)) {
                    if (!volume.intersectsRect(minX, minZ, maxX, maxZ)) {
                        continue;
                    }
                    if (volumes == null) {
                        volumes = new KList<>();
                    }
                    volumes.add(volume);
                }
            }
        }
        return volumes == null ? NativeStructureVolume.NONE : volumes;
    }

    private static Integer pinnedRuntimeId(Engine engine) {
        if (!(engine instanceof IrisEngine irisEngine) || !irisEngine.hasGenerationRuntimeScope()) {
            return null;
        }
        GenerationHistoryRuntimeRouter router = irisEngine.getGenerationHistoryRuntimeRouter().orElse(null);
        if (router == null) {
            return null;
        }
        GenerationHistoryRuntimeRouter.RuntimeOwnership ownership = router.currentRuntimeOwnership().orElse(null);
        int runtimeId = runtimeId(engine);
        return ownership != null && ownership.binding().runtimeId() == runtimeId ? runtimeId : null;
    }

    private KList<NativeStructureVolume> scopedOriginVolumes(
            Engine engine, int chunkX, int chunkZ, Integer pinnedRuntimeId
    ) {
        if (pinnedRuntimeId != null) {
            synchronized (originCache) {
                KList<NativeStructureVolume> cached = originCache.get(
                        new RuntimeChunkKey(pinnedRuntimeId, chunkKey(chunkX, chunkZ)));
                if (cached != null) {
                    return cached;
                }
            }
        }
        return originVolumes(engine, chunkX, chunkZ);
    }

    KList<NativeStructureVolume> originVolumes(Engine engine, int chunkX, int chunkZ) {
        try (GenerationHistoryRuntimeRouter.CoordinateScope ignored =
                     openHistoryCoordinateScope(engine, chunkX, chunkZ)) {
            int runtimeId = runtimeId(engine);
            return cached(
                    originCache,
                    originBuilds,
                    new RuntimeChunkKey(runtimeId, chunkKey(chunkX, chunkZ)),
                    () -> origins.volumesAt(engine, chunkX, chunkZ)
            );
        }
    }

    @FunctionalInterface
    public interface OriginResolver {
        KList<NativeStructureVolume> volumesAt(Engine engine, int chunkX, int chunkZ);
    }

    void evictRuntime(int runtimeId) {
        synchronized (originCache) {
            originBuilds.keySet().removeIf(key -> key.runtimeId() == runtimeId);
            originCache.keySet().removeIf(key -> key.runtimeId() == runtimeId);
        }
        synchronized (queryCache) {
            queryBuilds.keySet().removeIf(key -> key.runtimeId() == runtimeId);
            queryCache.keySet().removeIf(key -> key.runtimeId() == runtimeId);
        }
    }

    private KList<NativeStructureVolume> cached(
            Map<RuntimeChunkKey, KList<NativeStructureVolume>> cache,
            ConcurrentHashMap<RuntimeChunkKey, CompletableFuture<KList<NativeStructureVolume>>> builds,
            RuntimeChunkKey key,
            Supplier<KList<NativeStructureVolume>> loader
    ) {
        synchronized (cache) {
            KList<NativeStructureVolume> hit = cache.get(key);
            if (hit != null) {
                return hit;
            }
        }

        CompletableFuture<KList<NativeStructureVolume>> future = new CompletableFuture<>();
        CompletableFuture<KList<NativeStructureVolume>> existing = builds.putIfAbsent(key, future);
        if (existing != null) {
            return NativeBuildFutures.awaitBuild(existing, "Native structure volume build");
        }

        // Close the check-then-claim window: a racer whose cache check missed before the
        // previous builder cached could claim the build slot after it retired and rebuild.
        synchronized (cache) {
            KList<NativeStructureVolume> published = cache.get(key);
            if (published != null) {
                future.complete(published);
                builds.remove(key, future);
                return published;
            }
        }

        try {
            KList<NativeStructureVolume> built = loader.get();
            synchronized (cache) {
                if (builds.get(key) == future) {
                    cache.put(key, built);
                }
            }
            future.complete(built);
            return built;
        } catch (RuntimeException | Error error) {
            future.completeExceptionally(error);
            throw error;
        } finally {
            builds.remove(key, future);
        }
    }

    private static long chunkKey(int chunkX, int chunkZ) {
        return ((long) chunkX << 32) ^ (chunkZ & 0xffffffffL);
    }

    private static GenerationHistoryRuntimeRouter.CoordinateScope openHistoryCoordinateScope(
            Engine engine,
            int chunkX,
            int chunkZ
    ) {
        if (!(engine instanceof IrisEngine irisEngine)) {
            return null;
        }
        try {
            return irisEngine.openGenerationHistoryCoordinateScope(chunkX << 4, chunkZ << 4);
        } catch (IOException failure) {
            throw new IllegalStateException("Unable to route native structure origin "
                    + chunkX + "," + chunkZ + " through generation history.", failure);
        }
    }

    private static int runtimeId(Engine engine) {
        return engine == null ? 0 : engine.getCacheID();
    }

    private void attachRetirementListener(Engine engine) {
        if (engine instanceof IrisEngine irisEngine) {
            irisEngine.addGenerationRuntimeRetirementListener(retirementListener);
        }
    }

    private void detachRetirementListener(Engine engine) {
        if (engine instanceof IrisEngine irisEngine) {
            irisEngine.removeGenerationRuntimeRetirementListener(retirementListener);
        }
    }

    private static Map<RuntimeChunkKey, KList<NativeStructureVolume>> lru(int capacity) {
        return new LinkedHashMap<>(16, 0.75F, true) {
            @Override
            protected boolean removeEldestEntry(
                    Map.Entry<RuntimeChunkKey, KList<NativeStructureVolume>> eldest
            ) {
                return size() > capacity;
            }
        };
    }

    private record RuntimeChunkKey(int runtimeId, long chunkKey) {
    }

}
