package ninja.bytecode.iris;

import java.util.UUID;
import java.util.function.Function;

import org.bukkit.Bukkit;
import org.bukkit.GameMode;
import org.bukkit.Location;
import org.bukkit.World;
import org.bukkit.WorldCreator;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.HandlerList;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerCommandPreprocessEvent;
import org.bukkit.generator.ChunkGenerator;
import org.bukkit.plugin.Plugin;
import org.bukkit.plugin.java.JavaPlugin;
import org.bukkit.util.Vector;

import ninja.bytecode.iris.generator.IrisGenerator;
import ninja.bytecode.iris.generator.biome.IrisBiome;
import ninja.bytecode.shuriken.bench.Profiler;
import ninja.bytecode.shuriken.collections.GMap;
import ninja.bytecode.shuriken.collections.GSet;
import ninja.bytecode.shuriken.execution.TaskExecutor;

public class Iris extends JavaPlugin implements Listener
{
	public static Profiler profiler;
	public static TaskExecutor genPool;
	public static IrisGenerator gen;
	public static Settings settings;
	public static Iris instance;
	public static GMap<String, GMap<String, Function<Vector, Double>>> values;

	public void onEnable()
	{
		profiler = new Profiler(512);
		values = new GMap<>();
		instance = this;
		settings = new Settings();
		gen = new IrisGenerator();
		genPool = new TaskExecutor(getTC(), settings.performance.threadPriority, "Iris Generator");
		getServer().getPluginManager().registerEvents((Listener) this, this);
		new WandManager();
		
		// Debug world regens
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
		
		Bukkit.getScheduler().scheduleSyncRepeatingTask(this, () -> {
			for(World i : Bukkit.getWorlds())
			{
				if(i.getGenerator() instanceof IrisGenerator)
				{
					for(Player j : i.getPlayers())
					{
						IrisBiome biome = IrisBiome.findByBiome(j.getLocation().getBlock().getBiome());
						biome.applyEffects(j);
					}
				}
			}
		}, 0, 15);
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

	@EventHandler
	public void on(PlayerCommandPreprocessEvent e)
	{
		if(e.getMessage().toLowerCase().equals("/iris gen"))
		{
			e.setCancelled(true);
			World wold = e.getPlayer().getWorld();
			World w = createIrisWorld();
			e.getPlayer().teleport(new Location(w, 0, 256, 0));
			e.getPlayer().setFlying(true);
			e.getPlayer().setGameMode(GameMode.CREATIVE);
			wold.setAutoSave(false);
			Bukkit.unloadWorld(wold, false);
		}
	}

	private World createIrisWorld()
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
}
