package com.volmit.iris.command.util;

import java.io.File;
import java.io.IOException;
import java.lang.reflect.Field;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Modifier;
import java.util.Iterator;
import java.util.Map;

import org.bukkit.Bukkit;
import org.bukkit.command.Command;
import org.bukkit.command.CommandMap;
import org.bukkit.command.CommandSender;
import org.bukkit.command.PluginCommand;
import org.bukkit.command.SimpleCommandMap;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.event.HandlerList;
import org.bukkit.event.Listener;
import org.bukkit.permissions.PermissionDefault;
import org.bukkit.plugin.java.JavaPlugin;

import com.volmit.iris.Iris;
import com.volmit.iris.util.IO;
import com.volmit.iris.util.J;
import com.volmit.iris.util.KList;
import com.volmit.iris.util.KMap;
import com.volmit.iris.util.M;

public abstract class MortarPlugin extends JavaPlugin implements Listener
{
	private KMap<KList<String>, VirtualCommand> commands;
	private KList<MortarCommand> commandCache;
	private KList<MortarPermission> permissionCache;
	private KMap<String, IController> controllers;
	private KList<IController> cachedControllers;
	private KMap<Class<? extends IController>, IController> cachedClassControllers;

	public void l(Object l)
	{
		Iris.info("[" + getName() + "]: " + l);
	}

	public void w(Object l)
	{
		Iris.warn("[" + getName() + "]: " + l);
	}

	public void f(Object l)
	{
		Iris.error("[" + getName() + "]: " + l);
	}

	public void v(Object l)
	{
		Iris.verbose("[" + getName() + "]: " + l);
	}

	public void onEnable()
	{
		registerInstance();
		registerPermissions();
		registerCommands();
		registerControllers();
		Bukkit.getScheduler().scheduleSyncRepeatingTask(this, this::tickControllers, 0, 0);
		J.a(() -> outputInfo());
		registerListener(this);
		start();
	}

	public void unregisterAll()
	{
		stopControllers();
		unregisterListeners();
		unregisterCommands();
		unregisterPermissions();
		unregisterInstance();
	}

	private void outputInfo()
	{
		try
		{
			IO.delete(getDataFolder("info"));
			getDataFolder("info").mkdirs();
			outputPluginInfo();
			outputCommandInfo();
			outputPermissionInfo();
		}

		catch(Throwable e)
		{

		}
	}

	private void outputPermissionInfo() throws IOException
	{
		FileConfiguration fc = new YamlConfiguration();

		for(MortarPermission i : permissionCache)
		{
			chain(i, fc);
		}

		fc.save(getDataFile("info", "permissions.yml"));
	}

	private void chain(MortarPermission i, FileConfiguration fc)
	{
		KList<String> ff = new KList<String>();

		for(MortarPermission j : i.getChildren())
		{
			ff.add(j.getFullNode());
		}

		fc.set(i.getFullNode().replaceAll("\\Q.\\E", ",") + "." + "description", i.getDescription());
		fc.set(i.getFullNode().replaceAll("\\Q.\\E", ",") + "." + "default", i.isDefault());
		fc.set(i.getFullNode().replaceAll("\\Q.\\E", ",") + "." + "children", ff);

		for(MortarPermission j : i.getChildren())
		{
			chain(j, fc);
		}
	}

	private void outputCommandInfo() throws IOException
	{
		FileConfiguration fc = new YamlConfiguration();

		for(MortarCommand i : commandCache)
		{
			chain(i, "/", fc);
		}

		fc.save(getDataFile("info", "commands.yml"));
	}

	private void chain(MortarCommand i, String c, FileConfiguration fc)
	{
		String n = c + (c.length() == 1 ? "" : " ") + i.getNode();
		fc.set(n + "." + "description", i.getDescription());
		fc.set(n + "." + "required-permissions", i.getRequiredPermissions());
		fc.set(n + "." + "aliases", i.getAllNodes());

		for(MortarCommand j : i.getChildren())
		{
			chain(j, n, fc);
		}
	}

	private void outputPluginInfo() throws IOException
	{
		FileConfiguration fc = new YamlConfiguration();
		fc.set("version", getDescription().getVersion());
		fc.set("name", getDescription().getName());
		fc.save(getDataFile("info", "plugin.yml"));
	}

	private void registerPermissions()
	{
		permissionCache = new KList<>();

		for(Field i : getClass().getDeclaredFields())
		{
			if(i.isAnnotationPresent(Permission.class))
			{
				try
				{
					i.setAccessible(true);
					MortarPermission pc = (MortarPermission) i.getType().getConstructor().newInstance();
					i.set(Modifier.isStatic(i.getModifiers()) ? null : this, pc);
					registerPermission(pc);
					permissionCache.add(pc);
					v("Registered Permissions " + pc.getFullNode() + " (" + i.getName() + ")");
				}

				catch(IllegalArgumentException | IllegalAccessException | InstantiationException | InvocationTargetException | NoSuchMethodException | SecurityException e)
				{
					w("Failed to register permission (field " + i.getName() + ")");
					e.printStackTrace();
				}
			}
		}

		for(org.bukkit.permissions.Permission i : computePermissions())
		{
			try
			{
				Bukkit.getPluginManager().addPermission(i);
			}

			catch(Throwable e)
			{

			}
		}
	}

