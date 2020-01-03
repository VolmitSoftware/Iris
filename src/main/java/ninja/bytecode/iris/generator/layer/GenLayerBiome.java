package ninja.bytecode.iris.generator.layer;

import java.util.Random;
import java.util.function.Function;

import org.bukkit.Material;
import org.bukkit.World;

import ninja.bytecode.iris.Iris;
import ninja.bytecode.iris.generator.IrisGenerator;
import ninja.bytecode.iris.generator.biome.IrisBiome;
import ninja.bytecode.iris.util.GenLayer;
import ninja.bytecode.iris.util.MaxingGenerator;
import ninja.bytecode.iris.util.MaxingGenerator.EnumMaxingGenerator;
import ninja.bytecode.shuriken.math.CNG;
import ninja.bytecode.shuriken.math.RNG;

public class GenLayerBiome extends GenLayer
{
	private EnumMaxingGenerator<IrisBiome> biomeGenerator;
	private MaxingGenerator roads;
	private Function<CNG, CNG> factory;
	private CNG pathCheck;
	private CNG riverCheck;
	private CNG fracture;

	public GenLayerBiome(IrisGenerator iris, World world, Random random, RNG rng)
	{
		//@builder
		super(iris, world, random, rng);
		fracture = new CNG(rng.nextParallelRNG(28), 1D, 24).scale(0.0021).fractureWith(new CNG(rng.nextParallelRNG(34), 1D, 12).scale(0.01), 12250);
		factory = (g) -> g.fractureWith(new CNG(rng.nextParallelRNG(29), 1D, 4).scale(0.02), 56);
		riverCheck = new CNG(rng.nextParallelRNG(30), 1D, 2).scale(0.00096);
		pathCheck = new CNG(rng.nextParallelRNG(31), 1D, 1).scale(0.00096);
		roads = new MaxingGenerator(rng.nextParallelRNG(32), 5, 0.00055, 8, factory);
		biomeGenerator = new EnumMaxingGenerator<IrisBiome>(rng.nextParallelRNG(33), 0.00755 * Iris.settings.gen.biomeScale, 1, IrisBiome.getBiomes().toArray(new IrisBiome[IrisBiome.getBiomes().size()]), factory);
		//@done
	}

	public IrisBiome getBiome(double xx, double zz)
	{
		double x = xx + (fracture.noise(zz, xx) * 1550D);
		double z = zz - (fracture.noise(xx, zz) * 1550D);

		if(riverCheck.noise(x, z) > 0.75)
		{
			if(biomeGenerator.hasBorder(3, 3 + Math.pow(riverCheck.noise(x, z), 1.25) * 16, x, z))
			{
				return IrisBiome.RIVER;
			}
		}

		IrisBiome cbi = biomeGenerator.getChoice(x, z);

		if(pathCheck.noise(x, z) > 0.33)
		{
			IrisBiome road = IrisBiome.ROAD_GRAVEL;

			if(cbi.getSurface().get(0).material.equals(Material.GRASS))
			{
				road = IrisBiome.ROAD_GRASSY;
			}

			if(Math.abs(road.getHeight() - cbi.getHeight()) < 0.0001 && roads.hasBorder(4, 3, xx, zz))
			{
				return road;
			}
		}

		return cbi;
	}

	@Override
	public double generateLayer(double noise, double dx, double dz)
	{
		return noise;
	}
}
