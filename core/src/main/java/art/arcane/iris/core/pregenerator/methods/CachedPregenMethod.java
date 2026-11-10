package art.arcane.iris.core.pregenerator.methods;

import art.arcane.iris.core.pregenerator.PregenListener;
import art.arcane.iris.core.pregenerator.PregenTask;
import art.arcane.iris.core.pregenerator.PregeneratorMethod;
import art.arcane.iris.core.pregenerator.cache.PregenCache;
import art.arcane.volmlib.util.mantle.runtime.Mantle;

public class CachedPregenMethod implements PregeneratorMethod {
    private final PregeneratorMethod method;
    private final PregenCache cache;
    private final PregenTask task;
    private volatile PregenListener wrappedSource;
    private volatile PregenListener wrappedListener;

    public CachedPregenMethod(PregeneratorMethod method, PregenCache cache, PregenTask task) {
        this.method = method;
        this.cache = cache.sync();
        this.task = task;
    }

    @Override
    public void init() {
        method.init();
    }

    @Override
    public void close() {
        cache.write();
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
        return cache.isRegionCached(x, z) || method.supportsRegions(x, z, listener);
    }

    @Override
    public String getMethod(int x, int z) {
        if (cache.isRegionCached(x, z)) {
            return "Cached";
        }
        return method.getMethod(x, z);
    }

    @Override
    public void generateRegion(int x, int z, PregenListener listener) {
        if (cache.isRegionCached(x, z)) {
            listener.onRegionGenerated(x, z);
            task.iterateChunks(x, z, (cX, cZ) -> {
                listener.onChunkGenerated(cX, cZ, true);
                listener.onChunkCleaned(cX, cZ);
            });
            return;
        }
        method.generateRegion(x, z, listener);
        cache.cacheRegion(x, z);
    }

    @Override
    public void generateChunk(int x, int z, PregenListener listener) {
        if (cache.isChunkCached(x, z)) {
            listener.onChunkGenerated(x, z, true);
            listener.onChunkCleaned(x, z);
            return;
        }
        method.generateChunk(x, z, cachingListener(listener));
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
    public void onChunkGenerationFailed(int chunkX, int chunkZ, Throwable failure) {
        method.onChunkGenerationFailed(chunkX, chunkZ, failure);
    }

    @Override
    public void onRegionSubmitted(int regionX, int regionZ) {
        method.onRegionSubmitted(regionX, regionZ);
    }

    @Override
    public Mantle getMantle() {
        return method.getMantle();
    }
}
