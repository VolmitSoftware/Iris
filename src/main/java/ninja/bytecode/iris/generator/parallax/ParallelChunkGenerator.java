package ninja.bytecode.iris.generator.parallax;

import java.util.Random;
import java.util.concurrent.locks.ReentrantLock;

import org.bukkit.Material;
import org.bukkit.World;
import org.bukkit.block.Biome;
import org.bukkit.generator.ChunkGenerator;

import mortar.util.text.C;
import ninja.bytecode.iris.Iris;
import ninja.bytecode.iris.generator.atomics.AtomicChunkData;
import ninja.bytecode.iris.util.ChunkPlan;
import ninja.bytecode.iris.util.ChunkSpliceListener;
import ninja.bytecode.shuriken.execution.TaskExecutor;
import ninja.bytecode.shuriken.execution.TaskExecutor.TaskGroup;
import ninja.bytecode.shuriken.execution.TaskExecutor.TaskResult;
import ninja.bytecode.shuriken.logging.L;
import ninja.bytecode.shuriken.math.RollingSequence;
import ninja.bytecode.shuriken.reaction.O;

public abstract class ParallelChunkGenerator extends ChunkGenerator
{
	private int i;
	private int j;
	private int wx;
	private int wz;
	private ReentrantLock biomeLock;
	private TaskExecutor backupService;
	private TaskGroup tg;
	private boolean ready = false;
	int cg = 0;
	private RollingSequence rs = new RollingSequence(512);
	private World world;
	private ChunkSpliceListener splicer;

	public void setSplicer(ChunkSpliceListener splicer)
	{
		this.splicer = splicer;
	}

	public World getWorld()
	{
		return world;
	}

	public Biome generateFullColumn(int a, int b, int c, int d, ChunkPlan p, AtomicChunkData data)
	{
		return genColumn(a, b, c, d, p, data, false);
	}

	private TaskGroup work(String n)
	{
		if(Iris.instance == null || Iris.exec() == null)
		{
			if(backupService == null)
			{
				L.f(C.RED + "Cannot contact ExecutionController!" + C.YELLOW + " Did you reload iris?");
				L.w(C.YELLOW + "Spinning up a temporary backup service until the issue resolves...");
				backupService = new TaskExecutor(4, Thread.MAX_PRIORITY, "Iris Backup Handover");
				Iris.instance.reload();
			}

			return backupService.startWork();
		}

		else if(backupService != null)
		{
			L.i(C.GREEN + "Reconnected to the execution service. Closing backup service now...");
			backupService.close();
		}

		return Iris.exec().getExecutor(world, n).startWork();
	}

	public TaskGroup startParallaxWork()
	{
		return work("Parallax");
	}

	public TaskGroup startWork()
	{
		return work("Generator");
	}

	public ChunkData generateChunkData(World world, Random random, int x, int z, BiomeGrid biome)
	{
		random = new Random(world.getSeed());
		if(splicer != null)
		{
			AtomicChunkData d = splicer.onSpliceAvailable(world, random, x, z, biome);

			if(d != null)
			{
				return d.toChunkData();
			}
		}

		AtomicChunkData data = new AtomicChunkData(world);

		try
		{
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
			onDecorateChunk(world, x, z, data, plan.get());
			TaskGroup gd = startWork();

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
					gd.queue(() -> onDecorateColumn(world, c, d, a, b, data, plan.get()));
				}
			}

			gd.execute();
			postChunk(world, x, z, random, data, plan.get());
			rs.put(r.timeElapsed);
			cg++;
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

	protected abstract void onDecorateColumn(World world2, int i2, int j2, int wx2, int wz2, AtomicChunkData data, ChunkPlan chunkPlan);

	protected abstract void onDecorateChunk(World world2, int x, int z, AtomicChunkData data, ChunkPlan chunkPlan);

	public abstract void init(World world, Random random);

	public abstract ChunkPlan initChunk(World world, int x, int z, Random random);

	public abstract void postChunk(World world, int x, int z, Random random, AtomicChunkData data, ChunkPlan plan);

	public abstract Biome genColumn(int wx, int wz, int x, int z, ChunkPlan plan, AtomicChunkData data, boolean surfaceOnly);
}