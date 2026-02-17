/*
 * Iris is a World Generator for Minecraft Bukkit Servers
 * Copyright (c) 2022 Arcane Arts (Volmit Software)
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

package art.arcane.iris.engine.platform;

import art.arcane.iris.Iris;
import art.arcane.iris.core.IrisSettings;
import art.arcane.iris.core.IrisWorlds;
import art.arcane.iris.core.loader.IrisData;
import art.arcane.iris.core.nms.INMS;
import art.arcane.iris.core.service.StudioSVC;
import art.arcane.iris.engine.IrisEngine;
import art.arcane.iris.engine.data.cache.AtomicCache;
import art.arcane.iris.engine.data.chunk.TerrainChunk;
import art.arcane.iris.engine.framework.Engine;
import art.arcane.iris.engine.framework.EngineTarget;
import art.arcane.iris.engine.object.IrisDimension;
import art.arcane.iris.engine.object.IrisWorld;
import art.arcane.iris.engine.object.StudioMode;
import art.arcane.iris.engine.platform.studio.StudioGenerator;
import art.arcane.volmlib.util.collection.KList;
import art.arcane.volmlib.util.data.IrisBiomeStorage;
import art.arcane.iris.util.hunk.view.BiomeGridHunkHolder;
import art.arcane.iris.util.hunk.view.ChunkDataHunkHolder;
import art.arcane.volmlib.util.io.ReactiveFolder;
import art.arcane.volmlib.util.scheduling.ChronoLatch;
import art.arcane.iris.util.scheduling.J;
import art.arcane.volmlib.util.scheduling.Looper;
import io.papermc.lib.PaperLib;
import lombok.Data;
import lombok.EqualsAndHashCode;
import lombok.Setter;
import org.bukkit.*;
import org.bukkit.entity.Entity;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.world.WorldInitEvent;
import org.bukkit.generator.BiomeProvider;
import org.bukkit.generator.BlockPopulator;
import org.bukkit.generator.ChunkGenerator;
import org.bukkit.generator.WorldInfo;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.io.File;
import java.util.List;
import java.util.Random;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicLong;
import java.util.concurrent.locks.ReentrantLock;

@EqualsAndHashCode(callSuper = true)
@Data
public class BukkitChunkGenerator extends ChunkGenerator implements PlatformChunkGenerator, Listener {
    private static final int LOAD_LOCKS = Runtime.getRuntime().availableProcessors() * 4;
    private final Semaphore loadLock;
    private final IrisWorld world;
    private final File dataLocation;
    private final String dimensionKey;
    private final ReactiveFolder folder;
    private final ReentrantLock lock = new ReentrantLock();
    private final KList<BlockPopulator> populators;
    private final ChronoLatch hotloadChecker;
    private final AtomicBoolean setup;
    private final boolean studio;
    private final AtomicInteger a = new AtomicInteger(0);
    private final CompletableFuture<Integer> spawnChunks = new CompletableFuture<>();
    private final AtomicCache<EngineTarget> targetCache = new AtomicCache<>();
    private volatile Engine engine;
    private volatile Looper hotloader;
    private volatile StudioMode lastMode;
    private volatile DummyBiomeProvider dummyBiomeProvider;
    @Setter
    private volatile StudioGenerator studioGenerator;

    public BukkitChunkGenerator(IrisWorld world, boolean studio, File dataLocation, String dimensionKey) {
        setup = new AtomicBoolean(false);
        studioGenerator = null;
        dummyBiomeProvider = new DummyBiomeProvider();
        populators = new KList<>();
        loadLock = new Semaphore(LOAD_LOCKS);
        this.world = world;
        this.hotloadChecker = new ChronoLatch(1000, false);
        this.studio = studio;
        this.dataLocation = dataLocation;
        this.dimensionKey = dimensionKey;
        this.folder = new ReactiveFolder(
                dataLocation,
                (_a, _b, _c) -> hotload(),
                new KList<>(".iob", ".json", ".kts"),
                new KList<>(".iris"),
                new KList<>(".gradle.kts")
        );
        Bukkit.getServer().getPluginManager().registerEvents(this, Iris.instance);
    }

    @EventHandler(priority = EventPriority.LOWEST)
    public void onWorldInit(WorldInitEvent event) {
        if (!world.name().equals(event.getWorld().getName())) return;
        Iris.instance.unregisterListener(this);
        world.setRawWorldSeed(event.getWorld().getSeed());
        if (initialize(event.getWorld())) return;

        Iris.warn("Failed to get Engine for " + event.getWorld().getName() + " re-trying...");
        J.s(() -> {
            if (!initialize(event.getWorld())) {
                Iris.error("Failed to get Engine for " + event.getWorld().getName() + "!");
            }
        }, 10);
    }

    private boolean initialize(World world) {
        Engine engine = getEngine(world);
        if (engine == null) return false;
        try {
            INMS.get().inject(world.getSeed(), engine, world);
            Iris.info("Injected Iris Biome Source into " + world.getName());
        } catch (Throwable e) {
            Iris.reportError(e);
            Iris.error("Failed to inject biome source into " + world.getName());
            e.printStackTrace();
        }
        spawnChunks.complete(INMS.get().getSpawnChunkCount(world));
        Iris.instance.unregisterListener(this);
        IrisWorlds.get().put(world.getName(), dimensionKey);
        return true;
    }

    @Nullable
    @Override
    public Location getFixedSpawnLocation(@NotNull World world, @NotNull Random random) {
        Location location = new Location(world, 0, 64, 0);
        PaperLib.getChunkAtAsync(location)
                .thenAccept(c -> {
                    World w = c.getWorld();
                    if (!w.getSpawnLocation().equals(location))
                        return;
                    w.setSpawnLocation(location.add(0, w.getHighestBlockYAt(location) - 64, 0));
                });
        return location;
    }

    private void setupEngine() {
        lastMode = StudioMode.NORMAL;
        engine = new IrisEngine(getTarget(), studio);
        populators.clear();
        targetCache.reset();
    }

    @NotNull
    @Override
    public EngineTarget getTarget() {
        if (engine != null) return engine.getTarget();

        return targetCache.aquire(() -> {
            IrisData data = IrisData.get(dataLocation);
            data.dump();
            data.clearLists();
            IrisDimension dimension = data.getDimensionLoader().load(dimensionKey);

            if (dimension == null) {
                Iris.error("Oh No! There's no pack in " + data.getDataFolder().getPath() + " or... there's no dimension for the key " + dimensionKey);
                IrisDimension test = IrisData.loadAnyDimension(dimensionKey, null);

                if (test != null) {
                    Iris.warn("Looks like " + dimensionKey + " exists in " + test.getLoadFile().getPath() + " ");
                    test = Iris.service(StudioSVC.class).installInto(Iris.getSender(), dimensionKey, dataLocation);
                    Iris.warn("Attempted to install into " + data.getDataFolder().getPath());

                    if (test != null) {
                        Iris.success("Woo! Patched the Engine!");
                        dimension = test;
                    } else {
                        Iris.error("Failed to patch dimension!");
                        throw new RuntimeException("Missing Dimension: " + dimensionKey);
                    }
                } else {
                    Iris.error("Nope, you don't have an installation containing " + dimensionKey + " try downloading it?");
                    throw new RuntimeException("Missing Dimension: " + dimensionKey);
                }
            }

            return new EngineTarget(world, dimension, data);
        });
    }

    @Override
    public void injectChunkReplacement(World world, int x, int z, Executor syncExecutor) {
        boolean acquired = false;
        String phase = "start";
        try {
            phase = "acquire-load-lock";
            long acquireStart = System.currentTimeMillis();
            while (!loadLock.tryAcquire(5, TimeUnit.SECONDS)) {
                Iris.warn("Chunk replacement waiting for load lock at " + x + "," + z
                        + " for " + (System.currentTimeMillis() - acquireStart) + "ms.");
            }
            acquired = true;
            long acquireWait = System.currentTimeMillis() - acquireStart;
            if (acquireWait >= 5000L) {
                Iris.warn("Chunk replacement waited " + acquireWait + "ms for load lock at " + x + "," + z + ".");
            }
            IrisBiomeStorage st = new IrisBiomeStorage();
            TerrainChunk tc = TerrainChunk.createUnsafe(world, st);
            this.world.bind(world);

            phase = "engine-generate";
            long generateStart = System.currentTimeMillis();
            boolean useMulticore = IrisSettings.get().getGenerator().useMulticore && !J.isFolia();
            AtomicBoolean generateDone = new AtomicBoolean(false);
            AtomicLong generationWatchdogStart = new AtomicLong(System.currentTimeMillis());
            Thread generateThread = Thread.currentThread();
            J.a(() -> {
                while (!generateDone.get()) {
                    if (!J.sleep(5000)) {
                        return;
                    }
                    if (generateDone.get()) {
                        return;
                    }

                    Iris.warn("Chunk replacement still generating at " + x + "," + z
                            + " for " + (System.currentTimeMillis() - generationWatchdogStart.get()) + "ms"
                            + " thread=" + generateThread.getName()
                            + " state=" + generateThread.getState());
                }
            });
            try {
                getEngine().generate(x << 4, z << 4, tc, useMulticore);
            } finally {
                generateDone.set(true);
            }
            long generateTook = System.currentTimeMillis() - generateStart;
            if (generateTook >= 5000L) {
                Iris.warn("Chunk replacement terrain generation took " + generateTook + "ms at " + x + "," + z + ".");
            }

            if (J.isFolia()) {
                phase = "folia-run-region";
                CountDownLatch latch = new CountDownLatch(1);
                Throwable[] failure = new Throwable[1];
                long regionScheduleStart = System.currentTimeMillis();
                if (!J.runRegion(world, x, z, () -> {
                    try {
                        phaseUnsafeSet("folia-region-run", x, z);
                        Chunk c = world.getChunkAt(x, z);
                        Iris.tickets.addTicket(c);
                        try {
                            for (Entity ee : c.getEntities()) {
                                if (ee instanceof Player) {
                                    continue;
                                }

                                ee.remove();
                            }

                            for (int i = getEngine().getHeight() >> 4; i >= 0; i--) {
                                int finalI = i << 4;
                                for (int xx = 0; xx < 16; xx++) {
                                    for (int yy = 0; yy < 16; yy++) {
                                        for (int zz = 0; zz < 16; zz++) {
                                            if (yy + finalI >= engine.getHeight() || yy + finalI < 0) {
                                                continue;
                                            }
                                            int y = yy + finalI + world.getMinHeight();
                                            c.getBlock(xx, y, zz).setBlockData(tc.getBlockData(xx, y, zz), false);
                                        }
                                    }
                                }
                            }

                            INMS.get().placeStructures(c);
                            engine.getWorldManager().onChunkLoad(c, true);
                        } finally {
                            Iris.tickets.removeTicket(c);
                        }
                    } catch (Throwable e) {
                        failure[0] = e;
                    } finally {
                        latch.countDown();
                    }
                })) {
                    throw new IllegalStateException("Failed to schedule region task for chunk replacement at " + x + "," + z);
                }
                long regionScheduleTook = System.currentTimeMillis() - regionScheduleStart;
                if (regionScheduleTook >= 1000L) {
                    Iris.verbose("Chunk replacement region task scheduling took " + regionScheduleTook + "ms at " + x + "," + z + ".");
                }

                long regionWaitStart = System.currentTimeMillis();
                while (!latch.await(5, TimeUnit.SECONDS)) {
                    Iris.warn("Chunk replacement waiting on region task at " + x + "," + z
                            + " for " + (System.currentTimeMillis() - regionWaitStart) + "ms.");
                }
                long regionWaitTook = System.currentTimeMillis() - regionWaitStart;
                if (regionWaitTook >= 5000L) {
                    Iris.warn("Chunk replacement region task completed after " + regionWaitTook + "ms at " + x + "," + z + ".");
                }
                if (failure[0] != null) {
                    throw failure[0];
                }
            } else {
                phase = "paperlib-async-load";
                long loadChunkStart = System.currentTimeMillis();
                Chunk c = PaperLib.getChunkAtAsync(world, x, z).get();
                long loadChunkTook = System.currentTimeMillis() - loadChunkStart;
                if (loadChunkTook >= 5000L) {
                    Iris.warn("Chunk replacement chunk load took " + loadChunkTook + "ms at " + x + "," + z + ".");
                }

                phase = "non-folia-apply";
                Iris.tickets.addTicket(c);
                CompletableFuture.runAsync(() -> {
                    for (Entity ee : c.getEntities()) {
                        if (ee instanceof Player) {
                            continue;
                        }

                        ee.remove();
                    }
                }, syncExecutor).get();

                KList<CompletableFuture<?>> futures = new KList<>(1 + getEngine().getHeight() >> 4);
                for (int i = getEngine().getHeight() >> 4; i >= 0; i--) {
                    int finalI = i << 4;
                    futures.add(CompletableFuture.runAsync(() -> {
                        for (int xx = 0; xx < 16; xx++) {
                            for (int yy = 0; yy < 16; yy++) {
                                for (int zz = 0; zz < 16; zz++) {
                                    if (yy + finalI >= engine.getHeight() || yy + finalI < 0) {
                                        continue;
                                    }
                                    int y = yy + finalI + world.getMinHeight();
                                    c.getBlock(xx, y, zz).setBlockData(tc.getBlockData(xx, y, zz), false);
                                }
                            }
                        }
                    }, syncExecutor));
                }
                futures.add(CompletableFuture.runAsync(() -> INMS.get().placeStructures(c), syncExecutor));

                CompletableFuture.allOf(futures.toArray(new CompletableFuture[0]))
                        .thenRunAsync(() -> {
                            Iris.tickets.removeTicket(c);
                            engine.getWorldManager().onChunkLoad(c, true);
                        }, syncExecutor)
                        .get();
            }

            Iris.debug("Regenerated " + x + " " + z);
        } catch (Throwable e) {
            Iris.error("======================================");
            Iris.error("Chunk replacement failed at phase=" + phase + " chunk=" + x + "," + z);
            e.printStackTrace();
            Iris.reportErrorChunk(x, z, e, "CHUNK");
            Iris.error("======================================");
            throw new IllegalStateException("Chunk replacement failed at phase=" + phase + " chunk=" + x + "," + z, e);
        } finally {
            if (acquired) {
                loadLock.release();
            }
        }
    }

    private static void phaseUnsafeSet(String phase, int x, int z) {
        Iris.verbose("Chunk replacement phase=" + phase + " chunk=" + x + "," + z);
    }

    private Engine getEngine(WorldInfo world) {
        if (setup.get()) {
            return getEngine();
        }

        lock.lock();

        try {
            if (setup.get()) {
                return getEngine();
            }


            getWorld().setRawWorldSeed(world.getSeed());
            setupEngine();
            setup.set(true);
            this.hotloader = studio ? new Looper() {
                @Override
                protected long loop() {
                    if (hotloadChecker.flip()) {
                        folder.check();
                    }

                    return 250;
                }
            } : null;

            if (studio) {
                hotloader.setPriority(Thread.MIN_PRIORITY);
                hotloader.start();
                hotloader.setName(getTarget().getWorld().name() + " Hotloader");
            }

            return engine;
        } finally {
            lock.unlock();
        }
    }

    @Override
    public void close() {
        withExclusiveControl(() -> {
            if (isStudio()) {
                hotloader.interrupt();
            }

            final Engine engine = getEngine();
            if (engine != null && !engine.isClosed())
                engine.close();
            folder.clear();
            populators.clear();

        });
    }

    @Override
    public boolean isStudio() {
        return studio;
    }

    @Override
    public void hotload() {
        if (!isStudio()) {
            return;
        }

        withExclusiveControl(() -> getEngine().hotload());
    }

    public void withExclusiveControl(Runnable r) {
        J.a(() -> {
            try {
                loadLock.acquire(LOAD_LOCKS);
                r.run();
                loadLock.release(LOAD_LOCKS);
            } catch (Throwable e) {
                Iris.reportError(e);
            }
        });
    }

    @Override
    public void touch(World world) {
        getEngine(world);
    }

    @Override
    public void generateNoise(@NotNull WorldInfo world, @NotNull Random random, int x, int z, @NotNull ChunkGenerator.ChunkData d) {
        try {
            Engine engine = getEngine(world);
            computeStudioGenerator();
            TerrainChunk tc = TerrainChunk.create(d, new IrisBiomeStorage());
            this.world.bind(world);
            if (studioGenerator != null) {
                studioGenerator.generateChunk(engine, tc, x, z);
            } else {
                ChunkDataHunkHolder blocks = new ChunkDataHunkHolder(tc);
                BiomeGridHunkHolder biomes = new BiomeGridHunkHolder(tc, tc.getMinHeight(), tc.getMaxHeight());
                engine.generate(x << 4, z << 4, blocks, biomes, IrisSettings.get().getGenerator().useMulticore);
                blocks.apply();
                biomes.apply();
            }

            Iris.debug("Generated " + x + " " + z);
        } catch (Throwable e) {
            Iris.error("======================================");
            e.printStackTrace();
            Iris.reportErrorChunk(x, z, e, "CHUNK");
            Iris.error("======================================");

            for (int i = 0; i < 16; i++) {
                for (int j = 0; j < 16; j++) {
                    d.setBlock(i, 0, j, Material.RED_GLAZED_TERRACOTTA.createBlockData());
                }
            }
        }
    }

    @Override
    public int getBaseHeight(@NotNull WorldInfo worldInfo, @NotNull Random random, int x, int z, @NotNull HeightMap heightMap) {
        return 4;
    }

    private void computeStudioGenerator() {
        if (!getEngine().getDimension().getStudioMode().equals(lastMode)) {
            lastMode = getEngine().getDimension().getStudioMode();
            getEngine().getDimension().getStudioMode().inject(this);
        }
    }

    @NotNull
    @Override
    public List<BlockPopulator> getDefaultPopulators(@NotNull World world) {
        return populators;
    }

    @Override
    public boolean isParallelCapable() {
        return true;
    }

    @Override
    public boolean shouldGenerateCaves() {
        return false;
    }

    @Override
    public boolean shouldGenerateDecorations() {
        return false;
    }

    @Override
    public boolean shouldGenerateMobs() {
        return false;
    }

    @Override
    public boolean shouldGenerateStructures() {
        return false;
    }

    @Override
    public boolean shouldGenerateNoise() {
        return false;
    }

    @Override
    public boolean shouldGenerateSurface() {
        return false;
    }

    @Override
    public boolean shouldGenerateBedrock() {
        return false;
    }

    @Nullable
    @Override
    public BiomeProvider getDefaultBiomeProvider(@NotNull WorldInfo worldInfo) {
        return dummyBiomeProvider;
    }
}
