package ninja.bytecode.iris.util;

import java.io.File;
import java.util.concurrent.locks.ReentrantLock;

import com.google.gson.Gson;

import ninja.bytecode.iris.Iris;
import ninja.bytecode.iris.object.IrisRegisteredObject;
import ninja.bytecode.shuriken.collections.KList;
import ninja.bytecode.shuriken.collections.KMap;

public class ResourceLoader<T extends IrisRegisteredObject>
{
	protected File root;
	protected String folderName;
	protected String resourceTypeName;
	protected KMap<String, T> loadCache;
	protected KList<File> folderCache;
	protected Class<? extends T> objectClass;
	protected ReentrantLock lock;

	public ResourceLoader(File root, String folderName, String resourceTypeName, Class<? extends T> objectClass)
	{
		lock = new ReentrantLock();
		this.objectClass = objectClass;
		this.resourceTypeName = resourceTypeName;
		this.root = root;
		this.folderName = folderName;
		loadCache = new KMap<>();
	}

	protected T loadFile(File j, String key, String name)
	{
		try
		{
			T t = new Gson().fromJson(IO.readAll(j), objectClass);
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

	public T load(String name)
	{
		String key = name + "-" + objectClass.getCanonicalName();

		if(loadCache.containsKey(key))
		{
			T t = loadCache.get(key);
			return t;
		}

		lock.lock();
		for(File i : getFolders())
		{
			for(File j : i.listFiles())
			{
				if(j.isFile() && j.getName().endsWith(".json") && j.getName().split("\\Q.\\E")[0].equals(name))
				{
					return loadFile(j, key, name);
				}
			}

			File file = new File(i, name + ".json");

			if(file.exists())
			{
				return loadFile(file, key, name);
			}
		}

		Iris.warn("Couldn't find " + resourceTypeName + ": " + name);

		lock.unlock();
		return null;
	}

	public KList<File> getFolders()
	{
		if(folderCache == null)
		{
			folderCache = new KList<>();

			for(File i : root.listFiles())
			{
				if(i.isDirectory())
				{
					for(File j : i.listFiles())
					{
						if(j.isDirectory() && j.getName().equals(folderName))
						{
							folderCache.add(j);
							break;
						}
					}
				}
			}
		}

		return folderCache;
	}

	public void clearCache()
	{
		loadCache.clear();
		folderCache = null;
	}
}
