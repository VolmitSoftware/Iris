package ninja.bytecode.iris.command;

import org.bukkit.Sound;

import mortar.bukkit.command.MortarCommand;
import mortar.bukkit.command.MortarSender;
import ninja.bytecode.iris.controller.WandController;

public class CommandObjectWand extends MortarCommand
{
	public CommandObjectWand()
	{
		super("wand", "w");
		setDescription("Obtain Iris Wand");
	}

	@Override
	public boolean handle(MortarSender sender, String[] args)
	{
		if(!sender.isPlayer())
		{
			sender.sendMessage("Players Only");
			return true;
		}

		sender.player().getInventory().addItem(WandController.createWand());
		sender.player().playSound(sender.player().getLocation(), Sound.ITEM_ARMOR_EQUIP_DIAMOND, 1f, 1.55f);

		return true;
	}

}
