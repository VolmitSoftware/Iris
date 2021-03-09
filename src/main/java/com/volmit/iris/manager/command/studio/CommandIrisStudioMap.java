package com.volmit.iris.manager.command.studio;

import com.volmit.iris.Iris;
import com.volmit.iris.IrisSettings;
import com.volmit.iris.manager.gui.IrisVision;
import com.volmit.iris.scaffold.engine.IrisAccess;
import com.volmit.iris.util.KList;
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

		if(!IrisSettings.get().isUseServerLaunchedGuis())
		{
			sender.sendMessage("To use Iris Guis, please enable serverLaunchedGuis in Iris/settings.json");
			return true;
		}

		IrisAccess g = Iris.proj.getActiveProject().getActiveProvider();
		IrisVision.launch(g, 0);
		sender.sendMessage("Opening Map!");
		return true;
	}

	@Override
	protected String getArgsUsage()
	{
		return "";
	}
}
