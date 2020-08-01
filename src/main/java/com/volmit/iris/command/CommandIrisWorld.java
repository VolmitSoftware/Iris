package com.volmit.iris.command;

import com.volmit.iris.Iris;
import com.volmit.iris.command.util.Command;
import com.volmit.iris.command.util.MortarCommand;
import com.volmit.iris.command.util.MortarSender;

public class CommandIrisWorld extends MortarCommand
{
	@Command
	private CommandIrisGoto got0;

	@Command
	private CommandIrisMetrics metrics;

	public CommandIrisWorld()
	{
		super("world", "wrld");
		setDescription("Commands while in an iris world.");
		requiresPermission(Iris.perm.studio);
		setCategory("World");
		setDescription("Worldly commands");
	}

	@Override
	public boolean handle(MortarSender sender, String[] args)
	{
		sender.sendMessage("Iris In-World Commands");
		printHelp(sender);

		return true;
	}

	@Override
	protected String getArgsUsage()
	{
		return "[biome] [otherbiome] [-cave]";
	}
}
