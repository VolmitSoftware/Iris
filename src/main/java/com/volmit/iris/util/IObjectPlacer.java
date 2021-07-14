package com.volmit.iris.util;

import com.volmit.iris.object.tile.TileData;
import org.bukkit.block.TileState;
import org.bukkit.block.data.BlockData;

public interface IObjectPlacer {
    int getHighest(int x, int z);

    int getHighest(int x, int z, boolean ignoreFluid);

    void set(int x, int y, int z, BlockData d);

    BlockData get(int x, int y, int z);

    boolean isPreventingDecay();

    boolean isSolid(int x, int y, int z);

    boolean isUnderwater(int x, int z);

    int getFluidHeight();

    boolean isDebugSmartBore();

    void setTile(int xx, int yy, int zz, TileData<? extends TileState> tile);
}
