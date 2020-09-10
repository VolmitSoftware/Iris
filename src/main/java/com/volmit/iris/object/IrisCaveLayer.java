package com.volmit.iris.object;

import com.volmit.iris.util.Desc;
import com.volmit.iris.util.DontObfuscate;
import com.volmit.iris.util.MinNumber;
import com.volmit.iris.util.Required;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.experimental.Accessors;

@Accessors(chain = true)
@Builder
@AllArgsConstructor
@Desc("Translate objects")
@Data
public class IrisCaveLayer
{
	@Builder.Default
	@Required
	@DontObfuscate
	@Desc("The vertical slope this cave layer follows")
	private IrisShapedGeneratorStyle verticalSlope = new IrisShapedGeneratorStyle();

	@Builder.Default
	@Required
	@DontObfuscate
	@Desc("The horizontal slope this cave layer follows")
	private IrisShapedGeneratorStyle horizontalSlope = new IrisShapedGeneratorStyle();

	@Builder.Default
	@DontObfuscate
	@Desc("If defined, a cave fluid will fill this cave below (or above) the specified fluidHeight in this object.")
	private IrisCaveFluid fluid = new IrisCaveFluid();

	@Builder.Default
	@MinNumber(0.001)
	@DontObfuscate
	@Desc("The cave zoom. Higher values makes caves spread out further and branch less often, but are thicker.")
	private double caveZoom = 1D;

	@Builder.Default
	@MinNumber(0.001)
	@DontObfuscate
	@Desc("The cave thickness.")
	private double caveThickness = 1D;

	@Builder.Default
	@DontObfuscate
	@Desc("If set to true, this cave layer can break the surface")
	private boolean canBreakSurface = false;

	public IrisCaveLayer()
	{

	}
}
