package com.volmit.iris.engine;

import com.volmit.iris.engine.feature.IrisFeature;
import com.volmit.iris.engine.feature.standard.FeatureTerrain;
import com.volmit.iris.platform.IrisPlatform;
import com.volmit.iris.platform.PlatformBlock;
import com.volmit.iris.platform.PlatformNamespaceKey;
import com.volmit.iris.platform.PlatformRegistry;
import com.volmit.iris.platform.PlatformWorld;
import lombok.Data;
import manifold.util.concurrent.ConcurrentWeakHashMap;

import java.lang.ref.WeakReference;
import java.util.Map;
import java.util.Optional;

@Data
public class IrisEngine {
    private static final Map<Thread, WeakReference<IrisEngine>> engineContext = new ConcurrentWeakHashMap<>();
    private final IrisPlatform platform;
    private final EngineRegistry registry;
    private final EngineConfiguration configuration;
    private final PlatformWorld world;
    private final EngineBlockCache blockCache;

    private final FeatureTerrain terrainFeature;

    public IrisEngine(IrisPlatform platform, PlatformWorld world, EngineConfiguration configuration) {
        this.configuration = configuration;
        this.platform = platform;
        this.world = world;
        this.blockCache = new EngineBlockCache(this);
        this.registry = EngineRegistry.builder()
            .blockRegistry(new PlatformRegistry<>(platform.getBlocks()))
            .biomeRegistry(new PlatformRegistry<>(platform.getBiomes()))
            .build();

        terrainFeature = new FeatureTerrain(this);
    }

    public PlatformBlock block(String block)
    {
        return blockCache.get(block);
    }

    public PlatformNamespaceKey key(String nsk)
    {
        return getPlatform().key(nsk);
    }

    public static Optional<IrisEngine> context()
    {
        WeakReference<IrisEngine> reference = engineContext.get(Thread.currentThread());

        if(reference != null)
        {
            return Optional.ofNullable(reference.get());
        }

        return Optional.empty();
    }
}
