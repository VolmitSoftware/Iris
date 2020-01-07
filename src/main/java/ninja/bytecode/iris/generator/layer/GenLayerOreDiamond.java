package ninja.bytecode.iris.generator.layer;

import java.util.Random;

import org.bukkit.Material;
import org.bukkit.World;

import ninja.bytecode.iris.generator.IrisGenerator;
import ninja.bytecode.iris.spec.IrisBiome;
import ninja.bytecode.iris.util.GenLayer;
import ninja.bytecode.iris.util.MB;
import ninja.bytecode.shuriken.math.CNG;
import ninja.bytecode.shuriken.math.RNG;

public class GenLayerOreDiamond extends GenLayer
{
	private CNG ore;
	private CNG clamp;

	public GenLayerOreDiamond(IrisGenerator iris, World world, Random random, RNG rng, int shift)
	{
		//@builder
		super(iris, world, random, rng);
		ore = new CNG(rng.nextParallelRNG(281 + shift), 1D, 1).scale(0.08725).fractureWith(new CNG(rng.nextParallelRNG(412 + shift), 1D, 1)
				.scale(0.015).fractureWith(new CNG(rng.nextParallelRNG(412 + shift), 1D, 1)
						.scale(0.03), 33), 592);
		clamp = new CNG(rng.nextParallelRNG(299 + shift), 1D, 1).scale(0.0325).fractureWith(new CNG(rng.nextParallelRNG(412 + shift), 1D, 1)
				.scale(0.015).fractureWith(new CNG(rng.nextParallelRNG(412 + shift), 1D, 1)
						.scale(0.03), 33), 592);
		//@done
	}
	
	public void genOre(double wxx, double wzx, int x, int z, int s, IrisGenerator g, IrisBiome b)
	{
		double orenoise = ore.noise(wxx, wzx);		
		
		if(clamp.noise(wxx, wzx) > 0.85 && IrisGenerator.ROCK.contains(MB.of(g.getType(x, (int) (orenoise * 12D), z))))
		{
			g.setBlock(x, (int) (orenoise * 12D), z, Material.DIAMOND_ORE);
		}
	}

	@Override
	public double generateLayer(double noise, double dx, double dz)
	{
		return noise;
	}
}
