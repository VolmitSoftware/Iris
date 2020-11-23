package com.volmit.iris.nms;

import org.bukkit.World;
import org.bukkit.WorldCreator;
import org.bukkit.block.Biome;

public interface INMSBinding
{
	public INMSCreator getCreator();

	public Object getBiomeBase(World world, Biome biome);
	public Object getBiomeBase(Object registry, Biome biome);

	default World createWorld(WorldCreator creator)
	{
		return getCreator().createWorld(creator);
	}

	default World createWorld(WorldCreator creator, boolean loadSpawn)
	{
		return getCreator().createWorld(creator, loadSpawn);
	}
}
