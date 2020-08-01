package com.volmit.iris.command;

import java.util.Set;

import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;

import com.volmit.iris.Iris;
import com.volmit.iris.WandController;
import com.volmit.iris.util.MortarCommand;
import com.volmit.iris.util.MortarSender;

public class CommandIrisObjectP2 extends MortarCommand
{
	public CommandIrisObjectP2()
	{
		super("p2");
		requiresPermission(Iris.perm);
		setCategory("Object");
		setDescription("Set point 1 to pos (or look)");
	}

	@SuppressWarnings("deprecation")
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

		ItemStack wand = p.getInventory().getItemInMainHand();

		if(WandController.isWand(wand))
		{
			Location[] g = WandController.getCuboid(wand);
			g[1] = p.getLocation().getBlock().getLocation().clone().add(0, -1, 0);

			if(args.length == 1 && args[0].equals("-l"))
			{
				g[1] = p.getTargetBlock((Set<Material>) null, 256).getLocation().clone();
			}

			p.setItemInHand(WandController.createWand(g[0], g[1]));
		}

		return true;
	}

	@Override
	protected String getArgsUsage()
	{
		return "[-l]";
	}
}
