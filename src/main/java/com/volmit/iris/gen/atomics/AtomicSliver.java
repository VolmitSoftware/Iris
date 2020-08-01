package com.volmit.iris.gen.atomics;

import java.io.DataInputStream;
import java.io.DataOutputStream;
import java.io.IOException;
import java.util.concurrent.locks.ReentrantLock;

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
	private ReentrantLock lock = new ReentrantLock();
	private int highestBlock = 0;
	private int highestBiome = 0;
	private long last = M.ms();
	private int x;
	private int z;
	boolean modified = false;

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

		lock.lock();
		block.put(h, d);
		modified = true;

		if(d.getMaterial().equals(Material.AIR) || d.getMaterial().equals(Material.CAVE_AIR))
		{
			lock.unlock();
			return;
		}

		highestBlock = h > highestBlock ? h : highestBlock;
		lock.unlock();
	}

	public void setSilently(int h, BlockData d)
	{
		if(d == null)
		{
			return;
		}

		lock.lock();
		modified = true;
		block.put(h, d);
		lock.unlock();
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
		lock.lock();
		biome.put(h, d);
		modified = true;
		highestBiome = h > highestBiome ? h : highestBiome;
		lock.unlock();
	}

	public void set(int h, IrisBiome d)
	{
		lock.lock();
		modified = true;
		truebiome.put(h, d);
		lock.unlock();
	}

	public void write(ChunkData d)
	{
		lock.lock();
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
		lock.unlock();
	}

	public void write(BiomeGrid d)
	{
		lock.lock();
		for(int i = 0; i <= highestBiome; i++)
		{
			if(biome.get(i) != null)
			{
				d.setBiome(x, i, z, biome.get(i));
			}
		}
		lock.unlock();
	}

	public void write(HeightMap height)
	{
		lock.lock();
		height.setHeight(x, z, highestBlock);
		lock.unlock();
	}

	public void read(DataInputStream din) throws IOException
	{
		lock.lock();
		this.block = new KMap<Integer, BlockData>();
		int h = din.readByte() - Byte.MIN_VALUE;
		highestBlock = h;

		for(int i = 0; i <= h; i++)
		{
			BlockData v = BlockDataTools.getBlockData(din.readUTF());

			if(v == null)
			{
				block.put(i, AIR);
				continue;
			}

			block.put(i, v);
		}
		modified = false;
		lock.unlock();
	}

	public void write(DataOutputStream dos) throws IOException
	{
		lock.lock();
		dos.writeByte(highestBlock + Byte.MIN_VALUE);

		for(int i = 0; i <= highestBlock; i++)
		{
			BlockData dat = block.get(i);
			dos.writeUTF((dat == null ? AIR : dat).getAsString(true));
		}
		lock.unlock();
	}

	public void insert(AtomicSliver atomicSliver)
	{
		lock.lock();
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
		lock.unlock();
	}

	public void inject(ChunkData currentData)
	{
		lock.lock();
		for(int i = 0; i < 256; i++)
		{
			if(block.get(i) != null && !block.get(i).equals(AIR))
			{
				BlockData b = block.get(i);
				currentData.setBlock(x, i, z, b);
			}
		}
		lock.unlock();
	}

	public boolean isOlderThan(long m)
	{
		return M.ms() - last > m;
	}
}
