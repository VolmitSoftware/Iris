package com.volmit.iris.core.pregenerator.methods;

import com.volmit.iris.Iris;
import com.volmit.iris.core.IrisSettings;
import com.volmit.iris.core.nms.IHeadless;
import com.volmit.iris.core.nms.INMS;
import com.volmit.iris.core.pregenerator.PregenListener;
import com.volmit.iris.core.pregenerator.PregeneratorMethod;
import com.volmit.iris.engine.framework.Engine;
import com.volmit.iris.util.mantle.Mantle;
import com.volmit.iris.util.parallel.MultiBurst;
import org.bukkit.World;

import java.io.IOException;
import java.util.concurrent.Semaphore;

public class HeadlessPregenMethod implements PregeneratorMethod {
    private final Engine engine;
    private final IHeadless headless;
    private final Semaphore semaphore;
    private final int max;
    private final World world;

    public HeadlessPregenMethod(Engine engine) {
        this.world = engine.getWorld().realWorld();
        this.max = IrisSettings.getThreadCount(IrisSettings.get().getConcurrency().getParallelism());
        this.engine = engine;
        this.headless = INMS.get().createHeadless(engine);
        this.semaphore = new Semaphore(max);
    }

    @Override
    public void init() {}

    @Override
    public void close() {
        try {
            semaphore.acquire(max);
        } catch (InterruptedException ignored) {}
        headless.save();
        try {
            headless.close();
        } catch (IOException e) {
            Iris.error("Failed to close headless");
            e.printStackTrace();
        }
    }

    @Override
    public void save() {
        headless.save();
    }

    @Override
    public boolean supportsRegions(int x, int z, PregenListener listener) {
        return false;
    }

    @Override
    public String getMethod(int x, int z) {
        return "Headless";
    }

    @Override
    public void generateRegion(int x, int z, PregenListener listener) {}

    @Override
    public void generateChunk(int x, int z, PregenListener listener) {
        try {
            semaphore.acquire();
        } catch (InterruptedException ignored) {
            semaphore.release();
            return;
        }
        MultiBurst.burst.complete(() -> {
            try {
                listener.onChunkGenerating(x, z);
                headless.generateChunk(x, z);
                listener.onChunkGenerated(x, z);
            } finally {
                semaphore.release();
            }
        });
    }

    @Override
    public Mantle getMantle() {
        return engine.getMantle().getMantle();
    }

    @Override
    public World getWorld() {
        return world;
    }
}
