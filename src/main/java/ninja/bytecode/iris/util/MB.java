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
}
