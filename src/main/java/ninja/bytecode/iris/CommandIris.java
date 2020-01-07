package ninja.bytecode.iris;

import org.bukkit.Bukkit;
import org.bukkit.ChatColor;
import org.bukkit.GameMode;
import org.bukkit.Location;
import org.bukkit.World;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;

public class CommandIris implements CommandExecutor
{
	public void msg(CommandSender s, String msg)
	{
		s.sendMessage(ChatColor.DARK_PURPLE + "[" + ChatColor.GRAY + "Iris" + ChatColor.DARK_PURPLE + "]" + ChatColor.GRAY + ": " + msg);
	}

	@Override
	public boolean onCommand(CommandSender sender, Command command, String label, String[] args)
	{
		if(args.length == 0)
		{
			msg(sender, "/iris gen - Gen a new Iris World");
			msg(sender, "/ish - Iris Schematic Commands");
		}
		
		if(args.length > 0)
		{
			if(args[0].equalsIgnoreCase("gen"))
			{
				if(sender instanceof Player)
				{
					World wold = ((Player)sender).getWorld();
					World w = Iris.instance.createIrisWorld();
					((Player)sender).teleport(new Location(w, 0, 256, 0));
					((Player)sender).setFlying(true);
					((Player)sender).setGameMode(GameMode.CREATIVE);
					wold.setAutoSave(false);
					Bukkit.unloadWorld(wold, false);
				}
				
				else
				{
					Iris.instance.createIrisWorld();
				}
			}
		}

		return false;
	}
}
