package com.volmit.iris.platform.bukkit;

import art.arcane.chrono.Average;
import art.arcane.chrono.PrecisionStopwatch;
import art.arcane.spatial.hunk.Hunk;
import com.volmit.iris.engine.EngineConfiguration;
import com.volmit.iris.engine.Engine;
import com.volmit.iris.engine.feature.FeatureSizedTarget;
import com.volmit.iris.engine.feature.FeatureTarget;
import com.volmit.iris.engine.pipeline.PipedHunkStack;
import com.volmit.iris.platform.IrisPlatform;
import com.volmit.iris.platform.block.PlatformBlock;
import com.volmit.iris.platform.bukkit.util.StaticBiomeProvider;
import com.volmit.iris.platform.bukkit.wrapper.BukkitWorld;
import org.bukkit.Bukkit;
import org.bukkit.World;
import org.bukkit.block.Biome;
import org.bukkit.generator.BiomeProvider;
import org.bukkit.generator.ChunkGenerator;
import com.volmit.iris.platform.bukkit.util.ChunkDataHunkView;
import org.bukkit.generator.WorldInfo;

import java.io.Closeable;
import java.io.IOException;
import java.util.Random;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;
import java.util.concurrent.locks.ReentrantLock;

public class IrisBukkitChunkGenerator extends ChunkGenerator implements Closeable {
    private final IrisPlatform platform;
    private final EngineConfiguration configuration;
    private final AtomicReference<Engine> engine;
    private final ReentrantLock engineLock;
    private final AtomicInteger perSecond;
    private final PrecisionStopwatch p = PrecisionStopwatch.start();
    private final Average a = new Average(128);
    private final StaticBiomeProvider staticBiomeProvider;

    public IrisBukkitChunkGenerator(IrisPlatform platform, EngineConfiguration configuration) {
        this.perSecond = new AtomicInteger(0);
        this.platform = platform;
        this.configuration = configuration;
        this.staticBiomeProvider = new StaticBiomeProvider(Biome.PLAINS);
        this.engine = new AtomicReference<>();
        this.engineLock = new ReentrantLock();
    }

    @Override
    public ChunkData generateChunkData(World world, Random random, int x, int z, BiomeGrid biome) {
        PrecisionStopwatch pp = PrecisionStopwatch.start();
        initEngine(world);
        ChunkData data = Bukkit.createChunkData(world);
        Hunk<PlatformBlock> chunk = new ChunkDataHunkView(data);
        FeatureSizedTarget targetSize = FeatureSizedTarget.builder()
            .width(chunk.getWidth())
            .height(chunk.getHeight())
            .depth(chunk.getDepth())
            .offsetX(x << 4)
            .offsetZ(z << 4)
            .offsetY(0)
            .build();
        FeatureTarget<PlatformBlock> blockTarget = new FeatureTarget<>(chunk, targetSize);
        PipedHunkStack stack = new PipedHunkStack();
        stack.register(PlatformBlock.class, blockTarget);
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
                try {
                    engine.set(new Engine(platform, BukkitWorld.of(world), configuration));
                } catch (IOException e) {
                    throw new RuntimeException(e);
                }
            }

            engineLock.unlock();
        }
    }

    @Override
    public boolean canSpawn(World world, int x, int z) {
        return false;
    }

    @Override
    public BiomeProvider getDefaultBiomeProvider(WorldInfo worldInfo) {
        return staticBiomeProvider;
    }

    @Override
    public void close() throws IOException {
        engine.get().close();
    }

    @Override
    public boolean shouldGenerateNoise() {
        return super.shouldGenerateNoise();
    }

    @Override
    public boolean shouldGenerateSurface() {
        return super.shouldGenerateSurface();
    }

    @Override
    public boolean shouldGenerateBedrock() {
        return super.shouldGenerateBedrock();
    }

    @Override
    public boolean shouldGenerateCaves() {
        return super.shouldGenerateCaves();
    }

    @Override
    public boolean shouldGenerateDecorations() {
        return super.shouldGenerateDecorations();
    }

    @Override
    public boolean shouldGenerateMobs() {
        return super.shouldGenerateMobs();
    }

    @Override
    public boolean shouldGenerateStructures() {
        return super.shouldGenerateStructures();
    }
}
