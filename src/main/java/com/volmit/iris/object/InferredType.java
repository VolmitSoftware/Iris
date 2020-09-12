package com.volmit.iris.object;

import com.volmit.iris.util.Desc;
import com.volmit.iris.util.DontObfuscate;

@Desc("Represents a biome type")
public enum InferredType
{
	@Desc("Represents any shore biome type")
	@DontObfuscate
	SHORE,

	@Desc("Represents any land biome type")
	@DontObfuscate
	LAND,

	@Desc("Represents any sea biome type")
	@DontObfuscate
	SEA,

	@Desc("Represents any cave biome type")
	@DontObfuscate
	CAVE,

	@Desc("Represents any river biome type")
	@DontObfuscate
	RIVER,

	@Desc("Represents any lake biome type")
	@DontObfuscate
	LAKE,

	@Desc("Defers the type to whatever another biome type that already exists is.")
	@DontObfuscate
	DEFER;
}
