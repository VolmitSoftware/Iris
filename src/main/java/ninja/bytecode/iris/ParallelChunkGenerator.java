package ninja.bytecode.iris;

import java.util.Random;

import org.bukkit.Material;
import org.bukkit.World;
import org.bukkit.generator.ChunkGenerator;

import ninja.bytecode.shuriken.Shuriken;
import ninja.bytecode.shuriken.execution.ChronoLatch;
import ninja.bytecode.shuriken.execution.J;
import ninja.bytecode.shuriken.execution.TaskExecutor.TaskGroup;
import ninja.bytecode.shuriken.format.F;

public abstract class ParallelChunkGenerator extends ChunkGenerator
{
	private int i;
	private int j;
	private int wx;
	private int wz;
	private ChunkData data;
	private TaskGroup tg;
	private boolean ready = false;
	private ChronoLatch cl = new ChronoLatch(5000);

	public ChunkData generateChunkData(World world, Random random, int x, int z, BiomeGrid biome)
	{
		Shuriken.profiler.start("chunkgen-" + world.getName());

		if(!ready)
		{
			onInit(world, random);
			ready = true;
		}

		data = createChunkData(world);
		tg = Iris.executor.startWork();

		for(i = 0; i < 16; i++)
		{
			wx = (x * 16) + i;

			for(j = 0; j < 16; j++)
			{
				wz = (z * 16) + j;
				int a = wx;
				int b = wz;
				int c = i;
				int d = j;
				tg.queue(() -> genColumn(a, b, c, d));
			}
		}

		tg.execute();
		Shuriken.profiler.stop("chunkgen-" + world.getName());

		if(cl.flip())
		{
			J.a(() -> System.out.print("Gen: " + F.duration(Shuriken.profiler.getResult("chunkgen-" + world.getName()).getAverage(), 2)));
		}

		return data;
	}

	public abstract void onInit(World world, Random random);

	public abstract void genColumn(int wx, int wz, int x, int z);

	@SuppressWarnings("deprecation")
	protected void setBlock(int x, int y, int z, Material b)
	{
		setBlock(x, y, z, b.getId(), (byte) 0);
	}

	@SuppressWarnings("deprecation")
	protected void setBlock(int x, int y, int z, Material b, byte d)
	{
		setBlock(x, y, z, b.getId(), d);
	}

	protected void setBlock(int x, int y, int z, int b)
	{
		setBlock(x, y, z, b, (byte) 0);
	}

	@SuppressWarnings("deprecation")
	protected void setBlock(int x, int y, int z, int b, byte d)
	{
		data.setBlock(x, y, z, b, d);
	}
}