package com.volmit.iris.gen;

import java.util.List;
import java.util.Random;

import org.bukkit.Bukkit;
import org.bukkit.Chunk;
import org.bukkit.GameMode;
import org.bukkit.Location;
import org.bukkit.World;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.HandlerList;
import org.bukkit.event.Listener;
import org.bukkit.event.block.BlockBreakEvent;
import org.bukkit.event.block.BlockDropItemEvent;
import org.bukkit.event.block.BlockPlaceEvent;
import org.bukkit.event.entity.EntitySpawnEvent;
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
import com.volmit.iris.gen.atomics.AtomicCache;
import com.volmit.iris.gen.atomics.AtomicMulticache;
import com.volmit.iris.noise.CNG;
import com.volmit.iris.object.IrisBiome;
import com.volmit.iris.object.IrisDimension;
import com.volmit.iris.object.IrisGenerator;
import com.volmit.iris.object.IrisObject;
import com.volmit.iris.object.IrisRegion;
import com.volmit.iris.object.IrisStructure;
import com.volmit.iris.util.B;
import com.volmit.iris.util.BlockPosition;
import com.volmit.iris.util.C;
import com.volmit.iris.util.ChronoLatch;
import com.volmit.iris.util.J;
import com.volmit.iris.util.KList;
import com.volmit.iris.util.M;
import com.volmit.iris.util.RNG;

import lombok.Data;
import lombok.EqualsAndHashCode;

@Data
@EqualsAndHashCode(callSuper = false)
public abstract class ContextualChunkGenerator extends ChunkGenerator implements Listener
{
	private KList<BlockPosition> noLoot;
	private BlockPosition allowLoot;
	private AtomicMulticache cache;
	private IrisDataManager data;
	private boolean failing;
	private int task;
	private boolean dev;
	private boolean initialized;
	private RNG masterRandom;
	private ChronoLatch perSecond;
	private ChronoLatch tickLatch;
	private ChronoLatch pushLatch;
	private AtomicCache<IrisDimension> dimCache;
	private IrisMetrics metrics;
	private World world;
	private int generated;
	private int ticks;
	private long hlast;
	private boolean fastPregen = false;
	private boolean pregenDone;
	private volatile boolean hotloadable = false;

	public ContextualChunkGenerator()
	{
		pushLatch = new ChronoLatch(3000);
		tickLatch = new ChronoLatch(650);
		perSecond = new ChronoLatch(1000);
		hlast = M.ms();
		cache = new AtomicMulticache();
		CNG.creates = 0;
		generated = 0;
		ticks = 0;
		task = -1;
		initialized = false;
		allowLoot = new BlockPosition(0, 0, 0);
		failing = false;
		pregenDone = false;
		dimCache = new AtomicCache<>();
		dev = false;
		noLoot = new KList<>(1285);
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
		return getDimCache().aquire(() -> (getData() == null ? Iris.globaldata : getData()).getDimensionLoader().load(i));
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

		this.world = world;
		setData(new IrisDataManager(getWorld().getWorldFolder()));
		setMasterRandom(new RNG(world.getSeed()));
		setMetrics(new IrisMetrics(128));
		setInitialized(true);
		setTask(Bukkit.getScheduler().scheduleSyncRepeatingTask(Iris.instance, this::tick, 0, 0));
		Bukkit.getServer().getPluginManager().registerEvents(this, Iris.instance);
		onInit(world, masterRandom);
		setHotloadable(true);
	}

	private void tick()
	{
		if(isDev())
		{
			if(getPerSecond().flip())
			{
				if(getGenerated() > (isFastPregen() ? 1950 : 770))
				{
					setPregenDone(true);
				}

				if(isPregenDone())
				{
					getMetrics().getPerSecond().put(generated);
					setGenerated(0);
				}

				checkHotload();

				if(getNoLoot().size() > 1024)
				{
					for(int i = 0; i < 64; i++)
					{
						getNoLoot().remove(0);
					}
				}
			}
		}

		else
		{
			setPregenDone(true);
			setFastPregen(false);
		}

		onTick(ticks++);
	}

	@EventHandler(priority = EventPriority.MONITOR)
	public void on(EntitySpawnEvent e)
	{
		onSpawn(e);
	}

	protected abstract void onSpawn(EntitySpawnEvent e);

