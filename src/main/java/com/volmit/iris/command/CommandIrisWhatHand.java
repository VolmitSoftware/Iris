package com.volmit.iris.command;

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
		setDescription("What block holding");
	}

	@Override
	public boolean handle(MortarSender sender, String[] args)
	{
		if(sender.isPlayer())
		{
			Player p = sender.player();
			BlockData bd = p.getInventory().getItemInMainHand().getType().createBlockData();
			if(!bd.getMaterial().equals(Material.AIR)) {
				sender.sendMessage("Material: " + C.GREEN + bd.getMaterial().name());
				sender.sendMessage("Full: " + C.WHITE + bd.getAsString(true));
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
