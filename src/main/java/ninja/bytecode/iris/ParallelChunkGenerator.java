package ninja.bytecode.iris;

import java.util.Random;

import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.World;
import org.bukkit.block.Biome;
import org.bukkit.generator.ChunkGenerator;

import ninja.bytecode.iris.atomics.AtomicChunkData;
import ninja.bytecode.iris.pop.PopulatorLakes;
import ninja.bytecode.iris.pop.PopulatorTrees;
import ninja.bytecode.shuriken.Shuriken;
import ninja.bytecode.shuriken.collections.GList;
import ninja.bytecode.shuriken.execution.ChronoLatch;
import ninja.bytecode.shuriken.execution.J;
import ninja.bytecode.shuriken.execution.TaskExecutor.TaskGroup;
import ninja.bytecode.shuriken.execution.TaskExecutor.TaskResult;
import ninja.bytecode.shuriken.format.F;
import ninja.bytecode.shuriken.logging.L;
import ninja.bytecode.shuriken.math.RollingSequence;

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
	private ChronoLatch cs = new ChronoLatch(1000);
	private RollingSequence rs = new RollingSequence(512);
	private RollingSequence cps = new RollingSequence(3);
	private World world;
	
	@SuppressWarnings("deprecation")
	public ParallelChunkGenerator()
	{
		Bukkit.getScheduler().scheduleAsyncRepeatingTask(Iris.instance, () ->
		{
			J.attempt(() ->
			{
				if(world.getPlayers().isEmpty())
				{
					return;
				}
				
				if(cs.flip())
				{
					cps.put(cg);
					cg = 0;
				}
				
				double total = rs.getAverage() + PopulatorTrees.timings.getAverage() + PopulatorLakes.timings.getAverage();
				double rcs = (1000D / total);
				double work = cps.getAverage() / (rcs + 1);
				L.i("Terrain Gen for " + world.getName());
				L.i("- Terrain (MLTC): " + F.duration(rs.getAverage(), 2));
				L.i("- Trees (SGLC): " + F.duration(PopulatorTrees.timings.getAverage(), 2));
				L.i("- Lakes (SGLC): " + F.duration(PopulatorLakes.timings.getAverage(), 2));
				L.i("Total: " + F.duration(total, 3) + " Work: " + F.f(cps.getAverage(), 0) + "/s of " + F.f(rcs, 0) + "/s (" + F.pc(work, 0) + " utilization)");
				L.flush();
				System.out.println("");
			});

		}, 20, 5);
	}

	public ChunkData generateChunkData(World world, Random random, int x, int z, BiomeGrid biome)
	{
		this.world = world;
		Shuriken.profiler.start("chunkgen-" + world.getName());
		data = new AtomicChunkData(world);

		try
		{
			if(!ready)
			{
				onInit(world, random);
				ready = true;
			}

			tg = Iris.genPool.startWork();

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
					tg.queue(() -> biome.setBiome(c, d, genColumn(a, b, c, d)));
				}
			}

			onInitChunk(world, x, z, random);
			TaskResult r = tg.execute();
			onPostChunk(world, x, z, random, data);
			rs.put(r.timeElapsed);
			Shuriken.profiler.stop("chunkgen-" + world.getName());
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

		return data.toChunkData();
	}

	public abstract void onInit(World world, Random random);

	public abstract void onInitChunk(World world, int x, int z, Random random);

	public abstract GList<Runnable> onPostChunk(World world, int x, int z, Random random, AtomicChunkData data2);

	public abstract Biome genColumn(int wx, int wz, int x, int z);

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

	protected void setBlock(int x, int y, int z, int b, byte d)
	{
		data.setBlock(x, y, z, b, d);
	}

	protected Material getType(int x, int y, int z)
	{
		return data.getType(x, y, z);
	}

	protected byte getData(int x, int y, int z)
	{
		return data.getData(x, y, z);
	}
}