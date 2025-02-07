/*
 *  Iris is a World Generator for Minecraft Bukkit Servers
 *  Copyright (c) 2024 Arcane Arts (Volmit Software)
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License
 * along with this program.  If not, see <https://www.gnu.org/licenses/>.
 */

package com.volmit.iris.engine.object;

import com.volmit.iris.Iris;
import com.volmit.iris.core.nms.INMS;
import com.volmit.iris.core.nms.headless.IRegion;
import com.volmit.iris.core.nms.headless.IRegionStorage;
import com.volmit.iris.core.nms.headless.SerializableChunk;
import com.volmit.iris.core.pregenerator.PregenListener;
import com.volmit.iris.engine.data.cache.Cache;
import com.volmit.iris.engine.framework.Engine;
import com.volmit.iris.engine.framework.EngineStage;
import com.volmit.iris.engine.framework.WrongEngineBroException;
import com.volmit.iris.util.collection.KList;
import com.volmit.iris.util.collection.KMap;
import com.volmit.iris.util.context.ChunkContext;
import com.volmit.iris.util.context.IrisContext;
import com.volmit.iris.util.documentation.BlockCoordinates;
import com.volmit.iris.util.documentation.RegionCoordinates;
import com.volmit.iris.util.hunk.Hunk;
import com.volmit.iris.util.hunk.view.BiomeGridHunkHolder;
import com.volmit.iris.util.hunk.view.SyncChunkDataHunkHolder;
import com.volmit.iris.util.mantle.MantleFlag;
import com.volmit.iris.util.math.M;
import com.volmit.iris.util.math.Position2;
import com.volmit.iris.util.parallel.MultiBurst;
import com.volmit.iris.util.scheduling.J;
import com.volmit.iris.util.scheduling.Looper;
import com.volmit.iris.util.scheduling.PrecisionStopwatch;
import org.bukkit.Material;
import org.bukkit.block.data.BlockData;

import java.io.IOException;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.Consumer;

public class IrisHeadless {
    private final long KEEP_ALIVE = TimeUnit.SECONDS.toMillis(10L);
    private final Engine engine;
    private final IRegionStorage storage;
    private final ExecutorService executor = Executors.newCachedThreadPool();
    private final KMap<Long, Region> regions = new KMap<>();
    private final AtomicInteger loadedChunks = new AtomicInteger();
    private transient CompletingThread regionThread;
    private transient boolean closed = false;

    public IrisHeadless(Engine engine) {
        this.engine = engine;
        this.storage = INMS.get().createRegionStorage(engine);
        if (storage == null) throw new IllegalStateException("Failed to create region storage!");
        engine.getWorld().headless(this);
        startRegionCleaner();
    }

    private void startRegionCleaner() {
        var cleaner = new Looper() {
            @Override
            protected long loop() {
                if (closed) return -1;
                long time = M.ms() - KEEP_ALIVE;
                regions.values()
                        .stream()
                        .filter(r -> r.lastEntry < time)
                        .forEach(Region::submit);
                return closed ? -1 : 1000;
            }
        };
        cleaner.setName("Iris Region Cleaner - " + engine.getWorld().name());
        cleaner.setPriority(Thread.MIN_PRIORITY);
        cleaner.start();
    }

    public int getLoadedChunks() {
        return loadedChunks.get();
    }

    /**
     * Checks if the mca plate is fully generated or not.
     *
     * @param x coord of the chunk
     * @param z coord of the chunk
     * @return true if the chunk exists in .mca
     */
    public boolean exists(int x, int z) {
        if (closed) return false;
        if (engine.getWorld().hasRealWorld() && engine.getWorld().realWorld().isChunkLoaded(x, z))
            return true;
        return storage.exists(x, z);
    }

    public synchronized CompletableFuture<Void> generateRegion(MultiBurst burst, int x, int z, int maxConcurrent, PregenListener listener) {
        if (closed) return CompletableFuture.completedFuture(null);
        if (regionThread != null && !regionThread.future.isDone())
            throw new IllegalStateException("Region generation already in progress");

        regionThread = new CompletingThread(() -> {
            boolean listening = listener != null;
            Semaphore semaphore = new Semaphore(maxConcurrent);
            CountDownLatch latch = new CountDownLatch(1024);

            iterateRegion(x, z, pos -> {
                try {
                    semaphore.acquire();
                } catch (InterruptedException e) {
                    semaphore.release();
                    return;
                }

                burst.complete(() -> {
                    try {
                        if (listening) listener.onChunkGenerating(pos.getX(), pos.getZ());
                        generateChunk(pos.getX(), pos.getZ());
                        if (listening) listener.onChunkGenerated(pos.getX(), pos.getZ());
                    } finally {
                        semaphore.release();
                        latch.countDown();
                    }
                });
            });
            try {
                latch.await();
            } catch (InterruptedException ignored) {}
            if (listening) listener.onRegionGenerated(x, z);
        }, "Region Generator - " + x + "," + z, Thread.MAX_PRIORITY);

        return regionThread.future;
    }

