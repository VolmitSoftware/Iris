package com.volmit.iris.util;

import org.bukkit.generator.ChunkGenerator.ChunkData;

public interface IPostBlockAccess
{
	public FastBlockData getPostBlock(int x, int y, int z, int currentPostX, int currentPostZ, ChunkData currentData);

	public void setPostBlock(int x, int y, int z, FastBlockData d, int currentPostX, int currentPostZ, ChunkData currentData);

	public int highestTerrainOrFluidBlock(int x, int z);

	public int highestTerrainBlock(int x, int z);

	public void updateHeight(int x, int z, int h);

	public KList<CaveResult> caveFloors(int x, int z);
}
