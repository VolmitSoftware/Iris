package com.volmit.iris.manager.command.object;

import java.util.Set;

import com.volmit.iris.util.KList;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;

import com.volmit.iris.Iris;
import com.volmit.iris.IrisSettings;
import com.volmit.iris.manager.WandManager;
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


	@Override
	public void addTabOptions(MortarSender sender, String[] args, KList<String> list) {

	}
	@SuppressWarnings("deprecation")
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

		ItemStack wand = p.getInventory().getItemInMainHand();

		if(WandManager.isWand(wand))
		{
			Location[] g = WandManager.getCuboid(wand);
			g[1] = p.getLocation().getBlock().getLocation().clone().add(0, -1, 0);

			if(args.length == 1 && args[0].equals("-l"))
			{
				// TODO: WARNING HEIGHT
				g[1] = p.getTargetBlock((Set<Material>) null, 256).getLocation().clone();
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
