package com.volmit.iris.manager.link;

import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.util.Map;

import org.bukkit.Bukkit;
import org.bukkit.World;
import org.bukkit.World.Environment;
import org.bukkit.WorldType;
import org.bukkit.plugin.Plugin;

import com.volmit.iris.object.IrisDimension;

public class MultiverseCoreLink
{
	public MultiverseCoreLink()
	{

	}

	public boolean supported()
	{
		return getMultiverse() != null;
	}

	public boolean addWorld(String worldName, IrisDimension dim, String seed)
	{
		if(!supported())
		{
			return false;
		}

		try
		{
			Plugin p = getMultiverse();
			Object mvWorldManager = p.getClass().getDeclaredMethod("getMVWorldManager").invoke(p);
			Method m = mvWorldManager.getClass().getDeclaredMethod("addWorld",

					String.class, Environment.class, String.class, WorldType.class, Boolean.class, String.class, boolean.class);
			boolean b = (boolean) m.invoke(mvWorldManager, worldName, dim.getEnvironment(), seed, WorldType.NORMAL, dim.isVanillaStructures(), "Iris", false);
			saveConfig();
			return b;
		}

		catch(Throwable e)
		{
			e.printStackTrace();
		}

		return false;
	}

	@SuppressWarnings("unchecked")
	public Map<String, ?> getList()
	{
		try
		{
			Plugin p = getMultiverse();
			Object mvWorldManager = p.getClass().getDeclaredMethod("getMVWorldManager").invoke(p);
			Field f = mvWorldManager.getClass().getDeclaredField("worldsFromTheConfig");
			f.setAccessible(true);
			return (Map<String, ?>) f.get(mvWorldManager);
		}

		catch(Throwable e)
		{
			e.printStackTrace();
		}

		return null;
	}

	public void removeFromConfig(World world)
	{
		if(!supported())
		{
			return;
		}

		getList().remove(world.getName());
		saveConfig();
	}
	
	public void removeFromConfig(String world)
	{
		if(!supported())
		{
			return;
		}

		getList().remove(world);
		saveConfig();
	}

	public void saveConfig()
	{
		try
		{
			Plugin p = getMultiverse();
			Object mvWorldManager = p.getClass().getDeclaredMethod("getMVWorldManager").invoke(p);
			mvWorldManager.getClass().getDeclaredMethod("saveWorldsConfig").invoke(mvWorldManager);
		}

		catch(Throwable e)
		{
			e.printStackTrace();
		}
	}

	public Plugin getMultiverse()
	{
		Plugin p = Bukkit.getPluginManager().getPlugin("Multiverse-Core");

		if(p == null)
		{
			return null;
		}

		return p;
	}
}
