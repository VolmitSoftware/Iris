package com.volmit.iris.object;

import com.volmit.iris.util.DontObfuscate;

public enum CarvingMode
{
	@DontObfuscate
	SURFACE_ONLY,

	@DontObfuscate
	CARVING_ONLY,

	@DontObfuscate
	ANYWHERE;
	
	public boolean supportsCarving()
	{
		return this.equals(ANYWHERE) || this.equals(CARVING_ONLY);
	}
	
	public boolean supportsSurface()
	{
		return this.equals(ANYWHERE) || this.equals(SURFACE_ONLY);
	}
}
