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

public class GenLayerOreIron extends GenLayer
{
	private CNG ore;

	public GenLayerOreIron(IrisGenerator iris, World world, Random random, RNG rng, int shift)
	{
		//@builder
		super(iris, world, random, rng);
		ore = new CNG(rng.nextParallelRNG(281 + shift), 1D, 1).scale(0.1125).fractureWith(new CNG(rng.nextParallelRNG(412 + shift), 1D, 1)
				.scale(0.015).fractureWith(new CNG(rng.nextParallelRNG(412 + shift), 1D, 1)
						.scale(0.03), 33), 592);
		
		//@done
	}
	
	public void genOre(double wxx, double wzx, int x, int z, int s, IrisGenerator g, IrisBiome b)
	{
		double orenoise = ore.noise(wxx, wzx);		
		
		if(IrisGenerator.ROCK.contains(MB.of(g.getType(x, (int) (orenoise * 200D), z))))
		{
			g.setBlock(x, (int) (orenoise * 200D), z, Material.IRON_ORE);
		}
	}

	@Override
	public double generateLayer(double noise, double dx, double dz)
	{
		return noise;
	}
}
