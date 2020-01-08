package ninja.bytecode.iris.generator.layer;

import java.util.Random;
import java.util.function.Function;

import org.bukkit.Material;
import org.bukkit.World;

import ninja.bytecode.iris.Iris;
import ninja.bytecode.iris.generator.IrisGenerator;
import ninja.bytecode.iris.pack.IrisBiome;
import ninja.bytecode.iris.util.GenLayer;
import ninja.bytecode.iris.util.MaxingGenerator;
import ninja.bytecode.iris.util.MaxingGenerator.EnumMaxingGenerator;
import ninja.bytecode.shuriken.collections.GList;
import ninja.bytecode.shuriken.math.CNG;
import ninja.bytecode.shuriken.math.M;
import ninja.bytecode.shuriken.math.RNG;

public class GenLayerBiome extends GenLayer
{
	private EnumMaxingGenerator<IrisBiome> biomeGenerator;
	private MaxingGenerator roads;
	private Function<CNG, CNG> factory;
	private CNG pathCheck;
	private CNG riverCheck;
	private CNG fracture;
	private CNG island;

	public GenLayerBiome(IrisGenerator iris, World world, Random random, RNG rng, GList<IrisBiome> biomes)
	{
		//@builder
		super(iris, world, random, rng);
		island = new CNG(rng.nextParallelRNG(10334), 1D, 3).scale(0.003 * Iris.settings.gen.landScale).fractureWith(new CNG(rng.nextParallelRNG(34), 1D, 12).scale(0.6), 180);
		fracture = new CNG(rng.nextParallelRNG(28), 1D, 24).scale(0.0021).fractureWith(new CNG(rng.nextParallelRNG(34), 1D, 12).scale(0.01), 12250);
		factory = (g) -> g.fractureWith(new CNG(rng.nextParallelRNG(29), 1D, 4).scale(0.02), 56);
		riverCheck = new CNG(rng.nextParallelRNG(30), 1D, 2).scale(0.00096);
		pathCheck = new CNG(rng.nextParallelRNG(31), 1D, 1).scale(0.00096);
		roads = new MaxingGenerator(rng.nextParallelRNG(32), 5, 0.00055, 8, factory);
		biomeGenerator = new EnumMaxingGenerator<IrisBiome>(rng.nextParallelRNG(33), 0.00755 * Iris.settings.gen.biomeScale, 1, biomes.toArray(new IrisBiome[biomes.size()]), factory);
		//@done
	}

	public IrisBiome getBiome(double xx, double zz)
	{
		double x = xx + (fracture.noise(zz, xx) * 1550D);
		double z = zz - (fracture.noise(xx, zz) * 1550D);
		IrisBiome cbi = iris.biome("Ocean");
		double land = island.noise(x, z);
		double landChance = 1D - M.clip(Iris.settings.gen.landChance, 0D, 1D);
		
		if(land > landChance && land < landChance + 0.0175)
		{
			cbi = iris.biome("Beach");
		}
		
		else if(land > landChance + 0.0175)
		{
			if(riverCheck.noise(x, z) > 0.75)
			{
				if(biomeGenerator.hasBorder(3, 3 + Math.pow(riverCheck.noise(x, z), 1.25) * 16, x, z))
				{
					return iris.biome("River");
				}
			}

			cbi = biomeGenerator.getChoice(x, z);

			if(pathCheck.noise(x, z) > 0.33)
			{
				IrisBiome road = iris.biome("Beach");

				if(cbi.getSurface().get(0).material.equals(Material.GRASS))
				{
					road = IrisBiome.ROAD_GRASSY;
				}

				if(Math.abs(road.getHeight() - cbi.getHeight()) < 0.0001 && roads.hasBorder(4, 3, xx, zz))
				{
					return road;
				}
			}
		}
		
		else if(land < 0.3)
		{
			cbi = iris.biome("Deep Ocean");
		}
		
		return cbi;
	}

	@Override
	public double generateLayer(double noise, double dx, double dz)
	{
		return noise;
	}
}
