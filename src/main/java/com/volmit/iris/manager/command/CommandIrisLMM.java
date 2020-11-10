package com.volmit.iris.manager.command;

import com.volmit.iris.Iris;
import com.volmit.iris.util.KList;
import com.volmit.iris.util.MortarCommand;
import com.volmit.iris.util.MortarSender;

public class CommandIrisLMM extends MortarCommand
{
	public CommandIrisLMM()
	{
		super("lmm");
		setDescription("Toggle Low Memory Mode");
		requiresPermission(Iris.perm.studio);
		setCategory("World");
	}


	@Override
	public void addTabOptions(MortarSender sender, String[] args, KList<String> list) {

	}
	@Override
	public boolean handle(MortarSender sender, String[] args)
	{
		Iris.lowMemoryMode = !Iris.lowMemoryMode;
		sender.sendMessage("Low Memory Mode is " + (Iris.lowMemoryMode ? "On" : "Off"));
		return true;
	}

	@Override
	protected String getArgsUsage()
	{
		return "";
	}
}
