package ninja.bytecode.iris.generator.layer;

import java.util.Random;

import org.bukkit.Material;
import org.bukkit.World;

import ninja.bytecode.iris.Iris;
import ninja.bytecode.iris.generator.IrisGenerator;
import ninja.bytecode.iris.util.GenLayer;
import ninja.bytecode.iris.util.MaxingGenerator;
import ninja.bytecode.shuriken.math.CNG;
import ninja.bytecode.shuriken.math.RNG;

public class GenLayerCaves extends GenLayer
{
	private CNG caveHeight;
	private CNG caveGirth;
	private CNG caveClamp;
	private MaxingGenerator caveVeins;
	
	public GenLayerCaves(IrisGenerator iris, World world, Random random, RNG rng)
	{
		super(iris, world, random, rng);
		caveHeight = new CNG(rng.nextParallelRNG(-100001), 1D, 3).scale(0.00222);
		caveGirth = new CNG(rng.nextParallelRNG(-100002), 1D, 3).scale(0.03);
		caveClamp = new CNG(rng.nextParallelRNG(-10000), 1D, 3).scale(0.1422);
		caveVeins = new MaxingGenerator(rng.nextParallelRNG(-99999), 4, 0.002 * Iris.settings.gen.caveScale, 1, (g) -> g.fractureWith(new CNG(rng.nextParallelRNG(-5555), 1D, 4).scale(0.02), 70));
	}
	
	public void genCaves(double wxx, double wzx, int x, int z, int s, IrisGenerator g)
	{
		for(double itr = 0; itr < 0.1 * Iris.settings.gen.caveDensity; itr += 0.1)
		{
			double thickness = 0.25 + itr + (0.5 * caveClamp.noise(wxx, wzx));
			double size = 3.88D * thickness;
			double variance = 8.34D * thickness;
			double w = size + (variance * caveGirth.noise(wxx, wzx));
			double h = size + (variance * caveGirth.noise(wzx, wxx));
			double width = 0;
			double height = h;
			double elevation = (caveHeight.noise(wxx + (19949D * itr), wzx - (19949D * itr)) * (350)) - 80;
			while(width <= w && height > 1D)
			{
				width+=2;
				height-=2;

				if(caveVeins.hasBorder(3, width, wxx - (19949D * itr), wzx + (19949D * itr)))
				{
					double r = (((caveGirth.noise(wxx, wzx, width)) * variance) + height) / 2D;
					int f = 3;
					for(int i = (int) -r; i < r && f >= 0; i++)
					{
						if(i + height > s)
						{
							break;
						}
						
						Material t = g.getType(x, (int) (elevation + i) - 55, z);
						if(t.equals(Material.BEDROCK) || t.equals(Material.WATER) || t.equals(Material.STATIONARY_WATER))
						{
							continue;
						}
						
						g.setBlock(x, (int) (elevation + i) - 55, z, Material.AIR);
					}
				}
			}
		}
	}

	@Override
	public double generateLayer(double gnoise, double dx, double dz)
	{
		return gnoise;
	}
}
