package com.volmit.iris.command;

import org.bukkit.entity.Player;

import com.volmit.iris.Iris;
import com.volmit.iris.IrisSettings;
import com.volmit.iris.structure.StructureTemplate;
import com.volmit.iris.util.MortarCommand;
import com.volmit.iris.util.MortarSender;

public class CommandIrisStructureCreate extends MortarCommand
{
	public CommandIrisStructureCreate()
	{
		super("new", "create", "+");
		requiresPermission(Iris.perm);
		setCategory("Structure");
		setDescription("Create a structure");
	}

	@Override
	public boolean handle(MortarSender sender, String[] args)
	{
		if(!IrisSettings.get().isStudio())
		{
			sender.sendMessage("To use Iris Studio Structures, please enable studio in Iris/settings.json");
			return true;
		}
		
		if(!sender.isPlayer())
		{
			sender.sendMessage("You don't have a wand");
			return true;
		}

		Player p = sender.player();

		boolean d3 = false;

		for(String i : args)
		{
            if (i.equalsIgnoreCase("-3d")) {
                d3 = true;
                break;
            }
		}

		sender.sendMessage("Creating new Structure");
		new StructureTemplate(args[0], args[1], p, p.getLocation(), 5, Integer.valueOf(args[2]), Integer.valueOf(args[3]), d3);

		return true;
	}

	@Override
	protected String getArgsUsage()
	{
		return "<name> <dimension> <tile-width> <tile-height> [-3d]";
	}
}
