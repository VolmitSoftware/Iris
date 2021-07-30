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

import com.volmit.iris.Iris;
import com.volmit.iris.core.IrisDataManager;
import com.volmit.iris.core.IrisSettings;
import com.volmit.iris.engine.framework.Engine;
import com.volmit.iris.engine.framework.EngineCompound;
import com.volmit.iris.engine.framework.EngineData;
import com.volmit.iris.engine.framework.EngineTarget;
import com.volmit.iris.engine.hunk.Hunk;
import com.volmit.iris.engine.object.IrisDimension;
import com.volmit.iris.engine.object.IrisDimensionIndex;
import com.volmit.iris.engine.object.IrisPosition;
import com.volmit.iris.engine.object.common.IrisWorld;
import com.volmit.iris.engine.parallel.MultiBurst;
import com.volmit.iris.util.atomics.AtomicRollingSequence;
import com.volmit.iris.util.collection.KList;
import com.volmit.iris.util.collection.KMap;
import com.volmit.iris.util.documentation.BlockCoordinates;
import com.volmit.iris.util.documentation.ChunkCoordinates;
import com.volmit.iris.util.format.C;
import com.volmit.iris.util.format.Form;
import com.volmit.iris.util.scheduling.PrecisionStopwatch;
import lombok.Getter;
import lombok.Setter;
import org.bukkit.block.Biome;
import org.bukkit.block.data.BlockData;
import org.bukkit.command.CommandSender;
import org.bukkit.event.EventHandler;
import org.bukkit.event.world.WorldSaveEvent;
import org.bukkit.generator.BlockPopulator;

import java.io.File;
import java.util.List;

public class IrisEngineCompound implements EngineCompound {
    @Getter
    private final IrisWorld world;

    private final AtomicRollingSequence wallClock;

    private Engine defaultEngine;

    @Getter
    private final EngineData engineMetadata;

    private final Engine[] engines;

    @Getter
    private final MultiBurst burster;

    @Getter
    private final KList<BlockPopulator> populators;

    @Getter
    private final IrisDimension rootDimension;

    @Getter
    private final int threadCount = -1;

    @Getter
    @Setter
    private boolean studio;

    public IrisEngineCompound(IrisWorld world, IrisDimension rootDimension, IrisDataManager data, int maximumThreads) {
        wallClock = new AtomicRollingSequence(32);
        this.rootDimension = rootDimension;
        Iris.info("Initializing Engine Composite for " + world.name());
        this.world = world;
        engineMetadata = EngineData.load(getEngineMetadataFile());
        engineMetadata.setDimension(rootDimension.getLoadKey());
        engineMetadata.setLastVersion(Iris.instance.getDescription().getVersion());

        saveEngineMetadata();
        populators = new KList<>();

        if (rootDimension.getDimensionalComposite().isEmpty()) {
            burster = null;
            // TODO: WARNING HEIGHT
            engines = new Engine[]{new IrisEngine(new EngineTarget(world, rootDimension, data, 256, maximumThreads), this, 0)};
            defaultEngine = engines[0];
        } else {
            double totalWeight = 0D;
            engines = new Engine[rootDimension.getDimensionalComposite().size()];
            burster = engines.length > 1 ? new MultiBurst("Iris Compound " + rootDimension.getName(), IrisSettings.get().getConcurrency().getEngineThreadPriority(), engines.length) : null;
            int threadDist = (Math.max(2, maximumThreads - engines.length)) / engines.length;

            if ((threadDist * engines.length) + engines.length > maximumThreads) {
                Iris.warn("Using " + ((threadDist * engines.length) + engines.length) + " threads instead of the configured " + maximumThreads + " maximum thread count due to the requirements of this dimension!");
            }

            for (IrisDimensionIndex i : rootDimension.getDimensionalComposite()) {
                totalWeight += i.getWeight();
            }

            int buf = 0;

            for (int i = 0; i < engines.length; i++) {
                IrisDimensionIndex index = rootDimension.getDimensionalComposite().get(i);
                IrisDimension dimension = data.getDimensionLoader().load(index.getDimension());
                // TODO: WARNING HEIGHT
                engines[i] = new IrisEngine(new EngineTarget(world, dimension, data.copy(), (int) Math.floor(256D * (index.getWeight() / totalWeight)), index.isInverted(), threadDist), this, i);
                engines[i].setMinHeight(buf);
                buf += engines[i].getHeight();

                if (index.isPrimary()) {
                    defaultEngine = engines[i];
                }
            }

            if (defaultEngine == null) {
                defaultEngine = engines[0];
            }
        }

        for (Engine i : engines) {
            if (i instanceof BlockPopulator) {
                populators.add((BlockPopulator) i);
            }
        }

        Iris.instance.registerListener(this);
    }

