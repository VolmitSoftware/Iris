/*
 * Iris is a World Generator for Minecraft Bukkit Servers
 * Copyright (c) 2021 Arcane Arts (Volmit Software)
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

package com.volmit.iris.engine.platform;

import com.volmit.iris.Iris;
import com.volmit.iris.core.loader.IrisData;
import com.volmit.iris.core.service.StudioSVC;
import com.volmit.iris.engine.IrisEngine;
import com.volmit.iris.engine.data.chunk.TerrainChunk;
import com.volmit.iris.engine.framework.Engine;
import com.volmit.iris.engine.framework.EngineTarget;
import com.volmit.iris.engine.object.IrisDimension;
import com.volmit.iris.engine.object.IrisWorld;
import com.volmit.iris.engine.object.StudioMode;
import com.volmit.iris.engine.platform.studio.StudioGenerator;
import com.volmit.iris.util.collection.KList;
import com.volmit.iris.util.data.IrisBiomeStorage;
import com.volmit.iris.util.hunk.Hunk;
import com.volmit.iris.util.io.ReactiveFolder;
import com.volmit.iris.util.scheduling.ChronoLatch;
import com.volmit.iris.util.scheduling.J;
import com.volmit.iris.util.scheduling.Looper;
import lombok.Data;
import lombok.EqualsAndHashCode;
import lombok.Setter;
import org.bukkit.Bukkit;
import org.bukkit.Chunk;
import org.bukkit.Material;
import org.bukkit.World;
import org.bukkit.block.Biome;
import org.bukkit.block.data.BlockData;
import org.bukkit.entity.Entity;
import org.bukkit.entity.Player;
import org.bukkit.generator.BiomeProvider;
import org.bukkit.generator.BlockPopulator;
import org.bukkit.generator.ChunkGenerator;
import org.bukkit.generator.WorldInfo;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.io.File;
import java.util.List;
import java.util.Random;
import java.util.concurrent.Semaphore;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.function.Consumer;

@EqualsAndHashCode(callSuper = true)
@Data
public class BukkitChunkGenerator extends ChunkGenerator implements PlatformChunkGenerator {
    private static final int LOAD_LOCKS = 1_000_000;
    private final Semaphore loadLock;
    private final IrisWorld world;
    private final File dataLocation;
    private final String dimensionKey;
    private final ReactiveFolder folder;
    private final KList<BlockPopulator> populators;
    private final ChronoLatch hotloadChecker;
    private final AtomicBoolean setup;
    private final boolean studio;
    private Engine engine;
    private Looper hotloader;
    private StudioMode lastMode;
    @Setter
    private StudioGenerator studioGenerator;

    public BukkitChunkGenerator(IrisWorld world, boolean studio, File dataLocation, String dimensionKey) {
        setup = new AtomicBoolean(false);
        studioGenerator = null;
        populators = new KList<>();
        loadLock = new Semaphore(LOAD_LOCKS);
        this.world = world;
        this.hotloadChecker = new ChronoLatch(1000, false);
        this.studio = studio;
        this.dataLocation = dataLocation;
        this.dimensionKey = dimensionKey;
        this.folder = new ReactiveFolder(dataLocation, (_a, _b, _c) -> hotload());
    }

    private void setupEngine() {
        IrisData data = IrisData.get(dataLocation);
        IrisDimension dimension = data.getDimensionLoader().load(dimensionKey);

        if (dimension == null) {
            Iris.error("Oh No! There's no pack in " + data.getDataFolder().getPath() + " or... there's no dimension for the key " + dimensionKey);
            IrisDimension test = IrisData.loadAnyDimension(dimensionKey);

            if (test != null) {
                Iris.warn("Looks like " + dimensionKey + " exists in " + test.getLoadFile().getPath() + " ");
                Iris.service(StudioSVC.class).installIntoWorld(Iris.getSender(), dimensionKey, dataLocation.getParentFile().getParentFile());
                Iris.warn("Attempted to install into " + data.getDataFolder().getPath());
                data.dump();
                data.clearLists();
                test = data.getDimensionLoader().load(dimensionKey);

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

        lastMode = StudioMode.NORMAL;
        engine = new IrisEngine(new EngineTarget(world, dimension, data), studio);
        populators.clear();
    }

    @Override
    public boolean isHeadless() {
        return false;
    }

    @Override
    public void injectChunkReplacement(World world, int x, int z, Consumer<Runnable> jobs) {
        try {
            loadLock.acquire();
            IrisBiomeStorage st = new IrisBiomeStorage();
            TerrainChunk tc = TerrainChunk.createUnsafe(world, st);
            Hunk<BlockData> blocks = Hunk.view((ChunkData) tc);
            Hunk<Biome> biomes = Hunk.view((BiomeGrid) tc);
            this.world.bind(world);
            getEngine().generate(x << 4, z << 4, blocks, biomes, true);
            Iris.debug("Regenerated " + x + " " + z);
            int t = 0;
            for (int i = getEngine().getHeight() >> 4; i >= 0; i--) {
                if (!world.isChunkLoaded(x, z)) {
                    continue;
                }

                Chunk c = world.getChunkAt(x, z);
                for (Entity ee : c.getEntities()) {
                    if (ee instanceof Player) {
                        continue;
                    }

                    J.s(ee::remove);
                }

                J.s(() -> engine.getWorldManager().onChunkLoad(c, false));

                int finalI = i;
                jobs.accept(() -> {

                    for (int xx = 0; xx < 16; xx++) {
                        for (int yy = 0; yy < 16; yy++) {
                            for (int zz = 0; zz < 16; zz++) {
                                if (yy + (finalI << 4) >= engine.getHeight() || yy + (finalI << 4) < 0) {
                                    continue;
                                }

                                c.getBlock(xx, yy + (finalI << 4), zz).setBlockData(tc.getBlockData(xx, yy + (finalI << 4), zz), false);
                            }
                        }
                    }
                });
            }

            loadLock.release();
        } catch (Throwable e) {
            loadLock.release();
            Iris.error("======================================");
            e.printStackTrace();
            Iris.reportErrorChunk(x, z, e, "CHUNK");
            Iris.error("======================================");

            ChunkData d = Bukkit.createChunkData(world);

            for (int i = 0; i < 16; i++) {
                for (int j = 0; j < 16; j++) {
                    d.setBlock(i, 0, j, Material.RED_GLAZED_TERRACOTTA.createBlockData());
                }
            }
        }
    }

    private Engine getEngine(World world) {
        if (setup.get()) {
            return getEngine();
        }

        synchronized (this) {
            getWorld().setRawWorldSeed(world.getSeed());
            setupEngine();
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

            setup.set(true);
        }

        return engine;
    }

    @Override
    public void close() {
        withExclusiveControl(() -> {
            if (isStudio()) {
                hotloader.interrupt();
            }

            getEngine().close();
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
    public @NotNull ChunkData generateChunkData(@NotNull World world, @NotNull Random ignored, int x, int z, @NotNull BiomeGrid biome) {
        try {
            getEngine(world);
            loadLock.acquire();
            computeStudioGenerator();
            TerrainChunk tc = TerrainChunk.create(world, biome);
            this.world.bind(world);

            if (studioGenerator != null) {
                studioGenerator.generateChunk(getEngine(), tc, x, z);
            } else {
                Hunk<BlockData> blocks = Hunk.view((ChunkData) tc);
                Hunk<Biome> biomes = Hunk.view((BiomeGrid) tc);
                getEngine().generate(x << 4, z << 4, blocks, biomes, true);
            }

            ChunkData c = tc.getRaw();
            Iris.debug("Generated " + x + " " + z);
            loadLock.release();
            return c;
        } catch (Throwable e) {
            loadLock.release();
            Iris.error("======================================");
            e.printStackTrace();
            Iris.reportErrorChunk(x, z, e, "CHUNK");
            Iris.error("======================================");

            ChunkData d = Bukkit.createChunkData(world);

            for (int i = 0; i < 16; i++) {
                for (int j = 0; j < 16; j++) {
                    d.setBlock(i, 0, j, Material.RED_GLAZED_TERRACOTTA.createBlockData());
                }
            }

            return d;
        }
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
        return null;
    }
}
