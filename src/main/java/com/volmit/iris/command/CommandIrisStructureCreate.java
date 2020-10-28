package com.volmit.iris.command;

import com.volmit.iris.util.KList;
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
	public void addTabOptions(MortarSender sender, String[] args, KList<String> list) {

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


		if (args.length == 0){
			sender.sendMessage("Please specify the name of the object you wish to create");
			return true;
		} else if (args.length == 1){
			sender.sendMessage("Please specify the name of the dimension you are in");
			return true;
		} else if (args.length == 2){
			sender.sendMessage("No width and height specified. Taking defaults (5 and 5)");
			width = 5;
			height = 5;
		} else if (args.length == 3){
			if (args[2].equalsIgnoreCase("-3d"))
			{
				sender.sendMessage("No width and height specified. Taking defaults (5 and 5)");
				width = 5;
				height = 5;
				d3 = true;
			} else {
				sender.sendMessage("No height specified, taking width as height");
				width = Integer.parseInt(args[2]);
				height = Integer.parseInt(args[2]);
			}
		} else if (args.length == 4){
			width = Integer.parseInt(args[2]);

			if (args[3].equalsIgnoreCase("-3d"))
			{
				sender.sendMessage("No height specified, taking width as height");
				height = Integer.parseInt(args[2]);
			} else {
				height = Integer.parseInt(args[3]);
			}
		} else {
			for (String i : args){
				if (i.equalsIgnoreCase("-3d")){
					d3 = true;
				}
			}
			width = Integer.parseInt(args[2]);
			height = Integer.parseInt(args[3]);
		}

		if (width % 2 == 0){
			sender.sendMessage("Width is an even number. Adding one");
			width += width % 2 == 0 ? 1 : 0;
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
