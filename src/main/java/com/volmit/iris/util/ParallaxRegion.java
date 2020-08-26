package com.volmit.iris.util;

import java.io.DataInputStream;
import java.io.DataOutputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.util.zip.GZIPInputStream;

import org.bukkit.block.data.BlockData;

import com.volmit.iris.IrisSettings;

public class ParallaxRegion implements Writable
{
	private static final ParallaxChunk EMPTY = new ParallaxChunk();
	private ParallaxChunk[] chunks;
	private transient long last;

	public ParallaxRegion(File i) throws IOException
	{
		this();

		if(i.exists())
		{
			FileInputStream in = new FileInputStream(i);
			GZIPInputStream vin = new GZIPInputStream(in);
			DataInputStream min = new DataInputStream(vin);
			read(min);
			min.close();
		}
	}

	public ParallaxRegion(DataInputStream i) throws IOException
	{
		this();
		read(i);
	}

	public ParallaxRegion()
	{
		last = M.ms();
		chunks = new ParallaxChunk[1024];
	}

	@Override
	public void write(DataOutputStream o) throws IOException
	{
		int c = 0;

		for(ParallaxChunk i : chunks)
		{
			if(i != null)
			{
				c++;
			}
		}

		o.writeShort(c);

		for(int i = 0; i < 1024; i++)
		{
			ParallaxChunk ch = chunks[i];
			if(ch != null)
			{
				ch.write(o);
			}
		}
	}

	public void write(File file) throws IOException
	{
		file.getParentFile().mkdirs();
		FileOutputStream o = new FileOutputStream(file);
		CustomOutputStream g = new CustomOutputStream(o, IrisSettings.get().parallaxCompressionLevel);
		DataOutputStream d = new DataOutputStream(g);
		write(d);
		d.close();
	}

	@Override
	public void read(DataInputStream i) throws IOException
	{
		int v = i.readShort();

		for(int b = 0; b < v; b++)
		{
			chunks[b] = new ParallaxChunk(i);
		}
	}

	public boolean isOlderThan(long time)
	{
		return M.ms() - time > last;
	}

	public void set(int x, int y, int z, BlockData d)
	{
		getChunk(x >> 4, z >> 4, true).set(x & 15, y, z & 15, d);
	}

	public BlockData get(int x, int y, int z)
	{
		return getChunk(x >> 4, z >> 4, false).get(x & 15, y, z & 15);
	}

	private final ParallaxChunk getChunk(int x, int z, boolean create)
	{
		last = M.ms();
		int v = (z << 5) | x;

		if(chunks[v] == null)
		{
			if(create)
			{
				chunks[v] = new ParallaxChunk();
			}

			return EMPTY;
		}

		return chunks[v];
	}
}
