package com.volmit.iris.manager.command;

import com.volmit.iris.Iris;
import com.volmit.iris.util.*;

public class CommandIrisDownload extends MortarCommand
{
	public CommandIrisDownload()
	{
		super("download", "down", "dl");
		requiresPermission(Iris.perm.studio);
		setDescription("Download a project.");
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
			sender.sendMessage("/iris dl " + C.BOLD + "<NAME>");
			return true;
		}

		boolean trim = false;

		for(String i : args)
		{
			if(i.equals("-t") || i.equals("--trim"))
			{
				trim = true;
			}
		}

		boolean btrim = trim;

		J.a(() -> Iris.proj.downloadSearch(sender, args[0], btrim));

		return true;
	}

	@Override
	protected String getArgsUsage()
	{
		return "<name> [-t/--trim]";
	}
}
