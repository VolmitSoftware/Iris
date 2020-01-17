package ninja.bytecode.iris.util;

import java.lang.reflect.Field;
import java.util.concurrent.locks.ReentrantLock;

import org.bukkit.Material;
import org.bukkit.World;
import org.bukkit.craftbukkit.v1_12_R1.generator.CraftChunkData;
import org.bukkit.generator.ChunkGenerator;
import org.bukkit.generator.ChunkGenerator.ChunkData;
import org.bukkit.material.MaterialData;

public final class AtomicChunkData implements ChunkGenerator.ChunkData
{
	private static final Field t;
	private static final Field[] sections;
	private static final int h = 0x1000;
	private final int maxHeight;
	private static ReentrantLock[] locks;
	private char[] s0;
	private char[] s1;
	private char[] s2;
	private char[] s3;
	private char[] s4;
	private char[] s5;
	private char[] s6;
	private char[] s7;
	private char[] s8;
	private char[] s9;
	private char[] s10;
	private char[] s11;
	private char[] s12;
	private char[] s13;
	private char[] s14;
	private char[] s15;
	private char[][] m;
	private World w;

	public AtomicChunkData(World world)
	{
		this.maxHeight = world.getMaxHeight();
		this.w = world;
	}

	@Override
	public int getMaxHeight()
	{
		return maxHeight;
	}

	@SuppressWarnings("deprecation")
	@Override
	public void setBlock(int x, int y, int z, Material material)
	{
		setBlock(x, y, z, material.getId());
	}

	@SuppressWarnings("deprecation")
	@Override
	public void setBlock(int x, int y, int z, MaterialData material)
	{
		setBlock(x, y, z, material.getItemTypeId(), material.getData());
	}

	@SuppressWarnings("deprecation")
	@Override
	public void setRegion(int xMin, int yMin, int zMin, int xMax, int yMax, int zMax, Material material)
	{
		setRegion(xMin, yMin, zMin, xMax, yMax, zMax, material.getId());
	}

	@SuppressWarnings("deprecation")
	@Override
	public void setRegion(int xMin, int yMin, int zMin, int xMax, int yMax, int zMax, MaterialData material)
	{
		setRegion(xMin, yMin, zMin, xMax, yMax, zMax, material.getItemTypeId(), material.getData());
	}

	@SuppressWarnings("deprecation")
	@Override
	public Material getType(int x, int y, int z)
	{
		return Material.getMaterial(getTypeId(x, y, z));
	}

	@SuppressWarnings("deprecation")
	@Override
	public MaterialData getTypeAndData(int x, int y, int z)
	{
		return getType(x, y, z).getNewData(getData(x, y, z));
	}

	@Override
	public void setRegion(int xMin, int yMin, int zMin, int xMax, int yMax, int zMax, int blockId)
	{
		setRegion(xMin, yMin, zMin, xMax, yMax, zMax, blockId, (byte) 0);
	}

	@Override
	public void setRegion(int xMin, int yMin, int zMin, int xMax, int yMax, int zMax, int blockId, int data)
	{
		throw new UnsupportedOperationException("AtomicChunkData does not support setting regions");
	}

	@Override
	public void setBlock(int x, int y, int z, int blockId)
	{
		setBlock(x, y, z, blockId, (byte) 0);
	}

	@Override
	public void setBlock(int x, int y, int z, int blockId, byte data)
	{
		setBlock(x, y, z, (char) (blockId << 4 | data));
	}

	@Override
	public int getTypeId(int x, int y, int z)
	{
		if(x != (x & 0xf) || y < 0 || y >= maxHeight || z != (z & 0xf))
		{
			return 0;
		}

		char[] section = getChunkSection(y, false);

		if(section == null)
		{
			return 0;
		}

		else
		{
			return section[(y & 0xf) << 8 | z << 4 | x] >> 4;
		}
	}

	@Override
	public byte getData(int x, int y, int z)
	{
		if(x != (x & 0xf) || y < 0 || y >= maxHeight || z != (z & 0xf))
		{
			return (byte) 0;
		}

		char[] section = getChunkSection(y, false);

		if(section == null)
		{
			return (byte) 0;
		}

		else
		{
			return (byte) (section[(y & 0xf) << 8 | z << 4 | x] & 0xf);
		}
	}

	private void setBlock(int x, int y, int z, char type)
	{
		if(x != (x & 0xf) || y < 0 || y >= maxHeight || z != (z & 0xf))
		{
			return;
		}

		ReentrantLock l = locks[y >> 4];
		l.lock();
		getChunkSection(y, true)[(y & 0xf) << 8 | z << 4 | x] = type;
		l.unlock();
	}

	private char[] getChunkSection(int y, boolean c)
	{
		try
		{
			int s = y >> 4;
			Field sf = sections[s];
			char[] section = (char[]) sf.get(this);

			if(section == null && c)
			{
				sf.set(this, new char[h]);
				section = (char[]) sf.get(this);
			}

			return section;
		}

		catch(Throwable e)
		{
			e.printStackTrace();
		}

		return null;
	}

	public ChunkData toChunkData()
	{
		ChunkData c = new CraftChunkData(w);

		try
		{
			m = (char[][]) t.get(c);
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
		locks = new ReentrantLock[16];
		Field[] s = new Field[16];

		for(int i = 0; i < 16; i++)
		{
			try
			{
				s[i] = AtomicChunkData.class.getDeclaredField("s" + i);
				locks[i] = new ReentrantLock();
			}

			catch(Throwable e)
			{
				e.printStackTrace();
			}
		}

		sections = s;

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

		t = x;
	}
}