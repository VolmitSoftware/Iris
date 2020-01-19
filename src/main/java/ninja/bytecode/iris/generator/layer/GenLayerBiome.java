package ninja.bytecode.iris.generator.layer;

import java.util.Random;
import java.util.function.Function;

import org.bukkit.World;

import ninja.bytecode.iris.Iris;
import ninja.bytecode.iris.generator.IrisGenerator;
import ninja.bytecode.iris.pack.IrisBiome;
import ninja.bytecode.iris.pack.IrisRegion;
import ninja.bytecode.iris.util.BiomeLayer;
import ninja.bytecode.iris.util.GenLayer;
import ninja.bytecode.shuriken.collections.GList;
import ninja.bytecode.shuriken.collections.GMap;
import ninja.bytecode.shuriken.math.CNG;
import ninja.bytecode.shuriken.math.M;
import ninja.bytecode.shuriken.math.RNG;

public class GenLayerBiome extends GenLayer
{
	private GMap<String, IrisRegion> regions;
	private Function<CNG, CNG> factory;
	private CNG fracture;
	private CNG island;
	private BiomeLayer master;

	public GenLayerBiome(IrisGenerator iris, World world, Random random, RNG rng, GList<IrisBiome> biomes)
	{
		super(iris, world, random, rng);
		//@builder
		island = new CNG(rng.nextParallelRNG(10334), 1D, 1)
				.scale(0.003 * Iris.settings.gen.landScale)
				.fractureWith(new CNG(rng.nextParallelRNG(1211), 1D, 1)
						.scale(0.001 * Iris.settings.gen.landScale), 600);
		fracture = new CNG(rng.nextParallelRNG(28), 1D, 4).scale(0.0021)
				.fractureWith(new CNG(rng.nextParallelRNG(34), 1D, 2)
						.scale(0.01), 12250);
		factory = (g) -> g.fractureWith(new CNG(rng.nextParallelRNG(29), 1D, 3)
				.scale(0.005 * Iris.settings.gen.biomeScale), 1024D / Iris.settings.gen.biomeScale)
				.fractureWith(new CNG(rng.nextParallelRNG(1212), 1D, 2)
						.scale(0.04)
						.fractureWith(new CNG(rng.nextParallelRNG(1216), 1D, 3).scale(0.0004), 266), 66);
		//@done
		regions = new GMap<>();

		for(IrisBiome i : biomes)
		{
			if(i.getRegion().equals("default"))
			{
				continue;
			}

			if(!regions.containsKey(i.getRegion()))
			{
				regions.put(i.getRegion(), new IrisRegion(i.getRegion()));
			}

			regions.get(i.getRegion()).getBiomes().add(i);
		}

		for(IrisRegion i : regions.values())
		{
			i.load();
		}

		int m = 0;

		for(IrisBiome i : iris.getDimension().getBiomes())
		{
			i.seal(iris.getRTerrain().nextParallelRNG(3922 - m++));
		}

		master = BiomeLayer.compile(iris, 0.082 * Iris.settings.gen.biomeScale * 0.189, 1, factory);

		if(Iris.settings.performance.verbose)
		{
			master.print(2);
		}
	}

	public boolean hasBorder(int checks, double distance, double... dims)
	{
		IrisBiome current = getBiome(dims[0], dims[1]);
		double ajump = 360D / (double) checks;

		if(dims.length == 2)
		{
			for(int i = 0; i < checks; i++)
			{
				double dx = M.sin((float) Math.toRadians(ajump * i));
				double dz = M.cos((float) Math.toRadians(ajump * i));
				if(!current.equals(getBiome((dx * distance) + dims[0], (dz * distance) + dims[1])))
				{
					return true;
				}
			}
		}

		return false;
	}

	public boolean hasHeightBorder(int checks, double distance, double... dims)
	{
		IrisBiome current = getBiome(dims[0], dims[1]);
		double ajump = 360D / (double) checks;

		if(dims.length == 2)
		{
			for(int i = 0; i < checks; i++)
			{
				double dx = M.sin((float) Math.toRadians(ajump * i));
				double dz = M.cos((float) Math.toRadians(ajump * i));
				if(current.getHeight() != getBiome((dx * distance) + dims[0], (dz * distance) + dims[1]).getHeight())
				{
					return true;
				}
			}
		}

		return false;
	}

	public boolean isBorder(int wx, int wz, double range)
	{
		return hasHeightBorder(6, range, wx, wz);
	}

	public IrisBiome getBiome(double wxx, double wzx)
	{
		return getBiome(wxx, wzx, false);
	}

	public IrisBiome getBiome(double wxx, double wzx, boolean real)
	{
		double wx = Math.round((double) wxx * (Iris.settings.gen.horizontalZoom / 1.90476190476)) * Iris.settings.gen.biomeScale;
		double wz = Math.round((double) wzx * (Iris.settings.gen.horizontalZoom / 1.90476190476)) * Iris.settings.gen.biomeScale;
		double x = wx + ((fracture.noise(wx, wz) / 2D) * 200D * Iris.settings.gen.biomeEdgeScrambleScale);
		double z = wz - ((fracture.noise(wz, wx) / 2D) * 200D * Iris.settings.gen.biomeEdgeScrambleScale);

		if(real)
		{
			return master.computeBiome(x, z);
		}

		IrisBiome cbi = iris.biome("Ocean");
		double land = island.noise(x, z);
		double landChance = 1D - M.clip(Iris.settings.gen.landChance, 0D, 1D);

		if(land > landChance)
		{
			cbi = master.computeBiome(x, z);
		}

		else if(land < 0.1)
		{
			cbi = iris.biome("Deep Ocean");
		}

		else
		{
			cbi = iris.biome("Ocean");
		}

		return cbi;
	}

	@Override
	public double generateLayer(double noise, double dx, double dz)
	{
		return noise;
	}

	public IrisRegion getRegion(String name)
	{
		return regions.get(name);
	}

	public void compileInfo(BiomeLayer l)
	{
		l.compileChildren(0.082 * Iris.settings.gen.biomeScale * 0.189, 1, factory, true);
	}
}
