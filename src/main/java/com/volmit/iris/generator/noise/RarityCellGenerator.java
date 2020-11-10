package com.volmit.iris.generator.noise;

import com.volmit.iris.util.IRare;
import com.volmit.iris.util.KList;
import com.volmit.iris.util.RNG;

public class RarityCellGenerator<T extends IRare> extends CellGenerator
{
	public RarityCellGenerator(RNG rng)
	{
		super(rng);
	}

	public T get(double x, double z, KList<T> b)
	{
		if(b.size() == 0)
		{
			return null;
		}

		if(b.size() == 1)
		{
			return b.get(0);
		}

		KList<T> rarityMapped = new KList<>();
		boolean o = false;
		int max = 1;
		for(T i : b)
		{
			if(i.getRarity() > max)
			{
				max = i.getRarity();
			}
		}

		max++;

		for(T i : b)
		{
			for(int j = 0; j < max - i.getRarity(); j++)
			{
				if(o = !o)
				{
					rarityMapped.add(i);
				}

				else
				{
					rarityMapped.add(0, i);
				}
			}
		}

		if(rarityMapped.size() == 1)
		{
			return rarityMapped.get(0);
		}

		if(rarityMapped.isEmpty())
		{
			throw new RuntimeException("BAD RARITY MAP! RELATED TO: " + b.toString(", or possibly "));
		}

		return rarityMapped.get(getIndex(x, z, rarityMapped.size()));
	}
}
