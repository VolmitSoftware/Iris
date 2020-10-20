package com.volmit.iris.gen.scaffold;

import org.bukkit.World;

import com.volmit.iris.gen.IrisTerrainProvider;
import com.volmit.iris.gen.provisions.ProvisionBukkit;

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

	public static ProvisionBukkit getProvisioned(World world)
	{
		if(isIrisWorld(world))
		{
			if(world.getGenerator() instanceof ProvisionedHolder)
			{
				return (ProvisionBukkit) ((ProvisionedHolder) world.getGenerator()).getProvisioned();
			}

			return ((ProvisionBukkit) world.getGenerator());
		}

		return null;
	}
}
