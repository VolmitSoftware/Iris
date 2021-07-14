package com.volmit.iris.object;

import com.googlecode.concurrentlinkedhashmap.ConcurrentLinkedHashMap;
import com.volmit.iris.util.*;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;
import lombok.experimental.Accessors;

@Accessors(chain = true)
@NoArgsConstructor
@AllArgsConstructor
@Desc("Scale objects")
@Data
public class IrisObjectScale
{
	@MinNumber(1)
	@MaxNumber(32)
	@DontObfuscate
	@Desc("Iris Objects are scaled and cached to speed up placements. Because of this extra memory is used, so we evenly distribute variations across the defined scale range, then pick one randomly. If the differences is small, use a lower number. For more possibilities on the scale spectrum, increase this at the cost of memory.")
	private int variations = 7;

	@MinNumber(0.01)
	@MaxNumber(50)
	@DontObfuscate
	@Desc("The minimum scale")
	private double minimumScale = 1;

	@MinNumber(0.01)
	@MaxNumber(50)
	@DontObfuscate
	@Desc("The maximum height for placement (top of object)")
	private double maximumScale = 1;

	private final transient ConcurrentLinkedHashMap<IrisObject, KList<IrisObject>> cache
			= new ConcurrentLinkedHashMap.Builder<IrisObject, KList<IrisObject>>()
			.initialCapacity(64)
			.maximumWeightedCapacity(64)
			.concurrencyLevel(32)
			.build();

	public boolean shouldScale()
	{
		return ((minimumScale == maximumScale) && maximumScale == 1) || variations <= 0;
	}

	public int getMaxSizeFor(int indim)
	{
		return (int) (getMaxScale() * indim);
	}

	public double getMaxScale()
	{
		double mx = 0;

		for(double i = minimumScale; i < maximumScale; i+= (maximumScale - minimumScale) / (double)(Math.min(variations, 32)))
		{
			mx = i;
		}

		return mx;
	}

	public IrisObject get(RNG rng, IrisObject origin)
	{
		if(shouldScale())
		{
			return origin;
		}

		return cache.compute(origin, (k, v) -> {
			if(v != null)
			{
				return v;
			}

			KList<IrisObject> c = new KList<>();
			for(double i = minimumScale; i < maximumScale; i+= (maximumScale - minimumScale) / (double)(Math.min(variations, 32)))
			{
				c.add(origin.scaled(i));
			}

			return c;
		}).getRandom(rng);
	}

	public boolean canScaleBeyond() {
		return shouldScale() && maximumScale > 1;
	}
}
