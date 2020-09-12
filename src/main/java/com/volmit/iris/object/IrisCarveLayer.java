package com.volmit.iris.object;

import com.volmit.iris.gen.atomics.AtomicCache;
import com.volmit.iris.noise.CNG;
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
}
