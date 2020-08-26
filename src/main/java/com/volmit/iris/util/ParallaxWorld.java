package com.volmit.iris.util;

import java.io.File;
import java.io.IOException;

import org.bukkit.block.data.BlockData;

import com.volmit.iris.Iris;

public class ParallaxWorld
{
	private final KMap<Long, ParallaxRegion> loadedRegions;
	private final File dataFolder;

	public ParallaxWorld(File dataFolder)
	{
		loadedRegions = new KMap<>();
		this.dataFolder = dataFolder;
	}

	public void unloadAll()
	{
		for(long i : loadedRegions.k())
		{
			try
			{
				unload(i);
			}

			catch(IOException e)
			{
				Iris.error("Failed to save region " + i);
				e.printStackTrace();
			}
		}
	}

	public void clean(long time)
	{
		for(long i : loadedRegions.k())
		{
			ParallaxRegion r = loadedRegions.get(i);

			if(r.isOlderThan(time))
			{
				try
				{
					unload(i);
				}

				catch(IOException e)
				{
					Iris.error("Failed to save region " + i);
					e.printStackTrace();
				}

				break;
			}
		}
	}

	private void unload(long i) throws IOException
	{
		ParallaxRegion r = loadedRegions.get(i);
		r.write(new File(dataFolder, i + ".plx"));
		loadedRegions.remove(i);
	}

	public BlockData getBlock(int x, int y, int z)
	{
		if(y > 255 || y < 0)
		{
			throw new IllegalArgumentException(y + " exceeds 0-255");
		}

		return getRegion(x >> 5, z >> 5).get(x & 511, y, z & 511);
	}

	public void setBlock(int x, int y, int z, BlockData d)
	{
		if(d == null)
		{
			throw new IllegalArgumentException("Block data cannot be null");
		}

		if(y > 255 || y < 0)
		{
			throw new IllegalArgumentException(y + " exceeds 0-255");
		}

		getRegion(x >> 5, z >> 5).set(x & 511, y, z & 511, d);
	}

	public ParallaxRegion getRegion(int x, int z)
	{
		Long vb = (((long) x) << 32) | (z & 0xffffffffL);
		File ff = new File(dataFolder, vb + ".plx");

		return loadedRegions.compute(vb, (k, v) ->
		{
			if(k == null || v == null)
			{
				try
				{
					return new ParallaxRegion(ff);
				}

				catch(IOException e)
				{
					Iris.error("Failed to load parallax file: " + ff.getAbsolutePath() + " Assuming empty region!");
					ff.deleteOnExit();
					ff.delete();
					return new ParallaxRegion();
				}
			}

			return v;
		});
	}
}
