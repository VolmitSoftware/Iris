package com.volmit.iris.manager.command.object;

import com.volmit.iris.util.*;
import org.bukkit.Location;
import org.bukkit.Sound;
import org.bukkit.entity.Player;

import com.volmit.iris.Iris;
import com.volmit.iris.IrisSettings;
import com.volmit.iris.manager.WandManager;

public class CommandIrisObjectContract extends MortarCommand
{
	public CommandIrisObjectContract()
	{
		super("-");
		requiresPermission(Iris.perm);
		setCategory("Object");
		setDescription("Contract a selection based on your lookign direction");
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
			sender.sendMessage("You don't have a wand");
			return true;
		}

		Player p = sender.player();

		if(!WandManager.isWand(p))
		{
			sender.sendMessage("Ready your Wand.");
			return true;
		}

		int amt = args.length == 1 ? Integer.valueOf(args[0]) : 1;
		Location[] b = WandManager.getCuboid(p.getInventory().getItemInMainHand());
		Location a1 = b[0].clone();
		Location a2 = b[1].clone();
		Cuboid cursor = new Cuboid(a1, a2);
		Direction d = Direction.closest(p.getLocation().getDirection()).reverse();
		cursor = cursor.expand(d, -amt);
		b[0] = cursor.getLowerNE();
		b[1] = cursor.getUpperSW();
		p.getInventory().setItemInMainHand(WandManager.createWand(b[0], b[1]));
		p.updateInventory();
		p.playSound(p.getLocation(), Sound.ENTITY_ITEM_FRAME_ROTATE_ITEM, 1f, 0.55f);

		return true;
	}

	@Override
	protected String getArgsUsage()
	{
		return "[amt]";
	}
}
