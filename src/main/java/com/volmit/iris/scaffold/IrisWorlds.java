package com.volmit.iris.scaffold;

import com.volmit.iris.scaffold.engine.IrisAccess;
import com.volmit.iris.util.KMap;
import org.bukkit.World;

public class IrisWorlds
{
	private static final KMap<String, IrisAccess> provisioned = new KMap<>();

	public static void register(World w, IrisAccess p)
	{
		provisioned.put(w.getUID().toString(), p);
	}

	public static boolean isIrisWorld(World world)
	{
		if(provisioned.containsKey(world.getUID().toString()))
		{
			return true;
		}

		return world.getGenerator() instanceof IrisAccess;
	}

	public static IrisAccess access(World world)
	{
		if(isIrisWorld(world))
		{
			if(provisioned.containsKey(world.getUID().toString()))
			{
				return provisioned.get(world.getUID().toString());
			}

			return ((IrisAccess) world.getGenerator());
		}

		return null;
	}
}
