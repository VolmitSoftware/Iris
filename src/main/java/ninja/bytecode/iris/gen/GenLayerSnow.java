package ninja.bytecode.iris.gen;

import java.util.Random;

import org.bukkit.Material;
import org.bukkit.World;

import ninja.bytecode.iris.MB;
import ninja.bytecode.shuriken.math.CNG;
import ninja.bytecode.shuriken.math.RNG;

public class GenLayerSnow extends GenLayer
{
	private CNG snow;

	public GenLayerSnow(World world, Random random, RNG rng)
	{
		super(world, random, rng);
		//@builder
		snow = new CNG(rng.nextRNG(), 1, 1)
			.scale(0.1);
		//@done
	}
	
	public MB getSnow(double dx, double dz, double snowChance)
	{
		int m = (int) ((snowChance * 14) * snow.noise(dx, dz));
		m = m > 7 ? 7 : m;
		return new MB(Material.SNOW, m);
	}

	@Override
	public double generateLayer(double noise, double dx, double dz)
	{
		return noise;
	}
}
