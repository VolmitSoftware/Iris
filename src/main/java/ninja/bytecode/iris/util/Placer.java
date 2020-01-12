package ninja.bytecode.iris.util;

import org.bukkit.World;

public abstract class Placer implements IPlacer
{
	protected final World world;
	
	public Placer(World world)
	{
		this.world = world;
	}
	
	@Override
	public World getWorld()
	{
		return world;
	}
	
	public void flush()
	{
		
	}
}
