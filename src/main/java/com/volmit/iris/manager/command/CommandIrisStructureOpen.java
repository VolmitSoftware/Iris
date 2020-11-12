package com.volmit.iris.manager.command;

import com.volmit.iris.manager.IrisDataManager;
import com.volmit.iris.util.KList;
import org.bukkit.entity.Player;

import com.volmit.iris.Iris;
import com.volmit.iris.IrisSettings;
import com.volmit.iris.object.IrisStructure;
import com.volmit.iris.manager.structure.StructureTemplate;
import com.volmit.iris.util.MortarCommand;
import com.volmit.iris.util.MortarSender;

public class CommandIrisStructureOpen extends MortarCommand
{
	public CommandIrisStructureOpen()
	{
		super("load", "open", "o");
		requiresPermission(Iris.perm);
		setCategory("Structure");
		setDescription("Open an existing structure");
	}

	@Override
	public void addTabOptions(MortarSender sender, String[] args, KList<String> list) {

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

		if(args.length == 0){
			sender.sendMessage("Please specify the structure you wish to load");
			return true;
		}

		Player p = sender.player();

		IrisStructure structure = IrisDataManager.loadAnyStructure(args[0]);

		if(structure == null)
		{
			sender.sendMessage("Can't find " + args[0]);
			return true;
		}

		String dimensionGuess = structure.getLoadFile().getParentFile().getParentFile().getName();
		new StructureTemplate(structure.getName(), dimensionGuess, p, p.getLocation(), 9, structure.getGridSize(), structure.getGridHeight(), structure.getMaxLayers() > 1).loadStructures(structure);

		return true;
	}

	@Override
	protected String getArgsUsage()
	{
		return "<structure>";
	}
}
