package com.volmit.iris.object;

import com.volmit.iris.scaffold.cache.AtomicCache;
import com.volmit.iris.generator.noise.CellGenerator;
import com.volmit.iris.util.Desc;
import com.volmit.iris.util.DontObfuscate;
import com.volmit.iris.util.MinNumber;
import com.volmit.iris.util.RNG;
import com.volmit.iris.util.RegistryListBiome;
import com.volmit.iris.util.Required;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;
import lombok.experimental.Accessors;

@Accessors(chain = true)
@NoArgsConstructor
@AllArgsConstructor
@Desc("A spot config")
@Data
public class IrisRegionSpot
{

	@RegistryListBiome
	@Required
	@DontObfuscate
	@Desc("The biome to be placed")
	private String biome = "";

	@Required
	@DontObfuscate
	@Desc("Where this spot overrides. Land sea or shore")
	private InferredType type = InferredType.LAND;

	@DontObfuscate
	@Desc("What type this spot is (i.e. target SEA but as LAND) like an island. Default matches the target type")
	private InferredType as = InferredType.DEFER;

	@DontObfuscate
	@Desc("Use the distance from cell value to add or remove noise value. (Forces depth or height)")
	private double noiseMultiplier = 0;

	@MinNumber(0)
	@DontObfuscate
	@Desc("The scale of splotches")
	private double scale = 1;

	@Required
	@MinNumber(1)
	@DontObfuscate
	@Desc("Rarity is how often this splotch appears. higher = less often")
	private double rarity = 1;

	@MinNumber(0)
	@DontObfuscate
	@Desc("The shuffle or how natural the splotch looks like (anti-polygon)")
	private double shuffle = 128;

	@DontObfuscate
	@Desc("If the noise multiplier is below zero, what should the air be filled with?")
	private IrisBiomePaletteLayer air = new IrisBiomePaletteLayer().zero();

	private final transient AtomicCache<CellGenerator> spot = new AtomicCache<>();

	public CellGenerator getSpotGenerator(RNG rng)
	{
		return spot.aquire(() ->
		{
			CellGenerator spot = new CellGenerator(rng.nextParallelRNG((int) (168583 * (shuffle + 102) + rarity + (scale * 10465) + biome.length() + type.ordinal() + as.ordinal())));
			spot.setCellScale(scale);
			spot.setShuffle(shuffle);
			return spot;
		});
	}

	public double getSpotHeight(RNG rng, double x, double z)
	{
		if(getNoiseMultiplier() == 0)
		{
			return 0;
		}

		return getSpotGenerator(rng).getDistance(x, z) * getNoiseMultiplier();
	}

	public boolean isSpot(RNG rng, double x, double z)
	{
		if(getSpotGenerator(rng).getIndex(x, z, (int) (Math.round(rarity) + 8)) == (int) ((Math.round(rarity) + 8) / 2))
		{
			return true;
		}

		return false;
	}
}
