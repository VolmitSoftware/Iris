package com.volmit.iris.command;

import org.bukkit.entity.Player;

import com.volmit.iris.Iris;
import com.volmit.iris.util.MortarCommand;
import com.volmit.iris.util.MortarSender;

public class CommandIrisStructureVariants extends MortarCommand
{
	public CommandIrisStructureVariants()
	{
		super("variants", "var", "v");
		requiresPermission(Iris.perm);
		setCategory("Structure");
		setDescription("Change or add variants in tile looking at");
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

		Iris.struct.get(p).openVariants();

		return true;
	}

	@Override
	protected String getArgsUsage()
	{
		return "";
	}
}
