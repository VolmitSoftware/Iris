package com.volmit.iris.gen.atomics;

import java.io.DataInputStream;
import java.io.DataOutputStream;
import java.io.IOException;

import org.bukkit.Material;
import org.bukkit.block.Biome;
import org.bukkit.block.data.BlockData;
import org.bukkit.generator.ChunkGenerator.BiomeGrid;
import org.bukkit.generator.ChunkGenerator.ChunkData;

import com.volmit.iris.Iris;
import com.volmit.iris.util.B;
import com.volmit.iris.util.HeightMap;
import com.volmit.iris.util.IrisLock;
import com.volmit.iris.util.KList;
import com.volmit.iris.util.M;

import lombok.Data;

@Data
public class AtomicSliver
{
	public static final BlockData AIR = B.getBlockData("AIR");
	public static boolean forgetful = false;
	private transient Biome[] biome;
	private transient Biome onlyBiome;
	private transient IrisLock lock = new IrisLock("Sliver");
	private transient short highestBiome = 0;
	private transient long last = M.ms();
	private transient final byte x;
	private transient final byte z;
	private transient boolean modified = false;
	private BlockData[] block;
	private KList<Byte> blockUpdates;
	private int highestBlock = 0;

	public AtomicSliver(int x, int z)
	{
		onlyBiome = null;
		this.x = (byte) x;
		this.z = (byte) z;
		blockUpdates = new KList<>(4);
		this.block = new BlockData[256];
		this.biome = new Biome[256];
	}

	public Material getType(int h)
	{
		return getTypeSafe(h);
	}

	public Material getTypeSafe(int h)
	{
		return get(h > 255 ? 255 : Math.max(h, 0)).getMaterial();
	}

	public KList<Byte> getUpdatables()
	{
		return blockUpdates;
	}

	public void update(byte y)
	{
		if(forgetful)
		{
			return;
		}

		blockUpdates.addIfMissing((byte) (y + Byte.MIN_VALUE));
	}

	public void dontUpdate(byte y)
	{
		if(forgetful)
		{
			return;
		}

		blockUpdates.remove(Byte.valueOf((byte) (y + Byte.MIN_VALUE)));
	}

	public BlockData get(int h)
	{
		if(forgetful)
		{
			return null;
		}
		BlockData b = block[h];
		last = M.ms();

		if(b == null)
		{
			return AIR;
		}

		return b;
	}

	public BlockData getOrNull(int h)
	{
		if(forgetful || oob(h))
		{
			return null;
		}
		BlockData b = block[h];
		last = M.ms();

		if(b.getMaterial().equals(Material.AIR))
		{
			return null;
		}

		return b;
	}

	public void set(int h, BlockData d)
	{
		if(forgetful || oob(h))
		{
			return;
		}

		setSilently(h, d);
		modified = true;
		lock.lock();
		highestBlock = Math.max(h, highestBlock);
		lock.unlock();
	}

	public void setSilently(int h, BlockData d)
	{
		if(forgetful)
		{
			return;
		}

		if(d == null)
		{
			return;
		}

		if(oob(h))
		{
			return;
		}

		lock.lock();
		modified = true;
		block[h] = d;

		if(B.isUpdatable(d))
		{
			update((byte) h);
		}

		else
		{
			dontUpdate((byte) h);
		}

		lock.unlock();
	}

	private boolean oob(int h)
	{
		return h > 255 || h < 0;
	}

	public boolean isSolid(int h)
	{
		if(oob(h))
		{
			return false;
		}

		return getType(h).isSolid();
	}

	public void set(int h, Biome d)
	{
		if(oob(h))
		{
			return;
		}

		lock.lock();

		if(Iris.biome3d)
		{
			biome[h] = d;
		}

		else
		{
			onlyBiome = d;
		}

		modified = true;
		highestBiome = (short) (h > highestBiome ? h : highestBiome);
		lock.unlock();
	}

