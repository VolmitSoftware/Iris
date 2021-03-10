package com.volmit.iris.manager.command.object;

import com.volmit.iris.Iris;
import com.volmit.iris.IrisSettings;
import com.volmit.iris.manager.IrisDataManager;
import com.volmit.iris.manager.ProjectManager;
import com.volmit.iris.manager.WandManager;
import com.volmit.iris.object.IrisObject;
import com.volmit.iris.util.KList;
import com.volmit.iris.util.MortarCommand;
import com.volmit.iris.util.MortarSender;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.Sound;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;

import java.io.File;
import java.util.Set;

public class CommandIrisObjectPaste extends MortarCommand
{
	public CommandIrisObjectPaste()
	{
		super("paste", "pasta", "place", "p");
		requiresPermission(Iris.perm);
		setCategory("Object");
		setDescription("Paste an object");
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

		if(args.length == 0){
			sender.sendMessage("Please specify the name of of the object want to paste");
			return true;
		}

		Player p = sender.player();
		IrisObject obj = IrisDataManager.loadAnyObject(args[0]);

		if(obj == null)
		{

			sender.sendMessage("Can't find " + args[0] + " in the " + ProjectManager.WORKSPACE_NAME + " folder");
			return true;
		}

		File file = obj.getLoadFile();
		boolean intoWand = false;

		for(String i : args)
		{
			if(i.equalsIgnoreCase("-edit"))
			{
				intoWand = true;
			}
		}

		if(file == null || !file.exists())
		{
			sender.sendMessage("Can't find " + args[0] + " in the " + ProjectManager.WORKSPACE_NAME + " folder");
			return true;
		}

		ItemStack wand = sender.player().getInventory().getItemInMainHand();

		IrisObject o = IrisDataManager.loadAnyObject(args[0]);
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
