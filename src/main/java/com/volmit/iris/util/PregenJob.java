package com.volmit.iris.util;

import java.awt.Color;
import java.util.concurrent.atomic.AtomicInteger;

import org.bukkit.Bukkit;
import org.bukkit.Chunk;
import org.bukkit.World;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.world.ChunkUnloadEvent;

import com.volmit.iris.Iris;
import com.volmit.iris.IrisSettings;
import com.volmit.iris.gen.IrisTerrainProvider;
import com.volmit.iris.gen.provisions.ProvisionBukkit;
import com.volmit.iris.gui.PregenGui;

public class PregenJob implements Listener
{
	private World world;
	private int size;
	private int total;
	private int genned;
	private boolean completed;
	public static int task = -1;
	private AtomicInteger g = new AtomicInteger();
	private PrecisionStopwatch s;
	private ChronoLatch cl;
	private ChronoLatch clx;
	private ChronoLatch clf;
	private MortarSender sender;
	private int mcaWidth;
	private int mcaX;
	private int mcaZ;
	private int chunkX;
	private int chunkZ;
	private Runnable onDone;
	private Spiraler spiraler;
	private Spiraler chunkSpiraler;
	private boolean first;
	private Consumer2<ChunkPosition, Color> consumer;
	private IrisTerrainProvider tp;
	private int cubeSize = IrisSettings.get().getPregenTileSize();
	int xc = 0;

	public PregenJob(World world, int size, MortarSender sender, Runnable onDone)
	{
		g.set(0);
		this.s = PrecisionStopwatch.start();
		Iris.instance.registerListener(this);
		this.world = world;
		this.size = size;
		this.onDone = onDone;
		world.getWorldBorder().setCenter(0, 0);
		world.getWorldBorder().setWarningDistance(64);
		world.getWorldBorder().setSize(size);
		this.sender = sender;
		cl = new ChronoLatch(3000);
		clx = new ChronoLatch(20000);
		clf = new ChronoLatch(30000);
		total = (size / 16) * (size / 16);
		genned = 0;
		mcaWidth = Math.floorDiv(size >> 4, cubeSize) + cubeSize;
		this.mcaX = 0;
		this.mcaZ = 0;
		this.chunkX = 0;
		this.chunkZ = 0;
		completed = false;
		first = true;
		tp = (world.getGenerator() instanceof ProvisionBukkit) ? (IrisTerrainProvider) ((ProvisionBukkit) world.getGenerator()).getProvider() : null;

		chunkSpiraler = new Spiraler(cubeSize, cubeSize, (x, z) ->
		{
			chunkX = (mcaX * cubeSize) + x;
			chunkZ = (mcaZ * cubeSize) + z;
		});

		spiraler = new Spiraler(mcaWidth, mcaWidth, (x, z) ->
		{
			mcaX = x;
			mcaZ = z;
			chunkSpiraler.retarget(cubeSize, cubeSize);
		});

		chunkSpiraler.setOffset(Math.floorDiv(cubeSize, 2), Math.floorDiv(cubeSize, 2));

		if(task != -1)
		{
			stop();
		}
		PregenGui.launch(this);
		task = Bukkit.getScheduler().scheduleSyncRepeatingTask(Iris.instance, this::onTick, 0, 0);
	}

	public static void stop()
	{
		try
		{
			Bukkit.getScheduler().cancelTask(task);
		}

		catch(Throwable e)
		{

		}
		task = -1;
	}

	public void onTick()
	{
		if(completed)
		{
			return;
		}

		PrecisionStopwatch p = PrecisionStopwatch.start();

		while(p.getMilliseconds() < 7000)
		{
			tick();
		}

		if(cl.flip())
		{
			tickMetrics();
		}
	}

	private void tickMetrics()
	{
		long eta = (long) ((total - genned) * (s.getMilliseconds() / (double) genned));
		String ss = "Pregen: " + Form.pc(Math.min((double) genned / (double) total, 1.0), 0) + ", Elapsed: " + Form.duration((long) s.getMilliseconds()) + ", ETA: " + (genned >= total - 5 ? "Any second..." : s.getMilliseconds() < 25000 ? "Calculating..." : Form.duration(eta)) + " MS: " + Form.duration((s.getMilliseconds() / (double) genned), 2);
		Iris.info(ss);
		if(sender.isPlayer() && sender.player().isOnline())
		{
			sender.sendMessage(ss);
		}
	}

