package com.volmit.iris.command;

import org.bukkit.ChatColor;
import org.bukkit.FluidCollisionMode;
import org.bukkit.block.data.BlockData;
import org.bukkit.entity.Player;

import com.volmit.iris.Iris;
import com.volmit.iris.util.MortarCommand;
import com.volmit.iris.util.MortarSender;

public class CommandIrisWhatBlock extends MortarCommand
{
	public CommandIrisWhatBlock()
	{
		super("block", "b");
		setDescription("Get the block data for looking.");
		requiresPermission(Iris.perm.studio);
		setCategory("Wut");
		setDescription("WAILA,WAWLA etc");
	}

	@Override
	public boolean handle(MortarSender sender, String[] args)
	{
		if(sender.isPlayer())
		{
			Player p = sender.player();
			BlockData bd = p.getTargetBlockExact(128, FluidCollisionMode.NEVER).getBlockData();
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
