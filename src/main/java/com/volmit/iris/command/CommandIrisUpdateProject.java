package com.volmit.iris.command;

import com.volmit.iris.Iris;
import com.volmit.iris.util.C;
import com.volmit.iris.util.J;
import com.volmit.iris.util.MortarCommand;
import com.volmit.iris.util.MortarSender;

public class CommandIrisUpdateProject extends MortarCommand
{
	public CommandIrisUpdateProject()
	{
		super("update-project", "^project");
		requiresPermission(Iris.perm.studio);
		setDescription("Update a project from git.");
		setCategory("Studio");
	}

	@Override
	public boolean handle(MortarSender sender, String[] args)
	{
		if(args.length < 1)
		{
			sender.sendMessage("/iris update-project " + C.BOLD + "<PROJECT>");
			return true;
		}

		J.a(() -> Iris.proj.downloadSearch(sender, args[0], false, true));

		return true;
	}

	@Override
	protected String getArgsUsage()
	{
		return "<project>";
	}
}
