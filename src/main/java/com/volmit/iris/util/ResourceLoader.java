package com.volmit.iris.util;

import java.io.File;
import java.util.concurrent.locks.ReentrantLock;

import com.google.gson.Gson;
import com.volmit.iris.Iris;
import com.volmit.iris.object.IrisRegistrant;

public class ResourceLoader<T extends IrisRegistrant>
{
	protected File root;
	protected String folderName;
	protected String resourceTypeName;
	protected KMap<String, File> folderMapCache;
	protected KMap<String, T> loadCache;
	protected KList<File> folderCache;
	protected Class<? extends T> objectClass;
	protected String cname;
	protected ReentrantLock lock;

	public ResourceLoader(File root, String folderName, String resourceTypeName, Class<? extends T> objectClass)
	{
		lock = new ReentrantLock();
		folderMapCache = new KMap<>();
		this.objectClass = objectClass;
		cname = objectClass.getCanonicalName();
		this.resourceTypeName = resourceTypeName;
		this.root = root;
		this.folderName = folderName;
		loadCache = new KMap<>();
	}

	public long count()
	{
		return loadCache.size();
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
			t.setLoadFile(j);
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
		String key = name + "-" + cname;

		if(loadCache.containsKey(key))
		{
			T t = loadCache.get(key);
			return t;
		}

		lock.lock();
		for(File i : getFolders(name))
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

	public KList<File> getFolders(String rc)
	{
		KList<File> folders = getFolders().copy();

		if(rc.contains(":"))
		{
			for(File i : folders.copy())
			{
				if(!rc.startsWith(i.getName() + ":"))
				{
					folders.remove(i);
				}
			}
		}

		return folders;
	}

	public void clearCache()
	{
		loadCache.clear();
		folderCache = null;
	}

	public File fileFor(T b)
	{
		for(File i : getFolders())
		{
			for(File j : i.listFiles())
			{
				if(j.isFile() && j.getName().endsWith(".json") && j.getName().split("\\Q.\\E")[0].equals(b.getLoadKey()))
				{
					return j;
				}
			}

			File file = new File(i, b.getLoadKey() + ".json");

			if(file.exists())
			{
				return file;
			}
		}

		return null;
	}

	public boolean isLoaded(String next)
	{
		return loadCache.containsKey(next);
	}
}
