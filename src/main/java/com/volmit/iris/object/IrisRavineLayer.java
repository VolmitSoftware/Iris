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
@Desc("Represents a carving that slices through the surface")
@Data
public class IrisRavineLayer
{
	@Builder.Default
	@Required
	@DontObfuscate
	@Desc("The vertical slope this cave layer follows typically you would set both the min and max values to negative values so the ravine is always under the surface.")
	private IrisShapedGeneratorStyle verticalSlope = IrisShapedGeneratorStyle.builder().generator(new IrisGeneratorStyle(NoiseStyle.IRIS_THICK)).min(-19).max(-11).build();

	@Builder.Default
	@Required
	@DontObfuscate
	@Desc("The horizontal slope this cave layer follows. This affects if the ravine is straight or curves or even whirls around")
	private IrisShapedGeneratorStyle horizontalSlope = IrisShapedGeneratorStyle.builder().generator(new IrisGeneratorStyle(NoiseStyle.IRIS)).min(-30).max(30).build();

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
	@Desc("The ravine thickness.")
	private double ravineThickness = 1D;

	@Builder.Default
	@MinNumber(1)
	@DontObfuscate
	@Desc("The ravine rarity as 1 in rarity chance.")
	private int rarity = 12;

	@Builder.Default
	@MinNumber(0.001)
	@DontObfuscate
	@Desc("The ravine rarity zoom is how large of a check area at a time iris will do. For example, with higher zooms ravines will have the same effective rarity, but when you actually find a ravine, it will be near a whole patch of ravines. Setting a lower zoom such as 0.25 will make the check density higher resulting in a more uniform distribution of ravines. A zoom that is too small may also reduce the ravine sizes.")
	private double rarityZoom = 1;

	public IrisRavineLayer()
	{

	}
}
