package ninja.bytecode.iris.generator.parallax;

import java.util.Random;

import org.bukkit.Bukkit;
import org.bukkit.Chunk;
import org.bukkit.World;
import org.bukkit.block.Biome;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.world.ChunkLoadEvent;
import org.bukkit.event.world.WorldSaveEvent;
import org.bukkit.event.world.WorldUnloadEvent;

import mortar.api.nms.NMP;
import ninja.bytecode.iris.Iris;
import ninja.bytecode.iris.generator.IrisGenerator;
import ninja.bytecode.iris.generator.atomics.AtomicChunkData;
import ninja.bytecode.iris.util.ChunkPlan;
import ninja.bytecode.iris.util.IrisWorldData;
import ninja.bytecode.iris.util.ObjectMode;
import ninja.bytecode.iris.util.SChunkVector;
import ninja.bytecode.shuriken.bench.PrecisionStopwatch;
import ninja.bytecode.shuriken.collections.GSet;
import ninja.bytecode.shuriken.execution.ChronoLatch;
import ninja.bytecode.shuriken.execution.TaskExecutor.TaskGroup;
import ninja.bytecode.shuriken.math.RNG;

public abstract class ParallaxWorldGenerator extends ParallelChunkGenerator implements Listener
{
	private World world;
	private IrisWorldData data;
	private RNG rMaster;
	private AtomicChunkData buffer;
	private GSet<Chunk> fix;
	private ChronoLatch cl;
	protected boolean saving;

	@Override
	public final void init(World world, Random random)
	{
		this.world = world;
		saving = true;
		cl = new ChronoLatch(3000);
		fix = new GSet<>();
		buffer = new AtomicChunkData(world);
		this.data = new IrisWorldData(world);
		this.rMaster = new RNG(world.getSeed() + 1);
		onInit(world, rMaster.nextParallelRNG(1));
		Bukkit.getPluginManager().registerEvents(this, Iris.instance);
	}

	public void disableSaving()
	{
		saving = false;
		data.disableSaving();
	}

	public void enableSaving()
	{
		saving = true;
		data.enableSaving();
	}

	@EventHandler
	public void on(ChunkLoadEvent e)
	{
		if(!saving)
		{
			return;
		}

		if(Iris.settings.performance.objectMode.equals(ObjectMode.PARALLAX) && !Iris.settings.performance.fastMode && e.getWorld().equals(world))
		{
			NMP.host.relight(e.getChunk());
			Bukkit.getScheduler().scheduleSyncDelayedTask(Iris.instance, () -> fix.add(e.getChunk()), 20);

			if(cl.flip())
			{
				for(Chunk i : fix)
				{
					for(Player f : e.getWorld().getPlayers())
					{
						NMP.CHUNK.refreshIgnorePosition(f, i);
					}
				}

				fix.clear();
			}
		}
	}

	@EventHandler
	public void on(WorldUnloadEvent e)
	{
		if(e.getWorld().equals(world))
		{
			getWorldData().dispose();
			onUnload();
		}
	}

	@EventHandler
	public void on(WorldSaveEvent e)
	{
		if(!saving)
		{
			return;
		}

		if(e.getWorld().equals(world))
		{
			getWorldData().saveAll();
		}
	}

	public ParallaxAnchor computeAnchor(int wx, int wz, ChunkPlan heightBuffer, AtomicChunkData data)
	{
		onGenColumn(wx, wz, wx & 15, wz & 15, heightBuffer, data, false);

		return new ParallaxAnchor(heightBuffer.getRealHeight(wx & 15, wz & 15), heightBuffer.getRealWaterHeight(wx & 15, wz & 15), heightBuffer.getBiome(wx & 15, wz & 15), data);
	}

	public ParallaxAnchor computeAnchor(int wx, int wz)
	{
		ChunkPlan heightBuffer = new ChunkPlan();
		onGenColumn(wx, wz, wx & 15, wz & 15, heightBuffer, buffer, false);

		return new ParallaxAnchor(heightBuffer.getRealHeight(wx & 15, wz & 15), heightBuffer.getRealWaterHeight(wx & 15, wz & 15), heightBuffer.getBiome(wx & 15, wz & 15), buffer);
	}

	public void doGenParallax(int x, int z)
	{
		onGenParallax(x, z, getRMaster(x, z, -59328));
		getWorldData().getChunk(x, z);
	}

	@Override
	public final ChunkPlan initChunk(World world, int x, int z, Random random)
	{
		PrecisionStopwatch ps = PrecisionStopwatch.start();
		TaskGroup g = startWork();
		if(Iris.settings.performance.objectMode.equals(ObjectMode.PARALLAX))
		{
			for(int ii = Iris.settings.performance.fastMode ? -1 : -(getParallaxSize().getX() / 2) - 1; ii < (Iris.settings.performance.fastMode ? 1 : ((getParallaxSize().getX() / 2) + 1)); ii++)
			{
				int i = ii;

				for(int jj = Iris.settings.performance.fastMode ? -1 : -(getParallaxSize().getZ() / 2) - 1; jj < (Iris.settings.performance.fastMode ? 1 : ((getParallaxSize().getZ() / 2) + 1)); jj++)
				{
					int j = jj;
					int cx = x + i;
					int cz = z + j;

					if(!getWorldData().exists(cx, cz))
					{
						g.queue(() ->
						{
							onGenParallax(cx, cz, getRMaster(cx, cz, -59328));
							getWorldData().getChunk(cx, cz);
						});
					}
				}
			}

		}

		((IrisGenerator) this).getMetrics().put("parallax:ms:/chunk", ps.getMillis());

		return onInitChunk(world, x, z, random);
	}

	@Override
	public final void postChunk(World world, int x, int z, Random random, AtomicChunkData data, ChunkPlan plan)
	{
		getWorldData().inject(x, z, data);
		onPostChunk(world, x, z, random, data, plan);
	}

	@Override
	public final Biome genColumn(int wx, int wz, int x, int z, ChunkPlan plan, AtomicChunkData data, boolean surface)
	{
		return onGenColumn(wx, wz, x, z, plan, data, surface);
	}

	public World getWorld()
	{
		return world;
	}

	public IrisWorldData getWorldData()
	{
		return data;
	}

	public RNG getRMaster()
	{
		return rMaster;
	}

	public RNG getRMaster(int x, int z, int signature)
	{
		return rMaster.nextParallelRNG((int) (signature + x * z + z + x * 2.12));
	}

	protected abstract void onUnload();

	protected abstract SChunkVector getParallaxSize();

	public abstract void onGenParallax(int x, int z, Random random);

	public abstract void onInit(World world, Random random);

	public abstract ChunkPlan onInitChunk(World world, int x, int z, Random random);

	public abstract Biome onGenColumn(int wx, int wz, int x, int z, ChunkPlan plan, AtomicChunkData data, boolean surfaceOnly);

	public abstract void onPostChunk(World world, int x, int z, Random random, AtomicChunkData data, ChunkPlan plan);
}
