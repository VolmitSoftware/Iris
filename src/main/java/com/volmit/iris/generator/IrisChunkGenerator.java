package com.volmit.iris.generator;

import java.io.IOException;
import java.lang.reflect.Method;
import java.util.concurrent.locks.ReentrantLock;

import org.bukkit.Chunk;
import org.bukkit.World;
import org.bukkit.entity.Player;

import com.volmit.iris.Iris;
import com.volmit.iris.IrisContext;
import com.volmit.iris.object.IrisBiome;
import com.volmit.iris.object.IrisRegion;
import com.volmit.iris.object.atomics.AtomicRegionData;
import com.volmit.iris.util.BiomeResult;
import com.volmit.iris.util.CNG;
import com.volmit.iris.util.KMap;
import com.volmit.iris.util.RNG;

import lombok.Data;
import lombok.EqualsAndHashCode;

@Data
@EqualsAndHashCode(callSuper = false)
public class IrisChunkGenerator extends CeilingChunkGenerator implements IrisContext
{
	private Method initLighting;
	private ReentrantLock lock;
	private KMap<Player, IrisBiome> b = new KMap<>();

	public IrisChunkGenerator(String dimensionName, int threads)
	{
		super(dimensionName, threads);
		lock = new ReentrantLock();
	}

	@Override
	protected void onGenerate(RNG random, int x, int z, ChunkData data, BiomeGrid grid)
	{
		lock.lock();
		super.onGenerate(random, x, z, data, grid);
		lock.unlock();
	}

	public void onInit(World world, RNG rng)
	{
		try
		{
			super.onInit(world, rng);
		}

		catch(Throwable e)
		{
			fail(e);
		}
	}

	@Override
	public BiomeResult getBiome(int x, int z)
	{
		return sampleBiome(x, z);
	}

	@Override
	public IrisRegion getRegion(int x, int z)
	{
		return sampleRegion(x, z);
	}

	@Override
	public int getHeight(int x, int z)
	{
		return sampleHeight(x, z);
	}

	@Override
	protected void onTick(int ticks)
	{
		super.onTick(ticks);
	}

	@Override
	protected void onClose()
	{
		super.onClose();

		try
		{
			parallaxMap.saveAll();
			ceilingParallaxMap.saveAll();
			parallaxMap.getLoadedChunks().clear();
			parallaxMap.getLoadedRegions().clear();
			ceilingParallaxMap.getLoadedChunks().clear();
			ceilingParallaxMap.getLoadedRegions().clear();
		}

		catch(IOException e)
		{
			e.printStackTrace();
		}

		setBiomeCache(null);
		setAvailableFilters(null);
		setBiomeHitCache(null);
		setCacheTrueBiome(null);
		setCacheHeightMap(null);
		setCeilingSliverCache(null);
		setSliverCache(null);
		Iris.info("Closing Iris Dimension " + getWorld().getName());
	}

	@Override
	protected void onFailure(Throwable e)
	{

	}

	@Override
	protected void onChunkLoaded(Chunk c)
	{

	}

	@Override
	protected void onChunkUnloaded(Chunk c)
	{

	}

	@Override
	protected void onPlayerJoin(Player p)
	{

	}

	@Override
	protected void onPlayerLeft(Player p)
	{

	}

	@Override
	public void onHotloaded()
	{
		CNG.creates = 0;
		onHotload();
	}

	public long guessMemoryUsage()
	{
		long bytes = 1024 * 1024 * (8 + (getThreads() / 4));

		for(AtomicRegionData i : parallaxMap.getLoadedRegions().values())
		{
			bytes += i.guessMemoryUsage();
		}

		for(AtomicRegionData i : ceilingParallaxMap.getLoadedRegions().values())
		{
			bytes += i.guessMemoryUsage();
		}

		bytes += parallaxMap.getLoadedChunks().size() * 256 * 4 * 460;
		bytes += ceilingParallaxMap.getLoadedChunks().size() * 256 * 4 * 460;
		bytes += getSliverBuffer() * 220;
		bytes += 752 * Iris.data.getObjectLoader().getTotalStorage();

		return bytes;
	}
}
