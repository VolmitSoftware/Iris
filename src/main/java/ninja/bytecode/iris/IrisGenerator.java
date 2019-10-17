package ninja.bytecode.iris;

import java.util.Random;

import org.bukkit.Chunk;
import org.bukkit.Material;
import org.bukkit.World;
import org.bukkit.block.Biome;
import org.bukkit.block.data.Bisected;
import org.bukkit.block.data.Bisected.Half;
import org.bukkit.block.data.BlockData;
import org.bukkit.util.Vector;

import ninja.bytecode.iris.gen.GenLayerBase;
import ninja.bytecode.iris.gen.GenLayerBiome;
import ninja.bytecode.iris.gen.GenLayerDeepOcean;
import ninja.bytecode.iris.gen.IGenLayer;
import ninja.bytecode.iris.util.RealBiome;
import ninja.bytecode.shuriken.collections.GList;
import ninja.bytecode.shuriken.math.RNG;

public class IrisGenerator extends ParallelChunkGenerator
{
	private BlockData AIR = Material.AIR.createBlockData();
	private BlockData WATER = Material.WATER.createBlockData();
	private BlockData SAND = Material.SAND.createBlockData();
	private BlockData BEDROCK = Material.BEDROCK.createBlockData();
	private GList<IGenLayer> genLayers;
	private GenLayerBiome glBiome;
	private GenLayerBase glBase;
	private int waterLevel = 127;
	private GList<Vector> updates = new GList<>();

	public void doUpdates(Chunk c)
	{
		for(Vector i : updates)
		{
			c.getBlock(i.getBlockX(), i.getBlockY(), i.getBlockZ()).getState().update(true);
		}

		updates.clear();
	}

	@Override
	public void onInit(World world, Random random)
	{
		updates = new GList<>();
		genLayers = new GList<>();
		RNG rng = new RNG(world.getSeed());
		genLayers.add(glBiome = new GenLayerBiome(world, random, rng.nextRNG()));
		genLayers.add(glBase = new GenLayerBase(world, random, rng.nextRNG()));
		genLayers.add(new GenLayerDeepOcean(world, random, rng.nextRNG()));
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
		double temp = glBiome.getTemperature(wx, wz, height);
		RealBiome b = glBiome.getBiome(wx, wz, temp, height);
		boolean underwater = height < waterLevel;
		
		// Change biome to ocean / deep ocean if underwater height
		if(underwater)
		{
			if(waterLevel - height > 20)
			{
				if(temp <= 0.05)
				{
					b = RealBiome.of(Biome.DEEP_FROZEN_OCEAN);
				}

				else if(temp <= 0.35)
				{
					b = RealBiome.of(Biome.DEEP_COLD_OCEAN);
				}

				else if(temp <= 0.55)
				{
					b = RealBiome.of(Biome.DEEP_OCEAN);
				}

				else if(temp <= 0.65)
				{
					b = RealBiome.of(Biome.DEEP_LUKEWARM_OCEAN);
				}

				else
				{
					b = RealBiome.of(Biome.DEEP_WARM_OCEAN);
				}
			}

			else
			{
				if(temp <= 0.05)
				{
					b = RealBiome.of(Biome.FROZEN_OCEAN);
				}

				else if(temp <= 0.35)
				{
					b = RealBiome.of(Biome.COLD_OCEAN);
				}

				else if(temp <= 0.55)
				{
					b = RealBiome.of(Biome.OCEAN);
				}

				else if(temp <= 0.65)
				{
					b = RealBiome.of(Biome.LUKEWARM_OCEAN);
				}

				else
				{
					b = RealBiome.of(Biome.WARM_OCEAN);
				}
			}
		}

		if(height > 122 && height < 128 + (temp * 1.5) + (glBase.scatter(wx, wx * wz, wz) * 3.35))
		{
			b = RealBiome.of(Biome.BEACH);
		}

		for(int i = 0; i < Math.max(height, waterLevel); i++)
		{
			BlockData mb = AIR;

			// Bedrockify
			if(i == 0 || (!Iris.settings.gen.flatBedrock && ((i == 1 && glBase.scatterChance(wx, i, wz, 0.45)))))
			{
				mb = BEDROCK;
			}

			// Surface blocks
			else if(i == height - 1)
			{
				mb = b.surface(wx, i, wz, glBase);
			}

			// Dirt Blocks
			else if(!underwater && i > height - glBase.scatterInt(wx, i, wz, 12))
			{
				mb = b.dirt(wx, i, wz, glBase);
			}

			// Create Water blocks
			else if(i >= height && underwater)
			{
				mb = WATER;
			}

			// Below Dirt
			else
			{
				mb = b.rock(wx, i, wz, glBase);
			}

			if(mb.getMaterial().equals(Material.AIR))
			{
				continue;
			}

			setBlock(x, i, z, mb);
		}

		BlockData v = b.getSurfaceDecoration();

		if(v != null && underwater == b.isWater() && (underwater ? height < 125 : true))
		{
			if(v instanceof Bisected)
			{
				Bisected bs = (Bisected) v;
				bs.setHalf(Half.BOTTOM);
				setBlock(x, height, z, bs);
				Bisected bst = (Bisected) bs.clone();
				bst.setHalf(Half.TOP);
				setBlock(x, height + 1, z, bst);
			}

			else
			{
				setBlock(x, height, z, v);
			}
		}

		return b.getBiome();
	}

	private void scheduleUpdate(int x, int y, int z)
	{
		updates.add(new Vector(x, y, z));
	}

	public int pick(int max, double noise)
	{
		return (int) (noise * max);
	}

	public BlockData pick(BlockData[] array, double noise)
	{
		return array[pick(array.length, noise)];
	}
}