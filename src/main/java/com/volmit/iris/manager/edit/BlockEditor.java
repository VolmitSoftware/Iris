package com.volmit.iris.manager.edit;

import java.io.Closeable;

import org.bukkit.block.Biome;
import org.bukkit.block.data.BlockData;

public interface BlockEditor extends Closeable
{
	public long last();

	public void set(int x, int y, int z, BlockData d);

	public BlockData get(int x, int y, int z);
	
	public void setBiome(int x, int z, Biome b);

	public void setBiome(int x, int y, int z, Biome b);

	@Override
	public void close();

	public Biome getBiome(int x, int y, int z);

	public Biome getBiome(int x, int z);
}
