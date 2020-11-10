package com.volmit.iris.manager.command;

import com.volmit.iris.Iris;
import com.volmit.iris.IrisSettings;
import com.volmit.iris.util.KList;
import com.volmit.iris.util.MortarCommand;
import com.volmit.iris.util.MortarSender;

public class CommandIrisReload extends MortarCommand
{
	public CommandIrisReload()
	{
		super("reload", "rld");
		requiresPermission(Iris.perm.studio);
		setDescription("Reload configs");
		setCategory("Studio");
	}

	@Override
	public void addTabOptions(MortarSender sender, String[] args, KList<String> list) {

	}

	@Override
	public boolean handle(MortarSender sender, String[] args)
	{
		IrisSettings.invalidate();
		IrisSettings.get();
		sender.sendMessage("settings.json Reloaded");
		return true;
	}

	@Override
	protected String getArgsUsage()
	{
		return "<name> [-t/--trim]";
	}
}
