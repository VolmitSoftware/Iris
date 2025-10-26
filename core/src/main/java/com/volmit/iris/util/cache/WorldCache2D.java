package com.volmit.iris.util.cache;

import com.googlecode.concurrentlinkedhashmap.ConcurrentLinkedHashMap;
import com.volmit.iris.engine.data.cache.Cache;
import com.volmit.iris.util.function.Function2;

public class WorldCache2D<T> {
    private final ConcurrentLinkedHashMap<Long, ChunkCache2D<T>> chunks;
    private final Function2<Integer, Integer, T> resolver;

    public WorldCache2D(Function2<Integer, Integer, T> resolver) {
        this.resolver = resolver;
        chunks = new ConcurrentLinkedHashMap.Builder<Long, ChunkCache2D<T>>()
                .initialCapacity(1024)
                .maximumWeightedCapacity(1024)
                .concurrencyLevel(32)
                .build();
    }

    public T get(int x, int z) {
        ChunkCache2D<T> chunk = chunks.computeIfAbsent(Cache.key(x >> 4, z >> 4), $ -> new ChunkCache2D<>());
        return chunk.get(x, z, resolver);
    }

    public long getSize() {
        return chunks.size() * 256L;
    }
}
