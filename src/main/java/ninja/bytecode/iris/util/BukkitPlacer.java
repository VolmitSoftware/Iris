package ninja.bytecode.iris.util;

import org.bukkit.Location;
import org.bukkit.World;
import org.bukkit.block.Block;

public class BukkitPlacer extends Placer
{
	private final boolean applyPhysics;
	
	public BukkitPlacer(World world, boolean applyPhysics)
	{
		super(world);
		this.applyPhysics = applyPhysics;
	}

	@SuppressWarnings("deprecation")
	@Override
	public MB get(Location l)
	{
		Block b = world.getBlockAt(l);
		return MB.of(b.getType(), b.getData());
	}

	@SuppressWarnings("deprecation")
	@Override
	public void set(Location l, MB mb)
	{
		Block b = world.getBlockAt(l);
		b.setTypeIdAndData(mb.material.getId(), mb.data, applyPhysics);
	}

	@Override
	public int getHighestY(Location l)
	{
		return world.getHighestBlockYAt(l);
	}
}
