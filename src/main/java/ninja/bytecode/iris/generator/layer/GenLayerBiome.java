package ninja.bytecode.iris.generator.layer;

import java.util.Random;
import java.util.function.Function;

import org.bukkit.World;

import mortar.util.text.C;
import ninja.bytecode.iris.Iris;
import ninja.bytecode.iris.generator.IrisGenerator;
import ninja.bytecode.iris.pack.IrisBiome;
import ninja.bytecode.iris.pack.IrisRegion;
import ninja.bytecode.iris.util.BiomeLayer;
import ninja.bytecode.iris.util.GenLayer;
import ninja.bytecode.iris.util.PolygonGenerator;
import ninja.bytecode.shuriken.collections.GList;
import ninja.bytecode.shuriken.collections.GMap;
import ninja.bytecode.shuriken.logging.L;
import ninja.bytecode.shuriken.math.CNG;
import ninja.bytecode.shuriken.math.RNG;

public class GenLayerBiome extends GenLayer
{
	private GMap<String, IrisRegion> regions;
	private Function<CNG, CNG> factory;
	private CNG fracture;
	private CNG fuzz;
	private PolygonGenerator channel;
	private PolygonGenerator ocean;
	private BiomeLayer master;

	public GenLayerBiome(IrisGenerator iris, World world, Random random, RNG rng, GList<IrisBiome> biomes)
	{
		super(iris, world, random, rng);
		//@builder
		channel = new PolygonGenerator(rng.nextParallelRNG(-12), 2, 0.0005, 1, (g)->g.fractureWith(new CNG(rng.nextParallelRNG(34), 1D, 2)
				.scale(0.01), 30));
		ocean = new PolygonGenerator(rng.nextParallelRNG(-11), 6, 0.005, 1, (g)->g.fractureWith(new CNG(rng.nextParallelRNG(34), 1D, 2)
				.scale(0.01), 150));
		fuzz = new CNG(rng.nextParallelRNG(9112), 1D * 12 * Iris.settings.gen.biomeEdgeFuzzScale, 1).scale(6.5);
		fracture = new CNG(rng.nextParallelRNG(28), 1D, 4).scale(0.0021 * Iris.settings.gen.biomeEdgeScrambleScale)
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
			if(i.getRegionID().equals("default"))
			{
				continue;
			}

			if(!regions.containsKey(i.getRegionID()))
			{
				regions.put(i.getRegionID(), new IrisRegion(i.getRegionID()));
			}

			regions.get(i.getRegionID()).getBiomes().add(i);
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

	public IrisBiome getBiome(double wxx, double wzx)
	{
		return getBiome(wxx, wzx, false);
	}

	public boolean isNearAquatic(int wxx, int wzx)
	{
		double wx = Math.round((double) wxx * (Iris.settings.gen.horizontalZoom / 1.90476190476)) * Iris.settings.gen.biomeScale;
		double wz = Math.round((double) wzx * (Iris.settings.gen.horizontalZoom / 1.90476190476)) * Iris.settings.gen.biomeScale;
		double xf = wx + ((fracture.noise(wx, wz) / 2D) * 200D * Iris.settings.gen.biomeEdgeScrambleRange);
		double zf = wz - ((fracture.noise(wz, wx) / 2D) * 200D * Iris.settings.gen.biomeEdgeScrambleRange);
		double x = xf - fuzz.noise(wx, wz);
		double z = zf + fuzz.noise(wz, wx);

		if(ocean.getIndex(x, z) == 0)
		{
			return true;
		}

		if(channel.hasBorder(3, 44, xf, zf))
		{
			return true;
		}

		if(ocean.getClosestNeighbor(x, z) > 0.2)
		{
			return true;
		}

		if(channel.getClosestNeighbor(x, z) > 0.2)
		{
			return true;
		}

		if(ocean.hasBorder(3, 7, x, z) || ocean.hasBorder(3, 3, x, z))
		{
			return true;
		}

		if(channel.hasBorder(3, 7, xf, zf) || channel.hasBorder(3, 3, xf, zf))
		{
			return true;
		}

		return false;
	}

	public IrisBiome getBiome(double wxx, double wzx, boolean real)
	{
		double wx = Math.round((double) wxx * (Iris.settings.gen.horizontalZoom / 1.90476190476)) * Iris.settings.gen.biomeScale;
		double wz = Math.round((double) wzx * (Iris.settings.gen.horizontalZoom / 1.90476190476)) * Iris.settings.gen.biomeScale;
		double xf = wx + ((fracture.noise(wx, wz) / 2D) * 200D * Iris.settings.gen.biomeEdgeScrambleRange);
		double zf = wz - ((fracture.noise(wz, wx) / 2D) * 200D * Iris.settings.gen.biomeEdgeScrambleRange);
		double x = xf - fuzz.noise(wx, wz);
		double z = zf + fuzz.noise(wz, wx);
		IrisBiome biome = master.computeBiome(x, z);

		if(real)
		{
			return biome;
		}

		if(ocean.getIndex(x, z) == 0)
		{
			IrisRegion region = getRegion(biome.getRegionID());

			if(region == null)
			{
				L.f(C.YELLOW + "Cannot find Region " + C.RED + biome.getRegionID());
				return biome;
			}

			if(region.getOcean() == null)
			{
				L.f(C.YELLOW + "Cannot find Ocean in Region" + C.RED + biome.getRegionID());
				return biome;
			}

			return getRegion(biome.getRegionID()).getOcean();
		}

		if(channel.hasBorder(3, 44, xf, zf))
		{
			IrisRegion region = getRegion(biome.getRegionID());

			if(region == null)
			{
				L.f(C.YELLOW + "Cannot find Region " + C.RED + biome.getRegionID());
				return biome;
			}

			if(region.getChannel() == null)
			{
				L.f(C.YELLOW + "Cannot find Channel in Region" + C.RED + biome.getRegionID());
				return biome;
			}

			return getRegion(biome.getRegionID()).getChannel();
		}

		return biome;
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
