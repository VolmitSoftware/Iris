package com.volmit.iris.gen.atomics;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;

import org.bukkit.World;

import com.volmit.iris.Iris;
import com.volmit.iris.util.ChunkPosition;
import com.volmit.iris.util.KList;
import com.volmit.iris.util.KMap;
import com.volmit.iris.util.M;

public class AtomicWorldData
{
	private World world;
	private KMap<ChunkPosition, AtomicSliverMap> loadedChunks;
	private KMap<ChunkPosition, AtomicRegionData> loadedSections;
	private KMap<ChunkPosition, Long> lastRegion;
	private KMap<ChunkPosition, Long> lastChunk;
	private KList<ChunkPosition> unloadRegions;
	private KList<ChunkPosition> unloadChunks;
	private long last = M.ms();

	public AtomicWorldData(World world)
	{
		this.world = world;
		loadedSections = new KMap<>();
		loadedChunks = new KMap<>();
		lastRegion = new KMap<>();
		lastChunk = new KMap<>();
		unloadRegions = new KList<>();
		unloadChunks = new KList<>();
		getSubregionFolder().mkdirs();
	}

	public KMap<ChunkPosition, AtomicRegionData> getLoadedRegions()
	{
		return loadedSections;
	}

	public AtomicRegionData getSubregion(int x, int z) throws IOException
	{
		lastRegion.put(new ChunkPosition(x, z), M.ms());

		if(!isSectionLoaded(x, z))
		{
			loadedSections.put(new ChunkPosition(x, z), loadSection(x, z));
		}

		AtomicRegionData f = loadedSections.get(new ChunkPosition(x, z));

		return f;
	}

	public void saveAll() throws IOException
	{
		saveChunks();

		for(ChunkPosition i : loadedSections.keySet())
		{
			saveSection(i);
		}
	}

	public void unloadAll(boolean save) throws IOException
	{
		saveChunks();

		for(ChunkPosition i : loadedSections.keySet())
		{
			unloadSection(i, save);
		}

		loadedSections.clear();
		loadedChunks.clear();
		lastRegion.clear();
	}

	public void deleteSection(int x, int z) throws IOException
	{
		unloadSection(x, z, false);
		getSubregionFile(x, z).delete();
	}

	public boolean isSectionLoaded(int x, int z)
	{
		return isSectionLoaded(new ChunkPosition(x, z));
	}

	public boolean isSectionLoaded(ChunkPosition s)
	{
		return loadedSections.containsKey(s);
	}

	public boolean unloadSection(int x, int z, boolean save) throws IOException
	{
		return unloadSection(new ChunkPosition(x, z), save);
	}

	public boolean unloadSection(ChunkPosition s, boolean save) throws IOException
	{
		if(!isSectionLoaded(s))
		{
			Iris.warn("Cant unload because section isnt loaded?");
			return false;
		}

		if(save)
		{
			saveSection(s);
		}

		loadedSections.remove(s);
		lastRegion.remove(s);
		return true;
	}

	public boolean saveSection(int x, int z) throws IOException
	{
		return saveSection(new ChunkPosition(x, z));
	}

	public boolean saveSection(ChunkPosition s) throws IOException
	{
		if(!isSectionLoaded(s.getX(), s.getZ()))
		{
			Iris.warn("Cant save section " + s.getX() + " " + s.getZ() + " because section isnt loaded?");

			return false;
		}

		saveChunks(s);
		AtomicRegionData data = loadedSections.get(s);
		FileOutputStream fos = new FileOutputStream(getSubregionFile(s.getX(), s.getZ()));
		data.write(fos);
		fos.close();
		return true;
	}

	public void saveChunks() throws IOException
	{
		for(ChunkPosition i : loadedChunks.k())
		{
			saveChunk(i);
		}
	}

	public void saveChunks(ChunkPosition reg) throws IOException
	{
		for(ChunkPosition i : loadedChunks.k())
		{
			int x = i.getX();
			int z = i.getZ();

			if(x >> 5 == reg.getX() && z >> 5 == reg.getZ())
			{
				saveChunk(i);
			}
		}
	}

