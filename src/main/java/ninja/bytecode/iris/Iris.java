package ninja.bytecode.iris;

import java.io.File;
import java.util.UUID;

import org.bukkit.Bukkit;
import org.bukkit.ChatColor;
import org.bukkit.GameMode;
import org.bukkit.Location;
import org.bukkit.World;
import org.bukkit.WorldCreator;
import org.bukkit.command.Command;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;
import org.bukkit.generator.ChunkGenerator;
import org.bukkit.plugin.java.JavaPlugin;

import ninja.bytecode.iris.util.IO;
import ninja.bytecode.iris.util.KMap;

public class Iris extends JavaPlugin
{
	public static Iris instance;
	public static IrisDataManager data;
	public static IrisHotloadManager hotloader;

	public Iris()
	{
		IO.delete(new File("iris"));
	}

	public void onEnable()
	{
		instance = this;
		hotloader = new IrisHotloadManager();
		data = new IrisDataManager(getDataFolder());
		Bukkit.getScheduler().scheduleSyncDelayedTask(this, () ->
		{
			for(World i : Bukkit.getWorlds())
			{
				if(i.getName().startsWith("iris/"))
				{
					Bukkit.unloadWorld(i, false);
				}
			}

			World world = Bukkit.createWorld(new WorldCreator("iris/" + UUID.randomUUID()).generator(new IrisGenerator("overworld")));

			for(Player i : Bukkit.getOnlinePlayers())
			{
				i.teleport(new Location(world, 0, 100, 0));

				Bukkit.getScheduler().scheduleSyncDelayedTask(this, () ->
				{
					i.setGameMode(GameMode.SPECTATOR);
				}, 5);
			}
		});

	}

	public void onDisable()
	{

	}

	@Override
	public boolean onCommand(CommandSender sender, Command command, String label, String[] args)
	{
		return false;
	}

	@Override
	public ChunkGenerator getDefaultWorldGenerator(String worldName, String id)
	{
		return new IrisGenerator("overworld");
	}

	public static void msg(String string)
	{
		Bukkit.getConsoleSender().sendMessage(ChatColor.GREEN + "[Iris]: " + ChatColor.GRAY + string);
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
}
