package ninja.bytecode.iris.pop;

import java.util.Random;

import org.bukkit.Chunk;
import org.bukkit.Material;
import org.bukkit.World;
import org.bukkit.block.Block;
import org.bukkit.generator.BlockPopulator;

import ninja.bytecode.shuriken.bench.PrecisionStopwatch;
import ninja.bytecode.shuriken.math.RollingSequence;

public class PopulatorLakes extends BlockPopulator
{
	public static RollingSequence timings = new RollingSequence(512);

	@Override
	public void populate(World world, Random random, Chunk chunk)
	{
		PrecisionStopwatch f = PrecisionStopwatch.start();

		if(random.nextInt(100) < 10)
		{
			Block block;
			int chunkX = chunk.getX();
			int chunkZ = chunk.getZ();
			int X = chunkX * 16 + random.nextInt(15) - 8;
			int Z = chunkZ * 16 + random.nextInt(15) - 8;
			int Y;
			for(Y = world.getMaxHeight() - 1; chunk.getBlock(X, Y, Z).getType() == Material.AIR; Y--)
				;
			Y -= 7;
			block = world.getBlockAt(Z + 8, Y, Z + 8);
			if(random.nextInt(100) < 90)
				block.setType(Material.WATER);
			else
				block.setType(Material.LAVA);
			boolean[] aboolean = new boolean[2048];
			int i = random.nextInt(4) + 4;

			int j, j1, k1;

			for(j = 0; j < i; ++j)
			{
				double d0 = random.nextDouble() * 6.0D + 3.0D;
				double d1 = random.nextDouble() * 4.0D + 2.0D;
				double d2 = random.nextDouble() * 6.0D + 3.0D;
				double d3 = random.nextDouble() * (16.0D - d0 - 2.0D) + 1.0D + d0 / 2.0D;
				double d4 = random.nextDouble() * (8.0D - d1 - 4.0D) + 2.0D + d1 / 2.0D;
				double d5 = random.nextDouble() * (16.0D - d2 - 2.0D) + 1.0D + d2 / 2.0D;

				for(int k = 1; k < 15; ++k)
				{
					for(int l = 1; l < 15; ++l)
					{
						for(int i1 = 1; i1 < 7; ++i1)
						{
							double d6 = ((double) k - d3) / (d0 / 2.0D);
							double d7 = ((double) i1 - d4) / (d1 / 2.0D);
							double d8 = ((double) l - d5) / (d2 / 2.0D);
							double d9 = d6 * d6 + d7 * d7 + d8 * d8;

							if(d9 < 1.0D)
							{
								aboolean[(k * 16 + l) * 8 + i1] = true;
							}
						}
					}
				}
			}

			for(j = 0; j < 16; ++j)
			{
				for(k1 = 0; k1 < 16; ++k1)
				{
					for(j1 = 0; j1 < 8; ++j1)
					{
						if(aboolean[(j * 16 + k1) * 8 + j1])
						{
							world.getBlockAt(X + j, Y + j1, Z + k1).setType(j1 > 4 ? Material.AIR : block.getType());
						}
					}
				}
			}

			for(j = 0; j < 16; ++j)
			{
				for(k1 = 0; k1 < 16; ++k1)
				{
					for(j1 = 4; j1 < 8; ++j1)
					{
						if(aboolean[(j * 16 + k1) * 8 + j1])
						{
							int X1 = X + j;
							int Y1 = Y + j1 - 1;
							int Z1 = Z + k1;
							if(world.getBlockAt(X1, Y1, Z1).getType() == Material.DIRT)
							{
								world.getBlockAt(X1, Y1, Z1).setType(Material.GRASS, false);
							}
						}
					}
				}
			}
		}

		f.end();
		timings.put(f.getMilliseconds());
	}

}
