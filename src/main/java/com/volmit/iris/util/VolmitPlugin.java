package com.volmit.iris.util;

import com.volmit.iris.Iris;
import org.bukkit.Bukkit;
import org.bukkit.command.Command;
import org.bukkit.command.*;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.event.Event;
import org.bukkit.event.HandlerList;
import org.bukkit.event.Listener;
import org.bukkit.permissions.PermissionDefault;
import org.bukkit.plugin.Plugin;
import org.bukkit.plugin.RegisteredListener;
import org.bukkit.plugin.java.JavaPlugin;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.io.File;
import java.io.IOException;
import java.lang.reflect.Field;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Modifier;
import java.net.URLClassLoader;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.SortedSet;

public abstract class VolmitPlugin extends JavaPlugin implements Listener
{
	public static boolean bad = false;
	private KMap<KList<String>, VirtualCommand> commands;
	private KList<MortarCommand> commandCache;
	private KList<MortarPermission> permissionCache;
	private KMap<String, IController> controllers;
	private KList<IController> cachedControllers;
	private KMap<Class<? extends IController>, IController> cachedClassControllers;

	public void selfDistruct()
	{
		HandlerList.unregisterAll((Plugin)this);
		getServer().getScheduler().cancelTasks(this);
		File me = getFile();
		Plugin plugin = this;
		String name = plugin.getName();
		SimpleCommandMap commandMap = null;
		List<Plugin> plugins = null;
		Map<String, Plugin> names = null;
		Map<String, Command> commands = null;
		Map<Event, SortedSet<RegisteredListener>> listeners = null;

		try
		{

			Field pluginsField = Bukkit.getPluginManager().getClass().getDeclaredField("plugins");
			pluginsField.setAccessible(true);
			plugins = (List<Plugin>) pluginsField.get(getServer().getPluginManager());
			Field lookupNamesField = Bukkit.getPluginManager().getClass().getDeclaredField("lookupNames");
			lookupNamesField.setAccessible(true);
			names = (Map<String, Plugin>) lookupNamesField.get(getServer().getPluginManager());

			try
			{
				Field listenersField = Bukkit.getPluginManager().getClass().getDeclaredField("listeners");
				listenersField.setAccessible(true);
				listeners = (Map<Event, SortedSet<RegisteredListener>>) listenersField.get(getServer().getPluginManager());
			}

			catch(Throwable ignored)
			{

			}

			Field commandMapField = Bukkit.getPluginManager().getClass().getDeclaredField("commandMap");
			commandMapField.setAccessible(true);
			commandMap = (SimpleCommandMap) commandMapField.get(getServer().getPluginManager());
			Field knownCommandsField = SimpleCommandMap.class.getDeclaredField("knownCommands");
			knownCommandsField.setAccessible(true);
			commands = (Map<String, Command>) knownCommandsField.get(commandMap);

		}

		catch(Throwable e)
		{

		}

		getServer().getPluginManager().disablePlugin(plugin);
		plugins.remove(plugin);
		names.remove(name);

		if(listeners != null)
		{
			for(SortedSet<RegisteredListener> set : listeners.values())
			{
				set.removeIf(value -> value.getPlugin().equals(plugin));
			}
		}

		for(Iterator<Map.Entry<String, Command>> it = commands.entrySet().iterator(); it.hasNext();)
		{
			Map.Entry<String, Command> entry = it.next();
			if(entry.getValue() instanceof PluginCommand)
			{
				PluginCommand c = (PluginCommand) entry.getValue();
				if(c.getPlugin() == plugin)
				{
					c.unregister(commandMap);
					it.remove();
				}
			}
		}

		new Thread(() -> {
			// Attempt to close the classloader to unlock any handles on the
			// plugin's
			// jar file.
			ClassLoader cl = plugin.getClass().getClassLoader();

			if(cl instanceof URLClassLoader)
			{
				try
				{
					((URLClassLoader) cl).close();
				}
				catch(IOException ignored)
				{

				}
			}

			// Will not work on processes started with the -XX:+DisableExplicitGC
			// flag,
			// but lets try it anyway. This tries to get around the issue where
			// Windows
			// refuses to unlock jar files that were previously loaded into the JVM.
			System.gc();
			if(!me.delete())
			{
				me.deleteOnExit();
			}
		}).start();
	}

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
		if(bad)
		{
			return;
		}

