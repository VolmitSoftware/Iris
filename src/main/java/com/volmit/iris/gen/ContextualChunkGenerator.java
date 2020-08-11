package com.volmit.iris.gen;

import java.util.List;
import java.util.Random;

import org.bukkit.Bukkit;
import org.bukkit.Chunk;
import org.bukkit.Location;
import org.bukkit.World;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.HandlerList;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.event.player.PlayerTeleportEvent;
import org.bukkit.event.world.ChunkLoadEvent;
import org.bukkit.event.world.ChunkUnloadEvent;
import org.bukkit.event.world.WorldUnloadEvent;
import org.bukkit.generator.BlockPopulator;
import org.bukkit.generator.ChunkGenerator;

import com.volmit.iris.Iris;
import com.volmit.iris.IrisContext;
import com.volmit.iris.IrisDataManager;
import com.volmit.iris.IrisMetrics;
import com.volmit.iris.noise.CNG;
import com.volmit.iris.object.IrisBiome;
import com.volmit.iris.object.IrisDimension;
import com.volmit.iris.object.IrisGenerator;
import com.volmit.iris.object.IrisObject;
import com.volmit.iris.object.IrisRegion;
import com.volmit.iris.object.IrisStructure;
import com.volmit.iris.util.B;
import com.volmit.iris.util.ChronoLatch;
import com.volmit.iris.util.J;
import com.volmit.iris.util.PrecisionStopwatch;
import com.volmit.iris.util.RNG;

import lombok.Data;
import lombok.EqualsAndHashCode;
import net.md_5.bungee.api.ChatColor;

@Data
@EqualsAndHashCode(callSuper = false)
public abstract class ContextualChunkGenerator extends ChunkGenerator implements Listener
{
	private IrisDataManager data;
	protected boolean failing;
	protected int task;
	protected boolean dev;
	protected boolean initialized;
	protected RNG masterRandom;
	protected ChronoLatch perSecond;
	protected ChronoLatch tickLatch;
	protected ChronoLatch pushLatch;
	protected IrisMetrics metrics;
	protected World world;
	protected int generated;
	protected int ticks;
	private boolean fastPregen = false;
	protected boolean pregenDone;

	public ContextualChunkGenerator()
	{
		pushLatch = new ChronoLatch(3000);
		tickLatch = new ChronoLatch(650);
		perSecond = new ChronoLatch(1000);
		CNG.creates = 0;
		generated = 0;
		ticks = 0;
		task = -1;
		initialized = false;
		failing = false;
		pregenDone = false;
		dev = false;
	}

	protected abstract void onGenerate(RNG masterRandom, int x, int z, ChunkData data, BiomeGrid grid);

	protected abstract void onInit(World world, RNG masterRandom);

	protected abstract void onTick(int ticks);

	protected abstract void onClose();

	protected abstract void onFailure(Throwable e);

	protected abstract void onChunkLoaded(Chunk c);

	protected abstract void onChunkUnloaded(Chunk c);

	protected abstract void onPlayerJoin(Player p);

	protected abstract void onPlayerLeft(Player p);

	public IrisRegion loadRegion(String i)
	{
		return getData().getRegionLoader().load(i);
	}

	public IrisBiome loadBiome(String i)
	{
		return getData().getBiomeLoader().load(i);
	}

	public IrisStructure loadStructure(String i)
	{
		return getData().getStructureLoader().load(i);
	}

	public IrisObject loadObject(String i)
	{
		return getData().getObjectLoader().load(i);
	}

	public IrisDimension loadDimension(String i)
	{
		return (getData() == null ? Iris.globaldata : getData()).getDimensionLoader().load(i);
	}

	public IrisGenerator loadGenerator(String i)
	{
		return getData().getGeneratorLoader().load(i);
	}

	public IrisDataManager getData()
	{
		return isDev() ? Iris.globaldata : data;
	}

	private void init(World world, RNG rng)
	{
		if(initialized)
		{
			return;
		}

		data = new IrisDataManager(getWorld().getWorldFolder());
		this.world = world;
		this.masterRandom = new RNG(world.getSeed());
		metrics = new IrisMetrics(128);
		initialized = true;
		Bukkit.getServer().getPluginManager().registerEvents(this, Iris.instance);
		task = Bukkit.getScheduler().scheduleSyncRepeatingTask(Iris.instance, this::tick, 0, 0);
		onInit(world, masterRandom);
	}

	private void tick()
	{
		if(dev)
		{
			if(perSecond.flip())
			{
				if(generated > (fastPregen ? 1950 : 770))
				{
					pregenDone = true;
				}

				if(pregenDone)
				{
					metrics.getPerSecond().put(generated);
					generated = 0;
				}
			}
		}

		else
		{
			pregenDone = true;
			fastPregen = false;
		}

		onTick(ticks++);
	}

	@EventHandler
	public void on(PlayerTeleportEvent e)
	{
		if(e.getFrom().getWorld().equals(world) && !e.getTo().getWorld().equals(world))
		{
			tick();
			onPlayerLeft(e.getPlayer());
		}

		if(!e.getFrom().getWorld().equals(world) && e.getTo().getWorld().equals(world))
		{
			tick();
			onPlayerJoin(e.getPlayer());
		}
	}

