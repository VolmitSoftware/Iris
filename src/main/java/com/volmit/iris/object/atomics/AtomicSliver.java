package com.volmit.iris.object.atomics;

import java.io.DataInputStream;
import java.io.DataOutputStream;
import java.io.IOException;

import org.bukkit.Material;
import org.bukkit.block.Biome;
import org.bukkit.block.data.BlockData;
import org.bukkit.generator.ChunkGenerator.BiomeGrid;
import org.bukkit.generator.ChunkGenerator.ChunkData;

import com.volmit.iris.object.IrisBiome;
import com.volmit.iris.util.BlockDataTools;
import com.volmit.iris.util.HeightMap;
import com.volmit.iris.util.KMap;
import com.volmit.iris.util.M;

import lombok.Data;

@Data
public class AtomicSliver
{
	public static final BlockData AIR = BlockDataTools.getBlockData("AIR");
	private KMap<Integer, BlockData> block;
	private KMap<Integer, IrisBiome> truebiome;
	private KMap<Integer, Biome> biome;
	private int highestBlock = 0;
	private int highestBiome = 0;
	private long last = M.ms();
	private int x;
	private int z;

	public AtomicSliver(int x, int z)
	{
		this.x = x;
		this.z = z;
		this.block = new KMap<>();
		this.biome = new KMap<>();
		this.truebiome = new KMap<>();
	}

	public Material getType(int h)
	{
		return get(h).getMaterial();
	}

	public BlockData get(int h)
	{
		BlockData b = block.get(h);
		last = M.ms();

		if(b == null)
		{
			return AIR;
		}

		return b;
	}

	public void set(int h, BlockData d)
	{
		if(d == null)
		{
			return;
		}

		block.put(h, d);
		highestBlock = h > highestBlock ? h : highestBlock;
	}

	public void setSilently(int h, BlockData d)
	{
		if(d == null)
		{
			return;
		}

		block.put(h, d);
	}

	public boolean isSolid(int h)
	{
		return getType(h).isSolid();
	}

	public Biome getBiome(int h)
	{
		last = M.ms();
		return biome.containsKey(h) ? biome.get(h) : Biome.THE_VOID;
	}

	public IrisBiome getTrueBiome(int h)
	{
		last = M.ms();
		return truebiome.get(h);
	}

	public void set(int h, Biome d)
	{
		biome.put(h, d);
		highestBiome = h > highestBiome ? h : highestBiome;
	}

	public void set(int h, IrisBiome d)
	{
		truebiome.put(h, d);
	}

	public void write(ChunkData d)
	{
		for(int i = 0; i <= highestBlock; i++)
		{
			if(block.get(i) == null)
			{
				d.setBlock(x, i, z, AIR);
			}

			else
			{
				d.setBlock(x, i, z, block.get(i));
			}
		}
	}

	public void write(BiomeGrid d)
	{
		for(int i = 0; i <= highestBiome; i++)
		{
			if(biome.get(i) != null)
			{
				d.setBiome(x, i, z, biome.get(i));
			}
		}
	}

	public void write(HeightMap height)
	{
		height.setHeight(x, z, highestBlock);
	}

	public void read(DataInputStream din) throws IOException
	{
		this.block = new KMap<Integer, BlockData>();
		int h = din.readByte() - Byte.MIN_VALUE;
		highestBlock = h;

		for(int i = 0; i <= h; i++)
		{
			block.put(i, BlockDataTools.getBlockData(din.readUTF()));
		}
	}

	public void write(DataOutputStream dos) throws IOException
	{
		dos.writeByte(highestBlock + Byte.MIN_VALUE);

		for(int i = 0; i <= highestBlock; i++)
		{
			BlockData dat = block.get(i);
			dos.writeUTF((dat == null ? AIR : dat).getAsString(true));
		}
	}

	public void insert(AtomicSliver atomicSliver)
	{
		for(int i = 0; i < 256; i++)
		{
			if(block.get(i) == null || block.get(i).equals(AIR))
			{
				BlockData b = atomicSliver.block.get(i);
				if(b == null || b.equals(AIR))
				{
					continue;
				}

				block.put(i, b);
			}
		}
	}

	public void inject(ChunkData currentData)
	{
		for(int i = 0; i < 256; i++)
		{
			if(block.get(i) != null && !block.get(i).equals(AIR))
			{
				BlockData b = block.get(i);
				currentData.setBlock(x, i, z, b);
			}
		}
	}

	public boolean isOlderThan(long m)
	{
		return M.ms() - last > m;
	}
}
