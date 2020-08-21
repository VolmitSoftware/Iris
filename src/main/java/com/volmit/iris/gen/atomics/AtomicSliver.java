package com.volmit.iris.gen.atomics;

import java.io.DataInputStream;
import java.io.DataOutputStream;
import java.io.IOException;

import org.bukkit.Material;
import org.bukkit.block.Biome;
import org.bukkit.block.data.BlockData;
import org.bukkit.generator.ChunkGenerator.BiomeGrid;
import org.bukkit.generator.ChunkGenerator.ChunkData;

import com.volmit.iris.object.IrisBiome;
import com.volmit.iris.util.B;
import com.volmit.iris.util.HeightMap;
import com.volmit.iris.util.IrisLock;
import com.volmit.iris.util.KList;
import com.volmit.iris.util.KMap;
import com.volmit.iris.util.KSet;
import com.volmit.iris.util.M;

import lombok.Data;

@Data
public class AtomicSliver
{
	public static final BlockData AIR = B.getBlockData("AIR");
	private KMap<Integer, BlockData> block;
	private KMap<Integer, IrisBiome> truebiome;
	private KMap<Integer, Biome> biome;
	private KSet<Integer> update;
	private IrisLock lock = new IrisLock("Sliver");
	private int highestBlock = 0;
	private int highestBiome = 0;
	private long last = M.ms();
	private int x;
	private int z;
	boolean modified = false;

	public AtomicSliver(int x, int z)
	{
		lock.setDisabled(true);
		this.x = x;
		this.z = z;
		update = new KSet<>();
		this.block = new KMap<>();
		this.biome = new KMap<>();
		this.truebiome = new KMap<>();
	}

	public Material getType(int h)
	{
		return get(h).getMaterial();
	}

	public KSet<Integer> getUpdatables()
	{
		return update;
	}

	public void update(int y)
	{
		update.add(y);
	}

	public void dontUpdate(int y)
	{
		update.remove(y);
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
		setSilently(h, d);
		modified = true;
		lock.lock();
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

		if(B.isLit(d))
		{
			update(h);
		}

		else
		{
			dontUpdate(h);
		}

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
		int p = din.readByte() - Byte.MIN_VALUE;
		int u = din.readByte() - Byte.MIN_VALUE;
		KList<BlockData> palette = new KList<BlockData>();
		getUpdatables().clear();
		highestBlock = h;

		for(int i = 0; i < p; i++)
		{
			palette.add(B.getBlockData(din.readUTF()));
		}

		for(int i = 0; i <= h; i++)
		{
			block.put(i, palette.get(din.readByte() - Byte.MIN_VALUE).clone());
		}

		for(int i = 0; i <= u; i++)
		{
			update(din.readByte() - Byte.MIN_VALUE);
		}

		modified = false;
		lock.unlock();
	}

	public void write(DataOutputStream dos) throws IOException
	{
		lock.lock();
		dos.writeByte(highestBlock + Byte.MIN_VALUE);
		KList<String> palette = new KList<>();

		for(int i = 0; i <= highestBlock; i++)
		{
			BlockData dat = block.get(i);
			String d = (dat == null ? AIR : dat).getAsString(true);

			if(!palette.contains(d))
			{
				palette.add(d);
			}
		}

		dos.writeByte(palette.size() + Byte.MIN_VALUE);
		dos.writeByte(update.size() + Byte.MIN_VALUE);

		for(String i : palette)
		{
			dos.writeUTF(i);
		}

		for(int i = 0; i <= highestBlock; i++)
		{
			BlockData dat = block.get(i);
			String d = (dat == null ? AIR : dat).getAsString(true);
			dos.writeByte(palette.indexOf(d) + Byte.MIN_VALUE);
		}

		for(Integer i : getUpdatables())
		{
			dos.writeByte(i + Byte.MIN_VALUE);
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
