package ninja.bytecode.iris.generator.genobject;

import java.io.IOException;

import mortar.compute.math.M;
import mortar.util.text.C;
import ninja.bytecode.iris.Iris;
import ninja.bytecode.shuriken.logging.L;

public class PhantomGenObject
{
	private GenObject object;
	private String name;
	private boolean loaded;
	private long lastUse;
	private long evictionNotice;
	private int size;

	public PhantomGenObject(GenObject object) throws IOException
	{
		this.object = object;
		this.name = object.getName();
		object.perfectWrite(Iris.instance.getObjectCacheFolder());
		lastUse = M.ms();
		loaded = true;
		size = object.getSchematic().size();
		evictionNotice = 5000 + (size * 7);
	}

	public int getSize()
	{
		return size;
	}

	public boolean isLoaded()
	{
		return loaded;
	}

	public void attemptEviction()
	{
		if(loaded && M.ms() - lastUse > evictionNotice)
		{
			loaded = false;
			object.dispose();
		}
	}

	public GenObject get()
	{
		if(!loaded)
		{
			try
			{
				object.perfectRead(Iris.instance.getObjectCacheFolder(), name);
				loaded = true;
			}

			catch(IOException e)
			{
				L.f(C.RED + "Cannot Read Cached Object: " + name);
				L.ex(e);
			}
		}

		lastUse = M.ms();

		return object;
	}
}
