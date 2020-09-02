package com.volmit.iris.gen.atomics;

import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.Supplier;

import com.volmit.iris.IrisSettings;
import com.volmit.iris.object.IrisBiome;
import com.volmit.iris.object.IrisRegion;
import com.volmit.iris.util.KMap;

public class AtomicMulticache
{
	public static boolean broken = false;
	private final AtomicInteger x;
	private final AtomicInteger z;
	private int hit = 0;
	private int miss = 0;
	private final KMap<Long, Double> height;
	private final KMap<Long, IrisBiome> biome;
	private final KMap<Long, IrisBiome> rawBiome;
	private final KMap<Long, IrisRegion> region;

	public AtomicMulticache()
	{
		x = new AtomicInteger(0);
		z = new AtomicInteger(0);
		height = new KMap<Long, Double>();
		biome = new KMap<Long, IrisBiome>();
		rawBiome = new KMap<Long, IrisBiome>();
		region = new KMap<Long, IrisRegion>();
	}

	public void targetChunk(int x, int z)
	{
		if(broken)
		{
			return;
		}

		this.x.set(x);
		this.z.set(z);

		if(!IrisSettings.get().sharedCaching)
		{
			drop();
		}

		else
		{
			if(height.size() > getLimit())
			{
				height.clear();
			}

			if(biome.size() > getLimit())
			{
				biome.clear();
			}

			if(rawBiome.size() > getLimit())
			{
				rawBiome.clear();
			}

			if(region.size() > getLimit())
			{
				region.clear();
			}
		}
	}

	private int getLimit()
	{
		return 20000;
	}

	public double getHeight(int x, int z, Supplier<Double> g)
	{
		if(broken)
		{
			return -5784;
		}

		long pos = pos(x, z);
		Double r = height.get(pos);

		if(r == null)
		{
			miss++;
			r = g.get();
			height.put(pos, r);
		}

		else
		{
			hit++;
		}

		return r;
	}

	public IrisRegion getRegion(int x, int z, Supplier<IrisRegion> g)
	{
		long pos = pos(x, z);
		IrisRegion r = region.get(pos);

		if(r == null)
		{
			miss++;
			r = g.get();
			region.put(pos, r);
		}

		else
		{
			hit++;
		}

		return r;
	}

	public IrisBiome getBiome(int x, int z, Supplier<IrisBiome> g)
	{
		long pos = pos(x, z);
		IrisBiome r = biome.get(pos);

		if(r == null)
		{
			miss++;
			r = g.get();
			biome.put(pos, r);
		}

		else
		{
			hit++;
		}

		return r;
	}

	public IrisBiome getRawBiome(int x, int z, Supplier<IrisBiome> g)
	{
		if(broken)
		{
			return null;
		}
		long pos = pos(x, z);
		IrisBiome r = rawBiome.get(pos);

		if(r == null)
		{
			miss++;
			r = g.get();
			rawBiome.put(pos, r);
		}

		else
		{
			hit++;
		}

		return r;
	}

	public double getCacheHitRate()
	{
		return (double) hit / (double) (hit + miss);
	}

	private long pos(int x, int z)
	{
		if(broken)
		{
			return 1;
		}
		return (((long) x) << 32) | (z & 0xffffffffL);
	}

	public void updateHeight(int x, int z, int h)
	{
		if(broken)
		{
			return;
		}
		height.put(pos(x, z), (double) h);
	}

	public double getSize()
	{
		return height.size() + region.size() + biome.size() + rawBiome.size();
	}

	public void drop()
	{
		if(broken)
		{
			return;
		}

		hit = 0;
		miss = 0;
		height.clear();
		region.clear();
		biome.clear();
		rawBiome.clear();
	}
}
