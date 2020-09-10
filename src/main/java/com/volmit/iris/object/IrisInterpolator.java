package com.volmit.iris.object;

import com.volmit.iris.util.Desc;
import com.volmit.iris.util.DontObfuscate;
import com.volmit.iris.util.IrisInterpolation;
import com.volmit.iris.util.MaxNumber;
import com.volmit.iris.util.MinNumber;
import com.volmit.iris.util.NoiseProvider;
import com.volmit.iris.util.Required;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;
import lombok.experimental.Accessors;

@Accessors(chain = true)
@NoArgsConstructor
@AllArgsConstructor
@Desc("Configures rotation for iris")
@Data
public class IrisInterpolator
{

	@Required
	@DontObfuscate
	@Desc("The interpolation method when two biomes use different heights but this same generator")
	private InterpolationMethod function = InterpolationMethod.BICUBIC;

	@Required
	@MinNumber(1)
	@MaxNumber(8192)
	@DontObfuscate
	@Desc("The range checked horizontally. Smaller ranges yeild more detail but are not as smooth.")
	private double horizontalScale = 3;

	public double interpolate(double x, double z, NoiseProvider provider)
	{
		return interpolate((int) Math.round(x), (int) Math.round(z), provider);
	}

	public double interpolate(int x, int z, NoiseProvider provider)
	{
		return IrisInterpolation.getNoise(getFunction(), x, z, getHorizontalScale(), provider);
	}
}
