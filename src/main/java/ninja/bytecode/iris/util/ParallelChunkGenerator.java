package ninja.bytecode.iris.util;

import java.util.Random;

import org.bukkit.Material;
import org.bukkit.World;
import org.bukkit.block.Biome;
import org.bukkit.generator.ChunkGenerator;

import ninja.bytecode.iris.Iris;
import ninja.bytecode.shuriken.Shuriken;
import ninja.bytecode.shuriken.execution.ChronoLatch;
import ninja.bytecode.shuriken.execution.TaskExecutor.TaskGroup;
import ninja.bytecode.shuriken.execution.TaskExecutor.TaskResult;
import ninja.bytecode.shuriken.math.RollingSequence;
import ninja.bytecode.shuriken.reaction.O;

public abstract class ParallelChunkGenerator extends ChunkGenerator
{
	private int i;
	private int j;
	private int wx;
	private int wz;
	private AtomicChunkData data;
	private TaskGroup tg;
	private boolean ready = false;
	int cg = 0;
	private ChronoLatch cl = new ChronoLatch(1000);
	private RollingSequence rs = new RollingSequence(512);
	private World world;
	
	public World getWorld()
	{
		return world;
	}
	
	public void generateFullColumn(int a, int b, int c, int d, BiomeGrid g, ChunkPlan p)
	{
		g.setBiome(c, d, genColumn(a, b, c, d, p));
		decorateColumn(a, b, c, d, p);
	}

	public ChunkData generateChunkData(World world, Random random, int x, int z, BiomeGrid biome)
	{
		Iris.started("terrain");
		this.world = world;
		data = new AtomicChunkData(world);

		try
		{
			if(!ready)
			{
				onInit(world, random);
				ready = true;
			}

			tg = Iris.genPool.startWork();
			O<ChunkPlan> plan = new O<ChunkPlan>();
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
					tg.queue(() -> generateFullColumn(a, b, c, d, biome, plan.get()));
				}
			}

			plan.set(onInitChunk(world, x, z, random));
			TaskResult r = tg.execute();
			onPostChunk(world, x, z, random, data, plan.get());
			rs.put(r.timeElapsed);
			Iris.profiler.getResult("caves").put(plan.get().getCaveMs());
			cg++;
		}

		catch(Throwable e)
		{
			if(cl.flip())
			{
				e.printStackTrace();
			}

			for(int i = 0; i < 16; i++)
			{
				for(int j = 0; j < 16; j++)
				{
					data.setBlock(i, 0, j, Material.RED_GLAZED_TERRACOTTA);
				}
			}
		}

		Iris.stopped("terrain");
		
		return data.toChunkData();
	}

	public abstract void onInit(World world, Random random);

	public abstract ChunkPlan onInitChunk(World world, int x, int z, Random random);

	public abstract void onPostChunk(World world, int x, int z, Random random, AtomicChunkData data, ChunkPlan plan);

	public abstract Biome genColumn(int wx, int wz, int x, int z, ChunkPlan plan);
	
	public abstract void decorateColumn(int wx, int wz, int x, int z, ChunkPlan plan);
	
	@SuppressWarnings("deprecation")
	public void setBlock(int x, int y, int z, Material b)
	{
		setBlock(x, y, z, b.getId(), (byte) 0);
	}

	@SuppressWarnings("deprecation")
	public void setBlock(int x, int y, int z, Material b, byte d)
	{
		setBlock(x, y, z, b.getId(), d);
	}

	public void setBlock(int x, int y, int z, int b)
	{
		setBlock(x, y, z, b, (byte) 0);
	}

	public void setBlock(int x, int y, int z, int b, byte d)
	{
		data.setBlock(x, y, z, b, d);
	}

	public Material getType(int x, int y, int z)
	{
		return data.getType(x, y, z);
	}

	public byte getData(int x, int y, int z)
	{
		return data.getData(x, y, z);
	}
}