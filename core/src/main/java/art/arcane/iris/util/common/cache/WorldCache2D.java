package art.arcane.iris.util.cache;

import art.arcane.volmlib.util.cache.ChunkCache2D;

import com.googlecode.concurrentlinkedhashmap.ConcurrentLinkedHashMap;
import art.arcane.iris.engine.data.cache.Cache;
import art.arcane.volmlib.util.function.Function2;

public class WorldCache2D<T> {
    private final ConcurrentLinkedHashMap<Long, ChunkCache2D<T>> chunks;
    private final Function2<Integer, Integer, T> resolver;

    public WorldCache2D(Function2<Integer, Integer, T> resolver, int size) {
        this.resolver = resolver;
        chunks = new ConcurrentLinkedHashMap.Builder<Long, ChunkCache2D<T>>()
                .initialCapacity(size)
                .maximumWeightedCapacity(size)
                .concurrencyLevel(Math.max(32, Runtime.getRuntime().availableProcessors() * 4))
                .build();
    }

    public T get(int x, int z) {
        ChunkCache2D<T> chunk = chunks.computeIfAbsent(Cache.key(x >> 4, z >> 4), $ -> new ChunkCache2D<>("iris"));
        return chunk.get(x, z, resolver);
    }

    public long getSize() {
        return chunks.size() * 256L;
    }

    public long getMaxSize() {
        return chunks.capacity() * 256L;
    }
}
