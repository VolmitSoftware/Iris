package ninja.bytecode.iris;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.util.Enumeration;
import java.util.UUID;
import java.util.function.Function;
import java.util.zip.ZipEntry;
import java.util.zip.ZipFile;

import org.bukkit.Bukkit;
import org.bukkit.Chunk;
import org.bukkit.GameMode;
import org.bukkit.Location;
import org.bukkit.World;
import org.bukkit.WorldCreator;
import org.bukkit.entity.Player;
import org.bukkit.event.HandlerList;
import org.bukkit.event.Listener;
import org.bukkit.generator.ChunkGenerator;
import org.bukkit.plugin.Plugin;
import org.bukkit.plugin.java.JavaPlugin;
import org.bukkit.util.Vector;

import net.md_5.bungee.api.ChatColor;
import ninja.bytecode.iris.generator.IrisGenerator;
import ninja.bytecode.iris.schematic.Schematic;
import ninja.bytecode.iris.schematic.SchematicGroup;
import ninja.bytecode.iris.spec.IrisBiome;
import ninja.bytecode.iris.spec.IrisDimension;
import ninja.bytecode.iris.spec.IrisPack;
import ninja.bytecode.iris.util.Direction;
import ninja.bytecode.shuriken.bench.PrecisionStopwatch;
import ninja.bytecode.shuriken.bench.Profiler;
import ninja.bytecode.shuriken.collections.GMap;
import ninja.bytecode.shuriken.collections.GSet;
import ninja.bytecode.shuriken.execution.J;
import ninja.bytecode.shuriken.execution.TaskExecutor;
import ninja.bytecode.shuriken.execution.TaskExecutor.TaskGroup;
import ninja.bytecode.shuriken.format.F;
import ninja.bytecode.shuriken.io.IO;
import ninja.bytecode.shuriken.json.JSONException;
import ninja.bytecode.shuriken.json.JSONObject;
import ninja.bytecode.shuriken.logging.L;

public class Iris extends JavaPlugin implements Listener
{
	public static GSet<Chunk> refresh = new GSet<>();
	public static Profiler profiler;
	public static TaskExecutor genPool;
	public static IrisGenerator gen;
	public static Settings settings;
	public static Iris instance;
	public static GMap<String, GMap<String, Function<Vector, Double>>> values;
	public static GMap<String, IrisDimension> dimensions;
	public static GMap<String, IrisBiome> biomes;
	public static GMap<String, SchematicGroup> schematics;

	public void onEnable()
	{
		L.consoleConsumer = (s) -> Bukkit.getConsoleSender().sendMessage(s);
		Direction.calculatePermutations();
		dimensions = new GMap<>();
		biomes = new GMap<>();
		schematics = new GMap<>();
		profiler = new Profiler(512);
		values = new GMap<>();
		instance = this;
		settings = new Settings();
		J.attempt(() -> createTempCache());
		loadContent();
		gen = new IrisGenerator();
		genPool = new TaskExecutor(getTC(), settings.performance.threadPriority, "Iris Generator");
		getServer().getPluginManager().registerEvents((Listener) this, this);
		getCommand("iris").setExecutor(new CommandIris());
		getCommand("ish").setExecutor(new CommandIsh());
		new WandManager();
		loadComplete();
	}

	public static void started(String obj)
	{
		profiler.start(obj);
	}

	public static void stopped(String obj)
	{
		profiler.stop(obj);
	}

	private void loadComplete()
	{
		if(settings.performance.loadonstart)
		{
			GSet<String> ws = new GSet<>();

			World w = createIrisWorld();
			for(Player i : Bukkit.getOnlinePlayers())
			{
				Location m = i.getLocation();
				ws.add(i.getWorld().getName());
				i.teleport(new Location(w, m.getX(), m.getY(), m.getZ(), m.getYaw(), m.getPitch()));
				i.setFlying(true);
				i.setGameMode(GameMode.SPECTATOR);
			}

			for(String i : ws)
			{
				Bukkit.unloadWorld(i, false);
			}
		}
	}

	private static File internalResource(String resource)
	{
		if(new File(Iris.instance.getDataFolder(), "pack").exists())
		{
			return new File(Iris.instance.getDataFolder(), resource);
		}

		return new File(System.getProperty("java.io.tmpdir") + "/Iris/" + resource);
	}

	private void createTempCache() throws Throwable
	{
		File temp = new File(System.getProperty("java.io.tmpdir") + "/Iris/");
		temp.mkdirs();
		L.i("Iris Cache: " + temp.getAbsolutePath());
		ZipFile zipFile = new ZipFile(getFile());
		Enumeration<? extends ZipEntry> entries = zipFile.entries();
		while(entries.hasMoreElements())
		{
			ZipEntry entry = entries.nextElement();
			if(entry.getName().startsWith("pack/") && !entry.isDirectory())
			{
				File f = new File(temp, entry.getName());
				f.getParentFile().mkdirs();
				InputStream stream = zipFile.getInputStream(entry);
				FileOutputStream fos = new FileOutputStream(f);
				IO.fullTransfer(stream, fos, 16921);
				fos.close();
				stream.close();
			}
		}

		zipFile.close();
	}

