package com.volmit.iris.gen;

import java.util.Random;

import org.bukkit.Bukkit;
import org.bukkit.Chunk;
import org.bukkit.GameMode;
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

import com.volmit.iris.Iris;
import com.volmit.iris.gen.atomics.AtomicCache;
import com.volmit.iris.gen.atomics.AtomicMulticache;
import com.volmit.iris.gen.scaffold.IrisContext;
import com.volmit.iris.gen.scaffold.IrisMetrics;
import com.volmit.iris.gen.scaffold.Provisioned;
import com.volmit.iris.gen.scaffold.TerrainChunk;
import com.volmit.iris.gen.scaffold.TerrainProvider;
import com.volmit.iris.gen.scaffold.TerrainTarget;
import com.volmit.iris.manager.IrisDataManager;
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
import com.volmit.iris.util.KSet;
import com.volmit.iris.util.M;
import com.volmit.iris.util.RNG;

import lombok.Data;
import lombok.EqualsAndHashCode;

@Data
@EqualsAndHashCode(callSuper = false)
public abstract class ContextualTerrainProvider implements TerrainProvider, Listener
{
	private Provisioned provisioner;
	private KList<BlockPosition> noLoot;
	private BlockPosition allowLoot;
	private AtomicMulticache cache;
	private IrisDataManager data;
	private boolean failing;
	private int task;
	private boolean dev;
	private volatile boolean initialized;
	private RNG masterRandom;
	private ChronoLatch perSecond;
	private ChronoLatch tickLatch;
	private ChronoLatch pushLatch;
	private AtomicCache<IrisDimension> dimCache;
	private IrisMetrics metrics;
	private int generated;
	private int ticks;
	private long hlast;
	private boolean fastPregen = false;
	private boolean pregenDone;
	private volatile boolean hotloadable = false;
	private final TerrainTarget target;
	private KSet<String> warnings;

	public ContextualTerrainProvider(TerrainTarget target)
	{
		metrics = new IrisMetrics(256);
		warnings = new KSet<>();
		this.target = target;
		pushLatch = new ChronoLatch(3000);
		tickLatch = new ChronoLatch(650);
		perSecond = new ChronoLatch(1000);
		hlast = M.ms();
		cache = new AtomicMulticache((IrisTerrainProvider) this);
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

	public void warn(String warning)
	{
		if(!isDev())
		{
			return;
		}

		warnings.add(warning);
	}

	protected abstract void onGenerate(RNG masterRandom, int x, int z, TerrainChunk chunk);

	protected abstract void onInit(RNG masterRandom);

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

	private void init(RNG rng)
	{
		if(initialized)
		{
			return;
		}

		setInitialized(true);
		setData(new IrisDataManager(getTarget().getFolder()));
		setMasterRandom(new RNG(getTarget().getSeed()));
		setMetrics(new IrisMetrics(128));
		setTask(Bukkit.getScheduler().scheduleSyncRepeatingTask(Iris.instance, this::tick, 0, 0));
		Bukkit.getServer().getPluginManager().registerEvents(this, Iris.instance);
		onInit(masterRandom);
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

				doCheckHotload();

				if(getNoLoot().size() > 1024)
				{
					// noinspection ListRemoveInLoop
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
		if(!getTarget().isWorld(e.getBlock().getWorld()))
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
		if(!getTarget().isWorld(e.getBlock().getWorld()))
		{
			return;
		}

		getNoLoot().addIfMissing(new BlockPosition(e.getBlock().getX(), e.getBlock().getY(), e.getBlock().getZ()));
	}

	@EventHandler(priority = EventPriority.MONITOR)
	public void on(BlockDropItemEvent e)
	{
		if(!getTarget().isWorld(e.getBlock().getWorld()))
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
		if(getTarget().isWorld(e.getFrom().getWorld()) && !getTarget().isWorld(e.getTo().getWorld()))
		{
			tick();
			onPlayerLeft(e.getPlayer());
		}

		if(!getTarget().isWorld(e.getFrom().getWorld()) && getTarget().isWorld(e.getTo().getWorld()))
		{
			tick();
			onPlayerJoin(e.getPlayer());
		}
	}

	@EventHandler(priority = EventPriority.MONITOR)
	public void on(PlayerQuitEvent e)
	{
		if(getTarget().isWorld(e.getPlayer().getWorld()))
		{
			tick();
			onPlayerLeft(e.getPlayer());
		}
	}

	@EventHandler(priority = EventPriority.MONITOR)
	public void on(PlayerJoinEvent e)
	{
		if(getTarget().isWorld(e.getPlayer().getWorld()))
		{
			tick();
			onPlayerJoin(e.getPlayer());
		}
	}

	@EventHandler(priority = EventPriority.MONITOR)
	public void on(ChunkLoadEvent e)
	{
		if(getTarget().isWorld(e.getWorld()))
		{
			tick();
			onChunkLoaded(e.getChunk());
		}
	}

	@EventHandler(priority = EventPriority.MONITOR)
	public void on(ChunkUnloadEvent e)
	{
		if(getTarget().isWorld(e.getWorld()))
		{
			tick();
			onChunkUnloaded(e.getChunk());
		}
	}

	@EventHandler(priority = EventPriority.MONITOR)
	public void on(WorldUnloadEvent e)
	{
		if(getTarget().isWorld(e.getWorld()))
		{
			close();
		}
	}

	public void close()
	{
		if(!isDev())
		{
			return;
		}

		getNoLoot().clear();
		getNoLoot().trimToSize();
		HandlerList.unregisterAll(this);
		Bukkit.getScheduler().cancelTask(getTask());
		onClose();
	}

	protected void generateFailure(TerrainChunk chunk)
	{
		for(int i = 0; i < 16; i++)
		{
			for(int j = 0; j < 16; j++)
			{
				int h = 0;

				if(j == i || j + i == 16)
				{
					chunk.setBlock(i, h, j, B.getBlockData("RED_TERRACOTTA").getType());
				}

				else
				{
					chunk.setBlock(i, h, j, B.getBlockData("BLACK_TERRACOTTA").getType());
				}
			}
		}
	}

	@Override
	public void generate(Random no, int x, int z, TerrainChunk terrain)
	{
		setHotloadable(false);
		if(!isDev())
		{
			setPregenDone(true);
			setFastPregen(false);
		}

		if(failing)
		{
			generateFailure(terrain);
			return;
		}

		try
		{
			RNG random = new RNG(getTarget().getSeed());
			init(random.nextParallelRNG(0));
			onGenerate(random, x, z, terrain);
			generated++;
			long hits = CNG.hits;
			CNG.hits = 0;
			Iris.instance.hit(hits);
			setHotloadable(true);
			return;
		}

		catch(Throwable e)
		{
			fail(e);
		}

		setHotloadable(true);
		generateFailure(terrain);
	}

	private void doCheckHotload()
	{
		if(!isHotloadable())
		{
			return;
		}

		if(M.ms() - getHlast() < 1000)
		{
			return;
		}

		if(getPushLatch().flip())
		{
			Iris.hotloader.check((IrisContext) this);
			IrisContext.pushContext((IrisContext) this);
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

			for(Player i : getTarget().getPlayers())
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
	public boolean isParallelCapable()
	{
		return true;
	}
}
