package com.volmit.iris.object;

import com.volmit.iris.util.Desc;
import com.volmit.iris.util.DontObfuscate;
import com.volmit.iris.util.MaxNumber;
import com.volmit.iris.util.MinNumber;
import com.volmit.iris.util.RNG;
import com.volmit.iris.util.Required;

import lombok.Data;

@Desc("This represents a generator with a min and max height")
@Data
public class IrisShapedGeneratorStyle
{
	@Required
	@DontObfuscate
	@Desc("The generator id")
	private IrisGeneratorStyle generator = new IrisGeneratorStyle(NoiseStyle.IRIS);

	@Required
	@MinNumber(-256)
	@MaxNumber(256)
	@DontObfuscate
	@Desc("The min block value")
	private int min = 0;

	@Required
	@MinNumber(-256)
	@MaxNumber(256)
	@DontObfuscate
	@Desc("The max block value")
	private int max = 0;

	public double get(RNG rng, double... dim)
	{
		return generator.create(rng).fitDouble(min, max, dim);
	}

	public IrisShapedGeneratorStyle()
	{

	}
}
