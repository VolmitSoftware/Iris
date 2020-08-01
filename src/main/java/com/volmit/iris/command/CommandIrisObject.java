package com.volmit.iris.command;

import com.volmit.iris.Iris;
import com.volmit.iris.command.util.Command;
import com.volmit.iris.command.util.MortarCommand;
import com.volmit.iris.command.util.MortarSender;

public class CommandIrisObject extends MortarCommand
{
	@Command
	private CommandIrisObjectWand wand;

	@Command
	private CommandIrisObjectXPY xpy;

	@Command
	private CommandIrisObjectXAY xay;

	@Command
	private CommandIrisObjectShift shift;

	@Command
	private CommandIrisObjectExpand expand;

	@Command
	private CommandIrisObjectContract contract;

	@Command
	private CommandIrisObjectP1 p1;

	@Command
	private CommandIrisObjectP2 p2;

	@Command
	private CommandIrisObjectSave save;

	@Command
	private CommandIrisObjectPaste paste;

	public CommandIrisObject()
	{
		super("object", "iob", "o");
		requiresPermission(Iris.perm);
		setCategory("Object");
		setDescription("Object Commands");
	}

	@Override
	public boolean handle(MortarSender sender, String[] args)
	{
		sender.sendMessage("Iris Object Commands");
		printHelp(sender);
		return true;
	}

	@Override
	protected String getArgsUsage()
	{
		return "[subcommand]";
	}
}
