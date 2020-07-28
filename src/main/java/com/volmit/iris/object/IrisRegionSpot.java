package com.volmit.iris.object;

import com.volmit.iris.util.CellGenerator;
import com.volmit.iris.util.Desc;
import com.volmit.iris.util.DontObfuscate;
import com.volmit.iris.util.RNG;

import lombok.Data;

@Desc("A spot config")
@Data
public class IrisRegionSpot
{
	@DontObfuscate
	@Desc("The biome to be placed")
	private String biome;

	@DontObfuscate
	@Desc("Where this spot overrides. Land sea or shore")
	private InferredType type = InferredType.LAND;

	@DontObfuscate
	@Desc("What type this spot is (i.e. target SEA but as LAND) like an island. Default matches the target type")
	private InferredType as = InferredType.DEFER;

	@DontObfuscate
	@Desc("The scale of splotches")
	private double scale = 1;

	@DontObfuscate
	@Desc("Rarity is how often this splotch appears. higher = less often")
	private double rarity = 1;

	@DontObfuscate
	@Desc("The shuffle or how natural the splotch looks like (anti-polygon)")
	private double shuffle = 128;

	private transient CellGenerator spot;

	public IrisRegionSpot()
	{

	}

	public boolean isSpot(RNG rng, double x, double z)
	{
		if(spot == null)
		{
			spot = new CellGenerator(rng.nextParallelRNG(168583 * hashCode()));
			spot.setCellScale(scale);
			spot.setShuffle(shuffle);
		}

		if(spot.getIndex(x, z, (int) (Math.round(rarity) + 8)) == (int) ((Math.round(rarity) + 8) / 2))
		{
			return true;
		}

		return false;
	}
}
