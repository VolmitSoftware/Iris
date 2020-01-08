package ninja.bytecode.iris.schematic;

import java.io.File;
import java.io.IOException;
import java.util.concurrent.locks.ReentrantLock;

import ninja.bytecode.iris.Iris;
import ninja.bytecode.iris.util.Direction;
import ninja.bytecode.shuriken.collections.GList;
import ninja.bytecode.shuriken.execution.TaskExecutor;
import ninja.bytecode.shuriken.execution.TaskExecutor.TaskGroup;
import ninja.bytecode.shuriken.io.IO;
import ninja.bytecode.shuriken.logging.L;

public class SchematicGroup
{
	private GList<Schematic> schematics;
	private GList<String> flags;
	private String name;
	private int priority;

	public SchematicGroup(String name)
	{
		this.schematics = new GList<>();
		this.flags = new GList<>();
		priority = 0;
		this.name = name;
	}

	public String getName()
	{
		return name;
	}

	public void setName(String name)
	{
		this.name = name;
	}

	public GList<Schematic> getSchematics()
	{
		return schematics;
	}

	public void setSchematics(GList<Schematic> schematics)
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

	public static SchematicGroup load(String string)
	{
		File folder = Iris.loadFolder(string);

		if(folder != null)
		{
			SchematicGroup g = new SchematicGroup(string);
			
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
						Schematic s = Schematic.load(i);
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
		GList<Schematic> inject = new GList<>();
		L.v("Processing " + name + " Objects");
		L.v("# Creating Rotations for " + getSchematics().size() + " Objects");
		String x = Thread.currentThread().getName();
		ReentrantLock rr = new ReentrantLock();
		TaskExecutor ex = new TaskExecutor(Iris.settings.performance.compilerThreads, Iris.settings.performance.compilerPriority, x + "/Subroutine ");
		TaskGroup gg = ex.startWork();
		for(Schematic i : getSchematics())
		{
			for(Direction j : new Direction[] {Direction.S, Direction.E, Direction.W})
			{
				Schematic cp = i.copy();
				
				gg.queue(() -> {
					Schematic f = cp;
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
		L.v("# Generated " + inject.size() + " Rotated Objects to " + getName());

		for(Schematic i : getSchematics())
		{
			gg.queue(() -> {
				i.computeMountShift();

				for(String j : flags)
				{
					i.computeFlag(j);
				}
			});
		}
		
		gg.execute();
		ex.close();
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
		SchematicGroup other = (SchematicGroup) obj;
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
}
