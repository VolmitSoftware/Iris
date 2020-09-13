package com.volmit.iris.command;

import com.volmit.iris.Iris;
import com.volmit.iris.IrisSettings;
import com.volmit.iris.util.MortarCommand;
import com.volmit.iris.util.MortarSender;

public class CommandIrisStudioUpdate extends MortarCommand
{
	public CommandIrisStudioUpdate()
	{
		super("update", "upd", "u");
		requiresPermission(Iris.perm.studio);
		setDescription("Update your dimension project.");
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

		if(args.length == 0)
		{
			sender.sendMessage("/iris std package <DIMENSION>");
			return true;
		}

		Iris.proj.updateWorkspace(Iris.proj.getWorkspaceFile(args[0]));

		return true;
	}

	@Override
	protected String getArgsUsage()
	{
		return "[dimension]";
	}
}
