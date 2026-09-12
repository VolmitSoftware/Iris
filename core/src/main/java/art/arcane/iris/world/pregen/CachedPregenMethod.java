package art.arcane.iris.world.pregen;

import art.arcane.volmlib.util.mantle.runtime.Mantle;

public class CachedPregenMethod implements PregeneratorMethod {
    private final PregeneratorMethod method;
    private final PregenCache cache;
    private final PregenTask task;
    private final PregenSavedChunkStatus savedStatus;
    private volatile PregenListener wrappedSource;
    private volatile PregenListener wrappedListener;

    public CachedPregenMethod(Configuration configuration) {
        this.method = configuration.method();
        this.cache = configuration.cache().sync();
        this.task = configuration.task();
        this.savedStatus = configuration.savedStatus();
    }

    @Override
    public void init() {
        method.init();
    }

    @Override
    public void close() {
        method.close();
        cache.write();
    }

    @Override
    public void save() {
        method.save();
        cache.write();
    }

    @Override
    public boolean supportsRegions(int x, int z, PregenListener listener) {
        return isSavedRegionCached(x, z) || method.supportsRegions(x, z, listener);
    }

    @Override
    public String getMethod(int x, int z) {
        if (isSavedRegionCached(x, z)) {
            return "Cached";
        }
        return method.getMethod(x, z);
    }

    @Override
    public void generateRegion(int x, int z, PregenListener listener) {
        if (isSavedRegionCached(x, z)) {
            listener.onRegionGenerated(x, z);
            task.iterateChunks(x, z, (cX, cZ) -> {
                listener.onChunkGenerated(cX, cZ, true);
                listener.onChunkCleaned(cX, cZ);
            });
            return;
        }
        if (!method.supportsRegions(x, z, listener)) {
            task.iterateChunks(x, z, (chunkX, chunkZ) -> generateChunk(chunkX, chunkZ, listener));
            return;
        }
        method.generateRegion(x, z, cachingListener(listener));
    }

    @Override
    public void generateChunk(int x, int z, PregenListener listener) {
        if (cache.isChunkCached(x, z) && savedStatus.isFull(x, z)) {
            listener.onChunkGenerated(x, z, true);
            listener.onChunkCleaned(x, z);
            return;
        }
        method.generateChunk(x, z, cachingListener(listener));
    }

    private boolean isSavedRegionCached(int x, int z) {
        if (!cache.isRegionCached(x, z)) {
            return false;
        }
        for (int localX = 0; localX < 32; localX++) {
            for (int localZ = 0; localZ < 32; localZ++) {
                if (!savedStatus.isFull((x << 5) + localX, (z << 5) + localZ)) {
                    return false;
                }
            }
        }
        return true;
    }

    private PregenListener cachingListener(PregenListener listener) {
        PregenListener source = wrappedSource;
        PregenListener wrapped = wrappedListener;
        if (source == listener && wrapped != null) {
            return wrapped;
        }

        PregenListener created = new PregenListener() {
            @Override
            public void onTick(double chunksPerSecond, double chunksPerMinute, double regionsPerMinute, double percent, long generated, long totalChunks, long chunksRemaining, long eta, long elapsed, String method, boolean cached) {
                listener.onTick(chunksPerSecond, chunksPerMinute, regionsPerMinute, percent, generated, totalChunks, chunksRemaining, eta, elapsed, method, cached);
            }

            @Override
            public void onChunkGenerating(int x, int z) {
                listener.onChunkGenerating(x, z);
            }

            @Override
            public void onChunkGenerated(int x, int z, boolean cachedChunk) {
                if (!cachedChunk) {
                    cache.cacheChunk(x, z);
                }
                listener.onChunkGenerated(x, z, cachedChunk);
            }

            @Override
            public void onChunkFailed(int x, int z) {
                listener.onChunkFailed(x, z);
            }

            @Override
            public void onRegionGenerated(int x, int z) {
                listener.onRegionGenerated(x, z);
            }

            @Override
            public void onRegionGenerating(int x, int z) {
                listener.onRegionGenerating(x, z);
            }

            @Override
            public void onChunkCleaned(int x, int z) {
                listener.onChunkCleaned(x, z);
            }

            @Override
            public void onRegionSkipped(int x, int z) {
                listener.onRegionSkipped(x, z);
            }

            @Override
            public void onNetworkStarted(int x, int z) {
                listener.onNetworkStarted(x, z);
            }

            @Override
            public void onNetworkFailed(int x, int z) {
                listener.onNetworkFailed(x, z);
            }

            @Override
            public void onNetworkReclaim(int revert) {
                listener.onNetworkReclaim(revert);
            }

            @Override
            public void onNetworkGeneratedChunk(int x, int z) {
                listener.onNetworkGeneratedChunk(x, z);
            }

            @Override
            public void onNetworkDownloaded(int x, int z) {
                listener.onNetworkDownloaded(x, z);
            }

            @Override
            public void onClose() {
                listener.onClose();
            }

            @Override
            public void onSaving() {
                listener.onSaving();
            }

            @Override
            public void onChunkExistsInRegionGen(int x, int z) {
                listener.onChunkExistsInRegionGen(x, z);
            }
        };
        wrappedSource = listener;
        wrappedListener = created;
        return created;
    }

    @Override
    public void onRegionBounds(int minRegionX, int minRegionZ, int maxRegionX, int maxRegionZ) {
        method.onRegionBounds(minRegionX, minRegionZ, maxRegionX, maxRegionZ);
    }

    @Override
    public void onPregenStart(int centerBlockX, int centerBlockZ) {
        method.onPregenStart(centerBlockX, centerBlockZ);
    }

    @Override
    public void onRegionSubmitted(int regionX, int regionZ) {
        method.onRegionSubmitted(regionX, regionZ);
    }

    public record Configuration(PregeneratorMethod method, PregenCache cache, PregenTask task,
                                PregenSavedChunkStatus savedStatus) {
    }

    @Override
    public Mantle getMantle() {
        return method.getMantle();
    }
}
