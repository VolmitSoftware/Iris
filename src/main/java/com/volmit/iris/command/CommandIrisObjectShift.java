package com.volmit.iris.command;

import org.bukkit.Location;
import org.bukkit.Sound;
import org.bukkit.entity.Player;

import com.volmit.iris.Iris;
import com.volmit.iris.WandController;
import com.volmit.iris.util.Cuboid;
import com.volmit.iris.util.Direction;
import com.volmit.iris.util.MortarCommand;
import com.volmit.iris.util.MortarSender;

public class CommandIrisObjectShift extends MortarCommand
{
	public CommandIrisObjectShift()
	{
		super(">");
		requiresPermission(Iris.perm);
		setCategory("Object");
		setDescription("Shift selection based on direction");
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

		if(!WandController.isWand(p))
		{
			sender.sendMessage("Ready your Wand.");
			return true;
		}

		int amt = Integer.valueOf(args[2]);
		Location[] b = WandController.getCuboid(p.getInventory().getItemInMainHand());
		Location a1 = b[0].clone();
		Location a2 = b[1].clone();
		Direction d = Direction.closest(p.getLocation().getDirection()).reverse();
		a1.add(d.toVector().multiply(amt));
		a2.add(d.toVector().multiply(amt));
		Cuboid cursor = new Cuboid(a1, a2);
		b[0] = cursor.getLowerNE();
		b[1] = cursor.getUpperSW();
		p.getInventory().setItemInMainHand(WandController.createWand(b[0], b[1]));
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
