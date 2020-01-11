package ninja.bytecode.iris.util;

import org.bukkit.Location;
import org.bukkit.World;

public interface IPlacer
{
	public World getWorld();
	
	public MB get(Location l);
	
	public void set(Location l, MB mb);
	
	public int getHighestY(Location l);
}
