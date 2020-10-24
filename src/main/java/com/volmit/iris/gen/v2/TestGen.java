package com.volmit.iris.gen.v2;

import java.util.Random;
import java.util.UUID;
import java.util.concurrent.Executor;
import java.util.concurrent.ExecutorService;

import org.bukkit.Material;
import org.bukkit.World;
import org.bukkit.WorldCreator;
import org.bukkit.block.data.BlockData;
import org.bukkit.entity.Player;
import org.bukkit.generator.ChunkGenerator;

import com.volmit.iris.Iris;
import com.volmit.iris.gen.v2.scaffold.Hunk;
import com.volmit.iris.gen.v2.scaffold.layer.ProceduralStream;
import com.volmit.iris.object.NoiseStyle;

public class TestGen
{
	public static void gen(Player p)
	{
		p.teleport(new WorldCreator("t/" + UUID.randomUUID().toString()).generator(new ChunkGenerator()
		{
			IrisTerrainGenerator tg = new IrisTerrainGenerator(1337, Iris.globaldata.getDimensionLoader().load("overworld"), Iris.globaldata);

			@Override
			public ChunkData generateChunkData(World world, Random random, int x, int z, BiomeGrid biome)
			{
				ChunkData c = createChunkData(world);
				tg.generate(x, z, Hunk.view(c), null);
				return c;
			}
		}).createWorld().getSpawnLocation());
	}
}
