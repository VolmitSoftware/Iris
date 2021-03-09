package com.volmit.iris.manager.command.object;

import com.volmit.iris.util.KList;
import org.bukkit.Sound;

import com.volmit.iris.Iris;
import com.volmit.iris.IrisSettings;
import com.volmit.iris.manager.WandManager;
import com.volmit.iris.util.MortarCommand;
import com.volmit.iris.util.MortarSender;

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
	public void addTabOptions(MortarSender sender, String[] args, KList<String> list) {

	}
	@Override
	public boolean handle(MortarSender sender, String[] args)
	{
		if(!IrisSettings.get().isStudio())
		{
			sender.sendMessage("To use Iris Studio Objects, please enable studio in Iris/settings.json");
			return true;
		}
		
		if(!sender.isPlayer())
		{
			sender.sendMessage("You don't have an inventory");
			return true;
		}

		sender.player().getInventory().addItem(WandManager.createWand());
		sender.player().playSound(sender.player().getLocation(), Sound.ITEM_ARMOR_EQUIP_NETHERITE, 1f, 1.5f);

		return true;
	}

	@Override
	protected String getArgsUsage()
	{
		return "[subcommand]";
	}
}
