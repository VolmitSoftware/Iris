package com.volmit.iris.gen.v2;

import java.util.Random;
import java.util.UUID;

import org.bukkit.World;
import org.bukkit.WorldCreator;
import org.bukkit.block.Biome;
import org.bukkit.entity.Player;
import org.bukkit.generator.ChunkGenerator;

import com.volmit.iris.Iris;
import com.volmit.iris.gen.v2.scaffold.hunk.Hunk;
import com.volmit.iris.util.Form;
import com.volmit.iris.util.PrecisionStopwatch;

public class TestGen
{
	public static void gen(Player p)
	{
		p.teleport(new WorldCreator("t/" + UUID.randomUUID().toString()).generator(new ChunkGenerator()
		{
			IrisTerrainGenerator tg = new IrisTerrainGenerator(1337, Iris.globaldata.getDimensionLoader().load("overworld"), Iris.globaldata);

			public boolean isParallelCapable()
			{
				return true;
			}

			@Override
			public ChunkData generateChunkData(World world, Random random, int x, int z, BiomeGrid biome)
			{
				PrecisionStopwatch p = PrecisionStopwatch.start();
				ChunkData c = createChunkData(world);
				Hunk<Biome> b = Hunk.newHunk(16, 256, 16);
				tg.generate(x, z, Hunk.view(c), b);

				for(int i = 0; i < 16; i++)
				{
					for(int j = 0; j < 256; j++)
					{
						for(int k = 0; k < 16; k++)
						{
							biome.setBiome(i, j, k, b.get(i, j, k));
						}
					}
				}

				Iris.info("Generated " + x + " " + z + " in " + Form.duration(p.getMilliseconds(), 2));
				return c;
			}
		}).createWorld().getSpawnLocation());
	}
}
