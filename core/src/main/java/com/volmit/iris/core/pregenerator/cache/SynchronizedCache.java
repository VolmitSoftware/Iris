package com.volmit.iris.core.pregenerator.cache;

import lombok.AllArgsConstructor;

@AllArgsConstructor
class SynchronizedCache implements PregenCache {
    private final PregenCache cache;

    @Override
    public boolean isThreadSafe() {
        return true;
    }

    @Override
    public boolean isChunkCached(int x, int z) {
        synchronized (cache) {
            return cache.isChunkCached(x, z);
        }
    }

    @Override
    public boolean isRegionCached(int x, int z) {
        synchronized (cache) {
            return cache.isRegionCached(x, z);
        }
    }

    @Override
    public void cacheChunk(int x, int z) {
        synchronized (cache) {
            cache.cacheChunk(x, z);
        }
    }

    @Override
    public void cacheRegion(int x, int z) {
        synchronized (cache) {
            cache.cacheRegion(x, z);
        }
    }

    @Override
    public void write() {
        synchronized (cache) {
            cache.write();
        }
    }
}
