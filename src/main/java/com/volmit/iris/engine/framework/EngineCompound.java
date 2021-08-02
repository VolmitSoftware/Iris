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

package com.volmit.iris.engine.framework;

import com.volmit.iris.Iris;
import com.volmit.iris.core.project.loader.IrisData;
import com.volmit.iris.engine.actuator.IrisTerrainNormalActuator;
import com.volmit.iris.engine.data.DataProvider;
import com.volmit.iris.engine.hunk.Hunk;
import com.volmit.iris.engine.object.IrisBiome;
import com.volmit.iris.engine.object.IrisDimension;
import com.volmit.iris.engine.object.IrisPosition;
import com.volmit.iris.engine.object.common.IrisWorld;
import com.volmit.iris.engine.parallel.MultiBurst;
import com.volmit.iris.util.collection.KList;
import com.volmit.iris.util.collection.KMap;
import org.bukkit.block.Biome;
import org.bukkit.block.data.BlockData;
import org.bukkit.command.CommandSender;
import org.bukkit.event.Listener;
import org.bukkit.generator.BlockPopulator;

import java.util.List;

public interface EngineCompound extends Listener, Hotloadable, DataProvider {
    IrisDimension getRootDimension();

    void generate(int x, int z, Hunk<BlockData> blocks, Hunk<BlockData> postblocks, Hunk<Biome> biomes, boolean multicore);

    IrisWorld getWorld();

    List<IrisPosition> getStrongholdPositions();

    void printMetrics(CommandSender sender);

    int getSize();

    default int getHeight() {
        // TODO: WARNING HEIGHT
        return 256;
    }

    Engine getEngine(int index);

    MultiBurst getBurster();

    EngineData getEngineMetadata();

    void saveEngineMetadata();

    KList<BlockPopulator> getPopulators();

    default Engine getEngineForHeight(int height) {
        if (getSize() == 1) {
            return getEngine(0);
        }

        int buf = 0;

        for (int i = 0; i < getSize(); i++) {
            Engine e = getEngine(i);
            buf += e.getHeight();

            if (buf >= height) {
                return e;
            }
        }

        return getEngine(getSize() - 1);
    }

    default void recycle() {
        for (int i = 0; i < getSize(); i++) {
            getEngine(i).recycle();
        }
    }

    default void save() {
        saveEngineMetadata();
        for (int i = 0; i < getSize(); i++) {
            getEngine(i).save();
        }
    }

    default void saveNOW() {
        saveEngineMetadata();
        for (int i = 0; i < getSize(); i++) {
            getEngine(i).saveNow();
        }
    }

    IrisData getData(int height);

    default IrisData getData() {
        return getData(0);
    }

    default void close() {
        for (int i = 0; i < getSize(); i++) {
            getEngine(i).close();
        }
    }

    boolean isFailing();

    int getThreadCount();

    boolean isStudio();

    void setStudio(boolean std);

    default void clean() {
        for (int i = 0; i < getSize(); i++) {
            getEngine(i).clean();
        }
    }

    Engine getDefaultEngine();

    default KList<IrisBiome> getAllBiomes() {
        KMap<String, IrisBiome> v = new KMap<>();

        IrisDimension dim = getRootDimension();
        dim.getAllBiomes(this).forEach((i) -> v.put(i.getLoadKey(), i));

        try {
            dim.getDimensionalComposite().forEach((m) -> getData().getDimensionLoader().load(m.getDimension()).getAllBiomes(this).forEach((i) -> v.put(i.getLoadKey(), i)));
        } catch (Throwable ignored) {
            Iris.reportError(ignored);

        }

        return v.v();
    }

    default int getLowestBedrock() {
        int f = Integer.MAX_VALUE;

        for (int i = 0; i < getSize(); i++) {
            Engine e = getEngine(i);

            if (e.getDimension().isBedrock()) {
                int m = ((IrisTerrainNormalActuator) e.getFramework().getTerrainActuator()).getLastBedrock();

                if (f > m) {
                    f = m;
                }
            }
        }

        return f;
    }
}
