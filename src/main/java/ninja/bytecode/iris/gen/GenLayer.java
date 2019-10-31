package ninja.bytecode.iris.gen;

import java.util.Random;

import org.bukkit.World;
import org.bukkit.block.Biome;

import ninja.bytecode.iris.IrisGenerator;
import ninja.bytecode.shuriken.math.RNG;

public class GenLayer implements IGenLayer
{
	protected RNG rng;
	protected World world;
	protected Random random;
	protected IrisGenerator iris;
	protected Biome biome = Biome.OCEAN;
	
	public GenLayer(IrisGenerator iris, World world, Random random, RNG rng)
	{
		this.world = world;
		this.random = random;
		this.rng = rng;
		this.iris = iris;
	}
	
	@Override
	public double generateLayer(double noise, double dx, double dz)
	{
		return 0;
	}
}
