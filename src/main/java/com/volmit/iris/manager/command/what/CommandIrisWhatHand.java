package com.volmit.iris.manager.command.what;

import com.volmit.iris.util.KList;
import org.bukkit.Material;
import org.bukkit.block.data.BlockData;
import org.bukkit.entity.Player;

import com.volmit.iris.Iris;
import com.volmit.iris.util.C;
import com.volmit.iris.util.MortarCommand;
import com.volmit.iris.util.MortarSender;

public class CommandIrisWhatHand extends MortarCommand
{
	public CommandIrisWhatHand()
	{
		super("hand", "h");
		setDescription("Get the block data for holding.");
		requiresPermission(Iris.perm.studio);
		setCategory("Wut");
		setDescription("What block am I holding");
	}

	@Override
	public void addTabOptions(MortarSender sender, String[] args, KList<String> list) {

	}

	@Override
	public boolean handle(MortarSender sender, String[] args)
	{
		if(sender.isPlayer())
		{
			Player p = sender.player();
			try
			{
				BlockData bd = p.getInventory().getItemInMainHand().getType().createBlockData();
				if(!bd.getMaterial().equals(Material.AIR)) {
					sender.sendMessage("Material: " + C.GREEN + bd.getMaterial().name());
					sender.sendMessage("Full: " + C.WHITE + bd.getAsString(true));
				} else {
					sender.sendMessage("Please hold a block/item");
				}
			}

			catch(Throwable e)
			{
				Material bd = p.getInventory().getItemInMainHand().getType();
				if(!bd.equals(Material.AIR)) {
					sender.sendMessage("Material: " + C.GREEN + bd.name());
				} else {
					sender.sendMessage("Please hold a block/item");
				}
			}
		}

		else
		{
			sender.sendMessage("Players only.");
		}

		return true;
	}

	@Override
	protected String getArgsUsage()
	{
		return "";
	}
}
