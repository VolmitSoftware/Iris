package com.volmit.iris.command;

import org.bukkit.Sound;

import com.volmit.iris.Iris;
import com.volmit.iris.command.util.MortarCommand;
import com.volmit.iris.command.util.MortarSender;
import com.volmit.iris.wand.WandController;

public class CommandIrisObjectWand extends MortarCommand
{
	public CommandIrisObjectWand()
	{
		super("wand", "w");
		requiresPermission(Iris.perm);
		setCategory("Object");
		setDescription("Get an Iris Wand for selecting objects");
	}

	@Override
	public boolean handle(MortarSender sender, String[] args)
	{
		if(!sender.isPlayer())
		{
			sender.sendMessage("You don't have an inventory");
			return true;
		}

		sender.player().getInventory().addItem(WandController.createWand());
		sender.player().playSound(sender.player().getLocation(), Sound.ITEM_ARMOR_EQUIP_NETHERITE, 1f, 1.5f);

		return true;
	}

	@Override
	protected String getArgsUsage()
	{
		return "[subcommand]";
	}
}