		for(IController i : getControllers())
		{
			tickController(i);
		}
	}

	private void tickController(IController i)
	{
		if(bad)
		{
			return;
		}

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
		if(bad)
		{
			return new KList<>();
		}

		return cachedControllers;
	}

	private void registerControllers()
	{
		if(bad)
		{
			return;
		}
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
		if(bad)
		{
			return;
		}
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
		if(bad)
		{
			return;
		}
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
		if(bad)
		{
			return;
		}
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
		if(bad)
		{
			return;
		}
		commands = new KMap<>();
		commandCache = new KList<>();

		for(Field i : getClass().getDeclaredFields())
		{
			if(i.isAnnotationPresent(com.volmit.iris.util.Command.class))
			{
				try
				{
					i.setAccessible(true);
					MortarCommand pc = (MortarCommand) i.getType().getConstructor().newInstance();
					com.volmit.iris.util.Command c = i.getAnnotation(com.volmit.iris.util.Command.class);
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

	@Nullable
	@Override
	public List<String> onTabComplete(@NotNull CommandSender sender, @NotNull Command command,
							    @NotNull String alias, @NotNull String[] args) {
		KList<String> chain = new KList<String>();

		for(String i : args)
		{
			if(i.trim().isEmpty())
			{
				continue;
			}

			chain.add(i.trim());
		}

		for(KList<String> i : commands.k())
		{
			for(String j : i)
			{
				if(j.equalsIgnoreCase(alias))
				{
					VirtualCommand cmd = commands.get(i);

					List<String> v = cmd.hitTab(sender, chain.copy(), alias);
					if(v != null)
					{
						return v;
					}
				}
			}
		}

		return super.onTabComplete(sender, command, alias, args);
	}

	@Override
	public boolean onCommand(CommandSender sender, org.bukkit.command.Command command, String label, String[] args)
	{
		if(bad)
		{
			return false;
		}

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
		if(bad)
		{
			return;
		}

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
		if(bad)
		{
			return;
		}
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
		if(bad)
		{
			return "";
		}
		return getTag("");
	}

	public void registerListener(Listener l)
	{
		if(bad)
		{
			return;
		}
		Bukkit.getPluginManager().registerEvents(l, this);
	}

	public void unregisterListener(Listener l)
	{
		if(bad)
		{
			return;
		}
		HandlerList.unregisterAll(l);
	}

	public void unregisterListeners()
	{
		if(bad)
		{
			return;
		}
		HandlerList.unregisterAll((Listener) this);
	}

	public void unregisterCommands()
	{
		if(bad)
		{
			return;
		}
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
		if(bad)
		{
			return;
		}
		for(org.bukkit.permissions.Permission i : computePermissions())
		{
			Bukkit.getPluginManager().removePermission(i);
			v("Unregistered Permission " + i.getName());
		}
	}

	private void stopControllers()
	{
		if(bad)
		{
			return;
		}
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

	public File getDataFileList(String pre, String[] strings)
	{
		KList<String> v = new KList<String>(strings);
		v.add(0, pre);
		File f = new File(getDataFolder(), v.toString(File.separator));
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

	public File getDataFolderList(String pre, String[] strings)
	{
		KList<String> v = new KList<>(strings);
		v.add(0, pre);
		if(v.size() == 0)
		{
			return super.getDataFolder();
		}
		File f = new File(getDataFolder(), v.toString(File.separator));
		f.mkdirs();

		return f;
	}

	public abstract void start();

	public abstract void stop();

	public abstract String getTag(String subTag);
}
