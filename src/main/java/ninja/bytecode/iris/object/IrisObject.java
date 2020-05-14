package ninja.bytecode.iris.object;

import java.io.DataInputStream;
import java.io.DataOutputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;

import org.bukkit.Location;
import org.bukkit.block.data.BlockData;
import org.bukkit.util.BlockVector;

import lombok.Data;
import lombok.EqualsAndHashCode;
import ninja.bytecode.iris.util.BlockDataTools;
import ninja.bytecode.iris.util.IObjectPlacer;
import ninja.bytecode.shuriken.collections.KMap;

@Data
@EqualsAndHashCode(callSuper = false)
public class IrisObject extends IrisRegisteredObject
{
	private KMap<BlockVector, BlockData> blocks;
	private int w;
	private int d;
	private int h;
	private transient BlockVector center;

	public IrisObject(int w, int h, int d)
	{
		blocks = new KMap<>();
		this.w = w;
		this.h = h;
		this.d = d;
		center = new BlockVector(w / 2, h / 2, d / 2);
	}
	
	public static BlockVector sampleSize(File file) throws IOException
	{
		FileInputStream in = new FileInputStream(file);
		DataInputStream din = new DataInputStream(in);
		BlockVector bv = new BlockVector(din.readInt(), din.readInt(), din.readInt());
		din.close();
		return bv;
	}

	public void read(InputStream in) throws IOException
	{
		DataInputStream din = new DataInputStream(in);
		this.w = din.readInt();
		this.h = din.readInt();
		this.d = din.readInt();
		center = new BlockVector(w / 2, h / 2, d / 2);
		int s = din.readInt();

		for(int i = 0; i < s; i++)
		{
			blocks.put(new BlockVector(din.readShort(), din.readShort(), din.readShort()), BlockDataTools.getBlockData(din.readUTF()));
		}
	}

	public void read(File file) throws IOException
	{
		FileInputStream fin = new FileInputStream(file);
		read(fin);
		fin.close();
	}

	public void write(File file) throws IOException
	{
		file.getParentFile().mkdirs();
		FileOutputStream out = new FileOutputStream(file);
		write(out);
		out.close();
	}

	public void write(OutputStream o) throws IOException
	{
		DataOutputStream dos = new DataOutputStream(o);
		dos.writeInt(w);
		dos.writeInt(h);
		dos.writeInt(d);
		dos.writeInt(blocks.size());
		for(BlockVector i : blocks.k())
		{
			dos.writeShort(i.getBlockX());
			dos.writeShort(i.getBlockY());
			dos.writeShort(i.getBlockZ());
			dos.writeUTF(blocks.get(i).getAsString(true));
		}
	}

	public void setUnsigned(int x, int y, int z, BlockData block)
	{
		if(x >= w || y >= h || z >= d)
		{
			throw new RuntimeException(x + " " + y + " " + z + " exceeds limit of " + w + " " + h + " " + d);
		}

		BlockVector v = new BlockVector(x, y, z).subtract(center).toBlockVector();

		if(block == null)
		{
			blocks.remove(v);
		}

		else
		{
			blocks.put(v, block);
		}
	}

	public void place(int x, int z, IObjectPlacer placer)
	{
		int y = placer.getHighest(x, z) + getCenter().getBlockY();

		for(BlockVector i : blocks.k())
		{
			placer.set(x + i.getBlockX(), y + i.getBlockY(), z + i.getBlockZ(), blocks.get(i));
		}
	}

	public void place(Location at)
	{
		for(BlockVector i : blocks.k())
		{
			at.clone().add(0, getCenter().getY(), 0).add(i).getBlock().setBlockData(blocks.get(i), false);
		}
	}
}
