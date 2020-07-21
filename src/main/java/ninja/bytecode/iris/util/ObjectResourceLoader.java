package ninja.bytecode.iris.util;

import java.io.File;

import org.bukkit.util.BlockVector;

import ninja.bytecode.iris.Iris;
import ninja.bytecode.iris.object.IrisObject;

public class ObjectResourceLoader extends ResourceLoader<IrisObject>
{
	private ChunkPosition parallaxSize;

	public ObjectResourceLoader(File root, String folderName, String resourceTypeName)
	{
		super(root, folderName, resourceTypeName, IrisObject.class);
	}

	public ChunkPosition getParallaxSize()
	{
		lock.lock();
		if(parallaxSize == null)
		{
			int x = 0;
			int z = 0;

			for(File i : getFolders())
			{
				for(File j : i.listFiles())
				{
					if(j.isFile() && j.getName().endsWith(".iob"))
					{
						try
						{
							BlockVector b = IrisObject.sampleSize(j);
							x = b.getBlockX() > x ? b.getBlockX() : x;
							z = b.getBlockZ() > z ? b.getBlockZ() : z;
						}

						catch(Throwable e)
						{

						}
					}
				}
			}

			x = (Math.max(x, 16) + 16) >> 4;
			z = (Math.max(z, 16) + 16) >> 4;
			x = x % 2 == 0 ? x + 1 : x;
			z = z % 2 == 0 ? z + 1 : z;
			parallaxSize = new ChunkPosition(x, z);
			Iris.info("Parallax View Distance: " + x + "x" + z);
		}

		lock.unlock();

		return parallaxSize;
	}

	public IrisObject loadFile(File j, String key, String name)
	{
		try
		{
			IrisObject t = new IrisObject(0, 0, 0);
			t.read(j);
			loadCache.put(key, t);
			Iris.hotloader.track(j);
			Iris.info("Loading " + resourceTypeName + ": " + j.getPath());
			t.setLoadKey(name);
			lock.unlock();
			return t;
		}

		catch(Throwable e)
		{
			lock.unlock();
			Iris.warn("Couldn't read " + resourceTypeName + " file: " + j.getPath() + ": " + e.getMessage());
			return null;
		}
	}

	public IrisObject load(String name)
	{
		String key = name + "-" + objectClass.getCanonicalName();

		if(loadCache.containsKey(key))
		{
			IrisObject t = loadCache.get(key);
			return t;
		}

		lock.lock();
		for(File i : getFolders(name))
		{
			for(File j : i.listFiles())
			{
				if(j.isFile() && j.getName().endsWith(".iob") && j.getName().split("\\Q.\\E")[0].equals(name))
				{
					return loadFile(j, key, name);
				}
			}

			File file = new File(i, name + ".iob");

			if(file.exists())
			{
				return loadFile(file, key, name);
			}
		}

		Iris.warn("Couldn't find " + resourceTypeName + ": " + name);

		lock.unlock();
		return null;
	}
}
