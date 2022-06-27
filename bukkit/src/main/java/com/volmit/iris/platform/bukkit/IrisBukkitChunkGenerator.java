package com.volmit.iris.platform.bukkit;

import art.arcane.amulet.collections.hunk.Hunk;
import art.arcane.amulet.metric.Average;
import art.arcane.amulet.metric.PrecisionStopwatch;
import com.volmit.iris.engine.EngineConfiguration;
import com.volmit.iris.engine.IrisEngine;
import com.volmit.iris.engine.feature.IrisFeatureSizedTarget;
import com.volmit.iris.engine.feature.IrisFeatureTarget;
import com.volmit.iris.engine.feature.features.FeatureTerrain;
import com.volmit.iris.engine.pipeline.PipedHunkStack;
import com.volmit.iris.platform.IrisPlatform;
import com.volmit.iris.platform.PlatformBlock;
import org.bukkit.Bukkit;
import org.bukkit.World;
import org.bukkit.generator.ChunkGenerator;
import com.volmit.iris.platform.bukkit.util.ChunkDataHunkView;

import java.nio.channels.Pipe;
import java.util.Random;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;
import java.util.concurrent.locks.ReentrantLock;

public class IrisBukkitChunkGenerator extends ChunkGenerator {
    private final IrisPlatform platform;
    private final EngineConfiguration configuration;
    private final AtomicReference<IrisEngine> engine;
    private final ReentrantLock engineLock;
    private final AtomicInteger perSecond;
    private final PrecisionStopwatch p = PrecisionStopwatch.start();
    private final Average a = new Average(128);

    public IrisBukkitChunkGenerator(IrisPlatform platform, EngineConfiguration configuration)
    {
        this.perSecond = new AtomicInteger(0);
        this.platform = platform;
        this.configuration = configuration;
        engine = new AtomicReference<>();
        engineLock = new ReentrantLock();
    }

    @Override
    public ChunkData generateChunkData(World world, Random random, int x, int z, BiomeGrid biome) {
        PrecisionStopwatch pp = PrecisionStopwatch.start();
        initEngine(world);
        ChunkData data = Bukkit.createChunkData(world);
        Hunk<PlatformBlock> chunk = new ChunkDataHunkView(data);
        PipedHunkStack stack = new PipedHunkStack();
        stack.register(PlatformBlock.class, chunk);
        IrisFeatureSizedTarget targetSize = IrisFeatureSizedTarget.builder()
            .width(chunk.getWidth())
            .height(chunk.getHeight())
            .depth(chunk.getDepth())
            .offsetX(x << 4)
            .offsetZ(z << 4)
            .offsetY(0)
            .build();
        engine.get().getPlumbing().generate(engine.get(), targetSize, stack);
        perSecond.incrementAndGet();
        a.put(pp.getMilliseconds());

        if(p.getMilliseconds() > 1000)
        {
            p.reset();
            p.begin();
            System.out.println("PERSECOND: " + perSecond.getAndSet(0) + " AMS: " + ((int)Math.round(a.getAverage())) + "ms");
        }

        return data;
    }

    private void initEngine(World world) {
        if(engine.get() == null)
        {
            engineLock.lock();

            if(engine.get() == null)
            {
                engine.set(new IrisEngine(platform, world.bukkitWorld(), configuration));
            }

            engineLock.unlock();
        }
    }

    @Override
    public boolean canSpawn(World world, int x, int z) {
        return false;
    }
}
