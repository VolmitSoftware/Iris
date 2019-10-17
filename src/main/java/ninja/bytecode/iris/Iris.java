package ninja.bytecode.iris;

import java.util.UUID;

import org.bukkit.Bukkit;
import org.bukkit.GameMode;
import org.bukkit.Location;
import org.bukkit.World;
import org.bukkit.WorldCreator;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerCommandPreprocessEvent;
import org.bukkit.generator.ChunkGenerator;
import org.bukkit.plugin.java.JavaPlugin;

import ninja.bytecode.shuriken.collections.GSet;
import ninja.bytecode.shuriken.execution.TaskExecutor;

public class Iris extends JavaPlugin implements Listener
{
	public static TaskExecutor noisePool;
	public static IrisGenerator gen;
	public static Settings settings;
	public static Iris instance;

	public void onEnable()
	{
		instance = this;
		settings = new Settings();
		gen = new IrisGenerator();
		noisePool = new TaskExecutor(settings.performance.threadCount, settings.performance.threadPriority, "Iris Noise Generator");
		getServer().getPluginManager().registerEvents((Listener) this, this);

		// Debug world regens
		GSet<String> ws = new GSet<>();
		World w = createIrisWorld();
		for(Player i : Bukkit.getOnlinePlayers())
		{
			ws.add(i.getWorld().getName());
			i.teleport(new Location(w, 0, 256, 0));
			i.setFlying(true);
			i.setGameMode(GameMode.CREATIVE);
		}

		for(String i : ws)
		{
			Bukkit.unloadWorld(i, false);
		}
	}

	public void onDisable()
	{
		noisePool.close();
	}

	@Override
	public ChunkGenerator getDefaultWorldGenerator(String worldName, String id)
	{
		return new IrisGenerator();
	}

	@EventHandler
	public void on(PlayerCommandPreprocessEvent e)
	{
		if(e.getMessage().toLowerCase().equals("/iris"))
		{
			World wold = e.getPlayer().getWorld();
			World w = createIrisWorld();
			e.getPlayer().teleport(new Location(w, 0, 256, 0));
			e.getPlayer().setFlying(true);
			e.getPlayer().setGameMode(GameMode.CREATIVE);
			e.setCancelled(true);
			wold.setAutoSave(false);
			Bukkit.unloadWorld(wold, false);
		}
	}

	private World createIrisWorld()
	{
		World ww = Bukkit.createWorld(new WorldCreator("iris-worlds/" + UUID.randomUUID().toString()).generator(new IrisGenerator()).seed(0));
		ww.setSpawnFlags(false, false);
		ww.setAutoSave(false);
		ww.setKeepSpawnInMemory(false);
		ww.setSpawnLocation(0, 256, 0);
		return ww;
	}
}
