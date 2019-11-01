package ninja.bytecode.iris;

import java.util.List;
import java.util.Random;

import org.bukkit.Material;
import org.bukkit.World;
import org.bukkit.block.Biome;
import org.bukkit.generator.BlockPopulator;
import org.bukkit.util.Vector;

import ninja.bytecode.iris.biome.CBI;
import ninja.bytecode.iris.gen.GenLayerBase;
import ninja.bytecode.iris.gen.GenLayerBiome;
import ninja.bytecode.iris.util.MaxingGenerator;
import ninja.bytecode.shuriken.collections.GList;
import ninja.bytecode.shuriken.collections.GMap;
import ninja.bytecode.shuriken.execution.ChronoLatch;
import ninja.bytecode.shuriken.format.F;
import ninja.bytecode.shuriken.logging.L;
import ninja.bytecode.shuriken.math.M;
import ninja.bytecode.shuriken.math.RNG;

public class IrisGenerator extends ParallelChunkGenerator
{
	private GMap<Vector, Double> heightCache;
	private MB WATER = new MB(Material.STATIONARY_WATER);
	private MB BEDROCK = new MB(Material.BEDROCK);
	private GenLayerBase glBase;
	private GenLayerBiome glBiome;
	private RNG rng;
	private World world;

	@Override
	public void onInit(World world, Random random)
	{
		this.world = world;
		heightCache = new GMap<>();
		rng = new RNG(world.getSeed());
		glBase = new GenLayerBase(this, world, random, rng.nextRNG());
		glBiome = new GenLayerBiome(this, world, random, rng.nextRNG());
	}

	public int getHeight(double h)
	{
		double height = M.clip(h, 0D, 1D);

		return (int) (height * 253);
	}

	public int getHeight(double dx, double dz)
	{
		return getHeight(getRawHeight(dx, dz));
	}

	public double getRawHeight(double dx, double dz)
	{
		double noise = 0 + Iris.settings.gen.baseHeight;

		return M.clip(noise, 0D, 1D);
	}

	@Override
	public Biome genColumn(int wxx, int wzx, int x, int z)
	{
		return genBaseColumn(wxx, wzx, x, z);
	}

	private double lerp(double a, double b, double f)
	{
		return a + (f * (b - a));
	}
	
	private double blerp(double a, double b, double c, double d, double tx, double ty)
	{
		return lerp(lerp(a, b, tx), lerp(c, d, tx), ty);
	}

	private double getBiomedHeight(int x, int z)
	{
		Vector v = new Vector(x, z, x * z);
		if(heightCache.containsKey(v))
		{
			return heightCache.get(v);
		}
		
		int wx = (int) Math.round((double) x * Iris.settings.gen.horizontalZoom);
		int wz = (int) Math.round((double) z * Iris.settings.gen.horizontalZoom);
		CBI biome = glBiome.getBiome(wx * Iris.settings.gen.biomeScale, wz * Iris.settings.gen.biomeScale);
		double h = Iris.settings.gen.baseHeight + biome.getHeight();
		h += (glBase.getHeight(wx, wz) * biome.getAmp()) - (0.33 * biome.getAmp());
		heightCache.put(v, h);
		
		return h;
	}

	private double getBilinearNoise(int x, int z)
	{
		int h = 3;
		int fx = x >> h;
		int fz = z >> h;
		int xa = (fx << h) - 2;
		int za = (fz << h) - 2;
		int xb = ((fx + 1) << h) + 2;
		int zb = ((fz + 1) << h) + 2;
		double na = getBiomedHeight(xa, za);
		double nb = getBiomedHeight(xa, zb);
		double nc = getBiomedHeight(xb, za);
		double nd = getBiomedHeight(xb, zb);
		double px = M.rangeScale(0, 1, xa, xb, x);
		double pz = M.rangeScale(0, 1, za, zb, z);

		return blerp(na, nc, nb, nd, px, pz);
	}

	private double getBicubicNoise(int x, int z)
	{
		int h = 3;
		int fx = x >> h;
		int fz = z >> h;
		int xa = (fx << h);
		int za = (fz << h);
		int xb = ((fx + 1) << h);
		int zb = ((fz + 1) << h);
		double na = getBilinearNoise(xa, za);
		double nb = getBilinearNoise(xa, zb);
		double nc = getBilinearNoise(xb, za);
		double nd = getBilinearNoise(xb, zb);
		double px = M.rangeScale(0, 1, xa, xb, x);
		double pz = M.rangeScale(0, 1, za, zb, z);

		return blerp(na, nc, nb, nd, px, pz);
	}

	
	private Biome genBaseColumn(int wxx, int wzx, int x, int z)
	{
		int seaLevel = Iris.settings.gen.seaLevel;
		int wx = (int) Math.round((double) wxx * Iris.settings.gen.horizontalZoom);
		int wz = (int) Math.round((double) wzx * Iris.settings.gen.horizontalZoom);
		CBI biome = glBiome.getBiome(wx * Iris.settings.gen.biomeScale, wz * Iris.settings.gen.biomeScale);
		double hv = getBicubicNoise(wxx, wzx);
		int height = getHeight(hv);

		for(int i = 0; i < Math.max(height, seaLevel); i++)
		{
			MB mb = new MB(Material.STONE);
			boolean underwater = i >= height && i < seaLevel;
			boolean underground = i < height;

			if(underwater)
			{
				mb = WATER;
			}

			if(underground && (height - 1) - i < glBase.scatterInt(x, i, z, 4) + 2)
			{
				mb = biome.getDirt(wx, wz);
			}

			if(i == height - 1)
			{
				mb = biome.getSurface(wx, wz, rng);

				if(height < 254 && height >= seaLevel)
				{
					MB place = biome.getScatterChanceSingle();

					if(!place.material.equals(Material.AIR))
					{
						if(mb.material.equals(Material.GRASS) || mb.material.equals(Material.SAND) || mb.material.equals(Material.DIRT))
						{
							setBlock(x, i + 1, z, place.material, place.data);
						}
					}
				}
			}

			if(Iris.settings.gen.flatBedrock ? i == 0 : i < glBase.scatterInt(x, i, z, 3))
			{
				mb = BEDROCK;
			}

			setBlock(x, i, z, mb.material, mb.data);
		}

		return biome.getRealBiome();
	}

	@Override
	public List<BlockPopulator> getDefaultPopulators(World world)
	{
		GList<BlockPopulator> p = new GList<BlockPopulator>();

		return p;
	}

	public int pick(int max, double noise)
	{
		return (int) (noise * max);
	}

	public MB pick(MB[] array, double noise)
	{
		return array[pick(array.length, noise)];
	}

	@Override
	public void onInitChunk(World world, int x, int z, Random random)
	{
		heightCache.clear();
	}

	@Override
	public void onPostChunk(World world, int x, int z, Random random)
	{
		
	}
}