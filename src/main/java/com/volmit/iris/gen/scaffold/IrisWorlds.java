package com.volmit.iris.gen.scaffold;

import org.bukkit.World;

import com.volmit.iris.gen.IrisChunkGenerator;

public class IrisWorlds
{
	public boolean isIrisWorld(World world)
	{
		return world.getGenerator() instanceof Provisioned;
	}

	public IrisChunkGenerator getProvider(World world)
	{
		if(isIrisWorld(world))
		{
			return (IrisChunkGenerator) ((Provisioned) world.getGenerator()).getProvider();
		}

		return null;
	}
}
