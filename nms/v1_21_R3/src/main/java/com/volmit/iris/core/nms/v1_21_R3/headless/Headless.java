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

package com.volmit.iris.core.nms.v1_21_R3.headless;

import com.volmit.iris.Iris;
import com.volmit.iris.core.nms.IHeadless;
import com.volmit.iris.core.pregenerator.PregenListener;
import com.volmit.iris.engine.data.cache.AtomicCache;
import com.volmit.iris.engine.data.cache.Cache;
import com.volmit.iris.engine.framework.Engine;
import com.volmit.iris.engine.framework.EngineStage;
import com.volmit.iris.engine.framework.WrongEngineBroException;
import com.volmit.iris.engine.object.IrisBiome;
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
import it.unimi.dsi.fastutil.shorts.ShortArrayList;
import it.unimi.dsi.fastutil.shorts.ShortList;
import lombok.Getter;
import net.minecraft.Optionull;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Holder;
import net.minecraft.core.RegistryAccess;
import net.minecraft.core.registries.Registries;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.NbtIo;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.LevelHeightAccessor;
import net.minecraft.world.level.biome.Biome;
import net.minecraft.world.level.chunk.*;
import net.minecraft.world.level.chunk.status.ChunkStatus;
import net.minecraft.world.level.chunk.storage.RegionFile;
import net.minecraft.world.level.chunk.storage.SerializableChunkData;
import net.minecraft.world.level.levelgen.Heightmap;
import net.minecraft.world.level.levelgen.blending.BlendingData;
import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.block.data.BlockData;
import org.bukkit.craftbukkit.v1_21_R3.CraftServer;
import org.bukkit.craftbukkit.v1_21_R3.block.CraftBiome;

import java.io.DataOutputStream;
import java.io.IOException;
import java.util.*;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.Consumer;
import java.util.function.Function;
import java.util.stream.Collectors;

public class Headless implements IHeadless, LevelHeightAccessor {
    private static final AtomicCache<RegistryAccess> CACHE = new AtomicCache<>();
    private final long KEEP_ALIVE = TimeUnit.SECONDS.toMillis(10L);
    private final Engine engine;
    private final RegionFileStorage storage;
    private final ExecutorService executor = Executors.newCachedThreadPool();
    private final KMap<Long, Region> regions = new KMap<>();
    private final AtomicInteger loadedChunks = new AtomicInteger();
    private final KMap<String, Holder<Biome>> customBiomes = new KMap<>();
    private final KMap<org.bukkit.block.Biome, Holder<Biome>> minecraftBiomes;
    private final RNG biomeRng;
    private final @Getter int minY;
    private final @Getter int height;
    private transient CompletingThread regionThread;
    private transient boolean closed = false;

