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

package com.volmit.iris.core.nms.v1_20_R3;

import com.volmit.iris.Iris;
import com.volmit.iris.core.IrisSettings;
import com.volmit.iris.core.ServerConfigurator;
import com.volmit.iris.core.nms.IHeadless;
import com.volmit.iris.core.nms.v1_20_R3.mca.MCATerrainChunk;
import com.volmit.iris.core.nms.v1_20_R3.mca.RegionFileStorage;
import com.volmit.iris.core.pregenerator.PregenListener;
import com.volmit.iris.engine.data.cache.Cache;
import com.volmit.iris.engine.framework.Engine;
import com.volmit.iris.engine.framework.EngineStage;
import com.volmit.iris.engine.framework.WrongEngineBroException;
import com.volmit.iris.engine.object.IrisBiome;
import com.volmit.iris.server.node.IrisSession;
import com.volmit.iris.server.packet.work.ChunkPacket;
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
import com.volmit.iris.util.math.RNG;
import com.volmit.iris.util.parallel.MultiBurst;
import com.volmit.iris.util.scheduling.J;
import com.volmit.iris.util.scheduling.Looper;
import com.volmit.iris.util.scheduling.PrecisionStopwatch;
import lombok.Getter;
import lombok.RequiredArgsConstructor;
import net.minecraft.core.Holder;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.NbtIo;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.LevelHeightAccessor;
import net.minecraft.world.level.biome.Biome;
import net.minecraft.world.level.chunk.ChunkAccess;
import net.minecraft.world.level.chunk.ChunkStatus;
import net.minecraft.world.level.chunk.ProtoChunk;
import net.minecraft.world.level.chunk.storage.RegionFile;
import org.bukkit.Material;
import org.bukkit.block.data.BlockData;

import java.io.ByteArrayOutputStream;
import java.io.DataOutputStream;
import java.io.File;
import java.io.IOException;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.Consumer;

public class Headless implements IHeadless, LevelHeightAccessor {
    private final long KEEP_ALIVE = TimeUnit.SECONDS.toMillis(IrisSettings.get().getPerformance().getHeadlessKeepAlive());
    private final NMSBinding binding;
    private final Engine engine;
    private final RegionFileStorage storage;
    private final ExecutorService executor = Executors.newCachedThreadPool();
    private final KMap<Long, Region> regions = new KMap<>();
    private final AtomicInteger loadedChunks = new AtomicInteger();
    private final KMap<String, Holder<Biome>> customBiomes = new KMap<>();
    private final KMap<org.bukkit.block.Biome, Holder<Biome>> minecraftBiomes = new KMap<>();
    private final RNG BIOME_RNG;
    private final @Getter int minBuildHeight;
    private final @Getter int height;
    private IrisSession session;
    private CompletingThread regionThread;
    private boolean closed = false;

