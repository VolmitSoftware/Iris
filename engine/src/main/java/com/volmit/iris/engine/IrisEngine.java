package com.volmit.iris.engine;

import com.volmit.iris.engine.feature.features.FeatureTerrain;
import com.volmit.iris.engine.pipeline.EnginePipeline;
import com.volmit.iris.engine.pipeline.EnginePlumbing;
import com.volmit.iris.engine.pipeline.PipelinePhase;
import com.volmit.iris.engine.pipeline.PipelineTask;
import com.volmit.iris.platform.IrisPlatform;
import com.volmit.iris.platform.PlatformBlock;
import com.volmit.iris.platform.PlatformNamespaceKey;
import com.volmit.iris.platform.PlatformRegistry;
import com.volmit.iris.platform.PlatformWorld;
import lombok.Data;
import manifold.util.concurrent.ConcurrentWeakHashMap;

import java.io.Closeable;
import java.io.IOException;
import java.lang.ref.WeakReference;
import java.util.Map;
import java.util.Optional;

@Data
public class IrisEngine implements Closeable {
    private static final Map<Thread, WeakReference<IrisEngine>> engineContext = new ConcurrentWeakHashMap<>();
    private final IrisPlatform platform;
    private final EngineRegistry registry;
    private final EngineConfiguration configuration;
    private final PlatformWorld world;
    private final EngineBlockCache blockCache;
    private final EngineExecutor executor;
    private final EnginePlumbing plumbing;

    public IrisEngine(IrisPlatform platform, PlatformWorld world, EngineConfiguration configuration) {
        this.configuration = configuration;
        this.platform = platform;
        this.world = world;
        this.registry = EngineRegistry.builder()
            .blockRegistry(new PlatformRegistry<>(platform.getBlocks()))
            .biomeRegistry(new PlatformRegistry<>(platform.getBiomes()))
            .build();
        this.blockCache = new EngineBlockCache(this);
        this.executor = new EngineExecutor(this);
        this.plumbing = EnginePlumbing.builder().engine(this)
            .pipeline(EnginePipeline.builder()
                .phase(PipelinePhase.builder()
                    .task(PipelineTask.<PlatformBlock>builder().target(PlatformBlock.class).feature(new FeatureTerrain(this)).build())
                    .build())
                .build())
            .build();
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

    @Override
    public void close() throws IOException {
        getExecutor().close();
    }
}
