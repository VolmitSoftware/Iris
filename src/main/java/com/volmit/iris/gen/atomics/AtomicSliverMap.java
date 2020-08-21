package com.volmit.iris.gen.atomics;

import java.io.DataInputStream;
import java.io.DataOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;

import org.bukkit.generator.ChunkGenerator.BiomeGrid;
import org.bukkit.generator.ChunkGenerator.ChunkData;

import com.volmit.iris.util.HeightMap;

import lombok.Data;

@Data
public class AtomicSliverMap
{
	private final AtomicSliver[] slivers;
	private boolean parallaxGenerated;
	private boolean worldGenerated;

	public AtomicSliverMap()
	{
		parallaxGenerated = false;
		worldGenerated = false;
		slivers = new AtomicSliver[256];

		for(int i = 0; i < 16; i++)
		{
			for(int j = 0; j < 16; j++)
			{
				slivers[i * 16 + j] = new AtomicSliver(i, j);
			}
		}
	}

	public void insert(AtomicSliverMap map)
	{
		for(int i = 0; i < 256; i++)
		{
			slivers[i].insert(map.slivers[i]);
		}
	}

	public void write(OutputStream out) throws IOException
	{
		DataOutputStream dos = new DataOutputStream(out);
		dos.writeBoolean(isParallaxGenerated());
		dos.writeBoolean(isWorldGenerated());
		for(int i = 0; i < 256; i++)
		{
			slivers[i].write(dos);
		}

		dos.flush();
	}

	public void read(InputStream in) throws IOException
	{
		DataInputStream din = new DataInputStream(in);
		parallaxGenerated = din.readBoolean();
		worldGenerated = din.readBoolean();

		for(int i = 0; i < 256; i++)
		{
			try
			{
				slivers[i].read(din);
			}

			catch(Throwable e)
			{

			}
		}
	}

	public AtomicSliver getSliver(int x, int z)
	{
		return slivers[x * 16 + z];
	}

	public void write(ChunkData data, BiomeGrid grid, HeightMap height)
	{
		for(AtomicSliver i : slivers)
		{
			if(i != null)
			{
				i.write(data);
				i.write(grid);
				i.write(height);
			}
		}
	}

	public void inject(ChunkData currentData)
	{
		for(AtomicSliver i : slivers)
		{
			i.inject(currentData);
		}
	}

	public boolean isModified()
	{
		for(AtomicSliver i : slivers)
		{
			if(i.isModified())
			{
				return true;
			}
		}

		return false;
	}

	public void injectUpdates(AtomicSliverMap map)
	{
		for(int i = 0; i < 16; i++)
		{
			for(int j = 0; j < 16; j++)
			{
				getSliver(i, j).inject(map.getSliver(i, j).getUpdatables());
			}	
		}
	}
}
