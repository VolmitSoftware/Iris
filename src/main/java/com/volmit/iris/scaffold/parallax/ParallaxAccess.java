package com.volmit.iris.scaffold.parallax;

import com.volmit.iris.object.tile.TileData;
import com.volmit.iris.scaffold.hunk.Hunk;
import org.bukkit.block.TileState;
import org.bukkit.block.data.BlockData;

public interface ParallaxAccess {
    default BlockData getBlock(int x, int y, int z) {
        return getBlocksR(x >> 4, z >> 4).get(x & 15, y, z & 15);
    }

    default void setBlock(int x, int y, int z, BlockData d) {
        getBlocksRW(x >> 4, z >> 4).set(x & 15, y, z & 15, d);
    }

    default TileData<? extends TileState> getTile(int x, int y, int z) {
        return getTilesR(x >> 4, z >> 4).get(x & 15, y, z & 15);
    }

    default void setTile(int x, int y, int z, TileData<? extends TileState> d) {
        getTilesRW(x >> 4, z >> 4).set(x & 15, y, z & 15, d);
    }

    default String getObject(int x, int y, int z) {
        return getObjectsR(x >> 4, z >> 4).get(x & 15, y, z & 15);
    }

    default void setObject(int x, int y, int z, String d) {
        getObjectsRW(x >> 4, z >> 4).set(x & 15, y, z & 15, d);
    }

    default String getEntity(int x, int y, int z) {
        return getEntitiesR(x >> 4, z >> 4).get(x & 15, y, z & 15);
    }

    default void setEntity(int x, int y, int z, String d) {
        getEntitiesRW(x >> 4, z >> 4).set(x & 15, y, z & 15, d);
    }

    default Boolean isUpdate(int x, int y, int z) {
        return getUpdatesR(x >> 4, z >> 4).get(x & 15, y, z & 15);
    }

    default void updateBlock(int x, int y, int z) {
        setUpdate(x, y, z, true);
    }

    default void setUpdate(int x, int y, int z, boolean d) {
        getUpdatesRW(x >> 4, z >> 4).set(x & 15, y, z & 15, d);
    }

    default boolean isParallaxGenerated(int x, int z) {
        return getMetaR(x, z).isParallaxGenerated();
    }

    default boolean isChunkGenerated(int x, int z) {
        return getMetaR(x, z).isGenerated();
    }

    default boolean isFeatureGenerated(int x, int z) {
        return getMetaR(x, z).isFeatureGenerated();
    }

    default void setParallaxGenerated(int x, int z) {
        setParallaxGenerated(x, z, true);
    }

    default void setChunkGenerated(int x, int z) {
        setChunkGenerated(x, z, true);
    }

    default void setFeatureGenerated(int x, int z) {
        setFeatureGenerated(x, z, true);
    }

    default void setParallaxGenerated(int x, int z, boolean v) {
        getMetaRW(x, z).setParallaxGenerated(v);
    }

    default void maxMin(int x, int z, int value) {
        ParallaxChunkMeta meat = getMetaRW(x, z);

        if(value > meat.getMaxObject())
        {
            meat.setMaxObject(value);
        }

        if(meat.getMinObject() <=-1)
        {
            meat.setMinObject(value);
        }

        if(value < meat.getMinObject())
        {
            meat.setMinObject(value);
        }
    }

    default void setChunkGenerated(int x, int z, boolean v) {
        getMetaRW(x, z).setGenerated(v);
    }

    default void setFeatureGenerated(int x, int z, boolean v) {
        getMetaRW(x, z).setFeatureGenerated(v);
    }

    public Hunk<TileData<? extends TileState>> getTilesR(int x, int z);

    public Hunk<TileData<? extends TileState>> getTilesRW(int x, int z);

    public Hunk<BlockData> getBlocksR(int x, int z);

    public Hunk<BlockData> getBlocksRW(int x, int z);

    public Hunk<String> getObjectsR(int x, int z);

    public Hunk<String> getObjectsRW(int x, int z);

    public Hunk<String> getEntitiesRW(int x, int z);

    public Hunk<String> getEntitiesR(int x, int z);

    public Hunk<Boolean> getUpdatesR(int x, int z);

    public Hunk<Boolean> getUpdatesRW(int x, int z);

    public ParallaxChunkMeta getMetaR(int x, int z);

    public ParallaxChunkMeta getMetaRW(int x, int z);

    public void cleanup(long regionIdle, long chunkIdle);

    public void cleanup();

    public void saveAll();

    public void saveAllNOW();

    public int getRegionCount();

    public int getChunkCount();

    public default void delete(int x, int z)
    {
        getUpdatesRW(x, z).empty(false);
        getBlocksRW(x, z).empty(null);
        getTilesRW(x, z).empty(null);
        getEntitiesRW(x, z).empty(null);
        getObjectsRW(x, z).empty(null);
    }
}
