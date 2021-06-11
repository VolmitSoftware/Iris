package com.volmit.iris.manager.link;

import com.volmit.iris.object.IrisDimension;
import com.volmit.iris.util.KMap;
import org.bukkit.Bukkit;
import org.bukkit.World;
import org.bukkit.WorldType;
import org.bukkit.plugin.Plugin;

import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.util.Map;

public class MultiverseCoreLink
{
	private final KMap<String, String> worldNameTypes = new KMap<>();

	public MultiverseCoreLink()
	{

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

					String.class, World.Environment.class, String.class, WorldType.class, Boolean.class, String.class, boolean.class);
			boolean b = (boolean) m.invoke(mvWorldManager, worldName, dim.getEnvironment(), seed, WorldType.NORMAL, false, "Iris", false);
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

	public void assignWorldType(String worldName, String type)
	{
		worldNameTypes.put(worldName, type);
	}

	public String getWorldNameType(String worldName, String defaultType)
	{
		try
		{
			String t = worldNameTypes.get(worldName);
			return t == null ? defaultType : t;
		}

		catch(Throwable e)
		{
			return defaultType;
		}
	}

	public boolean supported()
	{
		return getMultiverse() != null;
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

	public String envName(World.Environment environment) {
		if(environment == null)
		{
			return "normal";
		}

		switch(environment)
		{
			case NORMAL:
				return "normal";
			case NETHER:
				return "nether";
			case THE_END:
				return "end";
		}

		return environment.toString().toLowerCase();
	}
}
