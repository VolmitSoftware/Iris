package com.volmit.iris.command;

import com.volmit.iris.Iris;
import com.volmit.iris.IrisSettings;
import com.volmit.iris.util.C;
import com.volmit.iris.util.J;
import com.volmit.iris.util.MortarCommand;
import com.volmit.iris.util.MortarSender;

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
	public boolean handle(MortarSender sender, String[] args)
	{
		if(!IrisSettings.get().isStudio())
		{
			sender.sendMessage("To use Iris Studio, please enable studio in Iris/settings.json");
			return true;
		}

		if(args.length < 1)
		{
			sender.sendMessage("/iris std dl " + C.BOLD + "<NAME>");
			return true;
		}

		boolean trim = false;

		for(String i : args)
		{
			if (i.equals("-t") || i.equals("--trim")) {
				trim = true;
				break;
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
