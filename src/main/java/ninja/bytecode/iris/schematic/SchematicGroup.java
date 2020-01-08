package ninja.bytecode.iris.schematic;

import java.io.File;
import java.io.IOException;

import ninja.bytecode.iris.Iris;
import ninja.bytecode.shuriken.collections.GList;
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
		L.v("Processing " + name + " Objects");
	}
}
