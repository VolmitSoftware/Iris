package com.volmit.iris.object;

import com.volmit.iris.gen.atomics.AtomicCache;
import com.volmit.iris.noise.CellGenerator;
import com.volmit.iris.util.Desc;
import com.volmit.iris.util.DontObfuscate;
import com.volmit.iris.util.MaxNumber;
import com.volmit.iris.util.MinNumber;
import com.volmit.iris.util.RNG;
import com.volmit.iris.util.RegistryListBiome;
import com.volmit.iris.util.Required;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;

import lombok.experimental.Accessors;

@Accessors(chain = true)
@Builder
@AllArgsConstructor
@Desc("A ridge config")
@Data
public class IrisRegionRidge
{
	@Builder.Default
	@RegistryListBiome
	@Required
	@DontObfuscate
	@Desc("The biome name")
	private String biome = "";

	@Builder.Default
	@Required
	@DontObfuscate
	@Desc("The type this biome should override (land sea or shore)")
	private InferredType type = InferredType.LAND;

	@Builder.Default
	@DontObfuscate
	@Desc("What type this spot is (i.e. target SEA but as LAND) like an island. Default matches the target type")
	private InferredType as = InferredType.DEFER;

	@Builder.Default
	@DontObfuscate
	@Desc("Use the distance from cell value to add or remove noise value. (Forces depth or height)")
	private double noiseMultiplier = 0;

	@Builder.Default
	@Required
	@MinNumber(0)
	@MaxNumber(1)
	@DontObfuscate
	@Desc("The chance this biome will be placed in a given spot")
	private double chance = 0.75;

	@Builder.Default
	@MinNumber(0)
	@DontObfuscate
	@Desc("The scale of the biome ridge. Higher values = wider veins & bigger connected cells")
	private double scale = 5;

	@Builder.Default
	@DontObfuscate
	@Desc("The chance scale (cell chances)")
	private double chanceScale = 4;

	@Builder.Default
	@MinNumber(0)
	@DontObfuscate
	@Desc("The shuffle, how 'natural' this looks. Compared to pure polygons")
	private double shuffle = 16;

	@Builder.Default
	@MinNumber(0)
	@DontObfuscate
	@Desc("The chance shuffle (polygon cell chances)")
	private double chanceShuffle = 128;

	@Builder.Default
	@MinNumber(0)
	@DontObfuscate
	@Desc("The thickness of the vein")
	private double thickness = 0.125;

	@Builder.Default
	@DontObfuscate
	@Desc("If the noise multiplier is below zero, what should the air be filled with?")
	private IrisBiomePaletteLayer air = new IrisBiomePaletteLayer().zero();

	private final transient AtomicCache<CellGenerator> spot = new AtomicCache<>();
	private final transient AtomicCache<CellGenerator> ridge = new AtomicCache<>();

	public IrisRegionRidge()
	{

	}

	public CellGenerator getSpotGenerator(RNG rng)
	{
		return spot.aquire(() ->
		{
			CellGenerator spot = new CellGenerator(rng.nextParallelRNG((int) (198523 * getChance())));
			spot.setCellScale(chanceScale);
			spot.setShuffle(shuffle);
			return spot;
		});
	}

	public CellGenerator getRidgeGenerator(RNG rng)
	{
		return spot.aquire(() ->
		{
			CellGenerator ridge = new CellGenerator(rng.nextParallelRNG((int) (465583 * getChance())));
			ridge.setCellScale(scale);
			ridge.setShuffle(shuffle);
			return ridge;
		});
	}

	public double getRidgeHeight(RNG rng, double x, double z)
	{
		if(getNoiseMultiplier() == 0)
		{
			return 0;
		}

		return getSpotGenerator(rng).getDistance(x, z) * getRidgeGenerator(rng).getDistance(x, z) * getNoiseMultiplier();
	}

	public boolean isRidge(RNG rng, double x, double z)
	{
		if(chance < 1)
		{
			if(getSpotGenerator(rng).getIndex(x, z, 1000) > chance * 1000)
			{
				return false;
			}
		}

		if(getRidgeGenerator(rng).getDistance(x, z) <= thickness)
		{
			return true;
		}

		return false;
	}
}