	private void loadContent()
	{
		PrecisionStopwatch p = PrecisionStopwatch.start();
		L.i("Loading Content");

		try
		{
			IrisPack master = new IrisPack(loadJSON("pack/manifest.json"));
			master.load();
		}

		catch(Throwable e)
		{
			e.printStackTrace();
		}

		int m = 0;

		for(SchematicGroup i : schematics.v())
		{
			m += i.size();
		}

		L.v(ChatColor.LIGHT_PURPLE + "Processing Content");

		TaskExecutor exf = new TaskExecutor(settings.performance.compilerThreads, settings.performance.compilerPriority, "Iris Compiler");
		TaskGroup gg = exf.startWork();
		for(SchematicGroup i : schematics.v())
		{
			gg.queue(i::processVariants);
		}
		gg.execute();
		exf.close();
		L.i(ChatColor.LIGHT_PURPLE + "Dimensions: " + ChatColor.WHITE + dimensions.size());
		L.i(ChatColor.LIGHT_PURPLE + "Biomes: " + ChatColor.WHITE + biomes.size());
		L.i(ChatColor.LIGHT_PURPLE + "Object Groups: " + ChatColor.WHITE + F.f(schematics.size()));
		L.i(ChatColor.LIGHT_PURPLE + "Objects: " + ChatColor.WHITE + F.f(m));
		L.i(ChatColor.LIGHT_PURPLE + "Compilation Time: " + ChatColor.WHITE + F.duration(p.getMilliseconds(), 2));
		L.i(ChatColor.GREEN + "Iris Dimensions Successfully Compiled!");
		L.flush();
	}

	private int getTC()
	{
		switch(settings.performance.performanceMode)
		{
			case HALF_CPU:
				return Math.max(Runtime.getRuntime().availableProcessors() / 2, 1);
			case MATCH_CPU:
				return Runtime.getRuntime().availableProcessors();
			case SINGLE_THREADED:
				return 1;
			case DOUBLE_CPU:
				return Runtime.getRuntime().availableProcessors() * 2;
			case UNLIMITED:
				return -1;
			case EXPLICIT:
				return settings.performance.threadCount;
			default:
				break;
		}

		return Math.max(Runtime.getRuntime().availableProcessors() / 2, 1);
	}

	public void onDisable()
	{
		genPool.close();
		HandlerList.unregisterAll((Plugin) this);
	}

	@Override
	public ChunkGenerator getDefaultWorldGenerator(String worldName, String id)
	{
		return new IrisGenerator();
	}

	public World createIrisWorld()
	{
		World ww = Bukkit.createWorld(new WorldCreator("iris-worlds/" + UUID.randomUUID().toString()).generator(new IrisGenerator()).seed(5944323));
		ww.setSpawnFlags(false, false);
		ww.setAutoSave(false);
		ww.setKeepSpawnInMemory(false);
		ww.setSpawnLocation(0, 256, 0);
		return ww;
	}

	public static void v(String w, String t, Function<Vector, Double> d)
	{
		if(!values.containsKey(w))
		{
			values.put(w, new GMap<>());
		}

		values.get(w).put(t, d);
	}

	public static IrisDimension loadDimension(String s) throws JSONException, IOException
	{
		return new IrisDimension(loadJSON("pack/dimensions/" + s + ".json"));
	}

	public static IrisBiome loadBiome(String s) throws JSONException, IOException
	{
		return new IrisBiome(loadJSON("pack/biomes/" + s + ".json"));
	}

	public static SchematicGroup loadSchematicGroup(String s)
	{
		SchematicGroup g = SchematicGroup.load("pack/objects/" + s);

		if(g != null)
		{
			schematics.put(s, g);
			return g;
		}

		L.i("Cannot load Object Group: " + s);

		return null;
	}

	public static Schematic loadSchematic(String s) throws IOException
	{
		return Schematic.load(loadResource("pack/objects/" + s + ".ish"));
	}

	public static JSONObject loadJSON(String s) throws JSONException, IOException
	{
		return new JSONObject(IO.readAll(loadResource(s)));
	}

	public static File loadFolder(String string)
	{
		File internal = internalResource(string);

		if(internal.exists())
		{
			return internal;
		}

		L.f(ChatColor.RED + "Cannot find folder: " + internal.getAbsolutePath());
		return null;
	}

	public static InputStream loadResource(String string) throws IOException
	{
		File internal = internalResource(string);

		if(internal.exists())
		{
			L.v(ChatColor.DARK_PURPLE + "Loading Resource: " + ChatColor.GRAY + internal.getAbsolutePath());
			L.flush();
			return new FileInputStream(internal);
		}

		else
		{
			L.f(ChatColor.RED + "Cannot find Resource: " + ChatColor.YELLOW + internal.getAbsolutePath());
			return null;
		}
	}
}
