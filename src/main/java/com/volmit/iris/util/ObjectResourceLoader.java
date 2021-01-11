package com.volmit.iris.util;

import com.volmit.iris.Iris;
import com.volmit.iris.manager.IrisDataManager;
import com.volmit.iris.object.IrisObject;

import java.io.File;
import java.util.concurrent.atomic.AtomicInteger;

public class ObjectResourceLoader extends ResourceLoader<IrisObject>
{
	private ChronoLatch useFlip = new ChronoLatch(2222);
	private KMap<String, Long> useCache = new KMap<>();
	private ChronoLatch cl;
	private AtomicInteger unload;

	public ObjectResourceLoader(File root, IrisDataManager idm, String folderName, String resourceTypeName)
	{
		super(root, idm, folderName, resourceTypeName, IrisObject.class);
		cl = new ChronoLatch(30000);
		unload = new AtomicInteger(0);
	}

	public int getSize()
	{
		return loadCache.size();
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
			unloadLast(30000);
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
		unload.getAndIncrement();

		if(unload.get() == 1)
		{
			cl.flip();
		}

		if(cl.flip())
		{
			J.a(() -> {
				Iris.verbose("Unloaded " + C.WHITE + unload.get() +" " + resourceTypeName + (unload.get() == 1 ? "" : "s") + C.GRAY + " to optimize memory usage." + " (" + Form.f(getLoadCache().size() )+ " " + resourceTypeName + (loadCache.size() == 1 ? "" : "s")+ " Loaded)");
				unload.set(0);
			});
		}
	}

	public IrisObject loadFile(File j, String key, String name)
	{
		lock.lock();
		try
		{
			IrisObject t = new IrisObject(0, 0, 0);
			t.read(j);
			loadCache.put(key, t);
			logLoad(j);
			t.setLoadKey(name);
			t.setLoader(manager);
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
				if(j.isFile() && j.getName().endsWith(".iob"))
				{
					m.add(j.getName().replaceAll("\\Q.iob\\E", ""));
				}

				else if(j.isDirectory())
				{
					for(File k : j.listFiles())
					{
						if(k.isFile() && k.getName().endsWith(".iob"))
						{
							m.add(j.getName() + "/" + k.getName().replaceAll("\\Q.iob\\E", ""));
						}

						else if(k.isDirectory())
						{
							for(File l : k.listFiles())
							{
								if(l.isFile() && l.getName().endsWith(".iob"))
								{
									m.add(j.getName() + "/" + k.getName() + "/" + l.getName().replaceAll("\\Q.iob\\E", ""));
								}
							}
						}
					}
				}
			}
		}

		KList<String> v = new KList<>(m);
		possibleKeys = v.toArray(new String[v.size()]);
		return possibleKeys;
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
		return load(name, true);
	}

	public IrisObject load(String name, boolean warn)
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
