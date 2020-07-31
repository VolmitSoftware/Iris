package com.volmit.iris.util;

import java.io.File;

import com.volmit.iris.Iris;
import com.volmit.iris.object.IrisObject;

public class ObjectResourceLoader extends ResourceLoader<IrisObject>
{
	private ChronoLatch useFlip = new ChronoLatch(2863);
	private KMap<String, Long> useCache = new KMap<>();

	public ObjectResourceLoader(File root, String folderName, String resourceTypeName)
	{
		super(root, folderName, resourceTypeName, IrisObject.class);
	}

	public int getTotalStorage()
	{
		int m = 0;

		for(IrisObject i : loadCache.values())
		{
			m += i.getBlocks().size();
		}

		return m;
	}

	public void clean()
	{
		if(useFlip.flip())
		{
			unloadLast(60000);
		}
	}

	public void unloadLast(long age)
	{
		String v = getOldest();

		if(v == null)
		{
			return;
		}

		if(M.ms() - useCache.get(v) > age)
		{
			unload(v);
		}
	}

	private String getOldest()
	{
		long min = M.ms();
		String v = null;

		for(String i : useCache.k())
		{
			long t = useCache.get(i);
			if(t < min)
			{
				min = t;
				v = i;
			}
		}

		return v;
	}

	private void unload(String v)
	{
		lock.lock();
		useCache.remove(v);
		loadCache.remove(v);
		lock.unlock();
		Iris.info("Unloaded Object: " + v);
	}

	public IrisObject loadFile(File j, String key, String name)
	{
		lock.lock();
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

	public File findFile(String name)
	{
		lock.lock();
		for(File i : getFolders(name))
		{
			for(File j : i.listFiles())
			{
				if(j.isFile() && j.getName().endsWith(".iob") && j.getName().split("\\Q.\\E")[0].equals(name))
				{
					lock.unlock();
					return j;
				}
			}

			File file = new File(i, name + ".iob");

			if(file.exists())
			{
				lock.unlock();
				return file;
			}
		}

		Iris.warn("Couldn't find " + resourceTypeName + ": " + name);

		lock.unlock();
		return null;
	}

	public IrisObject load(String name)
	{
		String key = name + "-" + objectClass.getCanonicalName();

		if(loadCache.containsKey(key))
		{
			IrisObject t = loadCache.get(key);
			useCache.put(key, M.ms());
			return t;
		}

		lock.lock();
		for(File i : getFolders(name))
		{
			for(File j : i.listFiles())
			{
				if(j.isFile() && j.getName().endsWith(".iob") && j.getName().split("\\Q.\\E")[0].equals(name))
				{
					useCache.put(key, M.ms());
					lock.unlock();
					return loadFile(j, key, name);
				}
			}

			File file = new File(i, name + ".iob");

			if(file.exists())
			{
				useCache.put(key, M.ms());
				lock.unlock();
				return loadFile(file, key, name);
			}
		}

		Iris.warn("Couldn't find " + resourceTypeName + ": " + name);

		lock.unlock();
		return null;
	}
}