    @RegionCoordinates
    private static void iterateRegion(int x, int z, Consumer<Position2> chunkPos) {
        int cX = x << 5;
        int cZ = z << 5;
        for (int xx = 0; xx < 32; xx++) {
            for (int zz = 0; zz < 32; zz++) {
                chunkPos.accept(new Position2(cX + xx, cZ + zz));
            }
        }
    }

    public void generateChunk(int x, int z) {
        if (closed || exists(x, z)) return;
        try {
            var chunk = storage.createChunk(x, z);
            loadedChunks.incrementAndGet();

            SyncChunkDataHunkHolder blocks = new SyncChunkDataHunkHolder(chunk);
            BiomeGridHunkHolder biomes = new BiomeGridHunkHolder(chunk, chunk.getMinHeight(), chunk.getMaxHeight());
            ChunkContext ctx = generate(engine, x << 4, z << 4, blocks, biomes);
            blocks.apply();
            biomes.apply();

            storage.fillBiomes(chunk, ctx);

            long key = Cache.key(x >> 5, z >> 5);
            regions.computeIfAbsent(key, Region::new)
                    .add(chunk);
        } catch (Throwable e) {
            loadedChunks.decrementAndGet();
            Iris.error("Failed to generate " + x + ", " + z);
            e.printStackTrace();
        }
    }

    @BlockCoordinates
    private ChunkContext generate(Engine engine, int x, int z, Hunk<BlockData> vblocks, Hunk<org.bukkit.block.Biome> vbiomes) throws WrongEngineBroException {
        if (engine.isClosed()) {
            throw new WrongEngineBroException();
        }

        engine.getContext().touch();
        engine.getEngineData().getStatistics().generatedChunk();
        ChunkContext ctx = null;
        try {
            PrecisionStopwatch p = PrecisionStopwatch.start();
            Hunk<BlockData> blocks = vblocks.listen((xx, y, zz, t) -> engine.catchBlockUpdates(x + xx, y + engine.getMinHeight(), z + zz, t));

            var dimension = engine.getDimension();
            if (dimension.isDebugChunkCrossSections() && ((x >> 4) % dimension.getDebugCrossSectionsMod() == 0 || (z >> 4) % dimension.getDebugCrossSectionsMod() == 0)) {
                for (int i = 0; i < 16; i++) {
                    for (int j = 0; j < 16; j++) {
                        blocks.set(i, 0, j, Material.CRYING_OBSIDIAN.createBlockData());
                    }
                }
            } else {
                ctx = new ChunkContext(x, z, engine.getComplex());
                IrisContext.getOr(engine).setChunkContext(ctx);

                for (EngineStage i : engine.getMode().getStages()) {
                    i.generate(x, z, blocks, vbiomes, false, ctx);
                }
            }

            engine.getMantle().getMantle().flag(x >> 4, z >> 4, MantleFlag.REAL, true);
            engine.getMetrics().getTotal().put(p.getMilliseconds());
            engine.addGenerated(x,z);
        } catch (Throwable e) {
            Iris.reportError(e);
            engine.fail("Failed to generate " + x + ", " + z, e);
        }
        return ctx;
    }

    public void close() throws IOException {
        if (closed) return;
        try {
            if (regionThread != null) {
                regionThread.future.join();
                regionThread = null;
            }

            regions.values().forEach(Region::submit);
            Iris.info("Waiting for " + loadedChunks.get() + " chunks to unload...");
            while (loadedChunks.get() > 0 || !regions.isEmpty())
                J.sleep(1);
            Iris.info("All chunks unloaded");
            executor.shutdown();
            storage.close();
            engine.getWorld().headless(null);
        } finally {
            closed = true;
        }
    }

    private class Region implements Runnable {
        private final int x, z;
        private final long key;
        private final KList<SerializableChunk> chunks = new KList<>(1024);
        private final AtomicReference<Future<?>> full = new AtomicReference<>();
        private long lastEntry = M.ms();

        public Region(long key) {
            this.x = Cache.keyX(key);
            this.z = Cache.keyZ(key);
            this.key = key;
        }

        @Override
        public void run() {
            try (IRegion region = storage.getRegion(x, z, false)){
                assert region != null;

                for (var chunk : chunks) {
                    try {
                        region.write(chunk);
                    } catch (Throwable e) {
                        Iris.error("Failed to save chunk " + chunk.getPos());
                        e.printStackTrace();
                    }
                    loadedChunks.decrementAndGet();
                }
            } catch (Throwable e) {
                Iris.error("Failed to load region file " + x + ", " + z);
                Iris.reportError(e);
            }

            regions.remove(key);
        }

        public synchronized void add(SerializableChunk chunk) {
            chunks.add(chunk);
            lastEntry = M.ms();
            if (chunks.size() < 1024)
                return;
            submit();
        }

        public void submit() {
            full.getAndUpdate(future -> {
                if (future != null) return future;
                return executor.submit(this);
            });
        }
    }

    private static class CompletingThread extends Thread {
        private final CompletableFuture<Void> future = new CompletableFuture<>();

        private CompletingThread(Runnable task, String name, int priority) {
            super(task, name);
            setPriority(priority);
            start();
        }

        @Override
        public void run() {
            try {
                super.run();
            } finally {
                future.complete(null);
            }
        }
    }
}