    public Headless(Engine engine) {
        this.engine = engine;
        this.storage = new RegionFileStorage(engine.getWorld().worldFolder());
        this.biomeRng = new RNG(engine.getSeedManager().getBiome());
        this.minY = engine.getDimension().getMinHeight();
        this.height = engine.getDimension().getMaxHeight() - minY;
        engine.getWorld().headless(this);

        AtomicInteger failed = new AtomicInteger();
        var dimKey = engine.getDimension().getLoadKey();
        for (var biome : engine.getAllBiomes()) {
            if (!biome.isCustom()) continue;
            for (var custom : biome.getCustomDerivitives()) {
                biomeHolder(dimKey, custom.getId()).ifPresentOrElse(holder -> customBiomes.put(custom.getId(), holder), () -> {
                    Iris.error("Failed to load custom biome " + dimKey + " " + custom.getId());
                    failed.incrementAndGet();
                });
            }
        }
        if (failed.get() > 0) {
            throw new IllegalStateException("Failed to load " + failed.get() + " custom biomes");
        }

        minecraftBiomes = new KMap<>(org.bukkit.Registry.BIOME.stream()
                .collect(Collectors.toMap(Function.identity(), CraftBiome::bukkitToMinecraftHolder)));
        minecraftBiomes.values().removeAll(customBiomes.values());
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
            ProtoChunk chunk = newProtoChunk(pos);
            var tc = new DirectTerrainChunk(chunk);
            loadedChunks.incrementAndGet();

            SyncChunkDataHunkHolder blocks = new SyncChunkDataHunkHolder(tc);
            BiomeGridHunkHolder biomes = new BiomeGridHunkHolder(tc, tc.getMinHeight(), tc.getMaxHeight());
            ChunkContext ctx = generate(engine, pos.x << 4, pos.z << 4, blocks, biomes);
            blocks.apply();
            biomes.apply();

            chunk.fillBiomesFromNoise((qX, qY, qZ, sampler) -> getNoiseBiome(engine, ctx, qX << 2, qY << 2, qZ << 2), null);
            chunk.setPersistedStatus(ChunkStatus.FULL);

            long key = Cache.key(pos.getRegionX(), pos.getRegionZ());
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

    private Holder<Biome> getNoiseBiome(Engine engine, ChunkContext ctx, int x, int y, int z) {
        int m = y - engine.getMinHeight();
        IrisBiome ib = ctx == null ? engine.getSurfaceBiome(x, z) : ctx.getBiome().get(x & 15, z & 15);
        if (ib.isCustom()) {
            return customBiomes.get(ib.getCustomBiome(biomeRng, x, m, z).getId());
        } else {
            return minecraftBiomes.get(ib.getSkyBiome(biomeRng, x, m, z));
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

    private class Region implements Runnable {
        private final int x, z;
        private final long key;
        private final KList<ProtoChunk> chunks = new KList<>(1024);
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
            if (regionFile == null) {
                Iris.error("Failed to load region file " + x + ", " + z);
                return;
            }

            for (var chunk : chunks) {
                try (DataOutputStream dos = regionFile.getChunkDataOutputStream(chunk.getPos())) {
                    NbtIo.write(write(chunk), dos);
                } catch (Throwable e) {
                    Iris.error("Failed to save chunk " + chunk.getPos().x + ", " + chunk.getPos().z);
                    e.printStackTrace();
                }
                loadedChunks.decrementAndGet();
            }
            regions.remove(key);
        }

        public synchronized void add(ProtoChunk chunk) {
            chunks.add(chunk);
            lastEntry = M.ms();
            if (chunks.size() < 1024)
                return;
            submit();
        }

        public void submit() {
            if (full.getAndSet(true)) return;
            executor.submit(this);
        }
    }

    private CompoundTag write(ProtoChunk chunk) {
        RegistryAccess access = registryAccess();
        List<SerializableChunkData.SectionData> list = new ArrayList<>();
        LevelChunkSection[] sections = chunk.getSections();

        int minLightSection = getMinSectionY() - 1;
        int maxLightSection = minLightSection + getSectionsCount() + 2;
        for(int y = minLightSection; y < maxLightSection; ++y) {
            int index = chunk.getSectionIndexFromSectionY(y);
            if (index < 0 || index >= sections.length) continue;
            LevelChunkSection section = sections[index].copy();
            list.add(new SerializableChunkData.SectionData(y, section, null, null));
        }

        List<CompoundTag> blockEntities = new ArrayList<>(chunk.getBlockEntitiesPos().size());

        for(BlockPos blockPos : chunk.getBlockEntitiesPos()) {
            CompoundTag nbt = chunk.getBlockEntityNbtForSaving(blockPos, access);
            if (nbt != null) {
                blockEntities.add(nbt);
            }
        }
        Map<Heightmap.Types, long[]> heightMap = new EnumMap<>(Heightmap.Types.class);
        for(Map.Entry<Heightmap.Types, Heightmap> entry : chunk.getHeightmaps()) {
            if (chunk.getPersistedStatus().heightmapsAfter().contains(entry.getKey())) {
                heightMap.put(entry.getKey(), entry.getValue().getRawData().clone());
            }
        }

        ChunkAccess.PackedTicks packedTicks = chunk.getTicksForSerialization(0);
        ShortList[] postProcessing = Arrays.stream(chunk.getPostProcessing()).map((shortlist) -> shortlist != null ? new ShortArrayList(shortlist) : null).toArray(ShortList[]::new);
        CompoundTag structureData = new CompoundTag();
        structureData.put("starts", new CompoundTag());
        structureData.put("References", new CompoundTag());

        CompoundTag persistentDataContainer = null;
        if (!chunk.persistentDataContainer.isEmpty()) {
            persistentDataContainer = chunk.persistentDataContainer.toTagCompound();
        }

        return new SerializableChunkData(access.lookupOrThrow(Registries.BIOME), chunk.getPos(),
                chunk.getMinSectionY(), 0, chunk.getInhabitedTime(), chunk.getPersistedStatus(),
                Optionull.map(chunk.getBlendingData(), BlendingData::pack), chunk.getBelowZeroRetrogen(),
                chunk.getUpgradeData().copy(), null, heightMap, packedTicks, postProcessing,
                chunk.isLightCorrect(), list, new ArrayList<>(), blockEntities, structureData, persistentDataContainer)
                .write();
    }

    private ProtoChunk newProtoChunk(ChunkPos pos) {
        return new ProtoChunk(pos, UpgradeData.EMPTY, this, registryAccess().lookupOrThrow(Registries.BIOME), null);
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

    private static RegistryAccess registryAccess() {
        return CACHE.aquire(() -> ((CraftServer) Bukkit.getServer()).getServer().registryAccess());
    }

    private static Optional<Holder.Reference<Biome>> biomeHolder(String namespace, String path) {
        return registryAccess().lookupOrThrow(Registries.BIOME).get(ResourceLocation.fromNamespaceAndPath(namespace, path));
    }
}
