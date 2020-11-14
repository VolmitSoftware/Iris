package com.volmit.iris.object;

import com.volmit.iris.scaffold.cache.AtomicCache;
import com.volmit.iris.generator.noise.CNG;
import com.volmit.iris.util.Desc;
import com.volmit.iris.util.DontObfuscate;
import com.volmit.iris.util.IrisInterpolation;
import com.volmit.iris.util.M;
import com.volmit.iris.util.MaxNumber;
import com.volmit.iris.util.MinNumber;
import com.volmit.iris.util.RNG;
import com.volmit.iris.util.Required;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;
import lombok.experimental.Accessors;

@Accessors(chain = true)
@NoArgsConstructor
@AllArgsConstructor
@Desc("Translate objects")
@Data
public class IrisCarveLayer
{
	@Required
	@DontObfuscate
	@Desc("The 4d slope this carve layer follows")
	private IrisGeneratorStyle style = new IrisGeneratorStyle();

	@MaxNumber(512)
	@MinNumber(-128)
	@DontObfuscate
	@Desc("The max height")
	private int maxHeight = 220;

	@MinNumber(0.0)
	@MaxNumber(1.0)
	@DontObfuscate
	@Desc("The full percentage means the 4D opacity of this carver will decay from 100% to 0% at the min & max vertical ranges. Setting the percent to 1.0 will make a very drastic & charp change at the edge of the vertical min & max. Where as 0.15 means only 15% of the vertical range will actually be 100% opacity.")
	private double fullPercent = 0.5;

	@MaxNumber(512)
	@MinNumber(-128)
	@DontObfuscate
	@Desc("The min height")
	private int minHeight = 147;

	@MaxNumber(1)
	@MinNumber(0)
	@DontObfuscate
	@Desc("The threshold used as: \n\ncarved = noise(x,y,z) > threshold")
	private double threshold = 0.5;

	private final transient AtomicCache<CNG> cng = new AtomicCache<>();

	public boolean isCarved(RNG rng, double x, double y, double z)
	{
		if(y > getMaxHeight() || y < getMinHeight())
		{
			return false;
		}

		double opacity = Math.pow(IrisInterpolation.sinCenter(M.lerpInverse(getMinHeight(), getMaxHeight(), y)), 4);
		return cng.aquire(() -> getStyle().create(rng.nextParallelRNG(-2340 * getMaxHeight() * getMinHeight()))).fitDouble(0D, 1D, x, y, z) * opacity > getThreshold();
	}

	public boolean isCarved2(RNG rng, double x, double y, double z)
	{
		if(y > getMaxHeight() || y < getMinHeight())
		{
			return false;
		}

		double innerRange = fullPercent * (maxHeight - minHeight);
		double opacity = 1D;

		if(y <= minHeight+innerRange)
		{
			opacity = IrisInterpolation.bezier(M.lerpInverse(getMinHeight(), minHeight+innerRange, y));
		}

		else if(y >=maxHeight - innerRange)
		{
			opacity = IrisInterpolation.bezier(1D - M.lerpInverse(maxHeight-innerRange, getMaxHeight(), y));
		}

		return cng.aquire(() -> getStyle().create(rng.nextParallelRNG(-2340 * getMaxHeight() * getMinHeight()))).fitDouble(0D, 1D, x, y, z) * opacity > getThreshold();
	}
}
