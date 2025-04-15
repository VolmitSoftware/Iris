package com.volmit.iris.core.pregenerator.cache;

import com.volmit.iris.util.documentation.ChunkCoordinates;
import com.volmit.iris.util.documentation.RegionCoordinates;

import java.io.File;

public interface PregenCache {
    default boolean isThreadSafe() {
        return false;
    }

    @ChunkCoordinates
    boolean isChunkCached(int x, int z);

    @RegionCoordinates
    boolean isRegionCached(int x, int z);

    @ChunkCoordinates
    void cacheChunk(int x, int z);

    @RegionCoordinates
    void cacheRegion(int x, int z);

    void write();

    static PregenCache create(File directory) {
        if (directory == null) return EMPTY;
        return new PregenCacheImpl(directory);
    }

    default PregenCache sync() {
        if (isThreadSafe()) return this;
        return new SynchronizedCache(this);
    }

    PregenCache EMPTY = new PregenCache() {
        @Override
        public boolean isThreadSafe() {
            return true;
        }

        @Override
        public boolean isChunkCached(int x, int z) {
            return false;
        }

        @Override
        public boolean isRegionCached(int x, int z) {
            return false;
        }

        @Override
        public void cacheChunk(int x, int z) {

        }

        @Override
        public void cacheRegion(int x, int z) {

        }

        @Override
        public void write() {

        }
    };


}
