package com.volmit.iris.util;

import com.volmit.iris.object.tile.TileData;
import org.bukkit.block.TileState;
import org.bukkit.block.data.BlockData;

public interface IObjectPlacer
{
	public int getHighest(int x, int z);

	public int getHighest(int x, int z, boolean ignoreFluid);

	public void set(int x, int y, int z, BlockData d);

	public BlockData get(int x, int y, int z);

	public boolean isPreventingDecay();

	public boolean isSolid(int x, int y, int z);

	public boolean isUnderwater(int x, int z);

	public int getFluidHeight();

	public boolean isDebugSmartBore();

    void setTile(int xx, int yy, int zz, TileData<? extends TileState> tile);
}
