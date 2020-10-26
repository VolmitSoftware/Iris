package com.volmit.iris.command;

import com.volmit.iris.Iris;
import com.volmit.iris.IrisSettings;
import com.volmit.iris.util.MortarCommand;
import com.volmit.iris.util.MortarSender;

public class CommandIrisStudioConvert extends MortarCommand
{
	public CommandIrisStudioConvert()
	{
		super("convert", "cvt");
		requiresPermission(Iris.perm.studio);
		setDescription("Convert .ewg schematics into Iris (.iob) files");
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

		Iris.convert.check(sender);
		return true;
	}

	@Override
	protected String getArgsUsage()
	{
		return "";
	}
}
