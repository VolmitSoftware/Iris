package ninja.bytecode.iris.util;

import java.io.File;
import java.io.IOException;

import org.bukkit.Bukkit;
import org.bukkit.event.HandlerList;

import ninja.bytecode.iris.Iris;
import ninja.bytecode.shuriken.collections.GMap;
import ninja.bytecode.shuriken.logging.L;
import ninja.bytecode.shuriken.tools.JarScanner;

public class IrisControllerSet
{
	private GMap<Class<?>, IrisController> controllers;
	
	public IrisControllerSet()
	{
		controllers = new GMap<>();
	}
	
	public void startControllers(File jar) throws IOException
	{
		JarScanner ja = new JarScanner(jar, "ninja.bytecode.iris.controller");
		ja.scan();
		for(Class<?> i : ja.getClasses())
		{
			try
			{
				IrisController c = (IrisController) i.getConstructor().newInstance();
				Bukkit.getPluginManager().registerEvents(c, Iris.instance);
				c.onStart();
				controllers.put(i, c);
			}
			
			catch(Throwable e)
			{
				L.ex(e);
			}
		}
	}
	
	public void stopControllers()
	{
		for(Class<?> i : controllers.k())
		{
			try
			{
				IrisController c = controllers.get(i);
				HandlerList.unregisterAll();
				c.onStop();
			}
			
			catch(Throwable e)
			{
				L.ex(e);
			}
		}
		
		controllers.clear();
	}

	public IrisController get(Class<? extends IrisController> c)
	{
		return controllers.get(c);
	}

	public int size()
	{
		return controllers.size();
	}
}
