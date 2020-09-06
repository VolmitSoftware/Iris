package com.volmit.iris.util;

import org.bukkit.Chunk;
import org.bukkit.World;

import com.volmit.iris.gen.TopographicTerrainProvider;

public abstract class WorldGenLayer
{
	public WorldGenLayer()
	{

	}

	public abstract void gen(TopographicTerrainProvider g, Chunk c, int x, int z, World w, RNG r);
}
