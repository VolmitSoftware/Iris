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

package com.volmit.iris.engine;

import com.google.gson.Gson;
import com.volmit.iris.Iris;
import com.volmit.iris.core.IrisSettings;
import com.volmit.iris.core.events.IrisEngineHotloadEvent;
import com.volmit.iris.engine.actuator.IrisBiomeActuator;
import com.volmit.iris.engine.actuator.IrisDecorantActuator;
import com.volmit.iris.engine.actuator.IrisTerrainIslandActuator;
import com.volmit.iris.engine.actuator.IrisTerrainNormalActuator;
import com.volmit.iris.engine.data.cache.AtomicCache;
import com.volmit.iris.engine.framework.*;
import com.volmit.iris.engine.modifier.IrisCaveModifier;
import com.volmit.iris.engine.modifier.IrisDepositModifier;
import com.volmit.iris.engine.modifier.IrisPostModifier;
import com.volmit.iris.engine.modifier.IrisRavineModifier;
import com.volmit.iris.engine.object.biome.IrisBiome;
import com.volmit.iris.engine.object.biome.IrisBiomePaletteLayer;
import com.volmit.iris.engine.object.decoration.IrisDecorator;
import com.volmit.iris.engine.object.engine.IrisEngineData;
import com.volmit.iris.engine.object.objects.IrisObjectPlacement;
import com.volmit.iris.engine.scripting.EngineExecutionEnvironment;
import com.volmit.iris.util.context.IrisContext;
import com.volmit.iris.util.documentation.BlockCoordinates;
import com.volmit.iris.util.documentation.ChunkCoordinates;
import com.volmit.iris.util.hunk.Hunk;
import com.volmit.iris.util.io.IO;
import com.volmit.iris.util.math.RNG;
import com.volmit.iris.util.scheduling.ChronoLatch;
import com.volmit.iris.util.scheduling.J;
import com.volmit.iris.util.scheduling.PrecisionStopwatch;
import lombok.Data;
import org.bukkit.Chunk;
import org.bukkit.World;
import org.bukkit.block.Biome;
import org.bukkit.block.data.BlockData;
import org.bukkit.generator.BlockPopulator;

import java.io.File;
import java.io.IOException;
import java.util.Random;
import java.util.concurrent.atomic.AtomicBoolean;

@Data
public class IrisEngine extends BlockPopulator implements Engine {
    private final EngineCompound compound;
    private final EngineTarget target;
    private final IrisContext context;
    private final EngineEffects effects;
    private final EngineExecutionEnvironment execution;
    private final EngineWorldManager worldManager;
    private volatile int parallelism;
    private final int index;
    private final EngineMetrics metrics;
    private volatile int minHeight;
    private boolean failing;
    private boolean closed;
    private int cacheId;
    private final int art;
    private double maxBiomeObjectDensity;
    private double maxBiomeLayerDensity;
    private double maxBiomeDecoratorDensity;
    private final IrisComplex complex;
    private final EngineParallaxManager engineParallax;
    private final EngineActuator<BlockData> terrainNormalActuator;
    private final EngineActuator<BlockData> terrainIslandActuator;
    private final EngineActuator<BlockData> decorantActuator;
    private final EngineActuator<Biome> biomeActuator;
    private final EngineModifier<BlockData> depositModifier;
    private final EngineModifier<BlockData> caveModifier;
    private final EngineModifier<BlockData> ravineModifier;
    private final EngineModifier<BlockData> postModifier;
    private final AtomicCache<IrisEngineData> engineData = new AtomicCache<>();
    private final AtomicBoolean cleaning;
    private final ChronoLatch cleanLatch;

    public IrisEngine(EngineTarget target, EngineCompound compound, int index) {
        execution = new IrisExecutionEnvironment(this);
        Iris.info("Initializing Engine: " + target.getWorld().name() + "/" + target.getDimension().getLoadKey() + " (" + target.getHeight() + " height)");
        metrics = new EngineMetrics(32);
        this.target = target;
        getData().setEngine(this);
        getEngineData();
        worldManager = new IrisWorldManager(this);
        this.compound = compound;
        minHeight = 0;
        failing = false;
        closed = false;
        this.index = index;
        cacheId = RNG.r.nextInt();
        effects = new IrisEngineEffects(this);
        art = J.ar(effects::tickRandomPlayer, 0);
        J.a(this::computeBiomeMaxes);
        Iris.callEvent(new IrisEngineHotloadEvent(this));
        context = new IrisContext(this);
        context.touch();
        this.complex = new IrisComplex(this);
        this.engineParallax = new IrisEngineParallax(this);
        this.terrainNormalActuator = new IrisTerrainNormalActuator(this);
        this.terrainIslandActuator = new IrisTerrainIslandActuator(this);
        this.decorantActuator = new IrisDecorantActuator(this);
        this.biomeActuator = new IrisBiomeActuator(this);
        this.depositModifier = new IrisDepositModifier(this);
        this.ravineModifier = new IrisRavineModifier(this);
        this.caveModifier = new IrisCaveModifier(this);
        this.postModifier = new IrisPostModifier(this);
        cleaning = new AtomicBoolean(false);
        cleanLatch = new ChronoLatch(Math.max(10000, Math.min(IrisSettings.get().getParallax()
                .getParallaxChunkEvictionMS(), IrisSettings.get().getParallax().getParallaxRegionEvictionMS())));

    }

