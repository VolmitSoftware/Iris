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
import com.volmit.iris.core.IrisSettings;
import com.volmit.iris.core.nms.INMS;
import com.volmit.iris.core.pregenerator.PregenListener;
import com.volmit.iris.core.pregenerator.PregenTask;
import com.volmit.iris.engine.IrisEngine;
import com.volmit.iris.engine.data.chunk.MCATerrainChunk;
import com.volmit.iris.engine.data.chunk.TerrainChunk;
import com.volmit.iris.engine.framework.Engine;
import com.volmit.iris.engine.framework.EngineTarget;
import com.volmit.iris.engine.object.common.HeadlessWorld;
import com.volmit.iris.util.collection.KList;
import com.volmit.iris.util.documentation.ChunkCoordinates;
import com.volmit.iris.util.documentation.RegionCoordinates;
import com.volmit.iris.util.hunk.Hunk;
import com.volmit.iris.util.math.Position2;
import com.volmit.iris.util.nbt.mca.MCAUtil;
import com.volmit.iris.util.nbt.mca.NBTWorld;
import com.volmit.iris.util.nbt.tag.CompoundTag;
import com.volmit.iris.util.parallel.BurstExecutor;
import com.volmit.iris.util.parallel.MultiBurst;
import lombok.Data;
import org.bukkit.Material;
import org.bukkit.block.data.BlockData;
import org.bukkit.generator.ChunkGenerator;

import java.io.File;
import java.io.IOException;

@Data
public class HeadlessGenerator implements PlatformChunkGenerator {
    private static final BlockData ERROR_BLOCK = Material.RED_GLAZED_TERRACOTTA.createBlockData();
    private static KList<Position2> EMPTYPOINTS = new KList<>();
    private final HeadlessWorld world;
    private final NBTWorld writer;
    private final MultiBurst burst;
    private final EngineProvider provider;

    public HeadlessGenerator(HeadlessWorld world) {
        this.world = world;
        burst = new MultiBurst("Iris Headless Generator", 9, IrisSettings.getThreadCount(IrisSettings.get().getConcurrency().getPregenThreadCount()));
        writer = new NBTWorld(world.getWorld().worldFolder());
        provider = new EngineProvider();
        provider.provideEngine(world.getWorld(), world.getDimension().getLoadKey(), world.getDimension().getLoader().getDataFolder(), isStudio(), (e) -> {});
    }

    @ChunkCoordinates
    public void generateChunk(int x, int z) {
        try {
            int ox = x << 4;
            int oz = z << 4;
            com.volmit.iris.util.nbt.mca.Chunk chunk = writer.getChunk(x, z);

            TerrainChunk tc = MCATerrainChunk.builder()
                    .writer(writer).ox(ox).oz(oz).mcaChunk(chunk)
                    .minHeight(world.getWorld().minHeight()).maxHeight(world.getWorld().maxHeight())
                    .injector((xx, yy, zz, biomeBase) -> chunk.setBiomeAt(ox + xx, yy, oz + zz,
                            INMS.get().getTrueBiomeBaseId(biomeBase)))
                    .build();
            getEngine().generate(x * 16, z * 16,
                    Hunk.view((ChunkGenerator.ChunkData) tc), Hunk.view((ChunkGenerator.BiomeGrid) tc),
                    false);
        } catch (Throwable e) {
            Iris.error("======================================");
            e.printStackTrace();
            Iris.reportErrorChunk(x, z, e, "MCA");
            Iris.error("======================================");
            com.volmit.iris.util.nbt.mca.Chunk chunk = writer.getChunk(x, z);
            CompoundTag c = NBTWorld.getCompound(ERROR_BLOCK);
            for (int i = 0; i < 16; i++) {
                for (int j = 0; j < 16; j++) {
                    chunk.setBlockStateAt(i, 0, j, c, false);
                }
            }
        }
    }

    @RegionCoordinates
    public void generateRegion(int x, int z) {
        generateRegion(x, z, null);
    }

    @RegionCoordinates
    public void generateRegion(int x, int z, PregenListener listener) {
        BurstExecutor e = burst.burst(1024);
        PregenTask.iterateRegion(x, z, (ii, jj) -> e.queue(() -> {
            if (listener != null) {
                listener.onChunkGenerating(ii, jj);
            }
            generateChunk(ii, jj);
            if (listener != null) {
                listener.onChunkGenerated(ii, jj);
            }
        }));

        e.complete();
    }

    @RegionCoordinates
    public File generateRegionToFile(int x, int z, PregenListener listener) {
        generateRegion(x, z, listener);
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
        provider.close();
        writer.close();
    }

    @Override
    public boolean isStudio() {
        return false;
    }

    public KList<Position2> getChunksInRegion(int x, int z) {
        try {
            return MCAUtil.sampleChunkPositions(writer.getRegionFile(x, z));
        } catch (IOException e) {
            e.printStackTrace();
        }

        return EMPTYPOINTS;
    }

    @Override
    public Engine getEngine() {
        return provider.getEngine();
    }

    @Override
    public boolean isHeadless() {
        return true;
    }

    @Override
    public void hotload() {

    }
}
