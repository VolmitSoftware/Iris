package com.volmit.iris.util;

import java.io.File;

import com.google.gson.Gson;
import com.volmit.iris.Iris;
import com.volmit.iris.object.IrisRegistrant;

import lombok.Data;

@Data
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
	protected IrisLock lock;
	protected String preferredFolder = null;
	protected String[] possibleKeys = null;

	public ResourceLoader(File root, String folderName, String resourceTypeName, Class<? extends T> objectClass)
	{
		lock = new IrisLock("Res");
		folderMapCache = new KMap<>();
		this.objectClass = objectClass;
		cname = objectClass.getCanonicalName();
		this.resourceTypeName = resourceTypeName;
		this.root = root;
		this.folderName = folderName;
		loadCache = new KMap<>();
	}

	public String[] getPossibleKeys()
	{
		if(possibleKeys != null)
		{
			return possibleKeys;
		}

		Iris.info("Building " + resourceTypeName + " Possibility Lists");
		KSet<String> m = new KSet<>();

		for(File i : getFolders())
		{
			for(File j : i.listFiles())
			{
				if(j.isFile() && j.getName().endsWith(".json"))
				{
					m.add(j.getName().replaceAll("\\Q.json\\E", ""));
				}

				else if(j.isDirectory())
				{
					for(File k : j.listFiles())
					{
						if(k.isFile() && k.getName().endsWith(".json"))
						{
							m.add(j.getName() + "/" + k.getName().replaceAll("\\Q.json\\E", ""));
						}
					}
				}
			}
		}

		KList<String> v = new KList<>(m);
		possibleKeys = v.toArray(new String[v.size()]);
		return possibleKeys;
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
			J.a(() -> Iris.verbose("Loading " + resourceTypeName + ": " + j.getPath()));
			t.setLoadKey(name);
			t.setLoadFile(j);
			lock.unlock();
			return t;
		}

		catch(Throwable e)
		{
			lock.unlock();
			J.a(() -> Iris.warn("Couldn't read " + resourceTypeName + " file: " + j.getPath() + ": " + e.getMessage()));
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

		J.a(() -> Iris.warn("Couldn't find " + resourceTypeName + ": " + name));

		lock.unlock();
		return null;
	}

	public KList<File> getFolders()
	{
		lock.lock();
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

			if(preferredFolder != null)
			{
				for(File i : folderCache.copy())
				{
					if(i.getParentFile().getName().equals(preferredFolder))
					{
						folderCache.remove(i);
						folderCache.add(0, i);
					}
				}
			}
		}
		lock.unlock();

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
		possibleKeys = null;
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

	public void preferFolder(String name)
	{
		preferredFolder = name;
	}

	public void clearList()
	{
		folderCache = null;
		possibleKeys = null;
	}
}
