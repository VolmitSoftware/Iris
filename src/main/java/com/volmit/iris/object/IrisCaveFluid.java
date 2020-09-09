package com.volmit.iris.object;

import com.volmit.iris.util.Desc;
import com.volmit.iris.util.DontObfuscate;
import com.volmit.iris.util.MinNumber;
import com.volmit.iris.util.Required;

import lombok.Data;

@Desc("Represents a cave fluid")
@Data
public class IrisCaveFluid
{
	@DontObfuscate
	@Desc("If set to true, this cave layer can break the surface")
	private boolean canBreakSurface = false;

	public IrisCaveFluid()
	{

	}
}
