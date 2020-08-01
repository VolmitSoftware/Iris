package com.volmit.iris.command;

import java.io.File;
import java.io.IOException;

import org.bukkit.Sound;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;

import com.volmit.iris.Iris;
import com.volmit.iris.WandController;
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

		Player p = sender.player();
		ItemStack wand = p.getInventory().getItemInMainHand();
		IrisObject o = WandController.createSchematic(wand);

		try
		{
			o.write(new File(Iris.instance.getDataFolder(), "objects/" + args[0] + ".iob"));
			sender.sendMessage("Saved " + "objects/" + args[0] + ".iob");
			p.getWorld().playSound(p.getLocation(), Sound.BLOCK_ENCHANTMENT_TABLE_USE, 1f, 1.5f);
		}

		catch(IOException e)
		{
			sender.sendMessage("Failed to save " + "objects/" + args[0] + ".iob. Are you holding your wand?");

			e.printStackTrace();
		}

		return true;
	}

	@Override
	protected String getArgsUsage()
	{
		return "[name]";
	}
}
