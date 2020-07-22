package ninja.bytecode.iris.util;

import org.apache.commons.lang.math.DoubleRange;

import ninja.bytecode.shuriken.collections.KList;
import ninja.bytecode.shuriken.collections.KMap;

public class RarityCellGenerator<T extends IRare> extends CellGenerator
{
	public RarityCellGenerator(RNG rng)
	{
		super(rng);
	}

	public T get(double x, double z, KList<T> t)
	{
		int totalWeight = 0;
		KMap<DoubleRange, T> ranges = new KMap<>();

		for(T i : t)
		{
			int weight = (int) Math.round(1000 * i.getWeight());

			if(weight < 1)
			{
				continue;
			}

			ranges.put(new DoubleRange(totalWeight, totalWeight + weight), i);
			totalWeight += weight;
		}

		int r = getIndex(x, z, totalWeight);

		for(DoubleRange i : ranges.keySet())
		{
			if(i.containsDouble(r))
			{
				return ranges.get(i);
			}
		}

		if(!t.isEmpty())
		{
			return t.get(0);
		}

		return null;
	}
}
