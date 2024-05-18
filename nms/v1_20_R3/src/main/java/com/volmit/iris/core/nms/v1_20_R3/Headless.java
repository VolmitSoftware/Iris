package com.volmit.iris.core.nms.v1_20_R3;

import com.volmit.iris.Iris;
import com.volmit.iris.core.nms.BiomeBaseInjector;
import com.volmit.iris.core.nms.IHeadless;
import com.volmit.iris.core.nms.v1_20_R3.mca.MCATerrainChunk;
import com.volmit.iris.core.nms.v1_20_R3.mca.RegionFileStorage;
import com.volmit.iris.core.pregenerator.PregenListener;
import com.volmit.iris.engine.framework.Engine;
import com.volmit.iris.engine.framework.EngineStage;
import com.volmit.iris.engine.framework.WrongEngineBroException;
import com.volmit.iris.engine.object.IrisBiome;
import com.volmit.iris.util.collection.KMap;
import com.volmit.iris.util.context.ChunkContext;
import com.volmit.iris.util.context.IrisContext;
import com.volmit.iris.util.documentation.BlockCoordinates;
import com.volmit.iris.util.documentation.RegionCoordinates;
import com.volmit.iris.util.hunk.Hunk;
import com.volmit.iris.util.hunk.view.BiomeGridHunkHolder;
import com.volmit.iris.util.hunk.view.ChunkDataHunkHolder;
import com.volmit.iris.util.mantle.MantleFlag;
import com.volmit.iris.util.math.RNG;
import com.volmit.iris.util.parallel.MultiBurst;
import com.volmit.iris.util.scheduling.Looper;
import com.volmit.iris.util.scheduling.PrecisionStopwatch;
import net.minecraft.core.Holder;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.LevelHeightAccessor;
import net.minecraft.world.level.biome.Biome;
import net.minecraft.world.level.chunk.ChunkAccess;
import net.minecraft.world.level.chunk.ChunkStatus;
import net.minecraft.world.level.chunk.ProtoChunk;
import org.bukkit.Material;
import org.bukkit.NamespacedKey;
import org.bukkit.block.data.BlockData;

import java.io.File;
import java.io.IOException;
import java.util.ArrayDeque;
import java.util.Queue;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.locks.ReentrantLock;
import java.util.function.Consumer;

public class Headless implements IHeadless, LevelHeightAccessor {
    private final NMSBinding binding;
    private final Engine engine;
    private final RegionFileStorage storage;
    private final Queue<ProtoChunk> chunkQueue = new ArrayDeque<>();
    private final ReentrantLock saveLock = new ReentrantLock();
    private final KMap<String, Holder<Biome>> customBiomes = new KMap<>();
    private final KMap<NamespacedKey, Holder<Biome>> minecraftBiomes = new KMap<>();
    private boolean closed = false;

    public Headless(NMSBinding binding, Engine engine) {
        this.binding = binding;
        this.engine = engine;
        this.storage = new RegionFileStorage(new File(engine.getWorld().worldFolder(), "region").toPath(), false);
        var queueLooper = new Looper() {
            @Override
            protected long loop() {
                save();
                return closed ? -1 : 100;
            }
        };
        queueLooper.setName("Region Save Looper");
        queueLooper.start();
    }

    @Override
    public boolean exists(int x, int z) {
        if (closed) return false;
        try {
            CompoundTag tag = storage.read(new ChunkPos(x, z));
            return tag != null && !"empty".equals(tag.getString("Status"));
        } catch (IOException e) {
            return false;
        }
    }

    @Override
    public void save() {
        if (closed) return;
        saveLock.lock();
        try {
            while (!chunkQueue.isEmpty()) {
                ChunkAccess chunk = chunkQueue.poll();
                if (chunk == null) break;
                try {
                    storage.write(chunk.getPos(), binding.serializeChunk(chunk, this));
                } catch (Throwable e) {
                    Iris.error("Failed to save chunk " + chunk.getPos().x + ", " + chunk.getPos().z);
                    e.printStackTrace();
                }
            }
        } finally {
            saveLock.unlock();
        }
    }

    @Override
    public void generateRegion(MultiBurst burst, int x, int z, PregenListener listener) {
        if (closed) return;
        boolean listening = listener != null;
        if (listening) listener.onRegionGenerating(x, z);
        CountDownLatch latch = new CountDownLatch(1024);
        iterateRegion(x, z, pos -> burst.complete(() -> {
            if (listening) listener.onChunkGenerating(pos.x, pos.z);
            generateChunk(pos.x, pos.z);
            if (listening) listener.onChunkGenerated(pos.x, pos.z);
            latch.countDown();
        }));
        try {
            latch.await();
        } catch (InterruptedException ignored) {}
        if (listening) listener.onRegionGenerated(x, z);
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

            ChunkDataHunkHolder blocks = new ChunkDataHunkHolder(tc);
            BiomeGridHunkHolder biomes = new BiomeGridHunkHolder(tc, tc.getMinHeight(), tc.getMaxHeight());
            ChunkContext ctx = generate(engine, pos.x << 4, pos.z << 4, blocks, biomes);
            blocks.apply();
            biomes.apply();

            inject(engine, tc.getBiomeBaseInjector(), chunk, ctx); //TODO improve
            chunk.setStatus(ChunkStatus.FULL);
            chunkQueue.add(chunk);
        } catch (Throwable e) {
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
            engine.addGenerated();

        } catch (Throwable e) {
            Iris.reportError(e);
            engine.fail("Failed to generate " + x + ", " + z, e);
        }
        return ctx;
    }

    private void inject(Engine engine, BiomeBaseInjector injector, ChunkAccess chunk, ChunkContext ctx) {
        var pos = chunk.getPos();
        for (int y = engine.getMinHeight(); y < engine.getMaxHeight(); y++) {
            for (int x = 0; x < 16; x++) {
                for (int z = 0; z < 16; z++) {
                    int wX = pos.getBlockX(x);
                    int wZ = pos.getBlockZ(z);
                    try {
                        injector.setBiome(x, y, z, getNoiseBiome(engine, ctx, x, z, wX, y, wZ));
                    } catch (Throwable e) {
                        Iris.error("Failed to inject biome for " + wX + ", " + y + ", " + wZ);
                        e.printStackTrace();
                    }
                }
            }
        }
    }

    private Holder<Biome> getNoiseBiome(Engine engine, ChunkContext ctx, int rX, int rZ, int x, int y, int z) {
        RNG rng = new RNG(engine.getSeedManager().getBiome());
        int m = (y - engine.getMinHeight()) << 2;
        IrisBiome ib = ctx == null ?
                engine.getComplex().getTrueBiomeStream().get(x << 2, z << 2) :
                ctx.getBiome().get(rX, rZ);
        if (ib.isCustom()) {
            return customBiomes.computeIfAbsent(ib.getCustomBiome(rng, x << 2, m, z << 2).getId(),
                    id -> binding.getBiomeHolder(engine.getDimension().getLoadKey(), id));
        } else {
            return minecraftBiomes.computeIfAbsent(ib.getSkyBiome(rng, x << 2, m, z << 2).getKey(),
                    id -> binding.getBiomeHolder(id.getNamespace(), id.getKey()));
        }
    }

    @Override
    public void close() throws IOException {
        if (closed) return;
        try {
            storage.close();
        } finally {
            closed = true;
            customBiomes.clear();
            minecraftBiomes.clear();
        }
    }

    @Override
    public int getHeight() {
        return engine.getHeight();
    }

    @Override
    public int getMinBuildHeight() {
        return engine.getMinHeight();
    }
}
