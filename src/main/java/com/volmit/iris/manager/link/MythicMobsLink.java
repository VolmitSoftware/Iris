package com.volmit.iris.manager.link;

import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.entity.Entity;
import org.bukkit.plugin.Plugin;

import com.volmit.iris.util.KList;

import io.lumine.xikage.mythicmobs.MythicMobs;
import io.lumine.xikage.mythicmobs.mobs.MythicMob;

public class MythicMobsLink
{
	public MythicMobsLink()
	{

	}

	public boolean supported()
	{
		return getMythicMobs() != null;
	}

	public Entity spawn(String name, Location a)
	{
		if(!supported())
		{
			return null;
		}

		MythicMobs m = (MythicMobs) getMythicMobs();
		return m.getMobManager().spawnMob(name, a).getEntity().getBukkitEntity();
	}

	public String[] getMythicMobTypes()
	{
		KList<String> v = new KList<>();

		if(supported())
		{
			MythicMobs m = (MythicMobs) getMythicMobs();

			for(MythicMob i : m.getMobManager().getMobTypes())
			{
				v.add(i.getInternalName());
			}
		}

		return v.toArray(new String[v.size()]);
	}

	public Plugin getMythicMobs()
	{
		Plugin p = Bukkit.getPluginManager().getPlugin("MythicMobs");

		if(p == null)
		{
			return null;
		}

		return p;
	}
}
