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

import ninja.bytecode.iris.util.Catalyst12;
import ninja.bytecode.iris.util.Direction;
import ninja.bytecode.iris.util.MB;
import ninja.bytecode.iris.util.VectorMath;
import ninja.bytecode.shuriken.collections.GMap;
import ninja.bytecode.shuriken.io.CustomOutputStream;
import ninja.bytecode.shuriken.logging.L;

public class Schematic
{
	private boolean centeredHeight;
	private int w;
	private int h;
	private int d;
	private int x;
	private int y;
	private int z;
	private final GMap<BlockVector, MB> s;

	public Schematic(int w, int h, int d, int x, int y, int z)
	{
		this.w = w;
		this.h = h;
		this.d = d;
		this.x = x;
		this.y = y;
		this.z = z;
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

	public int getX()
	{
		return x;
	}

	public int getY()
	{
		return y;
	}

	public int getZ()
	{
		return z;
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
		x = din.readInt();
		y = din.readInt();
		z = din.readInt();
		int l = din.readInt();
		clear();

		for(int i = 0; i < l; i++)
		{
			s.put(new BlockVector(din.readInt(), din.readInt(), din.readInt()), new MB(Material.getMaterial((int) din.readInt()), din.readInt()));
		}

		din.close();
		center();
	}

	@SuppressWarnings("deprecation")
	public void write(OutputStream out) throws IOException
	{
		CustomOutputStream cos = new CustomOutputStream(out, 9);
		DataOutputStream dos = new DataOutputStream(cos);
		dos.writeInt(w);
		dos.writeInt(h);
		dos.writeInt(d);
		dos.writeInt(x);
		dos.writeInt(y);
		dos.writeInt(z);
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

	public BlockVector getOffset()
	{
		return new BlockVector(x, y, z);
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
		Schematic s = new Schematic(w, h, d, x, y, z);
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

	public void place(World source, int wx, int wy, int wz)
	{
		Location start = new Location(source, wx, wy, wz).clone().subtract(0, centeredHeight ? h / 2 : 0, 0);

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
				Catalyst12.setBlock(source, f.getBlockX(), f.getBlockY(), f.getBlockZ(), b);
			}

			catch(Throwable e)
			{
				e.printStackTrace();
			}
		}
	}

	public static Schematic load(File f) throws IOException
	{
		Schematic s = new Schematic(1, 1, 1, 1, 1, 1);
		FileInputStream fin = new FileInputStream(f);
		s.read(fin);

		L.i("Loaded Schematic: " + f.getPath() + " Size: " + s.getSchematic().size());
		return s;
	}
	
	public void center()
	{
		GMap<BlockVector, MB> sf = getSchematic().copy();
		BlockVector max = new BlockVector(-w, -h, -d);
		BlockVector min = new BlockVector(w, h, d);
		clear();
		
		for(BlockVector i : sf.k())
		{			
			if(i.getX() <= min.getX() && i.getZ() <= min.getZ() && i.getY() <= min.getY())
			{
				min = i;
			}
			
			if(i.getX() >= max.getX() && i.getZ() >= max.getZ() && i.getY() >= max.getY())
			{
				max = i;
			}
		}
		
		BlockVector center = min.clone().add(max.clone().multiply(0.5D)).toBlockVector();
		center.setY(0);
		
		for(BlockVector i : sf.k())
		{
			getSchematic().put(i.clone().subtract(center).toBlockVector(), sf.get(i));
		}
		
		x = 0;
		y = 0;
		z = 0;
	}

	public void rotate(Direction from, Direction to)
	{
		GMap<BlockVector, MB> sf = getSchematic().copy();
		clear();
		
		for(BlockVector i : sf.k())
		{
			getSchematic().put(VectorMath.rotate(from, to, i).toBlockVector(), sf.get(i));
		}
		center();
	}
}
