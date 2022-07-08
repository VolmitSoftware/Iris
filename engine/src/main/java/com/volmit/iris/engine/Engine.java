package com.volmit.iris.engine;

import art.arcane.chrono.PrecisionStopwatch;
import com.volmit.iris.engine.dimension.IrisBiome;
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
import com.volmit.iris.util.NSK;
import lombok.Data;
import manifold.util.concurrent.ConcurrentWeakHashMap;

import java.io.Closeable;
import java.io.IOException;
import java.lang.ref.WeakReference;
import java.util.Map;
import java.util.Optional;

@Data
public class Engine implements Closeable {
    private static final Map<Thread, WeakReference<Engine>> engineContext = new ConcurrentWeakHashMap<>();
    private final IrisPlatform platform;
    private final EngineRegistry registry;
    private final EngineConfiguration configuration;
    private final PlatformWorld world;
    private final EngineBlockCache blockCache;
    private final EngineExecutor executor;
    private final EnginePlumbing plumbing;
    private final EngineSeedManager seedManager;
    private final EngineData data;

    public Engine(IrisPlatform platform, PlatformWorld world, EngineConfiguration configuration) throws IOException {
        PrecisionStopwatch p = PrecisionStopwatch.start();
        this.configuration = configuration;
        this.platform = platform;
        this.world = world;
        i("Initializing Iris Engine for " + platform.getPlatformName() + " in " + world.getName()
            + " with " + configuration.getThreads() + " priority " + configuration.getThreadPriority()
            + " threads in " + (configuration.isMutable() ? "edit mode" : "production mode"));
        this.data = new EngineData(this);
        this.seedManager = getSeedManager();
        this.blockCache = new EngineBlockCache(this);
        this.registry = EngineRegistry.builder()
            .blockRegistry(new PlatformRegistry<>("Block", platform.getBlocks()))
            .biomeRegistry(new PlatformRegistry<>("Biome", platform.getBiomes()))
            .build();
        this.executor = new EngineExecutor(this);
        this.plumbing = EnginePlumbing.builder().engine(this)
            .pipeline(EnginePipeline.builder()
                .phase(PipelinePhase.builder()
                    .task(new PipelineTask<>(new FeatureTerrain(this), PlatformBlock.class))
                    .build())
                .build())
            .build();
        data.loadData(getConfiguration().isMutable()
                ? getPlatform().getStudioFolder()
                : getWorld().getIrisDataFolder(), getConfiguration().getDimension());
    }

    public PlatformBlock block(String block)
    {
        return blockCache.get(block);
    }

    public PlatformNamespaceKey key(String nsk)
    {
        return getPlatform().key(nsk);
    }

    public static Optional<Engine> context()
    {
        WeakReference<Engine> reference = engineContext.get(Thread.currentThread());

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
