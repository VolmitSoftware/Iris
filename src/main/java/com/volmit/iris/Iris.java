package com.volmit.iris;

import java.io.File;
import java.io.IOException;
import java.lang.reflect.InvocationTargetException;
import java.util.List;

import org.bukkit.Bukkit;
import org.bukkit.ChatColor;
import org.bukkit.World;
import org.bukkit.command.Command;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;
import org.bukkit.event.HandlerList;
import org.bukkit.generator.ChunkGenerator;
import org.bukkit.plugin.Plugin;

import com.google.gson.Gson;
import com.volmit.iris.command.CommandIris;
import com.volmit.iris.command.PermissionIris;
import com.volmit.iris.gen.IrisChunkGenerator;
import com.volmit.iris.gen.post.PostFloatingNibDeleter;
import com.volmit.iris.gen.post.PostNibSmoother;
import com.volmit.iris.gen.post.PostPotholeFiller;
import com.volmit.iris.gen.post.PostSlabber;
import com.volmit.iris.gen.post.PostWallPatcher;
import com.volmit.iris.gen.post.PostWaterlogger;
import com.volmit.iris.object.IrisBiome;
import com.volmit.iris.util.BiomeResult;
import com.volmit.iris.util.BoardManager;
import com.volmit.iris.util.BoardProvider;
import com.volmit.iris.util.BoardSettings;
import com.volmit.iris.util.CNG;
import com.volmit.iris.util.ChronoLatch;
import com.volmit.iris.util.Desc;
import com.volmit.iris.util.Form;
import com.volmit.iris.util.GroupedExecutor;
import com.volmit.iris.util.IO;
import com.volmit.iris.util.IrisLock;
import com.volmit.iris.util.IrisPostBlockFilter;
import com.volmit.iris.util.IrisStructureResult;
import com.volmit.iris.util.J;
import com.volmit.iris.util.JSONException;
import com.volmit.iris.util.JSONObject;
import com.volmit.iris.util.JarScanner;
import com.volmit.iris.util.KList;
import com.volmit.iris.util.KMap;
import com.volmit.iris.util.MortarPlugin;
import com.volmit.iris.util.Permission;
import com.volmit.iris.util.RollingSequence;
import com.volmit.iris.util.ScoreDirection;

