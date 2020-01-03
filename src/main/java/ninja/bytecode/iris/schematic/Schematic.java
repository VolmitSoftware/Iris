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
import org.bukkit.block.Block;
import org.bukkit.util.BlockVector;

import ninja.bytecode.iris.util.MB;
import ninja.bytecode.shuriken.collections.GMap;
import ninja.bytecode.shuriken.io.CustomOutputStream;
import ninja.bytecode.shuriken.logging.L;

public class Schematic
{
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
			s.put(new BlockVector(din.readInt(), din.readInt(), din.readInt()), new MB(Material.getMaterial((int)din.readByte()), din.readByte()));
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
		dos.writeInt(x);
		dos.writeInt(y);
		dos.writeInt(z);
		dos.writeInt(s.size());
		
		for(BlockVector i : s.keySet())
		{
			dos.writeInt(i.getBlockX());
			dos.writeInt(i.getBlockY());
			dos.writeInt(i.getBlockZ());
			dos.writeByte(s.get(i).material.getId());
			dos.writeByte(s.get(i).data);
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

	@SuppressWarnings("deprecation")
	public void place(World source, int wx, int wy, int wz)
	{
		Location start = new Location(source, wx, wy, wz).clone().subtract(getOffset());

		for(BlockVector i : getSchematic().keySet())
		{
			MB b = getSchematic().get(i);
			Block blk = start.clone().add(i).getBlock();
			
			if(!blk.isEmpty() && !b.material.isOccluding())
			{
				continue;
			}
			
			blk.setTypeIdAndData(b.material.getId(), b.data, false);
		}
	}



	public static Schematic load(File f) throws IOException
	{
		L.i("Loading Schematic: " + f.getPath());
		Schematic s = new Schematic(1, 1, 1, 1, 1, 1);
		FileInputStream fin = new FileInputStream(f);
		s.read(fin);
		
		return s;
	}
}
