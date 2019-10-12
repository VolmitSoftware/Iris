package ninja.bytecode.iris;

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
}
