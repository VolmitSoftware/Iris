package ninja.bytecode.iris.util;

import org.bukkit.Material;

public class MB
{
	public final Material material;
	public final byte data;
	
	public MB(Material material, int data)
	{
		this.material = material;
		this.data = (byte) data;
	}
	
	public MB(Material material)
	{
		this(material, 0);
	}

	public static MB of(Material f)
	{
		return new MB(f);
	}
	
	public static MB of(Material f, int a)
	{
		return new MB(f, a);
	}

	@Override
	public int hashCode()
	{
		final int prime = 31;
		int result = 1;
		result = prime * result + data;
		result = prime * result + ((material == null) ? 0 : material.hashCode());
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
		MB other = (MB) obj;
		if(data != other.data)
			return false;
		if(material != other.material)
			return false;
		return true;
	}
}
