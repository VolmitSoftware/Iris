package ninja.bytecode.iris;

import java.util.List;
import java.util.Random;

import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.World;
import org.bukkit.generator.BlockPopulator;
import org.bukkit.generator.ChunkGenerator;

import ninja.bytecode.iris.util.ING;
import ninja.bytecode.iris.util.RNG;

public class IrisGenerator extends ChunkGenerator
{
	private boolean initialized = false;
	private ING sng;

	public void onInit()
	{
		if(initialized)
		{
			return;
		}

		initialized = true;

		sng = new ING(new RNG(), 2);
	}

	@Override
	public boolean canSpawn(World world, int x, int z)
	{
		return super.canSpawn(world, x, z);
	}

	@Override
	public ChunkData generateChunkData(World world, Random random, int x, int z, BiomeGrid biome)
	{
		onInit();
		ChunkData data = Bukkit.createChunkData(world);

		for(int i = 0; i < 16; i++)
		{
			for(int j = 0; j < 16; j++)
			{
				double wx = (x * 16) + i;
				double wz = (z * 16) + j;
				int y = (int) Math.round(sng.noise(wx / 30D, wz / 30D) * 20);
				for(int k = 0; k < 4; k++)
				{
					if(k < 0)
					{
						continue;
					}

					data.setBlock(i, k + y, j, Material.STONE.createBlockData());
				}
			}
		}

		return data;
	}

	@Override
	public List<BlockPopulator> getDefaultPopulators(World world)
	{
		return super.getDefaultPopulators(world);
	}

	@Override
	public Location getFixedSpawnLocation(World world, Random random)
	{
		return super.getFixedSpawnLocation(world, random);
	}

	@Override
	public boolean isParallelCapable()
	{
		return true;
	}
}
