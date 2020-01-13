package ninja.bytecode.iris.generator.genobject;

import java.io.File;
import java.io.IOException;
import java.util.concurrent.locks.ReentrantLock;

import net.md_5.bungee.api.ChatColor;
import ninja.bytecode.iris.Iris;
import ninja.bytecode.iris.controller.PackController;
import ninja.bytecode.iris.util.Direction;
import ninja.bytecode.shuriken.collections.GList;
import ninja.bytecode.shuriken.execution.TaskExecutor;
import ninja.bytecode.shuriken.execution.TaskExecutor.TaskGroup;
import ninja.bytecode.shuriken.format.F;
import ninja.bytecode.shuriken.io.IO;
import ninja.bytecode.shuriken.logging.L;

public class GenObjectGroup
{
	private GList<GenObject> schematics;
	private GList<String> flags;
	private String name;
	private int priority;
	private boolean noCascade;

	public GenObjectGroup(String name)
	{
		this.schematics = new GList<>();
		this.flags = new GList<>();
		priority = 0;
		this.name = name;
		this.noCascade = false;
	}

	public void applySnowFilter(int factor)
	{
		L.i(ChatColor.AQUA + "Applying Snow Filter to " + ChatColor.WHITE + getName());
		for(GenObject i : schematics)
		{
			i.applySnowFilter(factor);
		}
	}

	public GenObjectGroup copy(String suffix)
	{
		GenObjectGroup gog = new GenObjectGroup(name + suffix);
		gog.schematics = new GList<>();
		gog.flags = flags.copy();
		gog.priority = priority;
		gog.noCascade = noCascade;

		for(GenObject i : schematics)
		{
			GenObject g = i.copy();
			g.setName(i.getName() + suffix);
			gog.schematics.add(g);
		}

		return gog;
	}

	public String getName()
	{
		return name;
	}

	public void setName(String name)
	{
		this.name = name;
	}

	public GList<GenObject> getSchematics()
	{
		return schematics;
	}

	public void setSchematics(GList<GenObject> schematics)
	{
		this.schematics = schematics;
	}

	public GList<String> getFlags()
	{
		return flags;
	}

	public void setFlags(GList<String> flags)
	{
		this.flags = flags;
	}

	public int getPriority()
	{
		return priority;
	}

	public void setPriority(int priority)
	{
		this.priority = priority;
	}

	public int size()
	{
		return getSchematics().size();
	}

	public static GenObjectGroup load(String string)
	{
		File folder = Iris.getController(PackController.class).loadFolder(string);

		if(folder != null)
		{
			GenObjectGroup g = new GenObjectGroup(string);

			for(File i : folder.listFiles())
			{
				if(i.getName().endsWith(".ifl"))
				{
					try
					{
						g.flags.add(IO.readAll(i).split("\\Q\n\\E"));
					}

					catch(IOException e)
					{
						L.ex(e);
					}
				}

				if(i.getName().endsWith(".ish"))
				{
					try
					{
						GenObject s = GenObject.load(i);
						g.getSchematics().add(s);
					}

					catch(IOException e)
					{
						L.f("Cannot load Schematic: " + string + "/" + i.getName());
						L.ex(e);
					}
				}
			}

			return g;
		}

		return null;
	}

	public void processVariants()
	{
		GList<GenObject> inject = new GList<>();
		String x = Thread.currentThread().getName();
		ReentrantLock rr = new ReentrantLock();
		TaskExecutor ex = new TaskExecutor(Iris.settings.performance.compilerThreads, Iris.settings.performance.compilerPriority, x + "/Subroutine ");
		TaskGroup gg = ex.startWork();
		for(GenObject i : getSchematics())
		{
			for(Direction j : new Direction[] {Direction.S, Direction.E, Direction.W})
			{
				GenObject cp = i.copy();

				gg.queue(() ->
				{
					GenObject f = cp;
					f.rotate(Direction.N, j);
					rr.lock();
					inject.add(f);
					rr.unlock();
				});
			}
		}

		gg.execute();
		gg = ex.startWork();
		getSchematics().add(inject);

		for(GenObject i : getSchematics())
		{
			gg.queue(() ->
			{
				i.recalculateMountShift();

				for(String j : flags)
				{
					i.computeFlag(j);
				}
			});
		}

		gg.execute();
		ex.close();
		noCascade = true;

		for(GenObject i : getSchematics())
		{
			if(i.isCascading())
			{
				noCascade = false;
				break;
			}
		}

		L.i(ChatColor.LIGHT_PURPLE + "Processed " + ChatColor.WHITE + F.f(schematics.size()) + ChatColor.LIGHT_PURPLE + " Schematics in " + ChatColor.WHITE + name + (noCascade ? ChatColor.AQUA + "*" : ChatColor.YELLOW + "^"));
	}

	@Override
	public int hashCode()
	{
		final int prime = 31;
		int result = 1;
		result = prime * result + ((flags == null) ? 0 : flags.hashCode());
		result = prime * result + ((name == null) ? 0 : name.hashCode());
		result = prime * result + priority;
		return result;
	}

	@Override
	public boolean equals(Object obj)
	{
		if(this == obj)
			return true;
		if(obj == null)
			return false;
		if(getClass() != obj.getClass())
			return false;
		GenObjectGroup other = (GenObjectGroup) obj;
		if(flags == null)
		{
			if(other.flags != null)
				return false;
		}
		else if(!flags.equals(other.flags))
			return false;
		if(name == null)
		{
			if(other.name != null)
				return false;
		}
		else if(!name.equals(other.name))
			return false;
		if(priority != other.priority)
			return false;
		return true;
	}

	public boolean isCascading()
	{
		return !noCascade;
	}
}
