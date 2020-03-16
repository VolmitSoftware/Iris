package ninja.bytecode.iris;

import java.util.UUID;

import org.bukkit.Bukkit;
import org.bukkit.GameMode;
import org.bukkit.Location;
import org.bukkit.World;
import org.bukkit.WorldCreator;
import org.bukkit.command.Command;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;
import org.bukkit.generator.ChunkGenerator;
import org.bukkit.plugin.java.JavaPlugin;

public class Iris extends JavaPlugin
{
	public void onEnable()
	{

		Bukkit.getScheduler().scheduleSyncDelayedTask(this, () ->
		{
			for(World i : Bukkit.getWorlds())
			{
				if(i.getName().startsWith("iris/"))
				{
					Bukkit.unloadWorld(i, false);
				}
			}

			World world = Bukkit.createWorld(new WorldCreator("iris/" + UUID.randomUUID()).generator(new IrisGenerator()));

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
		return new IrisGenerator();
	}
}
