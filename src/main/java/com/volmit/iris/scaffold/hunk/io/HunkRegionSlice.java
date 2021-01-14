package com.volmit.iris.scaffold.hunk.io;

import com.volmit.iris.Iris;
import com.volmit.iris.object.tile.TileData;
import com.volmit.iris.scaffold.hunk.Hunk;
import com.volmit.iris.scaffold.parallel.BurstExecutor;
import com.volmit.iris.scaffold.parallel.GridLock;
import com.volmit.iris.scaffold.parallel.MultiBurst;
import com.volmit.iris.util.*;
import org.bukkit.block.TileState;
import org.bukkit.block.data.BlockData;

import java.io.IOException;
import java.util.concurrent.atomic.AtomicReference;

public class HunkRegionSlice<T>
{
	public static final Function2<Integer, CompoundTag, HunkRegionSlice<BlockData>> BLOCKDATA = (h, c) -> new HunkRegionSlice<>(h, Hunk::newMappedHunkSynced, new BlockDataHunkIOAdapter(), c, "blockdata");
	public static final Function2<Integer, CompoundTag, HunkRegionSlice<TileData<? extends TileState>>> TILE = (h, c) -> new HunkRegionSlice<>(h, Hunk::newMappedHunkSynced, new TileDataHunkIOAdapter(), c, "tile");
	public static final Function3<Integer, CompoundTag, String, HunkRegionSlice<String>> STRING = (h, c, t) -> new HunkRegionSlice<>(h, Hunk::newMappedHunkSynced, new StringHunkIOAdapter(), c, t);
	public static final Function3<Integer, CompoundTag, String, HunkRegionSlice<Boolean>> BOOLEAN = (h, c, t) -> new HunkRegionSlice<>(h, Hunk::newMappedHunkSynced, new BooleanHunkIOAdapter(), c, t);
	private final Function3<Integer, Integer, Integer, Hunk<T>> factory;
	private final GridLock lock;
	private final HunkIOAdapter<T> adapter;
	private final CompoundTag compound;
	private final String key;
	private final KMap<ChunkPosition, Hunk<T>> loadedChunks;
	private final KMap<ChunkPosition, Long> lastUse;
	private final KList<ChunkPosition> save;
	private final int height;

	public HunkRegionSlice(int height, Function3<Integer, Integer, Integer, Hunk<T>> factory, HunkIOAdapter<T> adapter, CompoundTag compound, String key)
	{
		this.lock = new GridLock(32, 32);
		this.height = height;
		this.loadedChunks = new KMap<>();
		this.factory = factory;
		this.adapter = adapter;
		this.compound = compound;
		this.save = new KList<>();
		this.key = key;
		this.lastUse = new KMap<>();
	}

	public synchronized int cleanup(long t)
	{
		int v = 0;
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
			Long l = lastUse.get(i);
			if(l == null || M.ms() - l > t)
			{
				v++;
				unload(i.getX(), i.getZ());
			}
		}

		return v;
	}

	public synchronized void clear()
	{
		for(String i : new KList<>(compound.getValue().keySet()))
		{
			if(i.startsWith(key + "."))
			{
				compound.getValue().remove(i);
			}
		}
	}

	public synchronized void save()
	{
		BurstExecutor e = MultiBurst.burst.burst();
		for(ChunkPosition i : save.copy())
		{
			if(i == null)
			{
				continue;
			}

			e.queue(() -> save(i.getX(), i.getZ()));

			try
			{
				lock.withNasty(i.getX(), i.getZ(), () -> save.remove(i));
			}

			catch(Throwable ef)
			{

			}
		}

		e.complete();
	}

	public boolean contains(int x, int z)
	{
		return compound.getValue().containsKey(key(x, z));
	}

	public void delete(int x, int z)
	{
		lock.with(x, z, () -> compound.getValue().remove(key(x, z)));
	}

	public Hunk<T> read(int x, int z) throws IOException
	{
		AtomicReference<IOException> e = new AtomicReference<>();
		Hunk<T> xt = lock.withResult(x, z, () -> {
			Tag t = compound.getValue().get(key(x, z));

			if(!(t instanceof ByteArrayTag))
			{
				Iris.verbose("NOT BYTE ARRAY!");
				return null;
			}

			try {
				return adapter.read(factory, (ByteArrayTag) t);
			} catch (IOException xe) {
				e.set(xe);
			}

			return null;
		});

		if(xt != null)
		{
			return xt;
		}

		if(e.get()!= null)
		{
			throw e.get();
		}

		return null;
	}

	public void write(Hunk<T> hunk, int x, int z) throws IOException
	{
		lock.withIO(x, z, () -> compound.getValue().put(key(x, z), hunk.writeByteArrayTag(adapter, key(x, z))));
	}

	public synchronized int unloadAll()
	{
		int v = 0;
		for(ChunkPosition i : loadedChunks.k())
		{
			unload(i.getX(), i.getZ());
			v++;
		}

		save.clear();
		loadedChunks.clear();
		lastUse.clear();
		return v;
	}

	public  void save(Hunk<T> region, int x, int z)
	{
		try
		{
			lock.withIO(x, z, () -> write(region, x, z));
		}
		catch(IOException e)
		{
			e.printStackTrace();
		}
	}

	public boolean isLoaded(int x, int z)
	{
		return lock.withResult(x, z, () -> loadedChunks.containsKey(new ChunkPosition(x, z)));
	}

	public  void save(int x, int z)
	{
		lock.with(x, z, () -> {
			if(isLoaded(x, z))
			{
				save(get(x, z), x, z);
			}
		});
	}

	public void unload(int x, int z)
	{
		lock.with(x, z, () -> {
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
		});
	}

	public  Hunk<T> load(int x, int z)
	{
		return lock.withResult(x, z, () -> {
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
		});
	}

	public  Hunk<T> get(int x, int z)
	{
		return lock.withResult(x, z, () -> {
			ChunkPosition key = new ChunkPosition(x, z);

			Hunk<T> c = loadedChunks.get(key);

			if(c == null)
			{
				c = load(x, z);
			}

			lastUse.put(new ChunkPosition(x, z), M.ms());

			return c;
		});
	}

	public  Hunk<T> getR(int x, int z)
	{
		return lock.withResult(x, z, () -> get(x, z).readOnly());
	}

	public  Hunk<T> getRW(int x, int z)
	{
		return lock.withResult(x, z, () -> {
			save.addIfMissing(new ChunkPosition(x, z));
			return get(x, z);
		});
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
