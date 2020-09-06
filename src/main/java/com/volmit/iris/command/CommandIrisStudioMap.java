package com.volmit.iris.command;

import com.volmit.iris.Iris;
import com.volmit.iris.IrisSettings;
import com.volmit.iris.gen.IrisTerrainProvider;
import com.volmit.iris.gui.IrisVision;
import com.volmit.iris.util.MortarCommand;
import com.volmit.iris.util.MortarSender;

public class CommandIrisStudioMap extends MortarCommand
{
	public CommandIrisStudioMap()
	{
		super("map", "render");
		setDescription("Render a map (gui outside of mc)");
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

		IrisTerrainProvider g = Iris.proj.getCurrentProject();
		IrisVision.launch(g);
		sender.sendMessage("Opening Map!");
		return true;
	}

	@Override
	protected String getArgsUsage()
	{
		return "";
	}
}
