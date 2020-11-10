package com.volmit.iris.manager.command;

import com.volmit.iris.Iris;
import com.volmit.iris.util.*;

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
	public void addTabOptions(MortarSender sender, String[] args, KList<String> list) {

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
