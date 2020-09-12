package com.volmit.iris.object;

import com.volmit.iris.util.Desc;
import com.volmit.iris.util.DontObfuscate;

@Desc("A structure tile condition is for a specific wall if a tile is allowed to place if a wall exists.")
public enum StructureTileCondition
{
	@Desc("This face REQUIRES a wall for this tile to place here")
	@DontObfuscate
	REQUIRED,

	@Desc("This face DOESNT CARE if a wall is here for this tile to place here")
	@DontObfuscate
	AGNOSTIC,

	@Desc("This face CANNOT HAVE a wall for this tile to place here")
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
