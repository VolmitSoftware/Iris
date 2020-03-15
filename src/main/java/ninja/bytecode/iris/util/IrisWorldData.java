package ninja.bytecode.iris.util;

import java.io.IOException;

import org.bukkit.Bukkit;
import org.bukkit.World;

import mortar.logic.format.F;
import mortar.util.text.C;
import ninja.bytecode.iris.Iris;
import ninja.bytecode.iris.generator.atomics.AtomicChunkData;
import ninja.bytecode.iris.generator.atomics.AtomicRegionData;
import ninja.bytecode.iris.generator.atomics.AtomicWorldData;
import ninja.bytecode.shuriken.collections.KList;
import ninja.bytecode.shuriken.collections.KMap;
import ninja.bytecode.shuriken.logging.L;

public class IrisWorldData
{
	private final World world;
	private final AtomicWorldData data;
	private boolean saving;
	private final KMap<SMCAVector, AtomicChunkData> loadedChunks;

	public IrisWorldData(World world)
	{
		this.world = world;
		saving = true;
		data = new AtomicWorldData(world);
		loadedChunks = new KMap<>();
		Bukkit.getScheduler().scheduleSyncRepeatingTask(Iris.instance, this::softUnloadWorld, 200, 20);
	}

	public void disableSaving()
	{
		saving = false;
	}

	public void enableSaving()
	{
		saving = true;
	}

	private void softUnloadWorld()
	{
		if(!saving)
		{
			return;
		}

		for(SMCAVector i : getLoadedChunks())
		{
			try
			{
				AtomicChunkData d = getChunk(i.getX(), i.getZ());
				if(d.getTimeSinceLastUse() > 15000)
				{
					unloadChunk(i.getX(), i.getZ(), true);
				}
			}

			catch(Throwable e)
			{
				e.printStackTrace();
			}
		}

		for(SMCAVector i : getLoadedRegions())
		{
			softUnloadRegion(i.getX(), i.getZ());
		}
	}

	private boolean softUnloadRegion(int rx, int rz)
	{
		if(!saving)
		{
			return false;
		}

		for(SMCAVector i : loadedChunks.keySet())
		{
			if(i.getX() >> 5 == rx && i.getZ() >> 5 == rz)
			{
				return false;
			}
		}

		try
		{
			data.unloadSection(rx, rz, true);
			return true;
		}

		catch(IOException e)
		{
			e.printStackTrace();
			L.f(C.RED + "Failed to save Iris Subregion " + C.YELLOW + rx + " " + rz);
		}

		return false;
	}

	public boolean deleteChunk(int x, int z)
	{
		if(isChunkLoaded(x, z))
		{
			unloadChunk(x, z, false);
		}

		try
		{
			AtomicRegionData region = data.getSubregion(x >> 5, z >> 5);
			region.delete(x & 31, z & 31);
			return true;
		}

		catch(IOException e)
		{
			L.f(C.RED + "Failed delete chunk " + C.YELLOW + x + " " + z + C.RED.toString() + " -> Failed to get Region " + C.YELLOW + (x >> 5) + " " + (z >> 5));
			e.printStackTrace();
		}

		return false;
	}

	public boolean unloadChunk(int x, int z, boolean save)
	{
		if(!isChunkLoaded(x, z))
		{
			return false;
		}

		if(save)
		{
			saveChunk(x, z);
		}

		loadedChunks.remove(new SMCAVector(x, z));
		return true;
	}

	public boolean saveChunk(int x, int z)
	{
		if(!isChunkLoaded(x, z))
		{
			return false;
		}

		try
		{
			AtomicRegionData region = data.getSubregion(x >> 5, z >> 5);
			region.set(x & 31, z & 31, getChunk(x, z));
			return true;
		}

		catch(IOException e)
		{
			L.f(C.RED + "Failed save chunk " + C.YELLOW + x + " " + z + C.RED.toString() + " -> Failed to get Region " + C.YELLOW + (x >> 5) + " " + (z >> 5));
			e.printStackTrace();
		}

		return false;
	}

	public AtomicChunkData getOnly(int x, int z)
	{
		if(!isChunkLoaded(x, z))
		{
			return null;
		}

		return getChunk(x, z);
	}

	public AtomicChunkData getChunk(int x, int z)
	{
		if(!isChunkLoaded(x, z))
		{
			try
			{
				AtomicRegionData region = data.getSubregion(x >> 5, z >> 5);

				if(region.contains(x & 31, z & 31))
				{
					AtomicChunkData chunk = region.get(x & 31, z & 31);
					loadedChunks.put(new SMCAVector(x, z), chunk);
				}

				else
				{
					AtomicChunkData data = new AtomicChunkData(world);
					loadedChunks.put(new SMCAVector(x, z), data);
				}
			}

			catch(IOException e)
			{
				L.f(C.RED + "Failed load chunk " + C.YELLOW + x + " " + z + C.RED.toString() + " -> Failed to get Region " + C.YELLOW + (x >> 5) + " " + (z >> 5));
				e.printStackTrace();
			}
		}

		return loadedChunks.get(new SMCAVector(x, z));
	}

	public boolean isChunkLoaded(int x, int z)
	{
		return loadedChunks.containsKey(new SMCAVector(x, z));
	}

	public void inject(int x, int z, AtomicChunkData data)
	{
		getChunk(x, z).inject(data);
	}

	public boolean exists(int x, int z)
	{
		try
		{
			return isChunkLoaded(x, z) || data.getSubregion(x >> 5, z >> 5).contains(x & 31, z & 31);
		}

		catch(IOException e)
		{
			L.f(C.RED + "Failed check chunk " + C.YELLOW + x + " " + z + C.RED.toString() + " -> Failed to get Region " + C.YELLOW + (x >> 5) + " " + (z >> 5));
			e.printStackTrace();
		}

		return false;
	}

	public KList<SMCAVector> getLoadedChunks()
	{
		return loadedChunks.k();
	}

	public KList<SMCAVector> getLoadedRegions()
	{
		return data.getLoadedRegions();
	}

	public void saveAll()
	{
		for(SMCAVector i : loadedChunks.k())
		{
			saveChunk(i.getX(), i.getZ());
		}

		try
		{
			data.saveAll();
		}

		catch(IOException e)
		{
			e.printStackTrace();
		}
	}

	public void setBlock(int x, int y, int z, int id, byte data)
	{
		getChunk(x >> 4, z >> 4).setBlock(x & 15, y, z & 15, id, data);
	}

	public void dispose()
	{
		for(SMCAVector i : getLoadedChunks())
		{
			unloadChunk(i.getX(), i.getZ(), true);
		}

		softUnloadWorld();
	}
}
