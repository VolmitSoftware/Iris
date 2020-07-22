package ninja.bytecode.iris.legacy;

import java.util.Objects;

public class SMCAVector
{
	private int x;
	private int z;

	public SMCAVector(int x, int z)
	{
		this.x = x;
		this.z = z;
	}

	public SMCAVector()
	{
		this(0, 0);
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
		if(!(obj instanceof SMCAVector))
		{
			return false;
		}
		SMCAVector other = (SMCAVector) obj;
		return x == other.x && z == other.z;
	}
}
