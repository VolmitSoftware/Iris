package com.volmit.iris.manager.command;

import com.volmit.iris.Iris;
import com.volmit.iris.manager.IrisDataManager;
import com.volmit.iris.util.KList;
import com.volmit.iris.util.MortarCommand;
import com.volmit.iris.util.MortarSender;

public class CommandIrisIDM extends MortarCommand
{
	public CommandIrisIDM()
	{
		super("idm");
		setDescription("Diagnostics for Iris Data Managers");
		requiresPermission(Iris.perm.studio);
		setCategory("World");
	}


	@Override
	public void addTabOptions(MortarSender sender, String[] args, KList<String> list) {

	}
	@Override
	public boolean handle(MortarSender sender, String[] args)
	{
		if(sender.isPlayer())
		{
			sender.sendMessage("Use this in the console.");
			return true;
		}

		sender.sendMessage("Total Managers: " + IrisDataManager.managers.size());
		IrisDataManager.dumpManagers();
		return true;
	}

	@Override
	protected String getArgsUsage()
	{
		return "";
	}
}