public class Iris extends MortarPlugin implements BoardProvider
{
	public static KList<GroupedExecutor> executors = new KList<>();
	public static Iris instance;
	public static IrisDataManager globaldata;
	public static ProjectManager proj;
	public static IrisHotloadManager hotloader;
	public static WandController wand;
	private static String last = "";
	private BoardManager manager;
	private String mem = "...";
	private ChronoLatch cl = new ChronoLatch(1000);
	private ChronoLatch clf = new ChronoLatch(1000);
	private KList<String> lines = new KList<>();
	public RollingSequence hits = new RollingSequence(20);
	public RollingSequence tp = new RollingSequence(100);
	private static IrisLock lock = new IrisLock("Iris");
	public static KList<Class<? extends IrisPostBlockFilter>> postProcessors;

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
		return ChatColor.BOLD + "" + ChatColor.DARK_GRAY + "[" + ChatColor.BOLD + "" + ChatColor.GREEN + "Iris" + ChatColor.BOLD + ChatColor.DARK_GRAY + "]" + ChatColor.RESET + "" + ChatColor.GRAY + ": ";
	}

	public void onEnable()
	{
		lock = new IrisLock("Iris");
		instance = this;
		hotloader = new IrisHotloadManager();
		globaldata = new IrisDataManager(getDataFolder());
		wand = new WandController();
		postProcessors = loadPostProcessors();
		proj = new ProjectManager();
		manager = new BoardManager(this, BoardSettings.builder().boardProvider(this).scoreDirection(ScoreDirection.UP).build());

		J.a(() ->
		{
			try
			{
				writeDocs();
			}

			catch(JSONException | InstantiationException | IllegalAccessException | IllegalArgumentException | InvocationTargetException | NoSuchMethodException | SecurityException | IOException e)
			{
				e.printStackTrace();
			}
		});
		super.onEnable();
	}

	public void onDisable()
	{
		lock.unlock();
		proj.close();

		for(GroupedExecutor i : executors)
		{
			i.close();
		}

		for(World i : Bukkit.getWorlds())
		{
			if(i.getGenerator() instanceof IrisChunkGenerator)
			{
				((IrisChunkGenerator) i).close();
			}
		}

		executors.clear();
		manager.onDisable();
		Bukkit.getScheduler().cancelTasks(this);
		HandlerList.unregisterAll((Plugin) this);
		super.onDisable();
	}

	@Override
	public String getTitle(Player player)
	{
		return ChatColor.GREEN + "Iris";
	}

	@Override
	public List<String> getLines(Player player)
	{
		if(!clf.flip())
		{
			return lines;
		}

		World world = player.getWorld();
		lines.clear();

		if(world.getGenerator() instanceof IrisChunkGenerator)
		{
			IrisChunkGenerator g = (IrisChunkGenerator) world.getGenerator();

			if(cl.flip())
			{
				mem = Form.memSize(g.guessMemoryUsage(), 2);
			}

			int x = player.getLocation().getBlockX();
			int y = player.getLocation().getBlockY();
			int z = player.getLocation().getBlockZ();
			BiomeResult er = g.sampleTrueBiome(x, y, z);
			IrisBiome b = er != null ? er.getBiome() : null;
			IrisStructureResult st = g.getStructure(x, y, z);

			tp.put(g.getMetrics().getSpeed());
			lines.add("&7&m-----------------");
			lines.add(ChatColor.GREEN + "Speed" + ChatColor.GRAY + ": " + ChatColor.BOLD + "" + ChatColor.GRAY + Form.f(g.getMetrics().getPerSecond().getAverage(), 0) + "/s " + Form.duration(g.getMetrics().getTotal().getAverage(), 1) + "");
			lines.add(ChatColor.GREEN + "Generators" + ChatColor.GRAY + ": " + Form.f(CNG.creates));
			lines.add(ChatColor.GREEN + "Noise" + ChatColor.GRAY + ": " + Form.f((int) hits.getAverage()));
			lines.add(ChatColor.GREEN + "Parallax Chunks" + ChatColor.GRAY + ": " + Form.f((int) g.getParallaxMap().getLoadedChunks().size()));
			lines.add(ChatColor.GREEN + "Objects" + ChatColor.GRAY + ": " + Form.f(g.getData().getObjectLoader().count()));
			lines.add(ChatColor.GREEN + "Memory" + ChatColor.GRAY + ": " + mem);

			if(er != null && b != null)
			{
				lines.add(ChatColor.GREEN + "Biome" + ChatColor.GRAY + ": " + b.getName());
				lines.add(ChatColor.GREEN + "File" + ChatColor.GRAY + ": " + b.getLoadKey());
			}

			if(st != null)
			{
				lines.add(ChatColor.GREEN + "Structure" + ChatColor.GRAY + ": " + st.getStructure().getName());
				lines.add(ChatColor.GREEN + "Tile" + ChatColor.GRAY + ": " + st.getTile().toString());
			}

			lines.add("&7&m-----------------");
		}

		else
		{
			lines.add(ChatColor.GREEN + "Join an Iris World!");
		}

		return lines;
	}

	private static KList<Class<? extends IrisPostBlockFilter>> loadPostProcessors()
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

	public void writeDocs() throws IOException, JSONException, InstantiationException, IllegalAccessException, IllegalArgumentException, InvocationTargetException, NoSuchMethodException, SecurityException
	{
		JarScanner j = new JarScanner(getFile(), "ninja.bytecode.iris.object");
		j.scan();
		File of = new File(getDataFolder(), "packs");
		of.mkdirs();
		KMap<String, String> files = new KMap<>();

		for(Class<?> i : j.getClasses())
		{
			if(i.isAnnotationPresent(Desc.class))
			{
				Desc d = i.getAnnotation(Desc.class);
				KList<String> page = new KList<>();
				page.add("# " + i.getSimpleName());
				page.add("> " + d.value());

				page.add("```json");
				page.add(new JSONObject(new Gson().toJson(i.getConstructor().newInstance())).toString(4));
				page.add("```");

				page.add("");
				for(java.lang.reflect.Field k : i.getDeclaredFields())
				{
					if(k.isAnnotationPresent(Desc.class))
					{
						page.add("## " + k.getName());
						page.add("> " + k.getAnnotation(Desc.class).value());
						page.add("");
					}
				}

				String pge = page.toString("\n");
				files.put(i.getSimpleName(), pge);
			}
		}

		for(String i : files.k())
		{
			IO.writeAll(new File(of, i + ".md"), files.get(i));
		}
	}

	@Override
	public boolean onCommand(CommandSender sender, Command command, String label, String[] args)
	{
		return super.onCommand(sender, command, label, args);
	}

	public void imsg(CommandSender s, String msg)
	{
		s.sendMessage(ChatColor.GREEN + "[" + ChatColor.DARK_GRAY + "Iris" + ChatColor.GREEN + "]" + ChatColor.GRAY + ": " + msg);
	}

	@Override
	public ChunkGenerator getDefaultWorldGenerator(String worldName, String id)
	{
		return new IrisChunkGenerator(16);
	}

	public static void msg(String string)
	{
		lock.lock();
		String msg = ChatColor.GREEN + "[Iris]: " + ChatColor.GRAY + string;

		if(last.equals(msg))
		{
			lock.unlock();
			return;
		}

		last = msg;
		Bukkit.getScheduler().scheduleSyncDelayedTask(Iris.instance, () -> Bukkit.getConsoleSender().sendMessage(msg));
		lock.unlock();
	}

	public static void warn(String string)
	{
		msg(ChatColor.YELLOW + string);
	}

	public static void error(String string)
	{
		msg(ChatColor.RED + string);
	}

	public static void verbose(String string)
	{
		msg(ChatColor.GRAY + string);
	}

	public static void success(String string)
	{
		msg(ChatColor.GREEN + string);
	}

	public static void info(String string)
	{
		msg(ChatColor.WHITE + string);
	}

	public void hit(long hits2)
	{
		hits.put(hits2);
	}
}
