package com.volmit.iris.command;

import com.volmit.iris.Iris;
import com.volmit.iris.IrisSettings;
import com.volmit.iris.util.Command;
import com.volmit.iris.util.KList;
import com.volmit.iris.util.MortarCommand;
import com.volmit.iris.util.MortarSender;

public class CommandIrisStructure extends MortarCommand
{
	@Command
	private CommandIrisStructureCreate create;

	@Command
	private CommandIrisStructureOpen open;

	@Command
	private CommandIrisStructureSave save;

	@Command
	private CommandIrisStructureMove more;

	@Command
	private CommandIrisStructureExpand expand;

	@Command
	private CommandIrisStructureVariants variants;

	@Command
	private CommandIrisStructureClose close;

	public CommandIrisStructure()
	{
		super("structure", "struct", "str");
		requiresPermission(Iris.perm);
		setCategory("Structure");
		setDescription("Structure Commands");
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
		
		sender.sendMessage("Iris Structure Commands");
		printHelp(sender);
		sender.sendMessage("Note: This is a Procedural Structure creator,");
		sender.sendMessage("not an object creator, see '/iris object'.");
		return true;
	}

	@Override
	protected String getArgsUsage()
	{
		return "[subcommand]";
	}
}
