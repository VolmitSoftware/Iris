package com.volmit.iris.manager.command;

import com.volmit.iris.util.MortarPermission;
import com.volmit.iris.util.Permission;

public class PermissionIris extends MortarPermission
{
	@Permission
	public PermissionIrisStudio studio;
	
	public PermissionIris()
	{
		super();
	}

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
