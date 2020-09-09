package com.volmit.iris.command;

import com.volmit.iris.Iris;
import com.volmit.iris.IrisSettings;
import com.volmit.iris.gui.NoiseExplorer;
import com.volmit.iris.util.MortarCommand;
import com.volmit.iris.util.MortarSender;

public class CommandIrisStudioExplorer extends MortarCommand
{
	public CommandIrisStudioExplorer()
	{
		super("noise", "nmap");
		setDescription("Explore different noise generators visually");
		requiresPermission(Iris.perm.studio);
		setCategory("World");
	}

	@Override
	public boolean handle(MortarSender sender, String[] args)
	{
		if(!IrisSettings.get().isStudio())
		{
			sender.sendMessage("To use Iris Studio, please enable studio in Iris/settings.json");
			return true;
		}

		if(!IrisSettings.get().isUseServerLaunchedGuis())
		{
			sender.sendMessage("To use Iris Guis, please enable serverLaunchedGuis in Iris/settings.json");
			return true;
		}

		NoiseExplorer.launch();
		sender.sendMessage("Opening Noise Explorer!");
		return true;
	}

	@Override
	protected String getArgsUsage()
	{
		return "";
	}
}
