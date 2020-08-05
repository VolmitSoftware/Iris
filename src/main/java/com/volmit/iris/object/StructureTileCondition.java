package com.volmit.iris.object;

import com.volmit.iris.util.DontObfuscate;

public enum StructureTileCondition
{
	@DontObfuscate
	REQUIRED,

	@DontObfuscate
	AGNOSTIC,

	@DontObfuscate
	NEVER;

	public boolean supported()
	{
		return !this.equals(NEVER);
	}

	public boolean required()
	{
		return this.equals(REQUIRED);
	}
}
