package ninja.bytecode.iris.gen;

import java.util.Random;

import org.bukkit.World;

import ninja.bytecode.iris.util.RealBiome;
import ninja.bytecode.shuriken.math.CNG;
import ninja.bytecode.shuriken.math.M;
import ninja.bytecode.shuriken.math.RNG;

public class GenLayerBiome extends GenLayer
{
	private CNG temperature;
	private CNG humidity;
	private CNG hfracture;
	private CNG alt;
	private CNG height;
	private CNG superheight;

	public GenLayerBiome(World world, Random random, RNG rng)
	{
		super(world, random, rng);
		//@builder
		temperature = new CNG(rng.nextRNG(), 1, 2)
				.scale(0.0022)
				.fractureWith(new CNG(rng.nextRNG(), 1, 1).scale(0.06), 32);
		hfracture = new CNG(rng.nextRNG(), 1, 2)
				.scale(0.0124);
		humidity = new CNG(rng.nextRNG(), 1, 2)
				.scale(0.0024)
				.fractureWith(new CNG(rng.nextRNG(), 1, 1).scale(0.06), 32);
		superheight = new CNG(rng.nextRNG(), 1, 8)
				.scale(0.0004)
				.fractureWith(new CNG(rng.nextRNG(), 1, 1).scale(0.021), 250);
		height = new CNG(rng.nextRNG(), 1, 8)
				.scale(0.0017601)
				.fractureWith(new CNG(rng.nextRNG(), 1, 6)
						.scale(0.0174)
						.fractureWith(new CNG(rng.nextRNG(), 1, 1)
								.scale(0.0034), 31)
							.scale(0.066), 58);
		alt = new CNG(rng.nextRNG(), 1, 1)
				.scale(0.0008)
				.fractureWith(new CNG(rng.nextRNG(), 1, 1).scale(0.3), 100);
		//@done
	}

	public RealBiome getBiome(double x, double z)
	{
		return RealBiome.match(getTemperature(x, z) * 2, getHumidity(x, z), getHeight(x, z), getAlt(x, z));
	}

	private double getAlt(double x, double z)
	{
		return alt.noise(x, z);
	}

	public double getTemperature(double x, double z)
	{
		return M.clip(temperature.noise(x, z) - (getHeight(x, z) * 0.19), 0D, 1D);
	}

	public double getHumidity(double x, double z)
	{
		return humidity.noise(x, z);
	}

	public double getHeight(double x, double z)
	{
		return M.clip(Math.pow(height.noise(x + (hfracture.noise(x, z) * 33), z + (hfracture.noise(z, x) * 33)), 2.31 + superheight.noise(x, z)) * 2.654, 0D, 1D);
	}

	@Override
	public double generateLayer(double noise, double dx, double dz)
	{
		return getHeight(dx, dz);
	}
}