	@EventHandler
	public void on(PlayerQuitEvent e)
	{
		if(e.getPlayer().getWorld().equals(world))
		{
			tick();
			onPlayerLeft(e.getPlayer());
		}
	}

	@EventHandler
	public void on(PlayerJoinEvent e)
	{
		if(e.getPlayer().getWorld().equals(world))
		{
			tick();
			onPlayerJoin(e.getPlayer());
		}
	}

	@EventHandler
	public void on(ChunkLoadEvent e)
	{
		if(e.getWorld().equals(world))
		{
			tick();
			onChunkLoaded(e.getChunk());
		}
	}

	@EventHandler
	public void on(ChunkUnloadEvent e)
	{
		if(e.getWorld().equals(world))
		{
			tick();
			onChunkUnloaded(e.getChunk());
		}
	}

	@EventHandler
	public void on(WorldUnloadEvent e)
	{
		if(world != null && e.getWorld().equals(world))
		{
			close();
		}
	}

	public void close()
	{
		HandlerList.unregisterAll(this);
		Bukkit.getScheduler().cancelTask(getTask());
		onClose();
	}

	@Override
	public boolean canSpawn(World world, int x, int z)
	{
		return super.canSpawn(world, x, z);
	}

	protected ChunkData generateChunkDataFailure(World world, Random no, int x, int z, BiomeGrid biomeGrid)
	{
		ChunkData c = Bukkit.createChunkData(world);

		for(int i = 0; i < 16; i++)
		{
			for(int j = 0; j < 16; j++)
			{
				int h = 0;

				if(j == i || j + i == 16)
				{
					c.setBlock(i, h, j, B.getBlockData("RED_TERRACOTTA"));
				}

				else
				{
					c.setBlock(i, h, j, B.getBlockData("BLACK_TERRACOTTA"));
				}
			}
		}

		return c;
	}

	protected ChunkData generateChunkFastPregen(World world, Random no, int x, int z, BiomeGrid biomeGrid)
	{
		ChunkData c = Bukkit.createChunkData(world);

		for(int i = 0; i < 16; i++)
		{
			for(int j = 0; j < 16; j++)
			{
				int h = 0;

				if(j == i || j + i == 16)
				{
					c.setBlock(i, h, j, B.getBlockData("BLUE_TERRACOTTA"));
				}

				else
				{
					c.setBlock(i, h, j, B.getBlockData("WHITE_TERRACOTTA"));
				}
			}
		}

		return c;
	}

	@Override
	public ChunkData generateChunkData(World world, Random no, int x, int z, BiomeGrid biomeGrid)
	{
		if(!dev)
		{
			pregenDone = true;
			fastPregen = false;
		}

		PrecisionStopwatch sx = PrecisionStopwatch.start();

		if(failing)
		{
			return generateChunkDataFailure(world, no, x, z, biomeGrid);
		}

		try
		{
			if(pushLatch.flip())
			{
				if(this.world == null)
				{
					this.world = world;
				}

				Iris.hotloader.check((IrisContext) this);

				if(this instanceof IrisContext)
				{
					IrisContext.pushContext((IrisContext) this);
				}
			}

			PrecisionStopwatch s = PrecisionStopwatch.start();
			RNG random = new RNG(world.getSeed());
			init(world, random.nextParallelRNG(0));

			ChunkData c = Bukkit.createChunkData(world);

			if(!pregenDone && fastPregen)
			{
				c = generateChunkFastPregen(world, no, x, z, biomeGrid);
			}

			else
			{
				onGenerate(random, x, z, c, biomeGrid);
			}

			metrics.getTotal().put(s.getMilliseconds());
			generated++;
			long hits = CNG.hits;
			CNG.hits = 0;
			Iris.instance.hit(hits);
			metrics.getLoss().put(sx.getMilliseconds() - s.getMilliseconds());
			return c;
		}

		catch(Throwable e)
		{
			fail(e);
		}

		return generateChunkDataFailure(world, no, x, z, biomeGrid);
	}

	public void onHotload()
	{

	}

	protected void fail(Throwable e)
	{
		if(failing)
		{
			return;
		}

		failing = true;

		e.printStackTrace();
		J.a(() ->
		{
			J.sleep(1000);
			Iris.error("---------------------------------------------------------------------------------------------------------");
			e.printStackTrace();
			Iris.error("---------------------------------------------------------------------------------------------------------");
			Iris.error("ERROR! Failed to generate chunk! Iris has entered a failed state!");
			Iris.error("---------------------------------------------------------------------------------------------------------");

			for(Player i : world.getPlayers())
			{
				Iris.instance.imsg(i, ChatColor.DARK_RED + "Iris Generator has entered a failed state!");
				Iris.instance.imsg(i, ChatColor.RED + "- Check the console for the error.");
				Iris.instance.imsg(i, ChatColor.RED + "- Then simply run /iris dev");
			}
		});

		onFailure(e);
	}

	@Override
	public List<BlockPopulator> getDefaultPopulators(World world)
	{
		return super.getDefaultPopulators(world);
	}

	@Override
	public Location getFixedSpawnLocation(World world, Random random)
	{
		return super.getFixedSpawnLocation(world, random);
	}

	@Override
	public boolean isParallelCapable()
	{
		return true;
	}
}
