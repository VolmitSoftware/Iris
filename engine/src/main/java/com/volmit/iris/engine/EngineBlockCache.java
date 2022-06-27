package com.volmit.iris.engine;

import com.volmit.iris.platform.PlatformBlock;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

public class EngineBlockCache
{
    private final IrisEngine engine;
    private final Map<String, PlatformBlock> cache;

    public EngineBlockCache(IrisEngine engine)
    {
        this.engine = engine;
        this.cache = new ConcurrentHashMap<>();
    }

    public PlatformBlock get(String t)
    {
        return cache.computeIfAbsent(t, (key) -> engine.getPlatform().parseBlock(key));
    }
}
