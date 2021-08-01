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
import com.volmit.iris.engine.cache.AtomicCache;
import com.volmit.iris.engine.framework.*;
import com.volmit.iris.engine.hunk.Hunk;
import com.volmit.iris.engine.object.*;
import com.volmit.iris.engine.object.engine.IrisEngineData;
import com.volmit.iris.util.collection.KList;
import com.volmit.iris.util.collection.KMap;
import com.volmit.iris.util.documentation.BlockCoordinates;
import com.volmit.iris.util.documentation.ChunkCoordinates;
import com.volmit.iris.util.format.C;
import com.volmit.iris.util.format.Form;
import com.volmit.iris.util.io.IO;
import com.volmit.iris.util.math.RNG;
import com.volmit.iris.util.scheduling.J;
import com.volmit.iris.util.scheduling.PrecisionStopwatch;
import lombok.Getter;
import lombok.Setter;
import org.bukkit.Chunk;
import org.bukkit.World;
import org.bukkit.block.Biome;
import org.bukkit.block.data.BlockData;
import org.bukkit.generator.BlockPopulator;
import org.jetbrains.annotations.NotNull;

import java.io.File;
import java.io.IOException;
import java.util.Random;

public class IrisEngine extends BlockPopulator implements Engine {
    @Getter
    private final EngineCompound compound;

    @Getter
    private final EngineTarget target;

    @Getter
    private final EngineFramework framework;

    @Getter
    private final EngineEffects effects;

    @Getter
    private final EngineWorldManager worldManager;

    @Setter
    @Getter
    private volatile int parallelism;

    @Getter
    private final int index;

    @Getter
    private final EngineMetrics metrics;

    @Setter
    @Getter
    private volatile int minHeight;
    private boolean failing;
    private boolean closed;
    private int cacheId;
    private final int art;

    @Getter
    private double maxBiomeObjectDensity;

    @Getter
    private double maxBiomeLayerDensity;

    @Getter
    private double maxBiomeDecoratorDensity;

    private AtomicCache<IrisEngineData> engineData = new AtomicCache<>();

    public IrisEngine(EngineTarget target, EngineCompound compound, int index) {
        Iris.info("Initializing Engine: " + target.getWorld().name() + "/" + target.getDimension().getLoadKey() + " (" + target.getHeight() + " height)");
        metrics = new EngineMetrics(32);
        getEngineData();
        this.target = target;
        this.framework = new IrisEngineFramework(this);
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
    }

    @Override
    public IrisEngineData getEngineData() {
        return engineData.aquire(() -> {
            File f = new File(getWorld().worldFolder(), "iris/engine-data/" + getDimension().getLoadKey() + "-" + getIndex() + ".json");

            if(!f.exists())
            {
                try {
                    f.getParentFile().mkdirs();
                    IO.writeAll(f, new Gson().toJson(new IrisEngineData()));
                } catch (IOException e) {
                    e.printStackTrace();
                }
            }

            try
            {
                return new Gson().fromJson(IO.readAll(f), IrisEngineData.class);
            }

            catch(Throwable e)
            {
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
        getFramework().close();
        getTarget().close();
        saveEngineData();
    }

    @Override
    public boolean isClosed() {
        return closed;
    }

    @Override
    public void recycle() {
        getFramework().recycle();
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
        getEngineData().getStatistics().generatedChunk();
        try {
            PrecisionStopwatch p = PrecisionStopwatch.start();
            Hunk<BlockData> blocks = vblocks.listen((xx, y, zz, t) -> catchBlockUpdates(x + xx, y + getMinHeight(), z + zz, t));

            switch (getDimension().getTerrainMode()) {
                case NORMAL -> {
                    getFramework().getEngineParallax().generateParallaxArea(x >> 4, z >> 4);
                    getFramework().getTerrainActuator().actuate(x, z, vblocks, multicore);
                    getFramework().getBiomeActuator().actuate(x, z, vbiomes, multicore);
                    getFramework().getCaveModifier().modify(x, z, vblocks, multicore);
                    getFramework().getRavineModifier().modify(x, z, vblocks, multicore);
                    getFramework().getPostModifier().modify(x, z, vblocks, multicore);
                    getFramework().getDecorantActuator().actuate(x, z, blocks, multicore);
                    getFramework().getEngineParallax().insertParallax(x >> 4, z >> 4, blocks);
                    getFramework().getDepositModifier().modify(x, z, blocks, multicore);
                }
                case ISLANDS -> {
                    getFramework().getTerrainActuator().actuate(x, z, vblocks, multicore);
                }
            }

            getMetrics().getTotal().put(p.getMilliseconds());

            if (IrisSettings.get().getGeneral().isDebug()) {
                KList<String> v = new KList<>();
                KMap<String, Double> g = getMetrics().pull();

                for (String i : g.sortKNumber()) {
                    if (g.get(i) != null) {
                        v.add(C.RESET + "" + C.LIGHT_PURPLE + i + ": " + C.UNDERLINE + C.BLUE + Form.duration(g.get(i), 0) + C.RESET + C.GRAY + "");
                    }
                }

                Iris.debug(v.toString(", "));
            }
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
        } catch (IOException e) {
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

    @Override
    public void hotloading() {
        getEngineData().getStatistics().hotloaded();
        close();
    }

    @ChunkCoordinates
    @Override
    public void populate(@NotNull World world, @NotNull Random random, @NotNull Chunk c) {
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
        cacheId = RNG.r.nextInt();
    }
}
