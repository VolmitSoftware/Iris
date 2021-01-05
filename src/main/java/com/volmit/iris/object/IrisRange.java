package com.volmit.iris.object;

import com.volmit.iris.util.Desc;
import com.volmit.iris.util.DontObfuscate;
import com.volmit.iris.util.RNG;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;
import lombok.experimental.Accessors;

@Accessors(chain = true)
@NoArgsConstructor
@AllArgsConstructor
@Desc("Represents a range")
@Data
public class IrisRange
{
	@DontObfuscate
	@Desc("The minimum value")
	private double min = 16;

	@DontObfuscate
	@Desc("The maximum value")
	private double max = 32;

	public double get(RNG rng)
	{
		if(min == max)
		{
			return min;
		}

		return rng.d(min, max);
	}
}
