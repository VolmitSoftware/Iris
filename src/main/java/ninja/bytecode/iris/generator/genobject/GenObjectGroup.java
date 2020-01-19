package ninja.bytecode.iris.generator.genobject;

import java.io.DataInputStream;
import java.io.DataOutputStream;
import java.io.File;
import java.io.IOException;
import java.util.function.Consumer;

import net.md_5.bungee.api.ChatColor;
import ninja.bytecode.iris.Iris;
import ninja.bytecode.iris.controller.PackController;
import ninja.bytecode.iris.util.Direction;
import ninja.bytecode.shuriken.collections.GList;
import ninja.bytecode.shuriken.format.F;
import ninja.bytecode.shuriken.io.IO;
import ninja.bytecode.shuriken.logging.L;

public class GenObjectGroup
{
	private GList<GenObject> schematics;
	private GList<String> flags;
	private String name;
	private int priority;

	public GenObjectGroup(String name)
	{
		this.schematics = new GList<>();
		this.flags = new GList<>();
		this.name = name;
		priority = Integer.MIN_VALUE;
	}

	public void read(DataInputStream din) throws IOException
	{
		flags.clear();
		schematics.clear();
		name = din.readUTF();
		int fl = din.readInt();
		int sc = din.readInt();

		for(int i = 0; i < fl; i++)
		{
			flags.add(din.readUTF());
		}

		for(int i = 0; i < sc; i++)
		{
			GenObject g = new GenObject(0, 0, 0);
			g.readDirect(din);
			schematics.add(g);
		}
	}

	public void write(DataOutputStream dos, Consumer<Double> progress) throws IOException
	{
		dos.writeUTF(name);
		dos.writeInt(flags.size());
		dos.writeInt(schematics.size());

		for(String i : flags)
		{
			dos.writeUTF(i);
		}

		int of = 0;

		if(progress != null)
		{
			progress.accept((double) of / (double) schematics.size());
		}

		for(GenObject i : schematics)
		{
			i.writeDirect(dos);
			of++;

			if(progress != null)
			{
				progress.accept((double) of / (double) schematics.size());
			}
		}
	}

	public void applySnowFilter(int factor)
	{
		if(flags.contains("no snow"))
		{
			L.i(ChatColor.DARK_AQUA + "Skipping Snow Filter for " + ChatColor.GRAY + getName());
			return;
		}

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

	public int size()
	{
		return getSchematics().size();
	}

	public int getPiority()
	{
		if(priority == Integer.MIN_VALUE)
		{
			for(String i : flags)
			{
				if(i.startsWith("priority "))
				{
					priority = Integer.valueOf(i.split("\\Q \\E")[1]);
					break;
				}
			}
		}

		return priority;
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
		for(GenObject i : getSchematics())
		{
			i.recalculateMountShift();

			for(String j : flags)
			{
				i.computeFlag(j);
			}
		}

		if(!flags.contains("no rotation"))
		{
			GList<GenObject> inject = new GList<>();
			for(GenObject i : getSchematics())
			{
				for(Direction j : new Direction[] {Direction.S, Direction.E, Direction.W})
				{
					GenObject cp = i.copy();
					GenObject f = cp;
					f.rotate(Direction.N, j);
					f.recalculateMountShift();
					inject.add(f);
				}
			}

			getSchematics().add(inject);
		}

		L.i(ChatColor.LIGHT_PURPLE + "Processed " + ChatColor.WHITE + F.f(schematics.size()) + ChatColor.LIGHT_PURPLE + " Schematics in " + ChatColor.WHITE + name);
	}

	public void dispose()
	{
		for(GenObject i : schematics)
		{
			i.dispose();
		}

		schematics.clear();
		flags.clear();
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

}
