package com.volmit.iris.manager.command.studio;

import com.volmit.iris.Iris;
import com.volmit.iris.IrisSettings;
import com.volmit.iris.manager.IrisProject;
import com.volmit.iris.util.KList;
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

		if(args.length == 0)
		{
			sender.sendMessage("/iris std package <DIMENSION>");
			return true;
		}

		if(new IrisProject(Iris.proj.getWorkspaceFolder(args[0])).updateWorkspace())
		{
			sender.sendMessage("Updated Code Workspace for " + args[0]);
		}

		else
		{
			sender.sendMessage("Invalid project: " + args[0] + ". Try deleting the code-workspace file and try again.");
		}

		return true;
	}

	@Override
	protected String getArgsUsage()
	{
		return "[dimension]";
	}
}
