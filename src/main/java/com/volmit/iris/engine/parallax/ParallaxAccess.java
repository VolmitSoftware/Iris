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

package com.volmit.iris.engine.parallax;

import com.volmit.iris.engine.hunk.Hunk;
import com.volmit.iris.engine.object.tile.TileData;
import com.volmit.iris.util.documentation.BlockCoordinates;
import com.volmit.iris.util.documentation.ChunkCoordinates;
import org.bukkit.block.TileState;
import org.bukkit.block.data.BlockData;

public interface ParallaxAccess {
    @BlockCoordinates
    default BlockData getBlock(int x, int y, int z) {
        return getBlocksR(x >> 4, z >> 4).get(x & 15, y, z & 15);
    }

    @BlockCoordinates
    default void setBlock(int x, int y, int z, BlockData d) {
        getBlocksRW(x >> 4, z >> 4).set(x & 15, y, z & 15, d);
    }

    @BlockCoordinates
    default TileData<? extends TileState> getTile(int x, int y, int z) {
        return getTilesR(x >> 4, z >> 4).get(x & 15, y, z & 15);
    }

    @BlockCoordinates
    default void setTile(int x, int y, int z, TileData<? extends TileState> d) {
        getTilesRW(x >> 4, z >> 4).set(x & 15, y, z & 15, d);
    }

    @BlockCoordinates
    default String getObject(int x, int y, int z) {
        return getObjectsR(x >> 4, z >> 4).get(x & 15, y, z & 15);
    }

    @BlockCoordinates
    default void setObject(int x, int y, int z, String d) {
        getObjectsRW(x >> 4, z >> 4).set(x & 15, y, z & 15, d);
    }

    @BlockCoordinates
    default String getEntity(int x, int y, int z) {
        return getEntitiesR(x >> 4, z >> 4).get(x & 15, y, z & 15);
    }

    @BlockCoordinates
    default void setEntity(int x, int y, int z, String d) {
        getEntitiesRW(x >> 4, z >> 4).set(x & 15, y, z & 15, d);
    }

    @BlockCoordinates
    default Boolean isUpdate(int x, int y, int z) {
        return getUpdatesR(x >> 4, z >> 4).get(x & 15, y, z & 15);
    }

    @BlockCoordinates
    default void updateBlock(int x, int y, int z) {
        setUpdate(x, y, z, true);
    }

    @BlockCoordinates
    default void setUpdate(int x, int y, int z, boolean d) {
        getUpdatesRW(x >> 4, z >> 4).set(x & 15, y, z & 15, d);
    }

    @ChunkCoordinates
    default boolean isParallaxGenerated(int x, int z) {
        return getMetaR(x, z).isParallaxGenerated();
    }

    @ChunkCoordinates
    default boolean isChunkGenerated(int x, int z) {
        return getMetaR(x, z).isGenerated();
    }

    @ChunkCoordinates
    default boolean isFeatureGenerated(int x, int z) {
        return getMetaR(x, z).isFeatureGenerated();
    }

    @ChunkCoordinates
    default void setParallaxGenerated(int x, int z) {
        setParallaxGenerated(x, z, true);
    }

    @ChunkCoordinates
    default void setChunkGenerated(int x, int z) {
        setChunkGenerated(x, z, true);
    }

    @ChunkCoordinates
    default void setFeatureGenerated(int x, int z) {
        setFeatureGenerated(x, z, true);
    }

    @ChunkCoordinates
    default void setParallaxGenerated(int x, int z, boolean v) {
        getMetaRW(x, z).setParallaxGenerated(v);
    }

    @ChunkCoordinates
    default void maxMin(int x, int z, int value) {
        ParallaxChunkMeta meat = getMetaRW(x, z);

        if (value > meat.getMaxObject()) {
            meat.setMaxObject(value);
        }

        if (meat.getMinObject() <= -1) {
            meat.setMinObject(value);
        }

        if (value < meat.getMinObject()) {
            meat.setMinObject(value);
        }
    }

    @ChunkCoordinates
    default void setChunkGenerated(int x, int z, boolean v) {
        getMetaRW(x, z).setGenerated(v);
    }

    @ChunkCoordinates
    default void setFeatureGenerated(int x, int z, boolean v) {
        getMetaRW(x, z).setFeatureGenerated(v);
    }

    @ChunkCoordinates
    Hunk<TileData<? extends TileState>> getTilesR(int x, int z);

    @ChunkCoordinates
    Hunk<TileData<? extends TileState>> getTilesRW(int x, int z);

    @ChunkCoordinates
    Hunk<BlockData> getBlocksR(int x, int z);

    @ChunkCoordinates
    Hunk<BlockData> getBlocksRW(int x, int z);

    @ChunkCoordinates
    Hunk<String> getObjectsR(int x, int z);

    @ChunkCoordinates
    Hunk<String> getObjectsRW(int x, int z);

    @ChunkCoordinates
    Hunk<String> getEntitiesRW(int x, int z);

    @ChunkCoordinates
    Hunk<String> getEntitiesR(int x, int z);

    @ChunkCoordinates
    Hunk<Boolean> getUpdatesR(int x, int z);

    @ChunkCoordinates
    Hunk<Boolean> getUpdatesRW(int x, int z);

    @ChunkCoordinates
    ParallaxChunkMeta getMetaR(int x, int z);

    @ChunkCoordinates
    ParallaxChunkMeta getMetaRW(int x, int z);

    void cleanup(long regionIdle, long chunkIdle);

    void cleanup();

    void saveAll();

    void saveAllNOW();

    int getRegionCount();

    int getChunkCount();

    @ChunkCoordinates
    default void delete(int x, int z) {
        getUpdatesRW(x, z).empty(false);
        getBlocksRW(x, z).empty(null);
        getTilesRW(x, z).empty(null);
        getEntitiesRW(x, z).empty(null);
        getObjectsRW(x, z).empty(null);
    }
}