	private KList<org.bukkit.permissions.Permission> computePermissions()
	{
		KList<org.bukkit.permissions.Permission> g = new KList<>();
		for(Field i : getClass().getDeclaredFields())
		{
			if(i.isAnnotationPresent(Permission.class))
			{
				try
				{
					MortarPermission x = (MortarPermission) i.get(Modifier.isStatic(i.getModifiers()) ? null : this);
					g.add(toPermission(x));
					g.addAll(computePermissions(x));
				}

				catch(IllegalArgumentException | IllegalAccessException | SecurityException e)
				{
					e.printStackTrace();
				}
			}
		}

		return g.removeDuplicates();
	}

	private KList<org.bukkit.permissions.Permission> computePermissions(MortarPermission p)
	{
		KList<org.bukkit.permissions.Permission> g = new KList<>();

		if(p == null)
		{
			return g;
		}

		for(MortarPermission i : p.getChildren())
		{
			if(i == null)
			{
				continue;
			}

			g.add(toPermission(i));
			g.addAll(computePermissions(i));
		}

		return g;
	}

	private org.bukkit.permissions.Permission toPermission(MortarPermission p)
	{
		if(p == null)
		{
			return null;
		}

		org.bukkit.permissions.Permission perm = new org.bukkit.permissions.Permission(p.getFullNode() + (p.hasParent() ? "" : ".*"));
		perm.setDescription(p.getDescription() == null ? "" : p.getDescription());
		perm.setDefault(p.isDefault() ? PermissionDefault.TRUE : PermissionDefault.OP);

		for(MortarPermission i : p.getChildren())
		{
			perm.getChildren().put(i.getFullNode(), true);
		}

		return perm;
	}

	private void registerPermission(MortarPermission pc)
	{

	}

	@Override
	public void onDisable()
	{
		stop();
		Bukkit.getScheduler().cancelTasks(this);
		unregisterListener(this);
		unregisterAll();
	}

	private void tickControllers()
	{
		for(IController i : getControllers())
		{
			tickController(i);
		}
	}

	private void tickController(IController i)
	{
		if(i.getTickInterval() < 0)
		{
			return;
		}

		M.tick++;
		if(M.interval(i.getTickInterval()))
		{
			try
			{
				i.tick();
			}

			catch(Throwable e)
			{
				w("Failed to tick controller " + i.getName());
				e.printStackTrace();
			}
		}
	}

	public KList<IController> getControllers()
	{
		return cachedControllers;
	}

	private void registerControllers()
	{
		controllers = new KMap<>();
		cachedClassControllers = new KMap<>();

		for(Field i : getClass().getDeclaredFields())
		{
			if(i.isAnnotationPresent(Control.class))
			{
				try
				{
					i.setAccessible(true);
					IController pc = (IController) i.getType().getConstructor().newInstance();
					registerController(pc);
					i.set(this, pc);
					v("Registered " + pc.getName() + " (" + i.getName() + ")");
				}

				catch(IllegalArgumentException | IllegalAccessException | InstantiationException | InvocationTargetException | NoSuchMethodException | SecurityException e)
				{
					w("Failed to register controller (field " + i.getName() + ")");
					e.printStackTrace();
				}
			}
		}

		cachedControllers = controllers.v();
	}

	public IController getController(Class<? extends IController> c)
	{
		return cachedClassControllers.get(c);
	}

	private void registerController(IController pc)
	{
		controllers.put(pc.getName(), pc);
		cachedClassControllers.put(pc.getClass(), pc);
		registerListener(pc);

		try
		{
			pc.start();
			v("Started " + pc.getName());
		}

		catch(Throwable e)
		{
			w("Failed to start controller " + pc.getName());
			e.printStackTrace();
		}
	}

	private void registerInstance()
	{
		for(Field i : getClass().getDeclaredFields())
		{
			if(i.isAnnotationPresent(Instance.class))
			{
				try
				{
					i.setAccessible(true);
					i.set(Modifier.isStatic(i.getModifiers()) ? null : this, this);
					v("Registered Instance " + i.getName());
				}

				catch(IllegalArgumentException | IllegalAccessException | SecurityException e)
				{
					w("Failed to register instance (field " + i.getName() + ")");
					e.printStackTrace();
				}
			}
		}
	}

	private void unregisterInstance()
	{
		for(Field i : getClass().getDeclaredFields())
		{
			if(i.isAnnotationPresent(Instance.class))
			{
				try
				{
					i.setAccessible(true);
					i.set(Modifier.isStatic(i.getModifiers()) ? null : this, null);
					v("Unregistered Instance " + i.getName());
				}

				catch(IllegalArgumentException | IllegalAccessException | SecurityException e)
				{
					w("Failed to unregister instance (field " + i.getName() + ")");
					e.printStackTrace();
				}
			}
		}
	}

