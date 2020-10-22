package com.volmit.iris.link;

import net.citizensnpcs.api.CitizensAPI;
import net.citizensnpcs.api.npc.NPC;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.entity.Entity;
import org.bukkit.entity.EntityType;
import org.bukkit.plugin.Plugin;

public class CitizensLink
{
	public CitizensLink()
	{

	}

	public boolean supported()
	{
		return getCitizens() != null;
	}

	public Entity spawn(EntityType type, String npcType, Location a)
	{
		if(!supported())
		{
			return null;
		}

		NPC npc = CitizensAPI.getNPCRegistry().createNPC(type, "");
		npc.spawn(a);
		return npc.getEntity();
	}

	public Plugin getCitizens()
	{
		Plugin p = Bukkit.getPluginManager().getPlugin("Citizens");

		if(p == null)
		{
			return null;
		}

		return p;
	}
}
