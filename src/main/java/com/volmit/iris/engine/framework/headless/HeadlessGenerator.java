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

package com.volmit.iris.engine.framework.headless;

import com.volmit.iris.core.IrisSettings;
import com.volmit.iris.core.pregenerator.PregenListener;
import com.volmit.iris.engine.framework.EngineCompositeGenerator;
import com.volmit.iris.util.collection.KList;
import com.volmit.iris.util.math.Position2;
import com.volmit.iris.util.nbt.mca.MCAUtil;
import com.volmit.iris.util.nbt.mca.NBTWorld;
import com.volmit.iris.util.parallel.MultiBurst;
import lombok.Data;

import java.io.File;
import java.io.IOException;

@Data
public class HeadlessGenerator {
    private static KList<Position2> EMPTYPOINTS = new KList<>();
    private final HeadlessWorld world;
    private final EngineCompositeGenerator generator;
    private final NBTWorld writer;
    private final MultiBurst burst;

    public HeadlessGenerator(HeadlessWorld world) {
        this.world = world;
        burst = new MultiBurst("Iris Headless Generator", 9, IrisSettings.getThreadCount(IrisSettings.get().getConcurrency().getPregenThreadCount()));
        writer = new NBTWorld(world.getWorld().worldFolder());
        generator = new EngineCompositeGenerator(world.getDimension().getLoadKey(), !world.isStudio());
        generator.assignHeadlessGenerator(this);
        generator.assignHeadlessNBTWriter(writer);
        generator.initialize(world.getWorld());
    }

    public void generateChunk(int x, int z) {
        generator.directWriteChunk(world.getWorld(), x, z, writer);
    }

    public void generateRegion(int x, int z) {
        generator.directWriteMCA(world.getWorld(), x, z, writer, burst);
    }

    public void generateRegion(int x, int z, PregenListener listener) {
        generator.directWriteMCA(world.getWorld(), x, z, writer, burst, listener);
    }

    public File generateRegionToFile(int x, int z, PregenListener listener) {
        generateRegionToFile(x, z, listener);
        flush();
        return writer.getRegionFile(x, z);
    }

    public void flush() {
        writer.flushNow();
    }

    public void save() {
        writer.save();
    }

    public void close() {
        burst.shutdownAndAwait();
        generator.close();
        writer.close();
    }

    public KList<Position2> getChunksInRegion(int x, int z) {
        try {
            return MCAUtil.sampleChunkPositions(writer.getRegionFile(x, z));
        } catch (IOException e) {
            e.printStackTrace();
        }

        return EMPTYPOINTS;
    }
}
