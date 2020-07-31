package com.volmit.iris.command;

import com.volmit.iris.Iris;
import com.volmit.iris.command.util.Command;
import com.volmit.iris.command.util.MortarCommand;
import com.volmit.iris.command.util.MortarSender;

public class CommandIrisStudio extends MortarCommand
{
	@Command
	private CommandIrisStudioCreate create;

	@Command
	private CommandIrisStudioOpen open;

	@Command
	private CommandIrisStudioClose close;

	@Command
	private CommandIrisStudioList list;

	public CommandIrisStudio()
	{
		super("studio", "std");
		requiresPermission(Iris.perm.studio);
	}

	@Override
	public boolean handle(MortarSender sender, String[] args)
	{
		sender.sendMessage("Iris Studio Commands");
		printHelp(sender);
		return true;
	}

	@Override
	protected String getArgsUsage()
	{
		return "[subcommand]";
	}
}
