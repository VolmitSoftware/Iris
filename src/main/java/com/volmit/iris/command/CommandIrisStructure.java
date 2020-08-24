package com.volmit.iris.command;

import com.volmit.iris.Iris;
import com.volmit.iris.util.Command;
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
	private CommandIrisStructureMore more;

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
	public boolean handle(MortarSender sender, String[] args)
	{
		sender.sendMessage("Iris Structure Commands");
		printHelp(sender);
		return true;
	}

	@Override
	protected String getArgsUsage()
	{
		return "[subcommand]";
	}
}
