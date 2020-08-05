package com.volmit.iris.util;

import org.bukkit.Bukkit;
import org.bukkit.Chunk;
import org.bukkit.World;

import com.volmit.iris.Iris;

public class PregenJob
{
	private World world;
	private int size;
	private int mcaX;
	private int mcaZ;
	private int rcx;
	private int rcz;
	private int total;
	private int genned;
	private boolean completed;
	public static int task = -1;
	private PrecisionStopwatch s;
	private ChronoLatch cl;

	public PregenJob(World world, int size)
	{
		this.s = PrecisionStopwatch.start();
		this.world = world;
		this.size = size;
		world.getWorldBorder().setCenter(0, 0);
		world.getWorldBorder().setWarningDistance(64);
		world.getWorldBorder().setSize(size);
		mcaX = mca(min());
		mcaZ = mca(min());
		rcx = 0;
		cl = new ChronoLatch(3000);
		rcz = 0;
		total = (size / 16) * (size / 16);
		genned = 0;
		completed = false;

		if(task != -1)
		{
			stop();
		}

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

		while(p.getMilliseconds() < 1500)
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
		Iris.info("Pregen: Generating: " + Form.pc(Math.min((double) genned / (double) total, 1.0), 0) + ", Elapsed: " + Form.duration((long) s.getMilliseconds()) + ", ETA: " + (genned >= total - 5 ? "Any second..." : s.getMilliseconds() < 25000 ? "Calculating..." : Form.duration(eta)) + " MS: " + Form.duration((s.getMilliseconds() / (double) genned), 2));
	}

	public void tick()
	{
		gen();
		nextPosition();
	}

	public void nextPosition()
	{
		rcx++;

		if(rcx > 31)
		{
			rcx = 0;
			rcz++;

			if(rcz > 31)
			{
				rcz = 0;
				mcaX++;

				if(mcaX > mca(max() / 16))
				{
					mcaX = mca(min() / 16);
					mcaZ++;

					if(mcaZ > mca(max() / 16))
					{
						mcaZ = mca(min() / 16);
						completed = true;
						stop();
						Iris.info("Pregen Completed!");

						for(Chunk i : world.getLoadedChunks())
						{
							i.unload(true);
						}

						world.save();
						return;
					}

					world.save();
				}
			}
		}
	}

	public void gen()
	{
		try
		{
			if(isChunkWithin(rcx + minMCA(mcaX), rcz + minMCA(mcaZ)))
			{
				Chunk c = world.getChunkAt(rcx + minMCA(mcaX), rcz + minMCA(mcaZ));
				c.load(true);
				world.unloadChunkRequest(rcx + minMCA(mcaX), rcz + minMCA(mcaZ));
				genned++;
			}
		}

		catch(Throwable e)
		{
			Iris.warn("Pregen Crash!");
			e.printStackTrace();
			stop();
		}
	}

	public int minMCA(int v)
	{
		return v << 5;
	}

	public int maxMCA(int v)
	{
		return (v << 5) + 31;
	}

	public int mca(int v)
	{
		return v >> 5;
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
		return Math.abs(z * 16) <= size / 2 && Math.abs(z * 16) <= size / 2;
	}
}
