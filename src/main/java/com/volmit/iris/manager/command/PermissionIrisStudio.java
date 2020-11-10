package com.volmit.iris.manager.command;

import com.volmit.iris.util.MortarPermission;

public class PermissionIrisStudio extends MortarPermission
{
	public PermissionIrisStudio()
	{
		super();
	}

	@Override
	protected String getNode()
	{
		return "studio";
	}

	@Override
	public String getDescription()
	{
		return "Iris Studio Permissions";
	}

	@Override
	public boolean isDefault()
	{
		return false;
	}
}
