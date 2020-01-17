package ninja.bytecode.iris.generator.layer;

import ninja.bytecode.iris.pack.IrisBiome;
import ninja.bytecode.iris.util.GenLayer;
import ninja.bytecode.shuriken.math.CNG;
import ninja.bytecode.shuriken.math.RNG;

public class BiomeNoiseGenerator
{
	protected IrisBiome biome;
	protected CNG gen;
	private double block = 1D / 255D;

	public BiomeNoiseGenerator(RNG rng, IrisBiome biome)
	{
		this.biome = biome;
		//@builder
		gen = new CNG(rng.nextParallelRNG(31289 - biome.getName().length() * biome.getRealBiome().ordinal()), 1D, 1)
			.scale(0.0075 * biome.getGenScale())
			.fractureWith(new CNG(rng.nextParallelRNG(2922 * biome.getName().length() - biome.getRealBiome().ordinal()), 1D, 1)
					.scale(0.0075 * biome.getGenSwirlScale()), 20D * biome.getGenSwirl());
		//@done
	}

	public double getHeight(double x, double z)
	{
		if(biome.getGenAmplifier() == 0)
		{
			return 0;
		}

		double r = block * 52;
		double m = biome.getGenAmplifier() < 1D ? (r - (biome.getGenAmplifier() * r)) : 0;
		return (gen.noise(x, z) * biome.getGenAmplifier() * r) + m;
	}
}
