package ninja.bytecode.iris.gen;

import java.util.Random;

import org.bukkit.World;

import ninja.bytecode.shuriken.math.RNG;

public class GenLayer implements IGenLayer
{
	protected RNG rng;
	protected World world;
	protected Random random;
	
	public GenLayer(World world, Random random, RNG rng)
	{
		this.world = world;
		this.random = random;
		this.rng = rng;
	}
	
	@Override
	public double generateLayer(double noise, double dx, double dz)
	{
		return 0;
	}
}