	@EventHandler(priority = EventPriority.MONITOR)
	public void on(BlockBreakEvent e)
	{
		if(!e.getBlock().getWorld().equals(getWorld()))
		{
			return;
		}

		BlockPosition bp = new BlockPosition(e.getBlock().getX(), e.getBlock().getY(), e.getBlock().getZ());

		if(!noLoot.contains(bp))
		{
			noLoot.add(bp);

			if(e.isDropItems() && e.getPlayer().getGameMode().equals(GameMode.SURVIVAL))
			{
				allowLoot = new BlockPosition(e.getBlock().getX(), e.getBlock().getY(), e.getBlock().getZ());
			}
		}
	}

	@EventHandler(priority = EventPriority.MONITOR)
	public void on(BlockPlaceEvent e)
	{
		if(!e.getBlock().getWorld().equals(getWorld()))
		{
			return;
		}

		getNoLoot().addIfMissing(new BlockPosition(e.getBlock().getX(), e.getBlock().getY(), e.getBlock().getZ()));
	}

	@EventHandler(priority = EventPriority.MONITOR)
	public void on(BlockDropItemEvent e)
	{
		if(!e.getBlock().getWorld().equals(getWorld()))
		{
			return;
		}

		BlockPosition bp = new BlockPosition(e.getBlock().getX(), e.getBlock().getY(), e.getBlock().getZ());

		if(noLoot.contains(bp) && !allowLoot.equals(bp))
		{
			return;
		}

		handleDrops(e);
	}

	protected abstract void handleDrops(BlockDropItemEvent e);

	@EventHandler(priority = EventPriority.MONITOR)
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

	@EventHandler(priority = EventPriority.MONITOR)
	public void on(PlayerQuitEvent e)
	{
		if(e.getPlayer().getWorld().equals(world))
		{
			tick();
			onPlayerLeft(e.getPlayer());
		}
	}

	@EventHandler(priority = EventPriority.MONITOR)
	public void on(PlayerJoinEvent e)
	{
		if(e.getPlayer().getWorld().equals(world))
		{
			tick();
			onPlayerJoin(e.getPlayer());
		}
	}

	@EventHandler(priority = EventPriority.MONITOR)
	public void on(ChunkLoadEvent e)
	{
		if(e.getWorld().equals(world))
		{
			tick();
			onChunkLoaded(e.getChunk());
		}
	}

	@EventHandler(priority = EventPriority.MONITOR)
	public void on(ChunkUnloadEvent e)
	{
		if(e.getWorld().equals(world))
		{
			tick();
			onChunkUnloaded(e.getChunk());
		}
	}

	@EventHandler(priority = EventPriority.MONITOR)
	public void on(WorldUnloadEvent e)
	{
		if(world != null && e.getWorld().equals(world))
		{
			close();
		}
	}

	public void close()
	{
		getNoLoot().clear();
		getNoLoot().trimToSize();
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
		setHotloadable(false);
		if(!isDev())
		{
			setPregenDone(true);
			setFastPregen(false);
		}

		if(failing)
		{
			return generateChunkDataFailure(world, no, x, z, biomeGrid);
		}

		try
		{
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

			generated++;
			long hits = CNG.hits;
			CNG.hits = 0;
			Iris.instance.hit(hits);
			setHotloadable(true);
			return c;
		}

		catch(Throwable e)
		{
			fail(e);
		}

		setHotloadable(true);
		return generateChunkDataFailure(world, no, x, z, biomeGrid);
	}

	public void checkHotload()
	{
		if(M.ms() - getHlast() < 1000)
		{
			return;
		}

		if(getWorld() != null)
		{
			checkHotload(getWorld());
		}
	}

	private void checkHotload(World world)
	{
		if(!isHotloadable())
		{
			return;
		}

		if(getPushLatch().flip())
		{
			if(getWorld() == null)
			{
				setWorld(world);
			}

			Iris.hotloader.check((IrisContext) this);

			if(this instanceof IrisContext)
			{
				IrisContext.pushContext((IrisContext) this);
			}
		}
	}

	public void onHotload()
	{
		setHlast(M.ms());
		getDimCache().reset();
	}

	protected void fail(Throwable e)
	{
		if(isFailing())
		{
			return;
		}

		setFailing(true);

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
				Iris.instance.imsg(i, C.DARK_RED + "Iris Generator has crashed!");
				Iris.instance.imsg(i, C.RED + "- Check the console for the error.");
				Iris.instance.imsg(i, C.RED + "- To Regen, use /iris std open <dim>");
				Iris.instance.imsg(i, C.RED + "- To Retry the chunk, use /iris world retry");
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
