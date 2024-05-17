package com.volmit.iris.core.pregenerator.methods;

import com.volmit.iris.Iris;
import com.volmit.iris.core.nms.IHeadless;
import com.volmit.iris.core.nms.INMS;
import com.volmit.iris.core.pregenerator.PregenListener;
import com.volmit.iris.core.pregenerator.PregeneratorMethod;
import com.volmit.iris.engine.framework.Engine;
import com.volmit.iris.util.collection.KList;
import com.volmit.iris.util.mantle.Mantle;
import com.volmit.iris.util.parallel.MultiBurst;

import java.io.IOException;
import java.util.Objects;
import java.util.concurrent.Future;

public class HeadlessPregenMethod implements PregeneratorMethod {
    private final Engine engine;
    private final IHeadless headless;
    private final MultiBurst burst;
    private final KList<Future<?>> futures;

    public HeadlessPregenMethod(Engine engine) {
        this.engine = engine;
        this.headless = INMS.get().createHeadless(engine);
        this.burst = new MultiBurst("Iris Headless", Thread.MAX_PRIORITY);
        this.futures = new KList<>();
    }

    @Override
    public void init() {}

    @Override
    public void close() {
        waitForChunksPartial(0);
        burst.close();
        headless.saveAll();
        try {
            headless.close();
        } catch (IOException e) {
            Iris.error("Failed to close headless");
            e.printStackTrace();
        }
    }

    @Override
    public void save() {
        headless.saveAll();
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
        futures.removeIf(Future::isDone);
        waitForChunksPartial(512);
        futures.add(burst.complete(() -> {
            listener.onChunkGenerating(x, z);
            headless.generateChunk(x, z);
            listener.onChunkGenerated(x, z);
        }));
    }

    @Override
    public Mantle getMantle() {
        return engine.getMantle().getMantle();
    }

    private void waitForChunksPartial(int maxWaiting) {
        futures.removeWhere(Objects::isNull);
        while (futures.size() > maxWaiting) {
            try {
                Future<?> i = futures.remove(0);

                if (i == null) {
                    continue;
                }

                i.get();
            } catch (Throwable e) {
                e.printStackTrace();
            }
        }
    }
}
