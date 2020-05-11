package ninja.bytecode.iris.object.atomics;

import java.io.DataInputStream;
import java.io.DataOutputStream;
import java.io.IOException;

import org.bukkit.block.Biome;
import org.bukkit.block.data.BlockData;
import org.bukkit.generator.ChunkGenerator.BiomeGrid;
import org.bukkit.generator.ChunkGenerator.ChunkData;

import lombok.Data;
import ninja.bytecode.iris.util.BlockDataTools;
import ninja.bytecode.iris.util.HeightMap;

@Data
public class AtomicSliver
{
	private static final BlockData AIR = BlockDataTools.getBlockData("AIR");
	private BlockData[] block;
	private Biome[] biome;
	private int highestBlock = 0;
	private int highestBiome = 0;
	private int x;
	private int z;

	public AtomicSliver(int x, int z)
	{
		this.x = x;
		this.z = z;
		this.block = new BlockData[256];
		this.biome = new Biome[256];
	}

	public void set(int h, BlockData d)
	{
		block[h] = d;
		highestBlock = h > highestBlock ? h : highestBlock;
	}

	public void set(int h, Biome d)
	{
		biome[h] = d;
		highestBiome = h > highestBiome ? h : highestBiome;
	}

	public void write(ChunkData d)
	{
		for(int i = 0; i <= highestBlock; i++)
		{
			if(block[i] == null)
			{
				d.setBlock(x, i, z, AIR);
			}

			else
			{
				d.setBlock(x, i, z, block[i]);
			}
		}
	}

	public void write(BiomeGrid d)
	{
		for(int i = 0; i <= highestBiome; i++)
		{
			d.setBiome(x, i, z, biome[i]);
		}
	}

	public void write(HeightMap height)
	{
		height.setHeight(x, z, highestBlock);
	}

	public void read(DataInputStream din) throws IOException
	{
		this.block = new BlockData[256];
		int h = din.readByte() - Byte.MIN_VALUE;
		for(int i = 0; i <= h; i++)
		{
			block[i] = BlockDataTools.getBlockData(din.readUTF());
		}
	}

	public void write(DataOutputStream dos) throws IOException
	{
		dos.writeByte(highestBlock + Byte.MIN_VALUE);

		for(int i = 0; i <= highestBlock; i++)
		{
			dos.writeUTF(block[i].getAsString(true));
		}
	}

	public void insert(AtomicSliver atomicSliver)
	{
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
	}
}