	private void registerCommands()
	{
		commands = new KMap<>();
		commandCache = new KList<>();

		for(Field i : getClass().getDeclaredFields())
		{
			if(i.isAnnotationPresent(com.volmit.iris.command.util.Command.class))
			{
				try
				{
					i.setAccessible(true);
					MortarCommand pc = (MortarCommand) i.getType().getConstructor().newInstance();
					com.volmit.iris.command.util.Command c = i.getAnnotation(com.volmit.iris.command.util.Command.class);
					registerCommand(pc, c.value());
					commandCache.add(pc);
					v("Registered Commands /" + pc.getNode() + " (" + i.getName() + ")");
				}

				catch(IllegalArgumentException | IllegalAccessException | InstantiationException | InvocationTargetException | NoSuchMethodException | SecurityException e)
				{
					w("Failed to register command (field " + i.getName() + ")");
					e.printStackTrace();
				}
			}
		}
	}

	@Override
	public boolean onCommand(CommandSender sender, org.bukkit.command.Command command, String label, String[] args)
	{
		KList<String> chain = new KList<String>();
		chain.add(args);

		for(KList<String> i : commands.k())
		{
			for(String j : i)
			{
				if(j.equalsIgnoreCase(label))
				{
					VirtualCommand cmd = commands.get(i);

					if(cmd.hit(sender, chain.copy(), label))
					{
						return true;
					}
				}
			}
		}

		return false;
	}

	public void registerCommand(ICommand cmd)
	{
		registerCommand(cmd, "");
	}

	public void registerCommand(ICommand cmd, String subTag)
	{
		commands.put(cmd.getAllNodes(), new VirtualCommand(cmd, subTag.trim().isEmpty() ? getTag() : getTag(subTag.trim())));
		PluginCommand cc = getCommand(cmd.getNode().toLowerCase());

		if(cc != null)
		{
			cc.setExecutor(this);
			cc.setUsage(getName() + ":" + getClass().toString() + ":" + cmd.getNode());
		}

		else
		{
			RouterCommand r = new RouterCommand(cmd, this);
			r.setUsage(getName() + ":" + getClass().toString());
			((CommandMap) new com.volmit.iris.util.V(Bukkit.getServer()).get("commandMap")).register("", r);
		}
	}

	public void unregisterCommand(ICommand cmd)
	{
		try
		{
			SimpleCommandMap m = new com.volmit.iris.util.V(Bukkit.getServer()).get("commandMap");

			Map<String, Command> k = new com.volmit.iris.util.V(m).get("knownCommands");

			for(Iterator<Map.Entry<String, Command>> it = k.entrySet().iterator(); it.hasNext();)
			{
				Map.Entry<String, Command> entry = it.next();
				if(entry.getValue() instanceof Command)
				{
					org.bukkit.command.Command c = (org.bukkit.command.Command) entry.getValue();
					String u = c.getUsage();

					if(u != null && u.equals(getName() + ":" + getClass().toString() + ":" + cmd.getNode()))
					{
						if(c.unregister(m))
						{
							it.remove();
							v("Unregistered Command /" + cmd.getNode());
						}

						else
						{
							Bukkit.getConsoleSender().sendMessage(getTag() + "Failed to unregister command " + c.getName());
						}
					}
				}
			}
		}

		catch(Throwable e)
		{
			e.printStackTrace();
		}
	}

	public String getTag()
	{
		return getTag("");
	}

	public void registerListener(Listener l)
	{
		Bukkit.getPluginManager().registerEvents(l, this);
	}

	public void unregisterListener(Listener l)
	{
		HandlerList.unregisterAll(l);
	}

	public void unregisterListeners()
	{
		HandlerList.unregisterAll((Listener) this);
	}

	public void unregisterCommands()
	{
		for(VirtualCommand i : commands.v())
		{
			try
			{
				unregisterCommand(i.getCommand());
			}

			catch(Throwable e)
			{

			}
		}
	}

	private void unregisterPermissions()
	{
		for(org.bukkit.permissions.Permission i : computePermissions())
		{
			Bukkit.getPluginManager().removePermission(i);
			v("Unregistered Permission " + i.getName());
		}
	}

	private void stopControllers()
	{
		for(IController i : controllers.v())
		{
			try
			{
				unregisterListener(i);
				i.stop();
				v("Stopped " + i.getName());
			}

			catch(Throwable e)
			{
				w("Failed to stop controller " + i.getName());
				e.printStackTrace();
			}
		}
	}

	public File getDataFile(String... strings)
	{
		File f = new File(getDataFolder(), new KList<String>(strings).toString(File.separator));
		f.getParentFile().mkdirs();
		return f;
	}

	public File getDataFolder(String... strings)
	{
		if(strings.length == 0)
		{
			return super.getDataFolder();
		}

		File f = new File(getDataFolder(), new KList<String>(strings).toString(File.separator));
		f.mkdirs();

		return f;
	}

	public abstract void start();

	public abstract void stop();

	public abstract String getTag(String subTag);
}
