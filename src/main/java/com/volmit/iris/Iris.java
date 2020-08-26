package com.volmit.iris;

import java.io.File;

import org.bukkit.Bukkit;
import org.bukkit.World;
import org.bukkit.command.Command;
import org.bukkit.command.CommandSender;
import org.bukkit.event.HandlerList;
import org.bukkit.generator.ChunkGenerator;
import org.bukkit.plugin.Plugin;

import com.volmit.iris.command.CommandIris;
import com.volmit.iris.command.PermissionIris;
import com.volmit.iris.gen.IrisChunkGenerator;
import com.volmit.iris.gen.post.PostFloatingNibDeleter;
import com.volmit.iris.gen.post.PostNibSmoother;
import com.volmit.iris.gen.post.PostPotholeFiller;
import com.volmit.iris.gen.post.PostSlabber;
import com.volmit.iris.gen.post.PostWallPatcher;
import com.volmit.iris.gen.post.PostWaterlogger;
import com.volmit.iris.util.BoardManager;
import com.volmit.iris.util.C;
import com.volmit.iris.util.GroupedExecutor;
import com.volmit.iris.util.IO;
import com.volmit.iris.util.IrisLock;
import com.volmit.iris.util.IrisPostBlockFilter;
import com.volmit.iris.util.KList;
import com.volmit.iris.util.MortarPlugin;
import com.volmit.iris.util.Permission;

public class Iris extends MortarPlugin
{
	public static KList<GroupedExecutor> executors = new KList<>();
	public static Iris instance;
	public static IrisDataManager globaldata;
	public static ProjectManager proj;
	public static IrisHotloadManager hotloader;
	public static WandManager wand;
	public static StructureManager struct;
	public static IrisBoardManager board;
	private BoardManager manager;
	private static IrisLock lock = new IrisLock("Iris");

	@Permission
	public static PermissionIris perm;

	@com.volmit.iris.util.Command
	public CommandIris commandIris;

	public Iris()
	{
		IO.delete(new File("iris"));
	}

	@Override
	public void start()
	{

	}

	@Override
	public void stop()
	{

	}

	@Override
	public String getTag(String subTag)
	{
		return C.BOLD + "" + C.DARK_GRAY + "[" + C.BOLD + "" + C.GREEN + "Iris" + C.BOLD + C.DARK_GRAY + "]" + C.RESET + "" + C.GRAY + ": ";
	}

	public void onEnable()
	{
		lock = new IrisLock("Iris");
		instance = this;
		hotloader = new IrisHotloadManager();
		globaldata = new IrisDataManager(getDataFolder());
		wand = new WandManager();
		struct = new StructureManager();
		proj = new ProjectManager();
		board = new IrisBoardManager();
		super.onEnable();
	}

	public void onDisable()
	{
		proj.close();

		for(World i : Bukkit.getWorlds())
		{
			if(i.getGenerator() instanceof IrisChunkGenerator)
			{
				((IrisChunkGenerator) i).close();
			}
		}
		for(GroupedExecutor i : executors)
		{
			i.close();
		}

		executors.clear();
		manager.onDisable();
		Bukkit.getScheduler().cancelTasks(this);
		HandlerList.unregisterAll((Plugin) this);
		super.onDisable();
	}

	public static KList<Class<? extends IrisPostBlockFilter>> loadPostProcessors()
	{
		KList<Class<? extends IrisPostBlockFilter>> g = new KList<Class<? extends IrisPostBlockFilter>>();

		g.add(PostFloatingNibDeleter.class);
		g.add(PostNibSmoother.class);
		g.add(PostPotholeFiller.class);
		g.add(PostSlabber.class);
		g.add(PostWallPatcher.class);
		g.add(PostWaterlogger.class);

		return g;
	}

	@Override
	public boolean onCommand(CommandSender sender, Command command, String label, String[] args)
	{
		return super.onCommand(sender, command, label, args);
	}

	public void imsg(CommandSender s, String msg)
	{
		s.sendMessage(C.GREEN + "[" + C.DARK_GRAY + "Iris" + C.GREEN + "]" + C.GRAY + ": " + msg);
	}

	@Override
	public ChunkGenerator getDefaultWorldGenerator(String worldName, String id)
	{
		return new IrisChunkGenerator(IrisSettings.get().threads);
	}

	public static void msg(String string)
	{
		lock.lock();
		String msg = C.GREEN + "[Iris]: " + C.GRAY + string;
		Bukkit.getConsoleSender().sendMessage(msg);
		lock.unlock();
	}

	public static void warn(String string)
	{
		msg(C.YELLOW + string);
	}

	public static void error(String string)
	{
		msg(C.RED + string);
	}

	public static void verbose(String string)
	{
		msg(C.GRAY + string);
	}

	public static void success(String string)
	{
		msg(C.GREEN + string);
	}

	public static void info(String string)
	{
		msg(C.WHITE + string);
	}

	public void hit(long hits2)
	{
		board.hits.put(hits2);
	}
}
