package com.volmit.iris.scaffold.hunk.io;

import com.volmit.iris.Iris;
import com.volmit.iris.scaffold.hunk.Hunk;
import com.volmit.iris.util.*;
import org.bukkit.block.data.BlockData;

import java.io.IOException;

public class HunkRegionSlice<T>
{
	public static final Function2<Integer, CompoundTag, HunkRegionSlice<BlockData>> BLOCKDATA = (h, c) -> new HunkRegionSlice<>(h, Hunk::newMappedHunk, new BlockDataHunkIOAdapter(), c, "blockdata");
	public static final Function3<Integer, CompoundTag, String, HunkRegionSlice<String>> STRING = (h, c, t) -> new HunkRegionSlice<>(h, Hunk::newMappedHunk, new StringHunkIOAdapter(), c, t);
	public static final Function3<Integer, CompoundTag, String, HunkRegionSlice<Boolean>> BOOLEAN = (h, c, t) -> new HunkRegionSlice<>(h, Hunk::newMappedHunk, new BooleanHunkIOAdapter(), c, t);
	private final Function3<Integer, Integer, Integer, Hunk<T>> factory;
	private final HunkIOAdapter<T> adapter;
	private final CompoundTag compound;
	private final String key;
	private final KMap<ChunkPosition, Hunk<T>> loadedChunks;
	private final KMap<ChunkPosition, Long> lastUse;
	private final KList<ChunkPosition> save;
	private final int height;

	public HunkRegionSlice(int height, Function3<Integer, Integer, Integer, Hunk<T>> factory, HunkIOAdapter<T> adapter, CompoundTag compound, String key)
	{
		this.height = height;
		this.loadedChunks = new KMap<>();
		this.factory = factory;
		this.adapter = adapter;
		this.compound = compound;
		this.save = new KList<>();
		this.key = key;
		this.lastUse = new KMap<>();
	}

	public void cleanup(long t)
	{
		if(loadedChunks.size() != lastUse.size())
		{
			Iris.warn("Incorrect chunk use counts in " + key);

			for(ChunkPosition i : lastUse.k())
			{
				if(!loadedChunks.containsKey(i))
				{
					Iris.warn("  Missing LoadChunkKey " + i);
				}
			}
		}

		for(ChunkPosition i : lastUse.k())
		{
			if(M.ms() - lastUse.get(i) > t)
			{
				unload(i.getX(), i.getZ());
			}
		}
	}

	public void clear()
	{
		synchronized(save)
		{
			for(String i : new KList<>(compound.getValue().keySet()))
			{
				if(i.startsWith(key + "."))
				{
					compound.getValue().remove(i);
				}
			}
		}
	}

	public void save()
	{
		for(ChunkPosition i : save)
		{
			save(i.getX(), i.getZ());
		}

		save.clear();
	}

	public boolean contains(int x, int z)
	{
		return compound.getValue().containsKey(key(x, z));
	}

	public void delete(int x, int z)
	{
		compound.getValue().remove(key(x, z));
	}

	public Hunk<T> read(int x, int z) throws IOException
	{
		Tag t = compound.getValue().get(key(x, z));

		if(!(t instanceof ByteArrayTag))
		{
			Iris.verbose("NOT BYTE ARRAY!");
			return null;
		}

		return adapter.read(factory, (ByteArrayTag) t);
	}

	public void write(Hunk<T> hunk, int x, int z) throws IOException
	{
		compound.getValue().put(key(x, z), hunk.writeByteArrayTag(adapter, key(x, z)));
	}

	public synchronized void unloadAll()
	{
		for(ChunkPosition i : loadedChunks.k())
		{
			unload(i.getX(), i.getZ());
		}

		save.clear();
		loadedChunks.clear();
		lastUse.clear();
	}

	public synchronized void save(Hunk<T> region, int x, int z)
	{
		try
		{
			write(region, x, z);
		}
		catch(IOException e)
		{
			e.printStackTrace();
		}
	}

	public boolean isLoaded(int x, int z)
	{
		return loadedChunks.containsKey(new ChunkPosition(x, z));
	}

	public synchronized void save(int x, int z)
	{
		if(isLoaded(x, z))
		{
			save(get(x, z), x, z);
		}
	}

	public synchronized void unload(int x, int z)
	{
		ChunkPosition key = new ChunkPosition(x, z);
		if(isLoaded(x, z))
		{
			if(save.contains(key))
			{
				save(x, z);
				save.remove(key);
			}

			lastUse.remove(key);
			loadedChunks.remove(key);
		}
	}

	public synchronized Hunk<T> load(int x, int z)
	{
		if(isLoaded(x, z))
		{
			return loadedChunks.get(new ChunkPosition(x, z));
		}

		Hunk<T> v = null;

		if(contains(x, z))
		{
			try
			{
				v = read(x, z);
			}

			catch(IOException e)
			{
				e.printStackTrace();
			}
		}

		if(v == null)
		{
			v = factory.apply(16, height, 16);
		}

		loadedChunks.put(new ChunkPosition(x, z), v);

		return v;
	}

	public Hunk<T> get(int x, int z)
	{
		ChunkPosition key = new ChunkPosition(x, z);

		Hunk<T> c = loadedChunks.get(key);

		if(c == null)
		{
			c = load(x, z);
		}

		lastUse.put(new ChunkPosition(x, z), M.ms());

		return c;
	}

	public Hunk<T> getR(int x, int z)
	{
		return get(x, z).readOnly();
	}

	public Hunk<T> getRW(int x, int z)
	{
		save.addIfMissing(new ChunkPosition(x, z));
		return get(x, z);
	}

	private String key(int x, int z)
	{
		if(x < 0 || x >= 32 || z < 0 || z >= 32)
		{
			throw new IndexOutOfBoundsException("The chunk " + x + " " + z + " is out of bounds max is 31x31");
		}

		return key + "." + x + "." + z;
	}

	public int getLoadCount()
	{
		return loadedChunks.size();
	}
}
