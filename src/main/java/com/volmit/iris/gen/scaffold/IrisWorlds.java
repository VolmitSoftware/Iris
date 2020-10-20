package com.volmit.iris.gen.scaffold;

import org.bukkit.World;

import com.volmit.iris.gen.IrisTerrainProvider;
import com.volmit.iris.gen.provisions.ProvisionBukkit;
import com.volmit.iris.util.KMap;

public class IrisWorlds
{
	private static final KMap<String, Provisioned> provisioned = new KMap<>();

	public static void register(World w, Provisioned p)
	{
		provisioned.put(w.getUID().toString(), p);
	}

	public static boolean isIrisWorld(World world)
	{
		if(provisioned.containsKey(world.getUID().toString()))
		{
			return true;
		}

		return world.getGenerator() instanceof Provisioned || world.getGenerator() instanceof ProvisionedHolder;
	}

	public static IrisTerrainProvider getProvider(World world)
	{
		if(isIrisWorld(world))
		{
			return (IrisTerrainProvider) getProvisioned(world).getProvider();
		}

		return null;
	}

	public static ProvisionBukkit getProvisioned(World world)
	{
		if(isIrisWorld(world))
		{
			if(provisioned.containsKey(world.getUID().toString()))
			{
				return (ProvisionBukkit) provisioned.get(world.getUID().toString());
			}

			if(world.getGenerator() instanceof ProvisionedHolder)
			{
				return (ProvisionBukkit) ((ProvisionedHolder) world.getGenerator()).getProvisioned();
			}

			return ((ProvisionBukkit) world.getGenerator());
		}

		return null;
	}
}