    public Headless(NMSBinding binding, Engine engine) {
        this.binding = binding;
        this.engine = engine;
        this.storage = new RegionFileStorage(new File(engine.getWorld().worldFolder(), "region").toPath(), true);
        this.BIOME_RNG = new RNG(engine.getSeedManager().getBiome());
        this.minBuildHeight = engine.getDimension().getMinHeight();
        this.height = engine.getDimension().getMaxHeight() - minBuildHeight;
        engine.getWorld().headless(this);

        var dimKey = engine.getDimension().getLoadKey();
        for (var biome : engine.getAllBiomes()) {
            if (!biome.isCustom()) continue;
            for (var custom : biome.getCustomDerivitives()) {
                binding.registerBiome(dimKey, custom, false);
                customBiomes.put(custom.getId(), binding.getBiomeHolder(dimKey, custom.getId()));
            }
        }
        for (var biome : org.bukkit.block.Biome.values()) {
            if (biome == org.bukkit.block.Biome.CUSTOM) continue;
            minecraftBiomes.put(biome, binding.getBiomeHolder(biome.getKey()));
        }
        ServerConfigurator.dumpDataPack();
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

    public void setSession(IrisSession session) {
        if (this.session != null)
            throw new IllegalStateException("Session already set");
        this.session = session;
    }

    @Override
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
    @Override
    public boolean exists(int x, int z) {
        if (closed) return false;
        if (engine.getWorld().hasRealWorld() && engine.getWorld().realWorld().isChunkLoaded(x, z))
            return true;
        try {
            CompoundTag tag = storage.read(new ChunkPos(x, z));
            return tag != null && !"empty".equals(tag.getString("Status"));
        } catch (IOException e) {
            return false;
        }
    }

    @Override
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
                        if (listening) listener.onChunkGenerating(pos.x, pos.z);
                        generateChunk(pos.x, pos.z);
                        if (listening) listener.onChunkGenerated(pos.x, pos.z);
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
    private static void iterateRegion(int x, int z, Consumer<ChunkPos> chunkPos) {
        int cX = x << 5;
        int cZ = z << 5;
        for (int xx = 0; xx < 32; xx++) {
            for (int zz = 0; zz < 32; zz++) {
                chunkPos.accept(new ChunkPos(cX + xx, cZ + zz));
            }
        }
    }

    @Override
    public void generateChunk(int x, int z) {
        if (closed || exists(x, z)) return;
        try {
            var pos = new ChunkPos(x, z);
            ProtoChunk chunk = binding.createProtoChunk(pos, this);
            var tc = new MCATerrainChunk(chunk);
            loadedChunks.incrementAndGet();

            SyncChunkDataHunkHolder blocks = new SyncChunkDataHunkHolder(tc);
            BiomeGridHunkHolder biomes = new BiomeGridHunkHolder(tc, tc.getMinHeight(), tc.getMaxHeight());
            ChunkContext ctx = generate(engine, pos.x << 4, pos.z << 4, blocks, biomes);
            blocks.apply();
            biomes.apply();

            inject(engine, chunk, ctx);
            chunk.setStatus(ChunkStatus.FULL);

            if (session != null) {
                session.completeChunk(x, z, write(chunk));
                loadedChunks.decrementAndGet();
                return;
            }

            long key = Cache.key(pos.getRegionX(), pos.getRegionZ());
            regions.computeIfAbsent(key, Region::new)
                    .add(chunk);
        } catch (Throwable e) {
            loadedChunks.decrementAndGet();
            Iris.error("Failed to generate " + x + ", " + z);
            e.printStackTrace();
        }
    }

    @Override
    public void addChunk(ChunkPacket packet) {
        if (closed) return;
        if (session != null) throw new IllegalStateException("Headless running as Server");
        var pos = new ChunkPos(packet.getX(), packet.getZ());
        regions.computeIfAbsent(Cache.key(pos.getRegionX(), pos.getRegionZ()), Region::new)
                .add(packet);
        loadedChunks.incrementAndGet();
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

    private void inject(Engine engine, ChunkAccess chunk, ChunkContext ctx) {
        chunk.fillBiomesFromNoise((qX, qY, qZ, sampler) -> getNoiseBiome(engine, ctx, qX << 2, qY << 2, qZ << 2), null);
        /*
        int qX = QuartPos.fromBlock(chunk.getPos().getMinBlockX());
        int qZ = QuartPos.fromBlock(chunk.getPos().getMinBlockZ());

        for (int i = chunk.getMinSection(); i < chunk.getMaxSection(); i++) {
            var section = chunk.getSection(chunk.getSectionIndexFromSectionY(i));
            PalettedContainer<Holder<Biome>> biomes = (PalettedContainer<Holder<Biome>>) section.getBiomes();
            int qY = QuartPos.fromSection(i);

            for (int sX = 0; sX < 4; sX++) {
                for (int sZ = 0; sZ < 4; sZ++) {
                    for (int sY = 0; sY < 4; sY++) {
                        biomes.getAndSetUnchecked(sX, sY, sZ, getNoiseBiome(engine, ctx, (qX + sX) << 2, (qY + sY) << 2, (qZ + sZ) << 2));
                    }
                }
            }
        }*/
    }

    private Holder<Biome> getNoiseBiome(Engine engine, ChunkContext ctx, int x, int y, int z) {
        int m = y - engine.getMinHeight();
        IrisBiome ib = ctx == null ? engine.getSurfaceBiome(x, z) : ctx.getBiome().get(x & 15, z & 15);
        if (ib.isCustom()) {
            return customBiomes.get(ib.getCustomBiome(BIOME_RNG, x, m, z).getId());
        } else {
            return minecraftBiomes.get(ib.getSkyBiome(BIOME_RNG, x, m, z));
        }
    }

    @Override
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
            customBiomes.clear();
            minecraftBiomes.clear();
        }
    }

    @RequiredArgsConstructor
    private class Region implements Runnable {
        private final int x, z;
        private final long key;
        private final KList<ProtoChunk> chunks = new KList<>(1024);
        private final KList<ChunkPacket> remoteChunks = new KList<>(1024);
        private final AtomicBoolean full = new AtomicBoolean();
        private long lastEntry = M.ms();

        public Region(long key) {
            this.x = Cache.keyX(key);
            this.z = Cache.keyZ(key);
            this.key = key;
        }

        @Override
        public void run() {
            RegionFile regionFile;
            try {
                regionFile = storage.getRegionFile(new ChunkPos(x, z), false);
            } catch (IOException e) {
                Iris.error("Failed to load region file " + x + ", " + z);
                Iris.reportError(e);
                return;
            }
            if (regionFile == null) return;

            for (var chunk : chunks) {
                try (DataOutputStream dos = regionFile.getChunkDataOutputStream(chunk.getPos())) {
                    NbtIo.write(binding.serializeChunk(chunk, Headless.this), dos);
                } catch (Throwable e) {
                    Iris.error("Failed to save chunk " + chunk.getPos().x + ", " + chunk.getPos().z);
                    e.printStackTrace();
                }
                loadedChunks.decrementAndGet();
            }
            for (var chunk : remoteChunks) {
                var pos = new ChunkPos(chunk.getX(), chunk.getZ());
                try (DataOutputStream dos = regionFile.getChunkDataOutputStream(pos)) {
                    dos.write(chunk.getData());
                } catch (Throwable e) {
                    Iris.error("Failed to save remote chunk " + pos.x + ", " + pos.z);
                    e.printStackTrace();
                }
                loadedChunks.decrementAndGet();
            }
            regions.remove(key);
        }

        public synchronized void add(ProtoChunk chunk) {
            chunks.add(chunk);
            lastEntry = M.ms();
            if (chunks.size() + remoteChunks.size() < 1024)
                return;
            submit();
        }

        public synchronized void add(ChunkPacket packet) {
            remoteChunks.add(packet);
            lastEntry = M.ms();
            if (chunks.size() + remoteChunks.size() < 1024)
                return;
            submit();
        }

        public void submit() {
            if (full.getAndSet(true)) return;
            executor.submit(this);
        }
    }

    private byte[] write(ProtoChunk chunk) throws IOException {
        try (ByteArrayOutputStream out = new ByteArrayOutputStream();
             DataOutputStream dos = new DataOutputStream(out)) {
            NbtIo.write(binding.serializeChunk(chunk, Headless.this), dos);
            return out.toByteArray();
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
