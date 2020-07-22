package ninja.bytecode.iris.legacy;

public class SChunkVector
{
	private byte x;
	private byte z;

	public SChunkVector(int x, int z)
	{
		this.x = (byte) (x);
		this.z = (byte) (z);
	}

	public SChunkVector(byte x, byte z)
	{
		this.x = x;
		this.z = z;
	}

	public SChunkVector(double x, double z)
	{
		this((int) Math.round(x), (int) Math.round(z));
	}

	public SChunkVector()
	{
		this((byte) 0, (byte) 0);
	}

	public int getX()
	{
		return x;
	}

	public void setX(int x)
	{
		this.x = (byte) x;
	}

	public int getZ()
	{
		return z;
	}

	public void setZ(int z)
	{
		this.z = (byte) z;
	}

	@Override
	public int hashCode()
	{
		final int prime = 31;
		int result = 1;
		result = prime * result + x;
		result = prime * result + z;
		return result;
	}

	@Override
	public boolean equals(Object obj)
	{
		if(this == obj)
			return true;
		if(obj == null)
			return false;
		if(getClass() != obj.getClass())
			return false;
		SChunkVector other = (SChunkVector) obj;
		if(x != other.x)
			return false;
		if(z != other.z)
			return false;
		return true;
	}
}
