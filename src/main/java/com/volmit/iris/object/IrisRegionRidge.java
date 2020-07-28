package com.volmit.iris.object;

import com.volmit.iris.util.CellGenerator;
import com.volmit.iris.util.Desc;
import com.volmit.iris.util.DontObfuscate;
import com.volmit.iris.util.RNG;

import lombok.Data;

@Desc("A ridge config")
@Data
public class IrisRegionRidge
{
	@DontObfuscate
	@Desc("The biome name")
	private String biome;

	@DontObfuscate
	@Desc("The type this biome should override (land sea or shore)")
	private InferredType type = InferredType.LAND;

	@DontObfuscate
	@Desc("What type this spot is (i.e. target SEA but as LAND) like an island. Default matches the target type")
	private InferredType as = InferredType.DEFER;

	@DontObfuscate
	@Desc("The chance this biome will be placed in a given spot")
	private double chance = 0.75;

	@DontObfuscate
	@Desc("The scale of the biome ridge. Higher values = wider veins & bigger connected cells")
	private double scale = 5;

	@DontObfuscate
	@Desc("The chance scale (cell chances)")
	private double chanceScale = 4;

	@DontObfuscate
	@Desc("The shuffle, how 'natural' this looks. Compared to pure polygons")
	private double shuffle = 16;

	@DontObfuscate
	@Desc("The chance shuffle (polygon cell chances)")
	private double chanceShuffle = 128;

	@DontObfuscate
	@Desc("The thickness of the vein")
	private double thickness = 0.125;

	private transient CellGenerator spot;
	private transient CellGenerator ridge;

	public IrisRegionRidge()
	{

	}

	public boolean isRidge(RNG rng, double x, double z)
	{
		if(ridge == null)
		{
			ridge = new CellGenerator(rng.nextParallelRNG(165583 * hashCode()));
			ridge.setCellScale(scale);
			ridge.setShuffle(shuffle);
		}

		if(spot == null)
		{
			spot = new CellGenerator(rng.nextParallelRNG(168523 * hashCode()));
			spot.setCellScale(chanceScale);
			spot.setShuffle(shuffle);
		}

		if(chance < 1)
		{
			if(spot.getIndex(x, z, 1000) > chance * 1000)
			{
				return false;
			}
		}

		if(ridge.getDistance(x, z) <= thickness)
		{
			return true;
		}

		return false;
	}
}
