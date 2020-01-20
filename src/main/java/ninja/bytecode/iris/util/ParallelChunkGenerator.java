package ninja.bytecode.iris.util;

import java.util.Random;
import java.util.concurrent.locks.ReentrantLock;

import org.bukkit.Material;
import org.bukkit.World;
import org.bukkit.block.Biome;
import org.bukkit.generator.ChunkGenerator;

import ninja.bytecode.iris.Iris;
import ninja.bytecode.iris.controller.ExecutionController;
import ninja.bytecode.iris.controller.TimingsController;
import ninja.bytecode.shuriken.execution.TaskExecutor;
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
	private ReentrantLock biomeLock;
	private TaskGroup tg;
	private boolean ready = false;
	int cg = 0;
	private RollingSequence rs = new RollingSequence(512);
	private World world;
	private TaskExecutor genPool;
	private TaskExecutor genPar;

	public World getWorld()
	{
		return world;
	}

	public Biome generateFullColumn(int a, int b, int c, int d, ChunkPlan p, AtomicChunkData data)
	{
		return genColumn(a, b, c, d, p, data);
	}

	public TaskGroup startParallaxWork()
	{
		if(genPar == null)
		{
			genPar = Iris.getController(ExecutionController.class).getExecutor(world, "Parallax");
		}

		return genPar.startWork();
	}

	public TaskGroup startWork()
	{
		if(genPool == null)
		{
			genPool = Iris.getController(ExecutionController.class).getExecutor(world, "Generator");
		}

		return genPool.startWork();
	}

	public ChunkData generateChunkData(World world, Random random, int x, int z, BiomeGrid biome)
	{
		AtomicChunkData data = new AtomicChunkData(world);

		try
		{
			Iris.getController(TimingsController.class).started("terrain");

			this.world = world;

			if(!ready)
			{
				biomeLock = new ReentrantLock();
				init(world, random);
				ready = true;
			}

			tg = startWork();
			O<ChunkPlan> plan = new O<ChunkPlan>();
			for(i = 0; i < 16; i++)
			{
				wx = (x << 4) + i;

				for(j = 0; j < 16; j++)
				{
					wz = (z << 4) + j;
					int a = wx;
					int b = wz;
					int c = i;
					int d = j;
					tg.queue(() ->
					{
						Biome f = generateFullColumn(a, b, c, d, plan.get(), data);
						biomeLock.lock();
						biome.setBiome(c, d, f);
						biomeLock.unlock();
					});
				}
			}

			plan.set(initChunk(world, x, z, random));
			TaskResult r = tg.execute();
			postChunk(world, x, z, random, data, plan.get());
			rs.put(r.timeElapsed);
			cg++;
			Iris.getController(TimingsController.class).stopped("terrain");
		}

		catch(Throwable e)
		{
			try
			{
				for(int i = 0; i < 16; i++)
				{
					for(int j = 0; j < 16; j++)
					{
						data.setBlock(i, 0, j, Material.RED_GLAZED_TERRACOTTA);
					}
				}
			}

			catch(Throwable ex)
			{

			}
			e.printStackTrace();
		}

		return data.toChunkData();
	}

	public abstract void init(World world, Random random);

	public abstract ChunkPlan initChunk(World world, int x, int z, Random random);

	public abstract void postChunk(World world, int x, int z, Random random, AtomicChunkData data, ChunkPlan plan);

	public abstract Biome genColumn(int wx, int wz, int x, int z, ChunkPlan plan, AtomicChunkData data);
}