package com.volmit.iris.util;

import java.io.DataInputStream;
import java.io.DataOutputStream;
import java.io.IOException;

import org.bukkit.Material;
import org.bukkit.block.data.BlockData;
import org.bukkit.generator.ChunkGenerator.ChunkData;

import com.volmit.iris.Iris;
import com.volmit.iris.gen.atomics.AtomicSliver;

public class ParallaxChunk implements Writable
{
	private static final ParallaxSection EMPTY = new ParallaxSection();
	private final ParallaxSection[] sections;
	private boolean parallaxGenerated;
	private boolean worldGenerated;

	public ParallaxChunk(DataInputStream in) throws IOException
	{
		this();
		read(in);
	}

	public ParallaxChunk()
	{
		parallaxGenerated = false;
		worldGenerated = false;
		sections = new ParallaxSection[16];
	}

	public boolean isParallaxGenerated()
	{
		return parallaxGenerated;
	}

	public void setParallaxGenerated(boolean parallaxGenerated)
	{
		this.parallaxGenerated = parallaxGenerated;
	}

	public boolean isWorldGenerated()
	{
		return worldGenerated;
	}

	public void setWorldGenerated(boolean worldGenerated)
	{
		this.worldGenerated = worldGenerated;
	}

	public void export(ChunkData d)
	{
		for(ParallaxSection i : sections)
		{
			if(i != null)
			{
				for(int x = 0; x < 16; x++)
				{
					for(int y = 0; y < 16; y++)
					{
						for(int z = 0; z < 16; z++)
						{
							BlockData b = get(x, y, z);

							if(b == null || b.getMaterial().equals(Material.AIR))
							{
								continue;
							}

							d.setBlock(x, y, z, b);
						}
					}
				}
			}
		}
	}

	public void injectUpdates(AtomicSliver sliver, int x, int z)
	{
		for(Integer i : sliver.getUpdatables())
		{
			if(i > 255 || i < 0)
			{
				Iris.warn("Block Update out of bounds: " + i);
			}

			getSection(i >> 4, true).update(x, i, z);
		}
	}

	@Override
	public void write(DataOutputStream o) throws IOException
	{
		o.writeBoolean(isParallaxGenerated());
		o.writeBoolean(isWorldGenerated());

		for(int i = 15; i > 0; i--)
		{
			ParallaxSection c = sections[i];

			if(c != null)
			{
				o.writeBoolean(true);
				c.write(o);
			}

			else
			{
				o.writeBoolean(false);
			}
		}
	}

	@Override
	public void read(DataInputStream i) throws IOException
	{
		setParallaxGenerated(i.readBoolean());
		setWorldGenerated(i.readBoolean());

		for(int iv = 15; iv > 0; iv--)
		{
			if(i.readBoolean())
			{
				sections[iv] = new ParallaxSection(i);
			}
		}
	}

	public BlockData get(int x, int y, int z)
	{
		return getSection(y >> 4, false).getBlock(x, y & 15, z);
	}

	public void set(int x, int y, int z, BlockData d)
	{
		getSection(y >> 4, true).setBlock(x, y & 15, z, d);
	}

	private final ParallaxSection getSection(int y, boolean create)
	{
		if(sections[y] == null)
		{
			if(create)
			{
				sections[y] = new ParallaxSection();
			}

			return EMPTY;
		}

		return sections[y];
	}
}
