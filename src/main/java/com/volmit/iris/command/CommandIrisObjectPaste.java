package com.volmit.iris.command;

import java.io.File;
import java.io.IOException;
import java.util.Set;

import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.Sound;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;

import com.volmit.iris.Iris;
import com.volmit.iris.WandController;
import com.volmit.iris.object.IrisObject;
import com.volmit.iris.util.MortarCommand;
import com.volmit.iris.util.MortarSender;

public class CommandIrisObjectPaste extends MortarCommand
{
	public CommandIrisObjectPaste()
	{
		super("paste", "pasta");
		requiresPermission(Iris.perm);
		setCategory("Object");
		setDescription("Paste an object");
	}

	@Override
	public boolean handle(MortarSender sender, String[] args)
	{
		if(!sender.isPlayer())
		{
			sender.sendMessage("You don't have a wand");
			return true;
		}

		Player p = sender.player();
		File file = new File(Iris.instance.getDataFolder(), "objects/" + args[0] + ".iob");
		boolean intoWand = false;

		for(String i : args)
		{
			if(i.equalsIgnoreCase("-edit"))
			{
				intoWand = true;
			}
		}

		if(!file.exists())
		{
			sender.sendMessage("Can't find " + "objects/" + args[0] + ".iob");
		}

		ItemStack wand = ((Player) sender).getInventory().getItemInMainHand();
		IrisObject o = new IrisObject(0, 0, 0);

		try
		{
			o.read(new File(Iris.instance.getDataFolder(), "objects/" + args[0] + ".iob"));
			sender.sendMessage("Loaded " + "objects/" + args[0] + ".iob");

			((Player) sender).getWorld().playSound(((Player) sender).getLocation(), Sound.BLOCK_ENCHANTMENT_TABLE_USE, 1f, 1.5f);
			Location block = ((Player) sender).getTargetBlock((Set<Material>) null, 256).getLocation().clone().add(0, 1, 0);

			if(intoWand && WandController.isWand(wand))
			{
				wand = WandController.createWand(block.clone().subtract(o.getCenter()).add(o.getW() - 1, o.getH(), o.getD() - 1), block.clone().subtract(o.getCenter()));
				p.getInventory().setItemInMainHand(wand);
				sender.sendMessage("Updated wand for " + "objects/" + args[0] + ".iob");
			}

			WandController.pasteSchematic(o, block);
			sender.sendMessage("Placed " + "objects/" + args[0] + ".iob");
		}

		catch(IOException e)
		{
			sender.sendMessage("Failed to load " + "objects/" + args[0] + ".iob");
			e.printStackTrace();
		}

		return true;
	}

	@Override
	protected String getArgsUsage()
	{
		return "[name] [-edit]";
	}
}
