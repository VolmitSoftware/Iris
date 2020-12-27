package com.volmit.iris.nms;

import org.bukkit.World;
import org.bukkit.WorldCreator;
import org.bukkit.block.Biome;

public interface INMSBinding
{
	public INMSCreator getCreator();

	public Object getBiomeBase(World world, Biome biome);
	public Object getBiomeBase(Object registry, Biome biome);

	public boolean isBukkit();

	default World createWorld(WorldCreator creator)
	{
		return getCreator().createWorld(creator);
	}

	default World createWorld(WorldCreator creator, boolean loadSpawn)
	{
		if(!isBukkit())
		{
			if(creator.environment().equals(World.Environment.NORMAL))
			{
				return getCreator().createWorld(creator, loadSpawn);
			}

			return creator.createWorld();
		}

		return getCreator().createWorld(creator, loadSpawn);

	}

    int getBiomeId(Biome biome);
}