	public void write(ChunkData d, boolean skipNull)
	{
		if(forgetful)
		{
			return;
		}

		lock.lock();

		for(int i = 0; i <= highestBlock; i++)
		{
			if(block[i] == null)
			{
				if(!skipNull)
				{
					d.setBlock(x, i, z, AIR);
				}
			}

			else
			{
				d.setBlock(x, i, z, block[i]);
			}
		}
		lock.unlock();
	}

	@SuppressWarnings("deprecation")
	public void write(BiomeGrid d)
	{
		lock.lock();

		if(!Iris.biome3d)
		{
			d.setBiome(x, z, onlyBiome);
			lock.unlock();
			return;
		}

		for(int i = 0; i <= highestBiome; i++)
		{
			if(biome[i] != null)
			{
				d.setBiome(x, i, z, biome[i]);
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
		this.block = new BlockData[256];

		getUpdatables().clear();
		// Block Palette
		int p = din.readByte() - Byte.MIN_VALUE;
		int h = din.readByte() - Byte.MIN_VALUE;
		int u = din.readByte() - Byte.MIN_VALUE;
		KList<BlockData> palette = new KList<BlockData>();
		highestBlock = h;

		for(int i = 0; i < p; i++)
		{
			palette.add(B.getBlockData(din.readUTF()));
		}

		// Blocks
		for(int i = 0; i <= h; i++)
		{
			block[i] = palette.get(din.readByte() - Byte.MIN_VALUE).clone();
		}

		// Updates
		for(int i = 0; i < u; i++)
		{
			update(din.readByte());
		}

		modified = false;
		lock.unlock();
	}

	public void write(DataOutputStream dos) throws IOException
	{
		if(forgetful)
		{
			return;
		}
		lock.lock();

		// Block Palette
		KList<String> palette = new KList<>();

		for(int i = 0; i <= highestBlock; i++)
		{
			BlockData dat = block[i];
			String d = (dat == null ? AIR : dat).getAsString(true);

			if(!palette.contains(d))
			{
				palette.add(d);
			}
		}

		dos.writeByte(palette.size() + Byte.MIN_VALUE);
		dos.writeByte(highestBlock + Byte.MIN_VALUE);
		dos.writeByte(blockUpdates.size() + Byte.MIN_VALUE);

		for(String i : palette)
		{
			dos.writeUTF(i);
		}

		// Blocks
		for(int i = 0; i <= highestBlock; i++)
		{
			BlockData dat = block[i];
			String d = (dat == null ? AIR : dat).getAsString(true);
			dos.writeByte(palette.indexOf(d) + Byte.MIN_VALUE);
		}

		// Updates
		for(Byte i : getUpdatables())
		{
			dos.writeByte(i);
		}

		lock.unlock();
	}

	public void insert(AtomicSliver atomicSliver)
	{
		if(forgetful)
		{
			return;
		}
		lock.lock();
		for(int i = 0; i < 256; i++)
		{
			if(block[i] == null || block[i].equals(AIR))
			{
				BlockData b = atomicSliver.block[i];
				if(b == null || b.equals(AIR))
				{
					continue;
				}

				block[i] = b;
			}
		}
		lock.unlock();
	}

	public void inject(ChunkData currentData)
	{
		if(forgetful)
		{
			return;
		}
		lock.lock();

		for(int i = 0; i < block.length; i++)
		{
			BlockData b = block[i];
			if(b != null)
			{
				if(b.getMaterial().equals(Material.AIR))
				{
					continue;
				}

				currentData.setBlock(x, i, z, b);
			}
		}

		lock.unlock();
	}

	public boolean isOlderThan(long m)
	{
		if(forgetful)
		{
			return false;
		}

		return M.ms() - last > m;
	}

	public void inject(KList<Byte> updatables)
	{
		if(forgetful)
		{
			return;
		}

		blockUpdates.addAll(updatables);
	}
}
