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
	public boolean handle(MortarSender sender, String[] args) {
		if (!IrisSettings.get().isStudio()) {
			sender.sendMessage("To use Iris Studio Structures, please enable studio in Iris/settings.json");
			return true;
		}

		if (!sender.isPlayer()) {
			sender.sendMessage("You cannot run this from console");
			return true;
		}

		Player p = sender.player();

		boolean d3 = false;
		int width;
		int height;


		switch (args.length) {
			case 0: {
				sender.sendMessage("Please specify the name of the object you wish to create");
				return true;
			}
			case 1: {
				sender.sendMessage("Please specify the name of the dimension you are in");
				return true;
			}
			case 2: {
				sender.sendMessage("No width and height specified. Taking defaults (5 and 5)");
				return true;
			}
			case 3: {
				sender.sendMessage("No height specified, taking width as height");
				width = Integer.parseInt(args[2]);
				height = Integer.parseInt(args[2]);
			}
			case 4: {
				if (!args[3].equalsIgnoreCase("-3d")) {
					sender.sendMessage("No height specified, taking width as height");
					width = Integer.parseInt(args[2]);
					height = Integer.parseInt(args[2]);
					return true;
				} else {
					width = Integer.parseInt(args[2]);
					height = Integer.parseInt(args[3]);
					break;
				}
			}
			case 5: {
				width = Integer.parseInt(args[2]);
				height = Integer.parseInt(args[3]);
				d3 = true;
			}
			default: {
				width = Integer.parseInt(args[2]);
				height = Integer.parseInt(args[3]);
				break;
			}
		}

		sender.sendMessage("Creating new Structure");
		new StructureTemplate(args[0], args[1], p, p.getLocation(), 5, width, height, d3);

		return true;
	}

	@Override
	protected String getArgsUsage()
	{
		return "<name> <dimension> <tile-width> <tile-height> [-3d]";
	}
}
