package com.volmit.iris.util;

import org.bukkit.block.data.BlockData;
import org.bukkit.generator.ChunkGenerator.ChunkData;

public interface IPostBlockAccess
{
	public BlockData getPostBlock(int x, int y, int z, int currentPostX, int currentPostZ, ChunkData currentData);

	public void setPostBlock(int x, int y, int z, BlockData d, int currentPostX, int currentPostZ, ChunkData currentData);

	public int highestTerrainOrFluidBlock(int x, int z);

	public int highestTerrainBlock(int x, int z);

	public void updateHeight(int x, int z, int h);

	public KList<CaveResult> caveFloors(int x, int z);
}
