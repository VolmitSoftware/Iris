package com.volmit.iris.nms;

import org.bukkit.World;
import org.bukkit.WorldCreator;
import org.bukkit.block.Biome;

public interface INMSBinding
{
	public Object getBiomeBase(World world, Biome biome);

	public Object getBiomeBase(Object registry, Biome biome);

	public boolean isBukkit();

    int getBiomeId(Biome biome);

    default World createWorld(WorldCreator c)
	{
		return c.createWorld();
	}
}