	public void tick()
	{
		if(completed)
		{
			return;
		}

		if(first)
		{
			sender.sendMessage("Pregen Started for " + Form.f((size >> 4 >> 5 * size >> 4 >> 5)) + " Regions containing " + Form.f((size >> 4) * (size >> 4)) + " Chunks");
			first = false;
			spiraler.next();

			while(chunkSpiraler.hasNext())
			{
				chunkSpiraler.next();

				if(isChunkWithin(chunkX, chunkZ))
				{
					if(consumer != null)
					{
						consumer.accept(new ChunkPosition(chunkX, chunkZ), Color.DARK_GRAY);
					}
				}
			}

			chunkSpiraler.retarget(cubeSize, cubeSize);
		}

		if(chunkSpiraler.hasNext())
		{
			chunkSpiraler.next();
			tickChunk();
		}

		else if(spiraler.hasNext())
		{
			saveAllRequest();
			spiraler.next();

			while(chunkSpiraler.hasNext())
			{
				chunkSpiraler.next();

				if(isChunkWithin(chunkX, chunkZ))
				{
					consumer.accept(new ChunkPosition(chunkX, chunkZ), Color.BLUE.darker().darker());
				}
			}
			chunkSpiraler.retarget(cubeSize, cubeSize);
		}

		else
		{
			for(Chunk i : world.getLoadedChunks())
			{
				i.unload(true);
			}

			saveAll();
			Iris.instance.unregisterListener(this);
			completed = true;
			sender.sendMessage("Pregen Completed!");
			onDone.run();
		}
	}

	private void tickChunk()
	{
		tickSyncChunk();
	}

	private void tickSyncChunk()
	{
		if(isChunkWithin(chunkX, chunkZ))
		{
			if(consumer != null)
			{
				consumer.accept(new ChunkPosition(chunkX, chunkZ), Color.blue.darker().darker());
			}

			world.loadChunk(chunkX, chunkZ);
			genned++;

			if(consumer != null)
			{
				consumer.accept(new ChunkPosition(chunkX, chunkZ), tp != null ? tp.render(chunkX * 16, chunkZ * 16) : Color.blue);
			}
		}

		else
		{
			if(consumer != null)
			{
				consumer.accept(new ChunkPosition(chunkX, chunkZ), Color.blue.brighter().brighter());
			}
		}
	}

	public void saveAllRequest()
	{
		if(clf.flip())
		{
			for(Chunk i : world.getLoadedChunks())
			{
				world.unloadChunkRequest(i.getX(), i.getZ());
			}
		}

		if(clx.flip())
		{
			saveAll();
		}
	}

	@EventHandler
	public void on(ChunkUnloadEvent e)
	{
		if(e.getWorld().equals(world) && isChunkWithin(e.getChunk().getX(), e.getChunk().getZ()) && consumer != null)
		{
			consumer.accept(new ChunkPosition(e.getChunk().getX(), e.getChunk().getZ()), tp != null ? tp.render(e.getChunk().getX() * 16, e.getChunk().getZ() * 16) : Color.blue.darker());
		}
	}

	public void saveAll()
	{
		for(Chunk i : world.getLoadedChunks())
		{
			world.unloadChunkRequest(i.getX(), i.getZ());
		}

		world.save();
		Bukkit.dispatchCommand(Bukkit.getConsoleSender(), "save-all");
	}

	public int max()
	{
		return size / 2;
	}

	public int min()
	{
		return -max();
	}

	public boolean isChunkWithin(int x, int z)
	{
		return !(Math.abs(x << 4) > Math.floorDiv(size, 2) + 16 || Math.abs(z << 4) > Math.floorDiv(size, 2) + 16);
	}

	public void subscribe(Consumer2<ChunkPosition, Color> s)
	{
		consumer = s;
	}

	public String[] getProgress()
	{
		long eta = (long) ((total - genned) * (s.getMilliseconds() / (double) genned));

		return new String[] {"Progress:  " + Form.pc(Math.min((double) genned / (double) total, 1.0), 0), "Generated: " + Form.f(genned) + " Chunks", "Remaining: " + Form.f(total - genned) + " Chunks", "Elapsed:   " + Form.duration((long) s.getMilliseconds(), 2), "Estimate:  " + ((genned >= total - 5 ? "Any second..." : s.getMilliseconds() < 25000 ? "Calculating..." : Form.duration(eta, 2))), "ChunksMS:  " + Form.duration((s.getMilliseconds() / (double) genned), 2), "Chunks/s:  " + Form.f(1000D / (s.getMilliseconds() / genned), 1),
		};
	}
}
