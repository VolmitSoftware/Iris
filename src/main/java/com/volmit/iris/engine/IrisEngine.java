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

import com.google.common.util.concurrent.AtomicDouble;
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
import com.volmit.iris.util.atomics.AtomicRollingSequence;
import com.volmit.iris.util.collection.KList;
import com.volmit.iris.util.collection.KMap;
import com.volmit.iris.util.context.IrisContext;
import com.volmit.iris.util.documentation.BlockCoordinates;
import com.volmit.iris.util.documentation.ChunkCoordinates;
import com.volmit.iris.util.format.C;
import com.volmit.iris.util.format.Form;
import com.volmit.iris.util.hunk.Hunk;
import com.volmit.iris.util.hunk.storage.AtomicDoubleHunk;
import com.volmit.iris.util.hunk.storage.AtomicLongHunk;
import com.volmit.iris.util.io.IO;
import com.volmit.iris.util.math.M;
import com.volmit.iris.util.math.RNG;
import com.volmit.iris.util.scheduling.ChronoLatch;
import com.volmit.iris.util.scheduling.J;
import com.volmit.iris.util.scheduling.PrecisionStopwatch;
import lombok.Data;
import lombok.EqualsAndHashCode;
import org.bukkit.Chunk;
import org.bukkit.World;
import org.bukkit.block.Biome;
import org.bukkit.block.data.BlockData;
import org.bukkit.command.CommandSender;
import org.bukkit.generator.BlockPopulator;

import java.io.File;
import java.io.IOException;
import java.util.Random;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicLong;

@EqualsAndHashCode(callSuper = true)
@Data
public class IrisEngine extends BlockPopulator implements Engine {
    // TODO: Remove block population, stop using bukkit
    private final AtomicInteger generated;
    private final AtomicInteger generatedLast;
    private final AtomicDouble perSecond;
    private final AtomicLong lastGPS;
    private final EngineTarget target;
    private final IrisContext context;
    private final EngineEffects effects;
    private final ChronoLatch perSecondLatch;
    private final EngineExecutionEnvironment execution;
    private final EngineWorldManager worldManager;
    private volatile int parallelism;
    private final EngineMetrics metrics;
    private volatile int minHeight;
    private final boolean studio;
    private boolean failing;
    private boolean closed;
    private int cacheId;
    private final AtomicRollingSequence wallClock;
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

    public IrisEngine(EngineTarget target, boolean studio) {
        this.studio = studio;
        generatedLast = new AtomicInteger(0);
        perSecond = new AtomicDouble(0);
        perSecondLatch = new ChronoLatch(1000, false);
        wallClock = new AtomicRollingSequence(32);
        lastGPS = new AtomicLong(M.ms());
        generated = new AtomicInteger(0);
        execution = new IrisExecutionEnvironment(this);
        // TODO: HEIGHT ------------------------------------------------------------------------------------------------------>
        Iris.info("Initializing Engine: " + target.getWorld().name() + "/" + target.getDimension().getLoadKey() + " (" + 256+ " height)");
        metrics = new EngineMetrics(32);
        this.target = target;
        getData().setEngine(this);
        getEngineData();
        worldManager = new IrisWorldManager(this);
        minHeight = 0;
        failing = false;
        closed = false;
        cacheId = RNG.r.nextInt();
        effects = new IrisEngineEffects(this);
        art = J.ar(effects::tickRandomPlayer, 0);
        J.a(this::computeBiomeMaxes);
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
            //TODO: Method this file
            File f = new File(getWorld().worldFolder(), "iris/engine-data/" + getDimension().getLoadKey() + ".json");

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

    @Override
    public int getGenerated() {
        return generated.get();
    }

    @Override
    public double getGeneratedPerSecond() {
        if(perSecondLatch.flip())
        {
            double g = generated.get() - generatedLast.get();
            generatedLast.set(generated.get());

            if(g == 0)
            {
                return 0;
            }

            long dur = M.ms() - lastGPS.get();
            lastGPS.set(M.ms());
            perSecond.set(1000D / ((double)dur / g));
        }

        return perSecond.get();
    }

    @Override
    public boolean isStudio() {
        return studio;
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


    public void printMetrics(CommandSender sender) {
        KMap<String, Double> totals = new KMap<>();
        KMap<String, Double> weights = new KMap<>();
        double masterWallClock = wallClock.getAverage();
        KMap<String, Double> timings = getMetrics().pull();
        double totalWeight = 0;
        double wallClock = getMetrics().getTotal().getAverage();

        for (double j : timings.values()) {
            totalWeight += j;
        }

        for (String j : timings.k()) {
            weights.put(getName() + "."  + j, (wallClock / totalWeight) * timings.get(j));
        }

        totals.put(getName(), wallClock);

        double mtotals = 0;

        for (double i : totals.values()) {
            mtotals += i;
        }

        for (String i : totals.k()) {
            totals.put(i, (masterWallClock / mtotals) * totals.get(i));
        }

        double v = 0;

        for (double i : weights.values()) {
            v += i;
        }

        for (String i : weights.k()) {
            weights.put(i, weights.get(i) / v);
        }

        sender.sendMessage("Total: " + C.BOLD + C.WHITE + Form.duration(masterWallClock, 0));

        for (String i : totals.k()) {
            sender.sendMessage("  Engine " + C.UNDERLINE + C.GREEN + i + C.RESET + ": " + C.BOLD + C.WHITE + Form.duration(totals.get(i), 0));
        }

        sender.sendMessage("Details: ");

        for (String i : weights.sortKNumber().reverse()) {
            String befb = C.UNDERLINE + "" + C.GREEN + "" + i.split("\\Q[\\E")[0] + C.RESET + C.GRAY + "[";
            String num = C.GOLD + i.split("\\Q[\\E")[1].split("]")[0] + C.RESET + C.GRAY + "].";
            String afb = C.ITALIC + "" + C.AQUA + i.split("\\Q]\\E")[1].substring(1) + C.RESET + C.GRAY;

            sender.sendMessage("  " + befb + num + afb + ": " + C.BOLD + C.WHITE + Form.pc(weights.get(i), 0));
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

    @BlockCoordinates
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
            generated.incrementAndGet();
        } catch (Throwable e) {
            Iris.reportError(e);
            fail("Failed to generate " + x + ", " + z, e);
        }
    }

    @Override
    public void saveEngineData() {
        //TODO: Method this file
        File f = new File(getWorld().worldFolder(), "iris/engine-data/" + getDimension().getLoadKey() + ".json");
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

    // TODO: Remove block population
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
}
