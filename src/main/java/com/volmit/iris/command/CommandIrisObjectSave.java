package com.volmit.iris.command;

import java.io.File;

import org.bukkit.Sound;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;

import com.volmit.iris.Iris;
import com.volmit.iris.WandManager;
import com.volmit.iris.object.IrisObject;
import com.volmit.iris.util.MortarCommand;
import com.volmit.iris.util.MortarSender;

public class CommandIrisObjectSave extends MortarCommand
{
	public CommandIrisObjectSave()
	{
		super("save");
		requiresPermission(Iris.perm);
		setCategory("Object");
		setDescription("Save an object");
	}

	@Override
	public boolean handle(MortarSender sender, String[] args)
	{
		if(!sender.isPlayer())
		{
			sender.sendMessage("You don't have a wand");
			return true;
		}

		if(args.length < 2)
		{
			sender.sendMessage("/iris o save <PROJECT> <object>");
			sender.sendMessage("I.e. /iris o save overworld some-tree/tree1");
			return true;
		}

		try
		{
			boolean overwrite = false;

			for(String i : args)
			{
				if(i.equals("-o"))
				{
					overwrite = true;
				}
			}

			Player p = sender.player();
			ItemStack wand = p.getInventory().getItemInMainHand();
			IrisObject o = WandManager.createSchematic(wand);
			File file = Iris.instance.getDataFile("packs", args[0], "objects", args[1] + ".iob");

			if(file.exists())
			{
				if(!overwrite)
				{
					sender.sendMessage("File Exists. Overwrite by adding -o");
					return true;
				}
			}

			o.write(file);
			sender.sendMessage("Saved " + args[1]);
			p.getWorld().playSound(p.getLocation(), Sound.BLOCK_ENCHANTMENT_TABLE_USE, 1f, 1.5f);
		}

		catch(Throwable e)
		{
			sender.sendMessage("Failed to save " + args[1] + ". Are you holding your wand?");

			e.printStackTrace();
		}

		return true;
	}

	@Override
	protected String getArgsUsage()
	{
		return "[project] [name]";
	}
}
