package ninja.bytecode.iris.util;

import java.io.File;

import com.google.gson.Gson;

import ninja.bytecode.iris.Iris;

public class ResourceLoader<T>
{
	private File root;
	private String folderName;
	private String resourceTypeName;
	private KMap<String, T> loadCache;
	private KList<File> folderCache;
	private Class<? extends T> objectClass;

	public ResourceLoader(File root, String folderName, String resourceTypeName, Class<? extends T> objectClass)
	{
		this.objectClass = objectClass;
		this.resourceTypeName = resourceTypeName;
		this.root = root;
		this.folderName = folderName;
		loadCache = new KMap<>();
	}

	public T load(String name)
	{
		String key = name + "-" + objectClass.getCanonicalName();

		if(loadCache.containsKey(key))
		{
			return loadCache.get(key);
		}

		for(File i : getFolders())
		{
			for(File j : i.listFiles())
			{
				if(j.isFile() && j.getName().endsWith(".json") && j.getName().split("\\Q.\\E")[0].equals(name))
				{
					try
					{
						T t = new Gson().fromJson(IO.readAll(j), objectClass);
						loadCache.put(key, t);
						Iris.hotloader.track(j);
						Iris.info("Loading " + resourceTypeName + ": " + j.getPath());

						return t;
					}

					catch(Throwable e)
					{
						Iris.warn("Couldn't read " + resourceTypeName + " file: " + j.getPath() + ": " + e.getMessage());
					}
				}
			}
		}

		Iris.warn("Couldn't find " + resourceTypeName + ": " + name);

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
