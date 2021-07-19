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

package com.volmit.iris.engine.headless;

import com.volmit.iris.core.pregenerator.PregenListener;
import com.volmit.iris.engine.data.mca.NBTWorld;
import com.volmit.iris.engine.framework.EngineCompositeGenerator;
import com.volmit.iris.engine.parallel.MultiBurst;
import lombok.Data;

import java.io.File;

@Data
public class HeadlessGenerator {
    private final HeadlessWorld world;
    private final EngineCompositeGenerator generator;
    private final NBTWorld writer;
    private final MultiBurst burst;

    public HeadlessGenerator(HeadlessWorld world)
    {
        this.world = world;
        burst = new MultiBurst("Iris Headless Generator", 9, Runtime.getRuntime().availableProcessors());
        generator = new EngineCompositeGenerator(world.getDimension().getLoadKey(), true);
        generator.initialize(world.getWorld());
        writer = new NBTWorld(world.getWorld().worldFolder());
    }

    public void generateChunk(int x, int z)
    {
        generator.directWriteChunk(world.getWorld(), x, z, writer);
    }

    public void generateRegion(int x, int z)
    {
        generator.directWriteMCA(world.getWorld(), x, z, writer, burst);
    }

    public void generateRegion(int x, int z, PregenListener listener)
    {
        generator.directWriteMCA(world.getWorld(), x, z, writer, burst, listener);
    }

    public File generateRegionToFile(int x, int z)
    {
        generateRegionToFile(x, z);
        flush();
        return writer.getRegionFile(x, z);
    }

    public void flush()
    {
        writer.flushNow();
    }

    public void save()
    {
        writer.save();
    }

    public void close()
    {
        burst.shutdownAndAwait();
        generator.close();
        writer.close();
    }
}
