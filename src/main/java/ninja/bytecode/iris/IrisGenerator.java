package ninja.bytecode.iris;

import java.util.Random;

import org.bukkit.Material;
import org.bukkit.World;
import org.bukkit.block.Biome;

import ninja.bytecode.iris.gen.GenLayerBase;
import ninja.bytecode.iris.gen.GenLayerBiome;
import ninja.bytecode.iris.gen.IGenLayer;
import ninja.bytecode.iris.util.RealBiome;
import ninja.bytecode.shuriken.collections.GList;
import ninja.bytecode.shuriken.math.RNG;

public class IrisGenerator extends ParallelChunkGenerator
{
	private GList<IGenLayer> genLayers;
	private GenLayerBiome glBiome;
	private GenLayerBase glBase;
	private int waterLevel = 127;

	@Override
	public void onInit(World world, Random random)
	{
		RNG rng = new RNG(world.getSeed());
		genLayers = new GList<>();
		genLayers.add(glBiome = new GenLayerBiome(world, random, rng));
		genLayers.add(glBase = new GenLayerBase(world, random, rng));
	}

	public int getHeight(double dx, double dz)
	{
		double noise = 0.5;

		for(IGenLayer i : genLayers)
		{
			noise = i.generateLayer(noise, dx, dz);
		}

		double n = noise * 250;
		n = n > 254 ? 254 : n;
		n = n < 0 ? 0 : n;

		return (int) n;
	}

	@Override
	public Biome genColumn(int wx, int wz, int x, int z)
	{
		int height = getHeight(wx, wz);
		RealBiome b = glBiome.getBiome(wx, wz);
		boolean underwater = height < waterLevel;

		for(int i = 0; i < Math.max(height, waterLevel); i++)
		{
			MB mb = underwater ? new MB(Material.STATIONARY_WATER) : new MB(Material.AIR);

			if(i > height && underwater)
			{
				mb = new MB(Material.STATIONARY_WATER);
			}

			else if(i == 0 || (i == 1 && glBase.scatterChance(wx, i, wz, 0.45)))
			{
				mb = new MB(Material.BEDROCK);
			}

			else if(i == height - 1)
			{
				if(underwater)
				{
					mb = new MB(Material.SAND);
				}

				else
				{
					mb = b.surface(wx, i, wz, glBase);
				}
			}

			else if(i > height - glBase.scatterInt(wx, i, wz, 12))
			{
				if(underwater)
				{
					mb = new MB(Material.SAND);
				}

				else
				{
					mb = b.dirt(wx, i, wz, glBase);
				}
			}

			else
			{
				mb = b.rock(wx, i, wz, glBase);
			}

			setBlock(x, i, z, mb.material, mb.data);
		}

		return b.getBiome();
	}

	public int pick(int max, double noise)
	{
		return (int) (noise * max);
	}

	public MB pick(MB[] array, double noise)
	{
		return array[pick(array.length, noise)];
	}
}