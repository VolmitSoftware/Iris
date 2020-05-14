package ninja.bytecode.iris.generator;

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

import lombok.Data;
import lombok.EqualsAndHashCode;
import net.md_5.bungee.api.ChatColor;
import ninja.bytecode.iris.Iris;
import ninja.bytecode.iris.IrisContext;
import ninja.bytecode.iris.IrisMetrics;
import ninja.bytecode.iris.util.BlockDataTools;
import ninja.bytecode.iris.util.CNG;
import ninja.bytecode.iris.util.ChronoLatch;
import ninja.bytecode.iris.util.RNG;
import ninja.bytecode.shuriken.bench.PrecisionStopwatch;
import ninja.bytecode.shuriken.logging.L;

@Data
@EqualsAndHashCode(callSuper = false)
public abstract class ContextualChunkGenerator extends ChunkGenerator implements Listener
{
	protected boolean failing;
	protected int task;
	protected boolean initialized;
	protected RNG masterRandom;
	protected ChronoLatch perSecond;
	protected ChronoLatch pushLatch;
	protected IrisMetrics metrics;
	protected World world;
	protected int generated;
	protected int ticks;
	protected boolean pregenDone;

	public ContextualChunkGenerator()
	{
		pushLatch = new ChronoLatch(3000);
		perSecond = new ChronoLatch(1000);
		CNG.creates = 0;
		generated = 0;
		ticks = 0;
		task = -1;
		initialized = false;
		failing = false;
		pregenDone = false;
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

	private void init(World world, RNG rng)
	{
		if(initialized)
		{
			return;
		}

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
		if(perSecond.flip())
		{
			if(generated > 770)
			{
				pregenDone = true;
			}

			if(pregenDone)
			{
				metrics.getPerSecond().put(generated);
				generated = 0;
			}
		}

		onTick(ticks++);
	}

	@EventHandler
	public void on(PlayerTeleportEvent e)
	{
		if(e.getFrom().getWorld().equals(world) && !e.getTo().getWorld().equals(world))
		{
			onPlayerLeft(e.getPlayer());
		}

		if(!e.getFrom().getWorld().equals(world) && e.getTo().getWorld().equals(world))
		{
			onPlayerJoin(e.getPlayer());
		}
	}

	@EventHandler
	public void on(PlayerQuitEvent e)
	{
		if(e.getPlayer().getWorld().equals(world))
		{
			onPlayerLeft(e.getPlayer());
		}
	}

	@EventHandler
	public void on(PlayerJoinEvent e)
	{
		if(e.getPlayer().getWorld().equals(world))
		{
			onPlayerJoin(e.getPlayer());
		}
	}

	@EventHandler
	public void on(ChunkLoadEvent e)
	{
		if(e.getWorld().equals(world))
		{
			onChunkLoaded(e.getChunk());
		}
	}

	@EventHandler
	public void on(ChunkUnloadEvent e)
	{
		if(e.getWorld().equals(world))
		{
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

	protected void close()
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
					c.setBlock(i, h, j, BlockDataTools.getBlockData("RED_TERRACOTTA"));
				}

				else
				{
					c.setBlock(i, h, j, BlockDataTools.getBlockData("BLACK_TERRACOTTA"));
				}
			}
		}

		return c;
	}

	@Override
	public ChunkData generateChunkData(World world, Random no, int x, int z, BiomeGrid biomeGrid)
	{
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

				Iris.hotloader.check();

				if(this instanceof IrisContext)
				{
					IrisContext.pushContext((IrisContext) this);
				}
			}

			PrecisionStopwatch s = PrecisionStopwatch.start();
			RNG random = new RNG(world.getSeed());
			init(world, random.nextParallelRNG(0));
			ChunkData c = Bukkit.createChunkData(world);
			onGenerate(random, x, z, c, biomeGrid);
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

	protected void fail(Throwable e)
	{
		failing = true;
		Iris.error("ERROR! Failed to generate chunk! Iris has entered a failed state!");

		for(Player i : world.getPlayers())
		{
			Iris.instance.imsg(i, ChatColor.DARK_RED + "Iris Generator has entered a failed state!");
			Iris.instance.imsg(i, ChatColor.RED + "- Check the console for the error.");
			Iris.instance.imsg(i, ChatColor.RED + "- Then simply run /iris dev");
		}

		L.ex(e);
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
