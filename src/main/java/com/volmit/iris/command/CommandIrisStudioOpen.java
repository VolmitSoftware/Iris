package com.volmit.iris.command;

import com.volmit.iris.Iris;
import com.volmit.iris.command.util.MortarCommand;
import com.volmit.iris.command.util.MortarSender;

public class CommandIrisStudioOpen extends MortarCommand
{
	public CommandIrisStudioOpen()
	{
		super("open", "o");
		requiresPermission(Iris.perm.studio);
		setDescription("Create a new temporary world to design a dimension.");
	}

	@Override
	public boolean handle(MortarSender sender, String[] args)
	{
		if(args.length != 1)
		{
			sender.sendMessage("/iris std open <DIMENSION> (file name without .json)");
		}

		Iris.proj.open(sender, args[0]);
		return true;
	}

	@Override
	protected String getArgsUsage()
	{
		return "[dimension]";
	}
}
