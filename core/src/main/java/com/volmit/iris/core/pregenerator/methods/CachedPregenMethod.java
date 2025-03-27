package com.volmit.iris.core.pregenerator.methods;

import com.volmit.iris.core.pregenerator.PregenListener;
import com.volmit.iris.core.pregenerator.PregeneratorMethod;
import com.volmit.iris.core.pregenerator.cache.PregenCache;
import com.volmit.iris.util.mantle.Mantle;
import lombok.AllArgsConstructor;

import java.io.*;

@AllArgsConstructor
public class CachedPregenMethod implements PregeneratorMethod {
    private final PregeneratorMethod method;
    private final PregenCache cache;

    public CachedPregenMethod(PregeneratorMethod method, File file) {
        this.method = method;
        cache = PregenCache.create(file);
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
        return cache.isRegionCached(x, z) || method.supportsRegions(x, z, listener);
    }

    @Override
    public String getMethod(int x, int z) {
        return method.getMethod(x, z);
    }

    @Override
    public void generateRegion(int x, int z, PregenListener listener) {
        if (cache.isRegionCached(x, z)) {
            listener.onRegionGenerated(x, z);

            int rX = x << 5, rZ = z << 5;
            for (int cX = 0; cX < 32; cX++) {
                for (int cZ = 0; cZ < 32; cZ++) {
                    listener.onChunkGenerated(rX + cX, rZ + cZ, true);
                    listener.onChunkCleaned(rX + cX, rZ + cZ);
                }
            }
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
        method.generateChunk(x, z, listener);
        cache.cacheChunk(x, z);
    }

    @Override
    public Mantle getMantle() {
        return method.getMantle();
    }
}
