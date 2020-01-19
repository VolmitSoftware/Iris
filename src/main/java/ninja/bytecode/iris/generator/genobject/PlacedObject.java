package ninja.bytecode.iris.generator.genobject;

public class PlacedObject
{
	private int x;
	private int y;
	private int z;
	private String f;

	public PlacedObject(int x, int y, int z, String f)
	{
		this.x = x;
		this.y = y;
		this.z = z;
		this.f = f;
	}

	public int getX()
	{
		return x;
	}

	public void setX(int x)
	{
		this.x = x;
	}

	public int getY()
	{
		return y;
	}

	public void setY(int y)
	{
		this.y = y;
	}

	public int getZ()
	{
		return z;
	}

	public void setZ(int z)
	{
		this.z = z;
	}

	public String getF()
	{
		return f;
	}

	public void setF(String f)
	{
		this.f = f;
	}

	@Override
	public int hashCode()
	{
		final int prime = 31;
		int result = 1;
		result = prime * result + ((f == null) ? 0 : f.hashCode());
		result = prime * result + x;
		result = prime * result + y;
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
		PlacedObject other = (PlacedObject) obj;
		if(f == null)
		{
			if(other.f != null)
				return false;
		}
		else if(!f.equals(other.f))
			return false;
		if(x != other.x)
			return false;
		if(y != other.y)
			return false;
		if(z != other.z)
			return false;
		return true;
	}
}
