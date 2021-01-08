package com.volmit.iris.scaffold;

import com.volmit.iris.Iris;
import com.volmit.iris.scaffold.engine.IrisAccess;
import com.volmit.iris.scaffold.engine.IrisAccessProvider;
import com.volmit.iris.util.KMap;
import com.volmit.iris.util.MortarSender;
import org.bukkit.Bukkit;
import org.bukkit.World;
import org.bukkit.entity.Player;

public class IrisWorlds
{
	private static final KMap<String, IrisAccess> provisioned = new KMap<>();

	public static void register(World w, IrisAccess p)
	{
		provisioned.put(w.getUID().toString(), p);
	}

	public static boolean isIrisWorld(World world)
	{
		if(world == null)
		{
			return false;
		}

		if(provisioned.containsKey(world.getUID().toString()))
		{
			return true;
		}

		return world.getGenerator() instanceof IrisAccess || world.getGenerator() instanceof IrisAccessProvider;
	}

	public static IrisAccess access(World world)
	{
		if(isIrisWorld(world))
		{
			if(provisioned.containsKey(world.getUID().toString()))
			{
				return provisioned.get(world.getUID().toString());
			}

			return world.getGenerator() instanceof IrisAccessProvider ? (((IrisAccessProvider)world.getGenerator()).getAccess()) : ((IrisAccess) world.getGenerator());
		}

		return null;
	}

    public static boolean evacuate(World world) {
		for(World i : Bukkit.getWorlds())
		{
			if(!i.getName().equals(world.getName()))
			{
				for(Player j : world.getPlayers())
				{
					new MortarSender(j, Iris.instance.getTag()).sendMessage("You have been evacuated from this world.");
					j.teleport(i.getSpawnLocation());
				}

				return true;
			}
		}

		return false;
    }
}
