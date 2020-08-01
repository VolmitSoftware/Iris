package com.volmit.iris.command;

import org.bukkit.ChatColor;
import org.bukkit.FluidCollisionMode;
import org.bukkit.block.data.BlockData;
import org.bukkit.entity.Player;

import com.volmit.iris.Iris;
import com.volmit.iris.command.util.MortarCommand;
import com.volmit.iris.command.util.MortarSender;

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
			BlockData bd = ((Player) sender).getTargetBlockExact(128, FluidCollisionMode.NEVER).getBlockData();
			sender.sendMessage("Material: " + ChatColor.GREEN + bd.getMaterial().name());
			sender.sendMessage("Full: " + ChatColor.WHITE + bd.getAsString(true));
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
