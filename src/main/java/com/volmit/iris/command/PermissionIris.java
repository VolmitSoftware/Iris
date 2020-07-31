package com.volmit.iris.command;

import com.volmit.iris.command.util.MortarPermission;
import com.volmit.iris.command.util.Permission;

public class PermissionIris extends MortarPermission
{
	@Permission
	public PermissionIrisStudio studio;

	@Override
	protected String getNode()
	{
		return "iris";
	}

	@Override
	public String getDescription()
	{
		return "Iris Permissions";
	}

	@Override
	public boolean isDefault()
	{
		return false;
	}
}
