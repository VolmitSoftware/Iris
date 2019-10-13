package ninja.bytecode.iris.atomics;

import java.lang.reflect.Field;

import org.bukkit.Material;
import org.bukkit.World;
import org.bukkit.craftbukkit.v1_12_R1.generator.CraftChunkData;
import org.bukkit.generator.ChunkGenerator;
import org.bukkit.generator.ChunkGenerator.ChunkData;
import org.bukkit.material.MaterialData;

public final class AtomicChunkData implements ChunkGenerator.ChunkData
{
	private static final Field t;
	private static final int h = 0x1000;
	private final int maxHeight;
	private volatile char[] s0;
	private volatile char[] s1;
	private volatile char[] s2;
	private volatile char[] s3;
	private volatile char[] s4;
	private volatile char[] s5;
	private volatile char[] s6;
	private volatile char[] s7;
	private volatile char[] s8;
	private volatile char[] s9;
	private volatile char[] sA;
	private volatile char[] sB;
	private volatile char[] sC;
	private volatile char[] sD;
	private volatile char[] sE;
	private volatile char[] sF;
	private volatile char[][] m;
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

		getChunkSection(y, true)[(y & 0xf) << 8 | z << 4 | x] = type;
	}

	private char[] getChunkSection(int y, boolean c)
	{
		//@done
		int s = y >> 4;

		if(s == 0)
		{
			if(c && s0 == null)
			{
				s0 = new char[h];
			}
			return s0;
		}
		else if(s == 1)
		{
			if(c && s1 == null)
			{
				s1 = new char[h];
			}
			return s1;
		}
		else if(s == 2)
		{
			if(c && s2 == null)
			{
				s2 = new char[h];
			}
			return s2;
		}
		else if(s == 3)
		{
			if(c && s3 == null)
			{
				s3 = new char[h];
			}
			return s3;
		}
		else if(s == 4)
		{
			if(c && s4 == null)
			{
				s4 = new char[h];
			}
			return s4;
		}
		else if(s == 5)
		{
			if(c && s5 == null)
			{
				s5 = new char[h];
			}
			return s5;
		}
		else if(s == 6)
		{
			if(c && s6 == null)
			{
				s6 = new char[h];
			}
			return s6;
		}
		else if(s == 7)
		{
			if(c && s7 == null)
			{
				s7 = new char[h];
			}
			return s7;
		}
		else if(s == 8)
		{
			if(c && s8 == null)
			{
				s8 = new char[h];
			}
			return s8;
		}
		else if(s == 9)
		{
			if(c && s9 == null)
			{
				s9 = new char[h];
			}
			return s9;
		}
		else if(s == 10)
		{
			if(c && sA == null)
			{
				sA = new char[h];
			}
			return sA;
		}
		else if(s == 11)
		{
			if(c && sB == null)
			{
				sB = new char[h];
			}
			return sB;
		}
		else if(s == 12)
		{
			if(c && sC == null)
			{
				sC = new char[h];
			}
			return sC;
		}
		else if(s == 13)
		{
			if(c && sD == null)
			{
				sD = new char[h];
			}
			return sD;
		}
		else if(s == 14)
		{
			if(c && sE == null)
			{
				sE = new char[h];
			}
			return sE;
		}
		else if(s == 15)
		{
			if(c && sF == null)
			{
				sF = new char[h];
			}
			return sF;
		}

		else
		{
			System.out.print("CANT FIND SECTION: " + s);
		}
		
		return null;
		//@done
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
			m[10] = sA;
			m[11] = sB;
			m[12] = sC;
			m[13] = sD;
			m[14] = sE;
			m[15] = sF;
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

		t = x;
	}
}