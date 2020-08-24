package com.volmit.iris.command;

import java.util.Set;

import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;

import com.volmit.iris.Iris;
import com.volmit.iris.WandManager;
import com.volmit.iris.util.MortarCommand;
import com.volmit.iris.util.MortarSender;

public class CommandIrisObjectP1 extends MortarCommand
{
	public CommandIrisObjectP1()
	{
		super("p1");
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

		if(!WandManager.isWand(p))
		{
			sender.sendMessage("Ready your Wand.");
			return true;
		}

		ItemStack wand = p.getInventory().getItemInMainHand();

		if(WandManager.isWand(wand))
		{
			Location[] g = WandManager.getCuboid(wand);
			g[0] = p.getLocation().getBlock().getLocation().clone().add(0, -1, 0);
			
			if(args.length == 1 && args[0].equals("-l"))
			{
				g[0] = p.getTargetBlock((Set<Material>) null, 256).getLocation().clone();
			}

			p.setItemInHand(WandManager.createWand(g[0], g[1]));
		}

		return true;
	}

	@Override
	protected String getArgsUsage()
	{
		return "[-l]";
	}
}
