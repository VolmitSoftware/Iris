package com.volmit.iris.util;

import com.volmit.iris.object.IrisBiome;

public class BiomeRarityCellGenerator extends CellGenerator
{
	public BiomeRarityCellGenerator(RNG rng)
	{
		super(rng);
	}

	public IrisBiome get(double x, double z, KList<IrisBiome> b)
	{
		if(b.size() == 0)
		{
			return null;
		}

		if(b.size() == 1)
		{
			return b.get(0);
		}

		KList<IrisBiome> rarityMapped = new KList<>();
		boolean o = false;
		int max = 1;
		for(IrisBiome i : b)
		{
			if(i.getRarity() > max)
			{
				max = i.getRarity();
			}
		}

		max++;

		for(IrisBiome i : b)
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
