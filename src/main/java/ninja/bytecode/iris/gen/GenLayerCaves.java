package ninja.bytecode.iris.gen;

import java.util.Random;

import org.bukkit.World;

import ninja.bytecode.iris.IrisGenerator;
import ninja.bytecode.shuriken.math.CNG;
import ninja.bytecode.shuriken.math.RNG;

public class GenLayerCaves extends GenLayer
{
	public GenLayerCaves(IrisGenerator iris, World world, Random random, RNG rng)
	{ 
		//@builder
		super(iris, world, random, rng);
		
		//@done
	}

	@Override
	public double generateLayer(double noise, double dx, double dz)
	{
		return noise;
	}
}
