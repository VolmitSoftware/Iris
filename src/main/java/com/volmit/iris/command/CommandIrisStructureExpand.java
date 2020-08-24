package com.volmit.iris.command;

import org.bukkit.entity.Player;

import com.volmit.iris.Iris;
import com.volmit.iris.util.MortarCommand;
import com.volmit.iris.util.MortarSender;
import com.volmit.iris.util.StructureTemplate;

public class CommandIrisStructureExpand extends MortarCommand
{
	public CommandIrisStructureExpand()
	{
		super("expand", "+++");
		requiresPermission(Iris.perm);
		setCategory("Structure");
		setDescription("Expand out more of the structure");
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
		StructureTemplate m = Iris.struct.get(p);

		if(m == null)
		{
			sender.sendMessage("You do not have an open structure");
			return true;
		}

		m.expand();
		sender.sendMessage("Loading More!");

		return true;
	}

	@Override
	protected String getArgsUsage()
	{
		return "";
	}
}
