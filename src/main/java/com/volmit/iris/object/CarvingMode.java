package com.volmit.iris.object;

import com.volmit.iris.util.Desc;
import com.volmit.iris.util.DontObfuscate;

@Desc("Defines if an object is allowed to place in carvings, surfaces or both.")
public enum CarvingMode
{
	@Desc("Only place this object on surfaces (NOT under carvings)")
	@DontObfuscate
	SURFACE_ONLY,

	@Desc("Only place this object under carvings (NOT on the surface)")
	@DontObfuscate
	CARVING_ONLY,

	@Desc("This object can place anywhere")
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
