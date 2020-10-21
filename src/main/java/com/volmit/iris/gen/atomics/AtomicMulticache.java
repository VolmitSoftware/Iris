package com.volmit.iris.gen.atomics;

import com.github.benmanes.caffeine.cache.Caffeine;
import com.github.benmanes.caffeine.cache.LoadingCache;
import com.volmit.iris.IrisSettings;
import com.volmit.iris.gen.SkyTerrainProvider;
import com.volmit.iris.object.IrisBiome;
import com.volmit.iris.object.IrisRegion;
import com.volmit.iris.util.ChunkPosition;

public class AtomicMulticache
{
	public static boolean broken = false;
	private final LoadingCache<ChunkPosition, Double> height;
	private final LoadingCache<ChunkPosition, Integer> carvedHeight;
	private final LoadingCache<ChunkPosition, Integer> carvedHeightIgnoreWater;
	private final LoadingCache<ChunkPosition, IrisBiome> biome;
	private final LoadingCache<ChunkPosition, IrisBiome> rawBiome;
	private final LoadingCache<ChunkPosition, IrisRegion> region;

	public AtomicMulticache(SkyTerrainProvider gen)
	{
		height = Caffeine.newBuilder().maximumSize(getLimit()).build((c) -> gen.getNoiseHeight(c.getX(), c.getZ()) + gen.getFluidHeight());
		carvedHeight = Caffeine.newBuilder().maximumSize(getLimit()).build((c) ->
		{
			int h = (int) Math.round(gen.getTerrainWaterHeight(c.getX(), c.getZ()));
			h = gen.getGlCarve().getSurfaceCarve(c.getX(), h, c.getZ());
			return h;
		});
		carvedHeightIgnoreWater = Caffeine.newBuilder().maximumSize(getLimit()).build((c) ->
		{
			int h = (int) Math.round(gen.getTerrainHeight(c.getX(), c.getZ()));
			h = gen.getGlCarve().getSurfaceCarve(c.getX(), h, c.getZ());
			return h;
		});
		biome = Caffeine.newBuilder().maximumSize(getLimit()).build((c) -> gen.sampleTrueBiomeBase(c.getX(), c.getZ()));
		rawBiome = Caffeine.newBuilder().maximumSize(getLimit()).build((c) -> gen.computeRawBiome(c.getX(), c.getZ()));
		region = Caffeine.newBuilder().maximumSize(getLimit()).build((c) ->
		{
			double wx = gen.getModifiedX(c.getX(), c.getZ());
			double wz = gen.getModifiedZ(c.getX(), c.getZ());
			return gen.getGlBiome().getRegion(wx, wz);
		});
	}

	private int getLimit()
	{
		return IrisSettings.get().getAtomicCacheSize();
	}

	public double getHeight(int x, int z)
	{
		return height.get(new ChunkPosition(x, z));
	}

	public int getCarvedHeight(int x, int z)
	{
		return carvedHeight.get(new ChunkPosition(x, z));
	}

	public int getCarvedHeightIgnoreWater(int x, int z)
	{
		return carvedHeightIgnoreWater.get(new ChunkPosition(x, z));
	}

	public IrisRegion getRegion(int x, int z)
	{
		return region.get(new ChunkPosition(x, z));
	}

	public IrisBiome getBiome(int x, int z)
	{
		return biome.get(new ChunkPosition(x, z));
	}

	public IrisBiome getRawBiome(int x, int z)
	{
		return rawBiome.get(new ChunkPosition(x, z));
	}

	public void updateHeight(int x, int z, int h)
	{
		if(broken)
		{
			return;
		}
		height.put(new ChunkPosition(x, z), (double) h);
	}

	public double getSize()
	{
		return height.estimatedSize() + region.estimatedSize() + biome.estimatedSize() + rawBiome.estimatedSize() + carvedHeight.estimatedSize() + carvedHeightIgnoreWater.estimatedSize();
	}

	public void drop()
	{
		if(broken)
		{
			return;
		}

		height.invalidateAll();
		region.invalidateAll();
		biome.invalidateAll();
		rawBiome.invalidateAll();
		carvedHeight.invalidateAll();
		carvedHeightIgnoreWater.invalidateAll();
	}
}
