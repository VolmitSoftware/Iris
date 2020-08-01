package com.volmit.iris.gen.atomics;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.util.zip.GZIPInputStream;

import org.bukkit.World;

import com.volmit.iris.IrisSettings;
import com.volmit.iris.util.ByteArrayTag;
import com.volmit.iris.util.CompoundTag;
import com.volmit.iris.util.CustomOutputStream;
import com.volmit.iris.util.KMap;
import com.volmit.iris.util.NBTInputStream;
import com.volmit.iris.util.NBTOutputStream;
import com.volmit.iris.util.Tag;

public class AtomicRegionData
{
	private final World world;
	private Tag[] tag;

	public AtomicRegionData(World world)
	{
		this.world = world;
		tag = new Tag[1024];
	}

	public int size()
	{
		return tag.length;
	}

	public void read(InputStream in) throws IOException
	{
		NBTInputStream nin = new NBTInputStream(in);
		KMap<String, Tag> tags = new KMap<>();
		tags.putAll(((CompoundTag) nin.readTag()).getValue());

		for(String i : tags.keySet())
		{
			int x = Integer.valueOf(i.split("\\Q.\\E")[0]);
			int z = Integer.valueOf(i.split("\\Q.\\E")[1]);
			tag[(z << 5) | x] = tags.get(i);
		}

		nin.close();
	}

	public void write(OutputStream out) throws IOException
	{
		NBTOutputStream nos = new NBTOutputStream(out);

		KMap<String, Tag> tags = new KMap<>();

		for(int i = 0; i < 32; i++)
		{
			for(int j = 0; j < 32; j++)
			{
				if(tag[(j << 5) | i] != null)
				{
					tags.put(i + "." + j, tag[(j << 5) | i]);
				}
			}
		}

		nos.writeTag(new CompoundTag("imca", tags));
		nos.close();
	}

	public boolean contains(int rx, int rz)
	{
		return tag[(rz << 5) | rx] != null;
	}

	public void delete(int rx, int rz)
	{
		tag[(rz << 5) | rx] = null;
	}

	public void set(int rx, int rz, AtomicSliverMap data) throws IOException
	{
		if(data == null)
		{
			return;
		}

		OutputStream out;
		ByteArrayOutputStream boas = new ByteArrayOutputStream();
		out = boas;

		if(IrisSettings.get().parallaxCompression)
		{
			out = new CustomOutputStream(boas, IrisSettings.get().parallaxCompressionLevel);
		}

		data.write(out);
		out.flush();
		out.close();
		tag[(rz << 5) | rx] = new ByteArrayTag(rx + "." + rz, boas.toByteArray());
	}

	public AtomicSliverMap get(int rx, int rz) throws IOException
	{
		AtomicSliverMap data = new AtomicSliverMap();

		if(!contains(rx, rz))
		{
			return data;
		}

		try
		{
			ByteArrayTag btag = (ByteArrayTag) tag[(rz << 5) | rx];

			InputStream in;

			if(IrisSettings.get().parallaxCompression)
			{
				in = new GZIPInputStream(new ByteArrayInputStream(btag.getValue()));
			}

			else
			{
				in = new ByteArrayInputStream(btag.getValue());
			}

			data.read(in);
		}

		catch(Throwable e)
		{

		}

		return data;
	}

	public World getWorld()
	{
		return world;
	}

	public long guessMemoryUsage()
	{
		long bytes = 0;

		for(int i = 0; i < 1024; i++)
		{
			if(tag[i] != null && tag[i] instanceof ByteArrayTag)
			{
				bytes += 122;
				bytes += ((ByteArrayTag) tag[i]).getValue().length;
			}
		}

		return bytes;
	}
}
