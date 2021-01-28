package com.volmit.iris.scaffold.parallax;

import com.volmit.iris.IrisSettings;
import com.volmit.iris.object.tile.TileData;
import com.volmit.iris.scaffold.hunk.Hunk;
import com.volmit.iris.util.J;
import com.volmit.iris.util.KList;
import com.volmit.iris.util.KMap;
import org.bukkit.block.TileState;
import org.bukkit.block.data.BlockData;

import java.io.File;
import java.io.IOException;

public class ParallaxWorld implements ParallaxAccess
{
	private final KMap<Long, ParallaxRegion> loadedRegions;
	private final KList<Long> save;
	private final File folder;
	private final int height;

	public ParallaxWorld(int height, File folder)
	{
		this.height = height;
		this.folder = folder;
		save = new KList<>();
		loadedRegions = new KMap<>();
		folder.mkdirs();
	}

	public int getRegionCount()
	{
		return loadedRegions.size();
	}

	public int getChunkCount()
	{
		int m = 0;

		try
		{
			for(ParallaxRegion i : loadedRegions.values())
			{
				m+= i.getChunkCount();
			}
		}

		catch(Throwable ignored)
		{

		}

		return m;
	}

	public void close()
	{
		for(ParallaxRegion i : loadedRegions.v())
		{
			unload(i.getX(), i.getZ());
		}

		save.clear();
		loadedRegions.clear();
	}

	public void save(ParallaxRegion region)
	{
		try
		{
			region.save();
		}

		catch(IOException e)
		{
			e.printStackTrace();
		}
	}

	public boolean isLoaded(int x, int z)
	{
		return loadedRegions.containsKey(key(x, z));
	}

	public void save(int x, int z)
	{
		if(isLoaded(x, z))
		{
			save(getR(x, z));
		}
	}

	public int unload(int x, int z)
	{
		long key = key(x, z);
		int v = 0;
		if(isLoaded(x, z))
		{
			if(save.contains(key))
			{
				save(x, z);
				save.remove(key);
			}

			ParallaxRegion lr = loadedRegions.remove(key);

			if(lr != null)
			{
				v += lr.unload();
			}
		}

		return v;
	}

	public ParallaxRegion load(int x, int z)
	{
		if(isLoaded(x, z))
		{
			return loadedRegions.get(key(x, z));
		}

		ParallaxRegion v = new ParallaxRegion(height, folder, x, z);
		loadedRegions.put(key(x, z), v);

		return v;
	}

	public ParallaxRegion getR(int x, int z)
	{
		long key = key(x, z);

		ParallaxRegion region = loadedRegions.get(key);

		if(region == null)
		{
			region = load(x, z);
		}

		return region;
	}

	public ParallaxRegion getRW(int x, int z)
	{
		save.addIfMissing(key(x, z));
		return getR(x, z);
	}

	private long key(int x, int z)
	{
		return (((long) x) << 32) | (((long) z) & 0xffffffffL);
	}

	@Override
	public Hunk<BlockData> getBlocksR(int x, int z)
	{
		return getR(x >> 5, z >> 5).getBlockSlice().getR(x & 31, z & 31);
	}

	@Override
	public Hunk<BlockData> getBlocksRW(int x, int z)
	{
		return getRW(x >> 5, z >> 5).getBlockSlice().getRW(x & 31, z & 31);
	}

	@Override
	public Hunk<TileData<? extends TileState>> getTilesR(int x, int z)
	{
		return getR(x >> 5, z >> 5).getTileSlice().getR(x & 31, z & 31);
	}

	@Override
	public Hunk<TileData<? extends TileState>> getTilesRW(int x, int z)
	{
		return getRW(x >> 5, z >> 5).getTileSlice().getRW(x & 31, z & 31);
	}

	@Override
	public Hunk<String> getObjectsR(int x, int z)
	{
		return getR(x >> 5, z >> 5).getObjectSlice().getR(x & 31, z & 31);
	}

	@Override
	public Hunk<String> getObjectsRW(int x, int z)
	{
		return getRW(x >> 5, z >> 5).getObjectSlice().getRW(x & 31, z & 31);
	}

	@Override
	public Hunk<String> getEntitiesRW(int x, int z) {
		return getRW(x >> 5, z >> 5).getEntitySlice().getRW(x & 31, z & 31);
	}

	@Override
	public Hunk<String> getEntitiesR(int x, int z) {
		return getRW(x >> 5, z >> 5).getEntitySlice().getR(x & 31, z & 31);
	}

	@Override
	public Hunk<Boolean> getUpdatesR(int x, int z)
	{
		return getR(x >> 5, z >> 5).getUpdateSlice().getR(x & 31, z & 31);
	}

	@Override
	public Hunk<Boolean> getUpdatesRW(int x, int z)
	{
		return getRW(x >> 5, z >> 5).getUpdateSlice().getRW(x & 31, z & 31);
	}

	@Override
	public ParallaxChunkMeta getMetaR(int x, int z)
	{
		return getR(x >> 5, z >> 5).getMetaR(x & 31, z & 31);
	}

	@Override
	public ParallaxChunkMeta getMetaRW(int x, int z)
	{
		return getRW(x >> 5, z >> 5).getMetaRW(x & 31, z & 31);
	}

	public void cleanup()
	{
		cleanup(IrisSettings.get().getParallaxRegionEvictionMS(), IrisSettings.get().getParallax().getParallaxChunkEvictionMS());
	}

	@Override
	public void cleanup(long r, long c) {
		J.a(() -> {
			try
			{
				int rr = 0;
				int cc = 0;

				for(ParallaxRegion i : loadedRegions.v())
				{
					if(i.hasBeenIdleLongerThan(r))
					{
						rr++;
						unload(i.getX(), i.getZ());
					}

					else
					{
						cc+= i.cleanup(c);
					}
				}
			}

			catch(Throwable e)
			{
				e.printStackTrace();
			}
		});
	}

	@Override
	public void saveAll() {
		J.a(this::saveAllNOW);
	}

	@Override
	public void saveAllNOW() {
		for(ParallaxRegion i : loadedRegions.v())
		{
			if(save.contains(key(i.getX(), i.getZ())))
			{
				save(i.getX(), i.getZ());
			}
		}
	}
}
