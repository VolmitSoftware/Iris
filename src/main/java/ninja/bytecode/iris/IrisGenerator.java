package ninja.bytecode.iris;

import java.util.List;
import java.util.Random;

import org.bukkit.BlockChangeDelegate;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.TreeType;
import org.bukkit.World;
import org.bukkit.block.Biome;
import org.bukkit.generator.BlockPopulator;

import ninja.bytecode.iris.biome.CBI;
import ninja.bytecode.iris.gen.GenLayerBase;
import ninja.bytecode.iris.gen.GenLayerBiome;
import ninja.bytecode.iris.gen.IGenLayer;
import ninja.bytecode.iris.util.PolygonGenerator;
import ninja.bytecode.shuriken.collections.GList;
import ninja.bytecode.shuriken.collections.GMap;
import ninja.bytecode.shuriken.logging.L;
import ninja.bytecode.shuriken.math.M;
import ninja.bytecode.shuriken.math.RNG;

public class IrisGenerator extends ParallelChunkGenerator
{
	private MB WATER = new MB(Material.STATIONARY_WATER);
	private MB BEDROCK = new MB(Material.BEDROCK);
	private GList<IGenLayer> genLayers;
	private GenLayerBase glBase;
	private GenLayerBiome glBiome;
	private GMap<Location, TreeType> trees;
	private RNG rng;
	private World world;
	private PolygonGenerator g;

	@Override
	public void onInit(World world, Random random)
	{
		this.world = world;
		trees = new GMap<>();
		genLayers = new GList<>();
		rng = new RNG(world.getSeed());
		genLayers.add(glBase = new GenLayerBase(this, world, random, rng.nextRNG()));
		genLayers.add(glBiome = new GenLayerBiome(this, world, random, rng.nextRNG()));
		g = new PolygonGenerator(rng, 16, 0.01, 1, (c) -> c);
	}

	public int getHeight(double dx, double dz)
	{
		double height = M.clip(getRawHeight(dx, dz), 0D, 1D);

		return (int) (height * 253);
	}

	public double getRawHeight(double dx, double dz)
	{
		double noise = 0 + Iris.settings.gen.baseHeight;

		for(IGenLayer i : genLayers)
		{
			noise = i.generateLayer(noise, dx, dz);
		}

		return M.clip(noise, 0D, 1D);
	}

	@Override
	public Biome genColumn(int wxx, int wzx, int x, int z)
	{
		if(true)
		{
			for(int i = 0; i < 1; i++)
			{
				setBlock(x, i, z, Material.CONCRETE, (byte) g.getIndex(wxx, wzx));
			}
			
			return Biome.PLAINS;
		}

		else
		{
			return genBaseColumn(wxx, wzx, x, z);
		}
	}

	private Biome genBaseColumn(int wxx, int wzx, int x, int z)
	{
		int seaLevel = Iris.settings.gen.seaLevel;
		int wx = (int) Math.round((double) wxx * Iris.settings.gen.horizontalZoom);
		int wz = (int) Math.round((double) wzx * Iris.settings.gen.horizontalZoom);
		CBI biome = glBiome.getBiome(wx * Iris.settings.gen.biomeScale, wz * Iris.settings.gen.biomeScale);
		int height = getHeight(wx, wz) + 25;

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

				if(height < 240 && height >= seaLevel)
				{
					TreeType s = biome.getTreeChanceSingle();

					if(s != null)
					{
						setBlock(x, i + 1, z, Material.AIR);
						trees.put(new Location(world, x, i + 1, z), s);
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

	}

	@Override
	public void onPostChunk(World world, int x, int z, Random random)
	{

	}

	public double getBiomeBorder(double dx, double dz)
	{
		return glBiome.getCenterPercent(dx, dz);
	}
}