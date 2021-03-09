package com.volmit.iris.manager.command.studio;

import com.volmit.iris.Iris;
import com.volmit.iris.IrisSettings;
import com.volmit.iris.util.C;
import com.volmit.iris.util.KList;
import com.volmit.iris.util.MortarCommand;
import com.volmit.iris.util.MortarSender;

public class CommandIrisStudioCreate extends MortarCommand
{
	public CommandIrisStudioCreate()
	{
		super("create", "new", "+");
		requiresPermission(Iris.perm.studio);
		setDescription("Create a new project & open it.");
		setCategory("Studio");
	}

	@Override
	public void addTabOptions(MortarSender sender, String[] args, KList<String> list) {

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
			sender.sendMessage("Please use a lowercase name with hyphens (-) for spaces.");
			sender.sendMessage("I.e. /iris std new " + C.BOLD + "aether");
			return true;
		}

		String template = null;

		for(String i : args)
		{
			if(i.startsWith("template="))
			{
				template = i.split("\\Q=\\E")[1];
			}
		}

		if(template != null)
		{
			Iris.proj.create(sender, args[0], template);
		}

		else
		{
			Iris.proj.create(sender, args[0]);
		}

		return true;
	}

	@Override
	protected String getArgsUsage()
	{
		return "[dimension] [template=<project>]";
	}
}
