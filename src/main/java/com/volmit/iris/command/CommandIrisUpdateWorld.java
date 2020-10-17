package com.volmit.iris.command;

import java.io.File;

import com.volmit.iris.Iris;
import com.volmit.iris.util.C;
import com.volmit.iris.util.J;
import com.volmit.iris.util.MortarCommand;
import com.volmit.iris.util.MortarSender;

public class CommandIrisUpdateWorld extends MortarCommand
{
	public CommandIrisUpdateWorld()
	{
		super("update-world", "^world");
		requiresPermission(Iris.perm.studio);
		setDescription("Update a world from a project.");
		setCategory("Studio");
	}

	@Override
	public boolean handle(MortarSender sender, String[] args)
	{
		if(args.length < 2)
		{
			sender.sendMessage("/iris update-world " + C.BOLD + "<WORLD> <PROJECT>");
			return true;
		}

		boolean fresh = false;

		for(String i : args)
		{
			if(i.equalsIgnoreCase("--fresh-download"))
			{
				fresh = true;
			}
		}

		boolean bfre = fresh;

		J.a(() ->
		{
			File folder = new File(args[0]);
			folder.mkdirs();

			if(bfre)
			{
				Iris.proj.downloadSearch(sender, args[1], false, true);
			}

			Iris.proj.installIntoWorld(sender, args[1], folder);
		});

		return true;
	}

	@Override
	protected String getArgsUsage()
	{
		return "<world> <project>";
	}
}
