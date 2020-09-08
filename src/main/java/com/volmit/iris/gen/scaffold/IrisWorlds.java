package com.volmit.iris.gen.scaffold;

import org.bukkit.World;

import com.volmit.iris.gen.IrisTerrainProvider;

public class IrisWorlds
{
	public static boolean isIrisWorld(World world)
	{
		return world.getGenerator() instanceof Provisioned;
	}

	public static IrisTerrainProvider getProvider(World world)
	{
		if(isIrisWorld(world))
		{
			return (IrisTerrainProvider) ((Provisioned) world.getGenerator()).getProvider();
		}

		return null;
	}
}
