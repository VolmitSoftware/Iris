package com.volmit.iris.command;

import com.volmit.iris.Iris;
import com.volmit.iris.util.Command;
import com.volmit.iris.util.MortarCommand;
import com.volmit.iris.util.MortarSender;

public class CommandIrisStudio extends MortarCommand
{
	@Command
	private CommandIrisStudioCreate create;

	@Command
	private CommandIrisStudioOpen open;

	@Command
	private CommandIrisStudioClose close;

	@Command
	private CommandIrisStudioPackage pkg;
	
	@Command
	private CommandIrisStudioVerify verify;

	@Command
	private CommandIrisStudioList list;

	public CommandIrisStudio()
	{
		super("studio", "std");
		requiresPermission(Iris.perm.studio);
		setCategory("Studio");
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
