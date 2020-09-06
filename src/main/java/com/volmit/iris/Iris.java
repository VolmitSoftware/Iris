package com.volmit.iris;

import java.io.BufferedInputStream;
import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.lang.reflect.InvocationTargetException;
import java.net.URL;

import org.bstats.bukkit.Metrics;
import org.bukkit.Bukkit;
import org.bukkit.World;
import org.bukkit.command.Command;
import org.bukkit.command.CommandSender;
import org.bukkit.craftbukkit.v1_16_R2.CraftServer;
import org.bukkit.event.HandlerList;
import org.bukkit.generator.ChunkGenerator;
import org.bukkit.plugin.Plugin;

import com.volmit.iris.command.CommandIris;
import com.volmit.iris.command.PermissionIris;
import com.volmit.iris.gen.IrisChunkGenerator;
import com.volmit.iris.gen.post.PostFloatingNibDeleter;
import com.volmit.iris.gen.post.PostFoliageCleaner;
import com.volmit.iris.gen.post.PostNibSmoother;
import com.volmit.iris.gen.post.PostPotholeFiller;
import com.volmit.iris.gen.post.PostSlabber;
import com.volmit.iris.gen.post.PostWallPatcher;
import com.volmit.iris.gen.post.PostWaterlogger;
import com.volmit.iris.link.MultiverseCoreLink;
import com.volmit.iris.util.C;
import com.volmit.iris.util.Form;
import com.volmit.iris.util.GroupedExecutor;
import com.volmit.iris.util.IO;
import com.volmit.iris.util.IrisLock;
import com.volmit.iris.util.IrisPostBlockFilter;
import com.volmit.iris.util.J;
import com.volmit.iris.util.KList;
import com.volmit.iris.util.MortarPlugin;
import com.volmit.iris.util.NMSVersion;
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
	public static String nmsTag = findNMSTag();
	public static MultiverseCoreLink linkMultiverseCore;
	private static IrisLock lock = new IrisLock("Iris");
	public static boolean customModels = doesSupportCustomModels();
	public static boolean awareEntities = doesSupportAwareness();
	public static boolean biome3d = doesSupport3DBiomes();
	public static boolean lowMemoryMode = false;

	@Permission
	public static PermissionIris perm;

	@com.volmit.iris.util.Command
	public CommandIris commandIris;

	public Iris()
	{
		IO.delete(new File("iris"));
		lowMemoryMode = Runtime.getRuntime().maxMemory() < 4 * 1000 * 1000 * 1000;
	}

	private static String findNMSTag()
	{
		try
		{
			return Bukkit.getServer().getClass().getCanonicalName().split("\\Q.\\E")[3];
		}

		catch(Throwable e)
		{
			Iris.error("Failed to determine server nms version!");
			e.printStackTrace();
		}

		return "UNKNOWN NMS VERSION";
	}

	private static boolean doesSupport3DBiomes()
	{
		int v = Integer.valueOf(Bukkit.getBukkitVersion().split("\\Q-\\E")[0].split("\\Q.\\E")[1]);

		return v >= 15;
	}

	private static boolean doesSupportCustomModels()
	{
		int v = Integer.valueOf(Bukkit.getBukkitVersion().split("\\Q-\\E")[0].split("\\Q.\\E")[1]);

		return v >= 14;
	}

	private static boolean doesSupportAwareness()
	{
		int v = Integer.valueOf(Bukkit.getBukkitVersion().split("\\Q-\\E")[0].split("\\Q.\\E")[1]);

		return v >= 15;
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
		linkMultiverseCore = new MultiverseCoreLink();
		J.a(() -> IO.delete(getTemp()));
		J.a(() -> bstats());
		J.s(this::splash, 20);
		super.onEnable();
	}

	private void bstats()
	{
		new Metrics(this, 8757);
	}

	public static File getTemp()
	{
		return instance.getDataFolder("cache", "temp");
	}

	public void onDisable()
	{
		if(IrisSettings.get().isStudio())
		{
			proj.close();

			for(World i : Bukkit.getWorlds())
			{
				if(i.getGenerator() instanceof IrisChunkGenerator)
				{
					((IrisChunkGenerator) i.getGenerator()).close();
				}
			}

			for(GroupedExecutor i : executors)
			{
				i.close();
			}
		}

		executors.clear();
		board.disable();
		Bukkit.getScheduler().cancelTasks(this);
		HandlerList.unregisterAll((Plugin) this);
		super.onDisable();
	}

	public static KList<Class<? extends IrisPostBlockFilter>> loadPostProcessors()
	{
		KList<Class<? extends IrisPostBlockFilter>> g = new KList<Class<? extends IrisPostBlockFilter>>();

		g.add(PostFloatingNibDeleter.class);
		g.add(PostFoliageCleaner.class);
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

	public static File getCached(String name, String url)
	{
		String h = IO.hash(name + "@" + url);
		File f = Iris.instance.getDataFile("cache", h.substring(0, 2), h.substring(3, 5), h);

		if(!f.exists())
		{
			try(BufferedInputStream in = new BufferedInputStream(new URL(url).openStream()); FileOutputStream fileOutputStream = new FileOutputStream(f))
			{
				byte dataBuffer[] = new byte[1024];
				int bytesRead;
				while((bytesRead = in.read(dataBuffer, 0, 1024)) != -1)
				{
					fileOutputStream.write(dataBuffer, 0, bytesRead);
					Iris.verbose("Aquiring " + name);
				}
			}

			catch(IOException e)
			{

			}
		}

		return f.exists() ? f : null;
	}

	public static String getNonCached(String name, String url)
	{
		String h = IO.hash(name + "*" + url);
		File f = Iris.instance.getDataFile("cache", h.substring(0, 2), h.substring(3, 5), h);

		try(BufferedInputStream in = new BufferedInputStream(new URL(url).openStream()); FileOutputStream fileOutputStream = new FileOutputStream(f))
		{
			byte dataBuffer[] = new byte[1024];
			int bytesRead;
			while((bytesRead = in.read(dataBuffer, 0, 1024)) != -1)
			{
				fileOutputStream.write(dataBuffer, 0, bytesRead);
			}
		}

		catch(IOException e)
		{

		}

		try
		{
			return IO.readAll(f);
		}

		catch(IOException e)
		{

		}

		return "";
	}

	public static File getNonCachedFile(String name, String url)
	{
		String h = IO.hash(name + "*" + url);
		File f = Iris.instance.getDataFile("cache", h.substring(0, 2), h.substring(3, 5), h);

		try(BufferedInputStream in = new BufferedInputStream(new URL(url).openStream()); FileOutputStream fileOutputStream = new FileOutputStream(f))
		{
			byte dataBuffer[] = new byte[1024];
			int bytesRead;
			while((bytesRead = in.read(dataBuffer, 0, 1024)) != -1)
			{
				fileOutputStream.write(dataBuffer, 0, bytesRead);
			}
		}

		catch(IOException e)
		{

		}

		return f;
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

	public void splash()
	{
		//@builder
		String padd = Form.repeat(" ", 8);
		String padd2 = Form.repeat(" ", 4);
		String[] info = {
				"",
				"",
				"",
				"",
				"",
				padd2 + C.GREEN + " Iris",
				padd2 + C.GRAY + " by "+C.randomColor()+"V"+C.randomColor()+"o"+C.randomColor()+"l"+C.randomColor()+"m"+C.randomColor()+"i"+C.randomColor()+"t"+C.randomColor()+"S"+C.randomColor()+"o"+C.randomColor()+"f"+C.randomColor()+"t"+C.randomColor()+"w"+C.randomColor()+"a"+C.randomColor()+"r"+C.randomColor()+"e",
				padd2 + C.GRAY + " v" + getDescription().getVersion(),
		};
        String[] splash = {
                padd + C.GRAY + "   @@@@@@@@@@@@@@"+ C.DARK_GRAY +"@@@",
                padd + C.GRAY + " @@&&&&&&&&&"+ C.DARK_GRAY +"&&&&&&" + C.GREEN + "   .(((()))).                     ",
                padd + C.GRAY + "@@@&&&&&&&&"+ C.DARK_GRAY +"&&&&&" + C.GREEN + "  .((((((())))))).                  ",
                padd + C.GRAY + "@@@&&&&&"+ C.DARK_GRAY +"&&&&&&&" + C.GREEN + "  ((((((((()))))))))               " + C.GRAY + " @",
                padd + C.GRAY + "@@@&&&&"+ C.DARK_GRAY +"@@@@@&" + C.GREEN + "    ((((((((-)))))))))              " + C.GRAY + " @@",
                padd + C.GRAY + "@@@&&" + C.GREEN + "            ((((((({ }))))))))           " + C.GRAY + " &&@@@",
                padd + C.GRAY + "@@" + C.GREEN + "               ((((((((-)))))))))    " + C.DARK_GRAY + "&@@@@@"+ C.GRAY +"&&&&@@@",
                padd + C.GRAY + "@" + C.GREEN + "                ((((((((()))))))))  " + C.DARK_GRAY + "&&&&&"+ C.GRAY +"&&&&&&&@@@",
                padd + C.GRAY + "" + C.GREEN + "                  '((((((()))))))'  " + C.DARK_GRAY + "&&&&&"+ C.GRAY +"&&&&&&&&@@@",
                padd + C.GRAY + "" + C.GREEN + "                     '(((())))'   " + C.DARK_GRAY + "%%&&&&&&"+ C.GRAY +"&&&&&&&@@",
                padd + C.GRAY + "                               "+C.DARK_GRAY+"@@@"+C.GRAY+"@@@@@@@@@@@@@@"
        };
		//@done
		Iris.info(Bukkit.getVersion());
		Iris.info(Bukkit.getBukkitVersion() + "   bk");
		for(int i = 0; i < info.length; i++)
		{
			splash[i] += info[i];
		}

		Iris.info("\n\n " + new KList<String>(splash).toString("\n") + "\n");

		if(lowMemoryMode)
		{
			Iris.verbose("* Low Memory mode Activated! For better performance, allocate 4gb or more to this server.");
		}

		if(!biome3d)
		{
			Iris.verbose("* This version of minecraft does not support 3D biomes (1.15 and up). Iris will generate as normal, but biome colors will not vary underground & in the sky.");
		}

		if(!customModels)
		{
			Iris.verbose("* This version of minecraft does not support custom model data in loot items (1.14 and up). Iris will generate as normal, but loot will not have custom models.");
		}

		if(!doesSupportAwareness())
		{
			Iris.verbose("* This version of minecraft does not support entity awareness.");
		}
	}

	public static String nmsTag()
	{
		return nmsTag;
	}
}