	public void saveChunk(ChunkPosition i) throws IOException
	{
		AtomicSliverMap m = loadedChunks.get(i);

		if(m.isModified())
		{
			int x = i.getX();
			int z = i.getZ();
			AtomicRegionData dat = loadSection(x >> 5, z >> 5, true);
			dat.set(x & 31, z & 31, m);
		}

		loadedChunks.remove(i);
		lastChunk.remove(i);
	}

	public AtomicSliverMap loadChunk(int x, int z) throws IOException
	{
		ChunkPosition pos = new ChunkPosition(x, z);
		lastChunk.put(pos, M.ms());
		if(loadedChunks.containsKey(pos))
		{
			return loadedChunks.get(pos);
		}

		AtomicRegionData dat = loadSection(x >> 5, z >> 5);
		AtomicSliverMap m = dat.get(x & 31, z & 31);
		loadedChunks.put(pos, m);

		return m;
	}

	public boolean hasChunk(int x, int z) throws IOException
	{
		return loadSection(x >> 5, z >> 5).contains(x & 31, z & 31);
	}

	public AtomicRegionData loadSection(int x, int z, boolean anonymous) throws IOException
	{
		ChunkPosition pos = new ChunkPosition(x, z);
		if(!anonymous)
		{
			lastRegion.put(pos, M.ms());
		}

		if(isSectionLoaded(x, z))
		{
			return loadedSections.get(pos);
		}

		File file = getSubregionFile(x, z);

		if(!file.exists())
		{
			AtomicRegionData dat = createSection(x, z);
			loadedSections.put(pos, dat);
			return dat;
		}

		FileInputStream fin = new FileInputStream(file);
		AtomicRegionData data = new AtomicRegionData(world);
		data.read(fin);
		fin.close();
		loadedSections.put(pos, data);
		return data;
	}

	public AtomicRegionData loadSection(int x, int z) throws IOException
	{
		return loadSection(x, z, false);
	}

	public AtomicRegionData createSection(int x, int z)
	{
		if(isSectionLoaded(x, z))
		{
			return loadedSections.get(new ChunkPosition(x, z));
		}

		AtomicRegionData data = new AtomicRegionData(world);
		loadedSections.put(new ChunkPosition(x, z), data);

		return data;
	}

	public File getSubregionFile(int x, int z)
	{
		return new File(getSubregionFolder(), "sr." + x + "." + z + ".smca");
	}

	public File getSubregionFolder()
	{
		return new File(world.getWorldFolder(), "region-parallax");
	}

	public KMap<ChunkPosition, AtomicSliverMap> getLoadedChunks()
	{
		return loadedChunks;
	}

	public void clean(int j)
	{
		if(M.ms() - last < getUnloadBatchSpeed())
		{
			return;
		}

		int m = 0;

		for(ChunkPosition i : lastRegion.keySet())
		{
			if(m > 1)
			{
				break;
			}

			if(M.ms() - lastRegion.get(i) > (Iris.lowMemoryMode ? 15000 : 30000))
			{
				unloadRegions.add(i);
				m++;
			}
		}

		m = 0;

		for(ChunkPosition i : unloadRegions)
		{
			lastRegion.remove(i);

			try
			{
				unloadSection(i, true);
			}

			catch(IOException e)
			{
				e.printStackTrace();
			}
		}

		unloadRegions.clear();

		for(ChunkPosition i : lastChunk.keySet())
		{
			if(m > getUnloadBatchSize())
			{
				break;
			}

			if(M.ms() - lastChunk.get(i) > (Iris.lowMemoryMode ? 8000 : 30000))
			{
				m++;
				unloadChunks.add(i);
			}
		}

		for(ChunkPosition i : unloadChunks)
		{
			try
			{
				saveChunk(i);
			}

			catch(IOException e)
			{
				Iris.warn("Failed to save chunk");
			}
		}

		unloadChunks.clear();
	}

	private int getUnloadBatchSize()
	{
		return Math.max(3, getLoadedRegions().size() / 85);
	}

	private int getUnloadBatchSpeed()
	{
		return Math.max(250, 2000 - getLoadedRegions().size());
	}
}
