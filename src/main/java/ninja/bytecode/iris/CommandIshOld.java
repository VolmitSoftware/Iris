package ninja.bytecode.iris;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;

import org.bukkit.ChatColor;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.Sound;
import org.bukkit.block.Block;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;
import org.bukkit.util.Vector;

import mortar.util.text.C;
import ninja.bytecode.iris.controller.WandController;
import ninja.bytecode.iris.generator.genobject.GenObject;
import ninja.bytecode.iris.util.Cuboid;
import ninja.bytecode.iris.util.Cuboid.CuboidDirection;
import ninja.bytecode.iris.util.Direction;
import ninja.bytecode.shuriken.format.Form;

public class CommandIshOld implements CommandExecutor
{
	public void msg(CommandSender s, String msg)
	{
		s.sendMessage(ChatColor.DARK_PURPLE + "[" + ChatColor.GRAY + "Iris" + ChatColor.DARK_PURPLE + "]" + ChatColor.GRAY + ": " + msg);
	}

	@SuppressWarnings("deprecation")
	@Override
	public boolean onCommand(CommandSender sender, Command command, String label, String[] args)
	{
		if(args.length == 0)
		{
			msg(sender, "/ish wand - Get an Iris Wand");
			msg(sender, "/ish save <name> - Save Schematic");
			msg(sender, "/ish load <name> [cursor] - Paste Schematic");
			msg(sender, "/ish expand <amount> - Expand Cuboid in direction");
			msg(sender, "/ish shift <amount> - Shift Cuboid in direction");
			msg(sender, "/ish shrinkwrap - Shrink to blocks");
			msg(sender, "/ish xup - Shift up, Expand up, Contract in.");
			msg(sender, "/ish xvert - Expand up, Expand down, Contract in.");
			msg(sender, "/ish id - What id am i looking at");
		}

		if(args.length > 0)
		{
			if(sender instanceof Player)
			{
				Player p = (Player) sender;
			
				if(args[0].equalsIgnoreCase("xvert"))
				{
					
				}
			}
		}

		return false;
	}
}
