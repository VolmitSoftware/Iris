package com.volmit.iris.command;

import com.volmit.iris.Iris;
import com.volmit.iris.IrisSettings;
import com.volmit.iris.util.C;
import com.volmit.iris.util.MortarCommand;
import com.volmit.iris.util.MortarSender;

public class CommandIrisStudioCreate extends MortarCommand
{
	public CommandIrisStudioCreate()
	{
		super("create", "new");
		requiresPermission(Iris.perm.studio);
		setDescription("Create a new project & open it.");
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

		if(args.length != 1)
		{
			sender.sendMessage("Please use a lowercase name with hyphens (-) for spaces.");
			sender.sendMessage("I.e. /iris std new " + C.BOLD + "aether");
			return true;
		}

		Iris.proj.create(sender, args[0]);

		return true;
	}

	@Override
	protected String getArgsUsage()
	{
		return "[dimension]";
	}
}
