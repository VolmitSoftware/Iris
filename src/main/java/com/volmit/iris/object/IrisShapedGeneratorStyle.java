package com.volmit.iris.object;

import com.volmit.iris.util.Desc;
import com.volmit.iris.util.DontObfuscate;
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
@Desc("This represents a generator with a min and max height")
@Data
public class IrisShapedGeneratorStyle
{
	@Required
	@DontObfuscate
	@Desc("The generator id")

	private IrisGeneratorStyle generator = new IrisGeneratorStyle(NoiseStyle.IRIS);

	@Required
	@MinNumber(-256) // TODO: WARNING HEIGHT
	@MaxNumber(256) // TODO: WARNING HEIGHT

	@DontObfuscate
	@Desc("The min block value")
	private int min = 0;

	@Required
	@MinNumber(-256) // TODO: WARNING HEIGHT
	@MaxNumber(256) // TODO: WARNING HEIGHT
	@DontObfuscate
	@Desc("The max block value")
	private int max = 0;

	public double get(RNG rng, double... dim)
	{
		return generator.create(rng).fitDouble(min, max, dim);
	}

	public IrisShapedGeneratorStyle(NoiseStyle style, int min, int max)
	{
		this(style);
		this.min = min;
		this.max = max;
	}

	public IrisShapedGeneratorStyle(NoiseStyle style)
	{
		this.generator = new IrisGeneratorStyle(style);
	}
}