    public List<IrisPosition> getStrongholdPositions() {
        return engineMetadata.getStrongholdPositions();
    }

    @EventHandler
    public void on(WorldSaveEvent e) {
        if (world != null && e.getWorld().equals(world)) {
            save();
        }
    }

    public void printMetrics(CommandSender sender) {
        KMap<String, Double> totals = new KMap<>();
        KMap<String, Double> weights = new KMap<>();
        double masterWallClock = wallClock.getAverage();

        for (int i = 0; i < getSize(); i++) {
            Engine e = getEngine(i);
            KMap<String, Double> timings = e.getMetrics().pull();
            double totalWeight = 0;
            double wallClock = e.getMetrics().getTotal().getAverage();

            for (double j : timings.values()) {
                totalWeight += j;
            }

            for (String j : timings.k()) {
                weights.put(e.getName() + "[" + e.getIndex() + "]." + j, (wallClock / totalWeight) * timings.get(j));
            }

            totals.put(e.getName() + "[" + e.getIndex() + "]", wallClock);
        }

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

    private File getEngineMetadataFile() {
        return new File(world.worldFolder(), "iris/engine-metadata.json");
    }

    @ChunkCoordinates
    @Override
    public void generate(int x, int z, Hunk<BlockData> blocks, Hunk<BlockData> postblocks, Hunk<Biome> biomes, boolean multicore) {
        recycle();
        PrecisionStopwatch p = PrecisionStopwatch.start();
        if (engines.length == 1 && !getEngine(0).getTarget().isInverted()) {
            engines[0].generate(x, z, blocks, biomes, multicore);
        } else {
            int i;
            int offset = 0;

            for (i = 0; i < engines.length; i++) {
                Engine engine = engines[i];
                int doffset = offset;
                int height = engine.getTarget().getHeight();
                Hunk<BlockData> cblock = Hunk.newArrayHunk(16, height, 16);
                Hunk<Biome> cbiome = Hunk.newArrayHunk(16, height, 16);

                if (engine.getTarget().isInverted()) {
                    cblock = cblock.invertY();
                    cbiome = cbiome.invertY();
                }

                engine.generate(x, z, cblock, cbiome, multicore);
                blocks.insert(0, doffset, 0, cblock);
                biomes.insert(0, doffset, 0, cbiome);
                offset += height;
            }
        }

        wallClock.put(p.getMilliseconds());
    }

    @Override
    public int getSize() {
        return engines.length;
    }

    @Override
    public Engine getEngine(int index) {
        return engines[index];
    }

    @Override
    public void saveEngineMetadata() {
        engineMetadata.save(getEngineMetadataFile());
    }

    @BlockCoordinates
    @Override
    public IrisDataManager getData(int height) {
        return getEngineForHeight(height).getData();
    }

    //TODO: FAIL
    @Override
    public boolean isFailing() {
        return false;
    }

    @Override
    public Engine getDefaultEngine() {
        return defaultEngine;
    }

    @Override
    public void hotload() {
        for (int i = 0; i < getSize(); i++) {
            getEngine(i).hotload();
        }
    }
}
