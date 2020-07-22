package ninja.bytecode.iris.legacy;

import java.util.Objects;

public class SChunkVectorShort
{
	private short x;
	private short z;

	public SChunkVectorShort(int x, int z)
	{
		this.x = (short) (x);
		this.z = (short) (z);
	}

	public SChunkVectorShort(short x, short z)
	{
		this.x = x;
		this.z = z;
	}

	public SChunkVectorShort(double x, double z)
	{
		this((int) Math.round(x), (int) Math.round(z));
	}

	public SChunkVectorShort()
	{
		this((short) 0, (short) 0);
	}

	public int getX()
	{
		return x;
	}

	public void setX(int x)
	{
		this.x = (short) x;
	}

	public int getZ()
	{
		return z;
	}

	public void setZ(int z)
	{
		this.z = (short) z;
	}

	@Override
	public int hashCode()
	{
		return Objects.hash(x, z);
	}

	@Override
	public boolean equals(Object obj)
	{
		if(this == obj)
		{
			return true;
		}
		if(!(obj instanceof SChunkVectorShort))
		{
			return false;
		}
		SChunkVectorShort other = (SChunkVectorShort) obj;
		return x == other.x && z == other.z;
	}
}
