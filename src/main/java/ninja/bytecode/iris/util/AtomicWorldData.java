package ninja.bytecode.iris.util;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;

import org.bukkit.World;

import ninja.bytecode.shuriken.collections.GList;
import ninja.bytecode.shuriken.collections.GMap;

public class AtomicWorldData
{
	private World world;
	private GMap<SMCAVector, AtomicMCAData> loadedSections;

	public AtomicWorldData(World world)
	{
		this.world = world;
		loadedSections = new GMap<>();
		getSubregionFolder().mkdirs();
	}

	public GList<SMCAVector> getLoadedRegions()
	{
		return loadedSections.k();
	}

	public AtomicMCAData getSubregion(int x, int z) throws IOException
	{
		if(!isSectionLoaded(x, z))
		{
			loadedSections.put(new SMCAVector(x, z), loadSection(x, z));
		}

		AtomicMCAData f = loadedSections.get(new SMCAVector(x, z));

		return f;
	}

	public void saveAll() throws IOException
	{
		for(SMCAVector i : loadedSections.keySet())
		{
			saveSection(i);
		}
	}

	public void unloadAll(boolean save) throws IOException
	{
		for(SMCAVector i : loadedSections.keySet())
		{
			unloadSection(i, save);
		}
	}

	public void deleteSection(int x, int z) throws IOException
	{
		unloadSection(x, z, false);
		getSubregionFile(x, z).delete();
	}

	public boolean isSectionLoaded(int x, int z)
	{
		return isSectionLoaded(new SMCAVector(x, z));
	}

	public boolean isSectionLoaded(SMCAVector s)
	{
		return loadedSections.containsKey(s);
	}

	public boolean unloadSection(int x, int z, boolean save) throws IOException
	{
		return unloadSection(new SMCAVector(x, z), save);
	}

	public boolean unloadSection(SMCAVector s, boolean save) throws IOException
	{
		if(!isSectionLoaded(s))
		{
			return false;
		}

		if(save)
		{
			saveSection(s);
		}

		loadedSections.remove(s);
		return true;
	}

	public boolean saveSection(int x, int z) throws IOException
	{
		return saveSection(new SMCAVector(x, z));
	}

	public boolean saveSection(SMCAVector s) throws IOException
	{
		if(!isSectionLoaded(s.getX(), s.getZ()))
		{
			return false;
		}

		AtomicMCAData data = loadedSections.get(s);
		FileOutputStream fos = new FileOutputStream(getSubregionFile(s.getX(), s.getZ()));
		data.write(fos);
		fos.close();
		return true;
	}

	public AtomicMCAData loadSection(int x, int z) throws IOException
	{
		if(isSectionLoaded(x, z))
		{
			return loadedSections.get(new SMCAVector(x, z));
		}

		File file = getSubregionFile(x, z);

		if(!file.exists())
		{
			return createSection(x, z);
		}

		FileInputStream fin = new FileInputStream(file);
		AtomicMCAData data = new AtomicMCAData(world);
		data.read(fin);
		fin.close();
		return data;
	}

	public AtomicMCAData createSection(int x, int z)
	{
		if(isSectionLoaded(x, z))
		{
			return loadedSections.get(new SMCAVector(x, z));
		}

		AtomicMCAData data = new AtomicMCAData(world);
		loadedSections.put(new SMCAVector(x, z), data);

		return data;
	}

	public File getSubregionFile(int x, int z)
	{
		return new File(getSubregionFolder(), "sr." + x + "." + z + ".smca");
	}

	public File getSubregionFolder()
	{
		return new File(world.getWorldFolder(), "subregion");
	}
}
