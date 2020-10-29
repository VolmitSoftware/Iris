package com.volmit.iris.v2;

import java.util.List;
import java.util.Random;
import java.util.UUID;

import com.volmit.iris.util.KList;
import org.bukkit.Location;
import org.bukkit.World;
import org.bukkit.WorldCreator;
import org.bukkit.block.Biome;
import org.bukkit.entity.Player;
import org.bukkit.generator.BlockPopulator;
import org.bukkit.generator.ChunkGenerator;

import com.volmit.iris.Iris;
import com.volmit.iris.v2.scaffold.hunk.Hunk;
import com.volmit.iris.util.Form;
import com.volmit.iris.util.PrecisionStopwatch;
import org.jetbrains.annotations.NotNull;

public class TestGen
{
	public static void gen(Player p)
	{
		IrisTerrainGenerator tg = new IrisTerrainGenerator(1337, Iris.globaldata.getDimensionLoader().load("overworld"), Iris.globaldata);
		p.teleport(new Location(new WorldCreator("t/" + UUID.randomUUID().toString()).generator(new ChunkGenerator()
		{
			public boolean isParallelCapable()
			{
				return true;
			}

			@NotNull
			@Override
			public List<BlockPopulator> getDefaultPopulators(@NotNull World world) {
				return new KList<BlockPopulator>().qadd(tg);
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
		}).createWorld(), 0, 200, 0));
	}
}
