package com.volmit.iris.command;

import com.volmit.iris.Iris;
import com.volmit.iris.IrisSettings;
import com.volmit.iris.util.MortarCommand;
import com.volmit.iris.util.MortarSender;

public class CommandIrisStudioTP extends MortarCommand
{
	public CommandIrisStudioTP()
	{
		super("tp");
		requiresPermission(Iris.perm.studio);
		setDescription("Go to the spawn of the currently open studio world.");
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

		if(!Iris.proj.isProjectOpen())
		{
			sender.sendMessage("There is not a studio currently loaded.");
			return true;
		}

		try
		{
			sender.sendMessage("Teleporting you to the active studio world.");
			sender.player().teleport(Iris.proj.getActiveProject().getActiveProvider().getTarget().getRealWorld().getSpawnLocation());
		}

		catch(Throwable e)
		{
			sender.sendMessage("Failed to teleport to the studio world. Try re-opening the project.");
		}

		return true;
	}

	@Override
	protected String getArgsUsage()
	{
		return "";
	}
}
