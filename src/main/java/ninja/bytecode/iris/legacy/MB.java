package ninja.bytecode.iris.legacy;

import org.bukkit.Bukkit;
import org.bukkit.Material;

public class MB
{
	public final Material material;
	public final byte data;

	@SuppressWarnings("deprecation")
	public static MB of(String dat)
	{
		String material = dat;
		byte data = 0;

		if(dat.contains(":"))
		{
			material = dat.split("\\Q:\\E")[0];
			data = Integer.valueOf(dat.split("\\Q:\\E")[1]).byteValue();
		}

		try
		{
			return new MB(Material.getMaterial("LEGACY_" + Integer.valueOf(material)), data);
		}

		catch(Throwable e)
		{

		}

		try
		{
			return new MB(Material.getMaterial(material), data);
		}

		catch(Throwable e)
		{

		}

		return MB.of(Material.AIR);
	}

	public String toString()
	{
		if(data == 0)
		{
			return material.name();
		}

		return material.name() + ":" + data;
	}

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