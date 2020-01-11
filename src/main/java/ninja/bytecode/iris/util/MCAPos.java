package ninja.bytecode.iris.util;

public class MCAPos
{
	private int x;
	private int z;

	public static MCAPos fromChunk(int x, int z)
	{
		return new MCAPos(x >> 5, z >> 5);
	}

	public MCAPos(String name)
	{
		fromFileName(name);
	}

	public MCAPos(int x, int z)
	{
		this.x = x;
		this.z = z;
	}

	public void fromFileName(String n)
	{
		String[] f = n.split("\\Q.\\E");
		x = Integer.valueOf(f[0]);
		z = Integer.valueOf(f[1]);
	}

	public String toFileName()
	{
		return x + "." + z + ".imc";
	}

	public int getX()
	{
		return x;
	}

	public void setX(int x)
	{
		this.x = x;
	}

	public int getZ()
	{
		return z;
	}

	public void setZ(int z)
	{
		this.z = z;
	}

	public int getMinChunkX()
	{
		return x << 5;
	}

	public int getMinChunkZ()
	{
		return z << 5;
	}

	public int getMaxChunkX()
	{
		return getMinChunkX() + 32;
	}

	public int getMaxChunkZ()
	{
		return getMinChunkZ() + 32;
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
		MCAPos other = (MCAPos) obj;
		if(x != other.x)
			return false;
		if(z != other.z)
			return false;
		return true;
	}
}
