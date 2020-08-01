package com.volmit.iris.command;

import com.volmit.iris.util.MortarPermission;

public class PermissionIrisStudio extends MortarPermission
{
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