    @Override
    public IrisEngineData getEngineData() {
        World w = null;

        return engineData.aquire(() -> {
            File f = new File(getWorld().worldFolder(), "iris/engine-data/" + getDimension().getLoadKey() + "-" + getIndex() + ".json");

            if (!f.exists()) {
                try {
                    f.getParentFile().mkdirs();
                    IO.writeAll(f, new Gson().toJson(new IrisEngineData()));
                } catch (IOException e) {
                    e.printStackTrace();
                }
            }

            try {
                return new Gson().fromJson(IO.readAll(f), IrisEngineData.class);
            } catch (Throwable e) {
                e.printStackTrace();
            }

            return new IrisEngineData();
        });
    }

    private void computeBiomeMaxes() {
        for (IrisBiome i : getDimension().getAllBiomes(this)) {
            double density = 0;

            for (IrisObjectPlacement j : i.getObjects()) {
                density += j.getDensity() * j.getChance();
            }

            maxBiomeObjectDensity = Math.max(maxBiomeObjectDensity, density);
            density = 0;

            for (IrisDecorator j : i.getDecorators()) {
                density += Math.max(j.getStackMax(), 1) * j.getChance();
            }

            maxBiomeDecoratorDensity = Math.max(maxBiomeDecoratorDensity, density);
            density = 0;

            for (IrisBiomePaletteLayer j : i.getLayers()) {
                density++;
            }

            maxBiomeLayerDensity = Math.max(maxBiomeLayerDensity, density);
        }
    }

    @Override
    public void close() {
        J.car(art);
        closed = true;
        getWorldManager().close();
        getTarget().close();
        saveEngineData();
        getEngineParallax().close();
        getTerrainActuator().close();
        getDecorantActuator().close();
        getBiomeActuator().close();
        getDepositModifier().close();
        getRavineModifier().close();
        getCaveModifier().close();
        getPostModifier().close();
    }

    @Override
    public boolean isClosed() {
        return closed;
    }

    @Override
    public void recycle() {
        if (!cleanLatch.flip()) {
            return;
        }

        if (cleaning.get()) {
            cleanLatch.flipDown();
            return;
        }

        cleaning.set(true);

        try {
            getParallax().cleanup();
            getData().getObjectLoader().clean();
        } catch (Throwable e) {
            Iris.reportError(e);
            Iris.error("Cleanup failed!");
            e.printStackTrace();
        }

        cleaning.lazySet(false);
    }


    public EngineActuator<BlockData> getTerrainActuator() {
        return switch (getDimension().getTerrainMode()) {
            case NORMAL -> getTerrainNormalActuator();
            case ISLANDS -> getTerrainIslandActuator();
        };
    }

    @BlockCoordinates
    @Override
    public double modifyX(double x) {
        return x / getDimension().getTerrainZoom();
    }

    @BlockCoordinates
    @Override
    public double modifyZ(double z) {
        return z / getDimension().getTerrainZoom();
    }

    @ChunkCoordinates
    @Override
    public void generate(int x, int z, Hunk<BlockData> vblocks, Hunk<Biome> vbiomes, boolean multicore) {
        context.touch();
        getEngineData().getStatistics().generatedChunk();
        try {
            PrecisionStopwatch p = PrecisionStopwatch.start();
            Hunk<BlockData> blocks = vblocks.listen((xx, y, zz, t) -> catchBlockUpdates(x + xx, y + getMinHeight(), z + zz, t));

            switch (getDimension().getTerrainMode()) {
                case NORMAL -> {
                    getEngineParallax().generateParallaxArea(x >> 4, z >> 4);
                    getTerrainActuator().actuate(x, z, vblocks, multicore);
                    getBiomeActuator().actuate(x, z, vbiomes, multicore);
                    getCaveModifier().modify(x, z, vblocks, multicore);
                    getRavineModifier().modify(x, z, vblocks, multicore);
                    getPostModifier().modify(x, z, vblocks, multicore);
                    getDecorantActuator().actuate(x, z, blocks, multicore);
                    getEngineParallax().insertParallax(x >> 4, z >> 4, blocks);
                    getDepositModifier().modify(x, z, blocks, multicore);
                }
                case ISLANDS -> {
                    getTerrainActuator().actuate(x, z, vblocks, multicore);
                }
            }

            getMetrics().getTotal().put(p.getMilliseconds());
        } catch (Throwable e) {
            Iris.reportError(e);
            fail("Failed to generate " + x + ", " + z, e);
        }
    }

    @Override
    public void saveEngineData() {
        File f = new File(getWorld().worldFolder(), "iris/engine-data/" + getDimension().getLoadKey() + "-" + getIndex() + ".json");
        f.getParentFile().mkdirs();
        try {
            IO.writeAll(f, new Gson().toJson(getEngineData()));
            Iris.debug("Saved Engine Data");
        } catch (IOException e) {
            Iris.error("Failed to save Engine Data");
            e.printStackTrace();
        }
    }

    @Override
    public IrisBiome getFocus() {
        if (getDimension().getFocus() == null || getDimension().getFocus().trim().isEmpty()) {
            return null;
        }

        return getData().getBiomeLoader().load(getDimension().getFocus());
    }

    @ChunkCoordinates
    @Override
    public void populate(World world, Random random, Chunk c) {
        updateChunk(c);
        placeTiles(c);
    }

    @Override
    public void fail(String error, Throwable e) {
        failing = true;
        Iris.error(error);
        e.printStackTrace();
    }

    @Override
    public boolean hasFailed() {
        return failing;
    }

    @Override
    public int getCacheID() {
        return cacheId;
    }

    @Override
    public void hotload() {
        Iris.callEvent(new IrisEngineHotloadEvent(this));
        getEngineData().getStatistics().hotloaded();
        cacheId = RNG.r.nextInt();
    }
}
