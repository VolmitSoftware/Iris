package ninja.bytecode.iris;

import java.util.UUID;

import org.bukkit.Bukkit;
import org.bukkit.GameMode;
import org.bukkit.Location;
import org.bukkit.World;
import org.bukkit.WorldCreator;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerCommandPreprocessEvent;
import org.bukkit.generator.ChunkGenerator;
import org.bukkit.plugin.java.JavaPlugin;

import ninja.bytecode.shuriken.execution.TaskExecutor;

public class Iris extends JavaPlugin implements Listener
{
	public static TaskExecutor executor;
	
	public void onEnable()
	{
		executor = new TaskExecutor(1, Thread.MIN_PRIORITY, "Iris Generator");
		getServer().getPluginManager().registerEvents((Listener) this, this);
	}
	
	public void onDisable()
	{
		executor.close();
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
			World ww = Bukkit.createWorld(new WorldCreator("iris-worlds/" + UUID.randomUUID().toString())
					.generator(new IrisGenerator())
					.seed(0));
			ww.setSpawnFlags(false, false);
			ww.setAutoSave(false);
			ww.setKeepSpawnInMemory(false);
			ww.setSpawnLocation(0, 256, 0);
			e.getPlayer().teleport(new Location(ww, 0, 256, 0));
			e.getPlayer().setFlying(true);
			e.getPlayer().setGameMode(GameMode.CREATIVE);
			e.setCancelled(true);
		}
	}
}
