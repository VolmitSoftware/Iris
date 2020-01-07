package ninja.bytecode.iris.schematic;

import java.io.DataInputStream;
import java.io.DataOutputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.util.zip.GZIPInputStream;

import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.World;
import org.bukkit.util.BlockVector;

import ninja.bytecode.iris.Iris;
import ninja.bytecode.iris.util.Catalyst12;
import ninja.bytecode.iris.util.MB;
import ninja.bytecode.shuriken.collections.GMap;
import ninja.bytecode.shuriken.io.CustomOutputStream;
import ninja.bytecode.shuriken.logging.L;

public class Schematic
{
	private boolean centeredHeight;
	private int w;
	private int h;
	private int d;
	private final GMap<BlockVector, MB> s;

	public Schematic(int w, int h, int d)
	{
		this.w = w;
		this.h = h;
		this.d = d;
		s = new GMap<>();
		centeredHeight = false;
	}

	public void setCenteredHeight()
	{
		this.centeredHeight = true;
	}

	public int getW()
	{
		return w;
	}

	public int getH()
	{
		return h;
	}

	public int getD()
	{
		return d;
	}

	public GMap<BlockVector, MB> getSchematic()
	{
		return s;
	}

	@SuppressWarnings("deprecation")
	public void read(InputStream in) throws IOException
	{
		GZIPInputStream gzi = new GZIPInputStream(in);
		DataInputStream din = new DataInputStream(gzi);
		w = din.readInt();
		h = din.readInt();
		d = din.readInt();
		int l = din.readInt();
		clear();

		for(int i = 0; i < l; i++)
		{
			s.put(new BlockVector(din.readInt(), din.readInt(), din.readInt()), new MB(Material.getMaterial((int) din.readInt()), din.readInt()));
		}

		din.close();
	}

	@SuppressWarnings("deprecation")
	public void write(OutputStream out) throws IOException
	{
		CustomOutputStream cos = new CustomOutputStream(out, 9);
		DataOutputStream dos = new DataOutputStream(cos);
		dos.writeInt(w);
		dos.writeInt(h);
		dos.writeInt(d);
		dos.writeInt(s.size());

		for(BlockVector i : s.keySet())
		{
			dos.writeInt(i.getBlockX());
			dos.writeInt(i.getBlockY());
			dos.writeInt(i.getBlockZ());
			dos.writeInt(s.get(i).material.getId());
			dos.writeInt(s.get(i).data);
		}

		dos.close();
	}

	public MB get(int x, int y, int z)
	{
		return s.get(new BlockVector(x, y, z));
	}

	public boolean has(int x, int y, int z)
	{
		return s.contains(new BlockVector(x, y, z));
	}

	public void put(int x, int y, int z, MB mb)
	{
		s.put(new BlockVector(x, y, z), mb);
	}

	public Schematic copy()
	{
		Schematic s = new Schematic(w, h, d);
		s.fill(this.s);
		s.centeredHeight = centeredHeight;
		return s;
	}

	public void clear()
	{
		s.clear();
	}

	public void fill(GMap<BlockVector, MB> b)
	{
		clear();
		s.put(b);
	}

	public int sh(int g)
	{
		int m = (g / 2);
		return g % 2 == 0 ? m : m + 1;
	}

	public void place(World source, int wx, int wy, int wz)
	{
		Location start = new Location(source, wx, wy, wz).clone().add(sh(w), sh(h) + 1, sh(d));

		for(BlockVector i : getSchematic().k())
		{
			MB b = getSchematic().get(i);
			Location f = start.clone().add(i);

			if(b.material.equals(Material.SKULL))
			{
				continue;
			}

			try
			{
				Iris.refresh.add(f.getChunk());
				Catalyst12.setBlock(source, f.getBlockX(), f.getBlockY(), f.getBlockZ(), b);
			}

			catch(Throwable e)
			{
				e.printStackTrace();
			}
		}
	}
	
	public static Schematic load(InputStream in) throws IOException
	{
		Schematic s = new Schematic(1, 1, 1);
		s.read(in);

		L.i("Loaded Internal Schematic: " + s.getSchematic().size());
		return s;
	}

	public static Schematic load(File f) throws IOException
	{
		Schematic s = new Schematic(1, 1, 1);
		FileInputStream fin = new FileInputStream(f);
		s.read(fin);
		

		L.i("Loaded Schematic: " + f.getPath() + " Size: " + s.getSchematic().size());
		return s;
	}
}
