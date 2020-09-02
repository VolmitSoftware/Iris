package com.volmit.iris.gen.atomics;

import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.Supplier;

import com.volmit.iris.IrisSettings;
import com.volmit.iris.object.IrisRegion;
import com.volmit.iris.util.BiomeResult;
import com.volmit.iris.util.KMap;

public class AtomicMulticache
{
	public static boolean broken = false;
	private final AtomicInteger x;
	private final AtomicInteger z;
	private final KMap<Long, Double> height;
	private final KMap<Long, BiomeResult> biome;
	private final KMap<Long, BiomeResult> rawBiome;
	private final KMap<Long, IrisRegion> region;

	public AtomicMulticache()
	{
		x = new AtomicInteger(0);
		z = new AtomicInteger(0);
		height = new KMap<Long, Double>();
		biome = new KMap<Long, BiomeResult>();
		rawBiome = new KMap<Long, BiomeResult>();
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

		if(!IrisSettings.get().sharedCaching || getSize() > 42000)
		{
			drop();
		}
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
			r = g.get();
			height.put(pos, r);
		}

		return r;
	}

	public IrisRegion getRegion(int x, int z, Supplier<IrisRegion> g)
	{
		long pos = pos(x, z);
		IrisRegion r = region.get(pos);

		if(r == null)
		{
			r = g.get();
			region.put(pos, r);
		}

		return r;
	}

	public BiomeResult getBiome(int x, int z, Supplier<BiomeResult> g)
	{
		long pos = pos(x, z);
		BiomeResult r = biome.get(pos);

		if(r == null)
		{
			r = g.get();
			biome.put(pos, r);
		}

		return r;
	}

	public BiomeResult getRawBiome(int x, int z, Supplier<BiomeResult> g)
	{
		if(broken)
		{
			return null;
		}
		long pos = pos(x, z);
		BiomeResult r = rawBiome.get(pos);

		if(r == null)
		{
			r = g.get();
			rawBiome.put(pos, r);
		}

		return r;
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

		height.clear();
		region.clear();
		biome.clear();
		rawBiome.clear();
	}
}
