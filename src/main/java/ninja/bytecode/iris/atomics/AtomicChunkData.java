package ninja.bytecode.iris.atomics;

import java.lang.reflect.Field;
import java.util.concurrent.locks.ReentrantLock;

import org.bukkit.Material;
import org.bukkit.World;
import org.bukkit.block.data.BlockData;
import org.bukkit.craftbukkit.v1_14_R1.block.data.CraftBlockData;
import org.bukkit.craftbukkit.v1_14_R1.generator.CraftChunkData;
import org.bukkit.craftbukkit.v1_14_R1.util.CraftMagicNumbers;
import org.bukkit.generator.ChunkGenerator;
import org.bukkit.generator.ChunkGenerator.ChunkData;
import org.bukkit.material.MaterialData;

import net.minecraft.server.v1_14_R1.Blocks;
import net.minecraft.server.v1_14_R1.ChunkSection;
import net.minecraft.server.v1_14_R1.IBlockData;

public final class AtomicChunkData implements ChunkGenerator.ChunkData
{
	private static final Field t;
	private static final int h = 0x1000;
	private static final Field[] f = new Field[16];
	private final int maxHeight;
	private final ReentrantLock[] locks = makeLocks();
	private ChunkSection s0;
	private ChunkSection s1;
	private ChunkSection s2;
	private ChunkSection s3;
	private ChunkSection s4;
	private ChunkSection s5;
	private ChunkSection s6;
	private ChunkSection s7;
	private ChunkSection s8;
	private ChunkSection s9;
	private ChunkSection s10;
	private ChunkSection s11;
	private ChunkSection s12;
	private ChunkSection s13;
	private ChunkSection s14;
	private ChunkSection s15;
	private ChunkSection[] m;
	private World w;

	public AtomicChunkData(World world)
	{
		this.maxHeight = world.getMaxHeight();
		this.w = world;
	}

	private ReentrantLock[] makeLocks()
	{
		ReentrantLock[] f = new ReentrantLock[16];

		for(int i = 0; i < 16; i++)
		{
			f[i] = new ReentrantLock();
		}

		return f;
	}

	private ChunkSection ofSection(int y, boolean c)
	{
		int s = y >> 4;

		try
		{
			locks[s].lock();
			ChunkSection v = (ChunkSection) f[s].get(this);

			if(v == null)
			{
				v = new ChunkSection(y);
				f[s].set(this, v);
			}

			locks[s].unlock();
			return v;
		}

		catch(Throwable e)
		{
			e.printStackTrace();
		}

		return null;
		//@done
	}

	public ChunkData toChunkData()
	{
		ChunkData c = new CraftChunkData(w);

		try
		{
			m = (ChunkSection[]) t.get(c);
			m[0] = s0;
			m[1] = s1;
			m[2] = s2;
			m[3] = s3;
			m[4] = s4;
			m[5] = s5;
			m[6] = s6;
			m[7] = s7;
			m[8] = s8;
			m[9] = s9;
			m[10] = s10;
			m[11] = s11;
			m[12] = s12;
			m[13] = s13;
			m[14] = s14;
			m[15] = s15;
		}

		catch(IllegalArgumentException | IllegalAccessException e)
		{
			e.printStackTrace();
		}

		return c;
	}

	static
	{
		Field x = null;

		try
		{
			x = CraftChunkData.class.getDeclaredField("sections");
			x.setAccessible(true);
		}

		catch(Throwable e)
		{
			e.printStackTrace();
		}

		for(int i = 0; i < 16; i++)
		{
			try
			{
				Field g = AtomicChunkData.class.getDeclaredField("s" + i);
				g.setAccessible(true);
				f[i] = g;
			}

			catch(Throwable e)
			{
				e.printStackTrace();
			}
		}

		t = x;
	}

	public int getMaxHeight()
	{
		return this.maxHeight;
	}

	public void setBlock(int x, int y, int z, Material material)
	{
		this.setBlock(x, y, z, material.createBlockData());
	}

	public void setBlock(int x, int y, int z, MaterialData material)
	{
		this.setBlock(x, y, z, CraftMagicNumbers.getBlock(material));
	}

	public void setBlock(int x, int y, int z, BlockData blockData)
	{
		this.setBlock(x, y, z, ((CraftBlockData) blockData).getState());
	}

	public void setRegion(int xMin, int yMin, int zMin, int xMax, int yMax, int zMax, Material material)
	{
		this.setRegion(xMin, yMin, zMin, xMax, yMax, zMax, material.createBlockData());
	}

	public void setRegion(int xMin, int yMin, int zMin, int xMax, int yMax, int zMax, MaterialData material)
	{
		this.setRegion(xMin, yMin, zMin, xMax, yMax, zMax, CraftMagicNumbers.getBlock(material));
	}

	public void setRegion(int xMin, int yMin, int zMin, int xMax, int yMax, int zMax, BlockData blockData)
	{
		this.setRegion(xMin, yMin, zMin, xMax, yMax, zMax, ((CraftBlockData) blockData).getState());
	}

	public Material getType(int x, int y, int z)
	{
		return CraftMagicNumbers.getMaterial(this.getTypeId(x, y, z).getBlock());
	}

	public MaterialData getTypeAndData(int x, int y, int z)
	{
		return CraftMagicNumbers.getMaterial(this.getTypeId(x, y, z));
	}

	public BlockData getBlockData(int x, int y, int z)
	{
		return CraftBlockData.fromData(this.getTypeId(x, y, z));
	}

	public void setRegion(int xMin, int yMin, int zMin, int xMax, int yMax, int zMax, IBlockData type)
	{
		throw new RuntimeException("Not Supported!");
	}

	public IBlockData getTypeId(int x, int y, int z)
	{
		if(x == (x & 15) && y >= 0 && y < this.maxHeight && z == (z & 15))
		{
			ChunkSection section = ofSection(y, false);
			return section == null ? Blocks.AIR.getBlockData() : section.getType(x, y & 15, z);
		}
		else
		{
			return Blocks.AIR.getBlockData();
		}
	}

	public byte getData(int x, int y, int z)
	{
		return CraftMagicNumbers.toLegacyData(this.getTypeId(x, y, z));
	}

	private void setBlock(int x, int y, int z, IBlockData type)
	{
		if(x == (x & 15) && y >= 0 && y < this.maxHeight && z == (z & 15))
		{
			ChunkSection section = ofSection(y, true);
			section.setType(x, y & 15, z, type);
		}
	}
}