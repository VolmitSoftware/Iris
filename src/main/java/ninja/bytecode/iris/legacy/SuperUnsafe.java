package ninja.bytecode.iris.legacy;

import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.block.data.BlockData;

@SuppressWarnings("deprecation")
public class SuperUnsafe
{
	public static Material getMaterial(String material, byte d)
	{
		return Bukkit.getUnsafe().getMaterial(material, d);
	}

	public static BlockData getBlockData(String material, byte d)
	{
		return Bukkit.getUnsafe().fromLegacy(Bukkit.getUnsafe().toLegacy(getMaterial(material, d)), d);
	}
}
