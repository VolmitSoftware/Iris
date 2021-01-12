package com.volmit.iris.util;

import com.google.gson.Gson;
import com.volmit.iris.Iris;
import com.volmit.iris.manager.IrisDataManager;
import com.volmit.iris.object.IrisRegistrant;
import lombok.Data;

import java.io.File;
import java.util.concurrent.atomic.AtomicInteger;

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
	protected String[] possibleKeys = null;
	protected IrisDataManager manager;
	protected AtomicInteger loads;
	protected ChronoLatch sec;

	public ResourceLoader(File root, IrisDataManager manager, String folderName, String resourceTypeName, Class<? extends T> objectClass)
	{
		lock = new IrisLock("Res");
		this.manager = manager;
		sec = new ChronoLatch(5000);
		loads = new AtomicInteger();
		folderMapCache = new KMap<>();
		this.objectClass = objectClass;
		cname = objectClass.getCanonicalName();
		this.resourceTypeName = resourceTypeName;
		this.root = root;
		this.folderName = folderName;
		loadCache = new KMap<>();
	}

	public void logLoad(File path)
	{
		loads.getAndIncrement();

		if(loads.get() == 1)
		{
			sec.flip();
		}

		if(sec.flip())
		{
			J.a(() -> {
				Iris.verbose("Loaded " + C.WHITE + loads.get()  + " " + resourceTypeName + (loads.get() == 1 ? "" : "s") + C.GRAY + " (" + Form.f(getLoadCache().size() ) + " " + resourceTypeName + (loadCache.size() == 1 ? "" : "s") + " Loaded)");
				loads.set(0);
			});
		}
	}

	public void failLoad(File path, Throwable e)
	{
		J.a(() -> Iris.warn("Couldn't Load " + resourceTypeName + " file: " + path.getPath() + ": " + e.getMessage()));
	}

	public String[] getPossibleKeys()
	{
		if(possibleKeys != null)
		{
			return possibleKeys;
		}

		Iris.info("Building " + resourceTypeName + " Registry Lists");
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
			logLoad(j);
			t.setLoadKey(name);
			t.setLoadFile(j);
			t.setLoader(manager);
			lock.unlock();
			return t;
		}

		catch(Throwable e)
		{
			lock.unlock();
			failLoad(j, e);
			return null;
		}
	}

	public T load(String name)
	{
		return load(name, true);
	}

	public T load(String name, boolean warn)
	{
		if(name == null)
		{
			return null;
		}

		if(name.trim().isEmpty())
		{
			return null;
		}

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

		if(warn && !resourceTypeName.equals("Dimension"))
		{
			J.a(() -> Iris.warn("Couldn't find " + resourceTypeName + ": " + name));
		}

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
					if(i.getName().equals(folderName))
					{
						folderCache.add(i);
						break;
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
		lock.lock();
		possibleKeys = null;
		loadCache.clear();
		folderCache = null;
		lock.unlock();
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

	public void clearList()
	{
		lock.lock();
		folderCache = null;
		possibleKeys = null;
		lock.unlock();
	}
}
