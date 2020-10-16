package com.volmit.iris.command;

import java.io.File;
import java.util.Set;

import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.Sound;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;

import com.volmit.iris.Iris;
import com.volmit.iris.IrisSettings;
import com.volmit.iris.manager.ProjectManager;
import com.volmit.iris.manager.WandManager;
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
		File file = Iris.globaldata.getObjectLoader().findFile(args[0]);
		boolean intoWand = false;

		for(String i : args)
		{
            if (i.equalsIgnoreCase("-edit")) {
                intoWand = true;
                break;
            }
		}

		if(file == null || !file.exists())
		{
			sender.sendMessage("Can't find " + args[0] + " in the " + ProjectManager.workspaceName + " folder");
		}

		ItemStack wand = sender.player().getInventory().getItemInMainHand();

		IrisObject o = Iris.globaldata.getObjectLoader().load(args[0]);
		if(o == null)
		{
			sender.sendMessage("Error, cant find");
			return true;
		}
		sender.sendMessage("Loaded " + "objects/" + args[0] + ".iob");

		sender.player().getWorld().playSound(sender.player().getLocation(), Sound.BLOCK_ENCHANTMENT_TABLE_USE, 1f, 1.5f);
		Location block = sender.player().getTargetBlock((Set<Material>) null, 256).getLocation().clone().add(0, 1, 0);

		if(intoWand && WandManager.isWand(wand))
		{
			wand = WandManager.createWand(block.clone().subtract(o.getCenter()).add(o.getW() - 1, o.getH(), o.getD() - 1), block.clone().subtract(o.getCenter()));
			p.getInventory().setItemInMainHand(wand);
			sender.sendMessage("Updated wand for " + "objects/" + args[0] + ".iob");
		}

		WandManager.pasteSchematic(o, block);
		sender.sendMessage("Placed " + "objects/" + args[0] + ".iob");

		return true;
	}

	@Override
	protected String getArgsUsage()
	{
		return "[name] [-edit]";
	}
}
