package ninja.bytecode.iris.generator.placer;

import org.bukkit.Location;

import ninja.bytecode.iris.generator.IrisGenerator;
import ninja.bytecode.iris.generator.parallax.ParallaxCache;
import ninja.bytecode.iris.util.IrisWorldData;
import ninja.bytecode.iris.util.MB;
import ninja.bytecode.iris.util.Placer;

public class AtomicParallaxPlacer extends Placer
{
	private IrisWorldData data;
	private ParallaxCache cache;

	public AtomicParallaxPlacer(IrisGenerator g, ParallaxCache cache)
	{
		super(g.getWorld());
		this.data = g.getWorldData();
		this.cache = cache;
	}

	@Override
	public MB get(Location l)
	{
		return cache.get(l.getBlockX(), l.getBlockY(), l.getBlockZ());
	}

	@SuppressWarnings("deprecation")
	@Override
	public void set(Location l, MB mb)
	{
		data.setBlock(l.getBlockX(), l.getBlockY(), l.getBlockZ(), mb.material.getId(), mb.data);
	}

	@Override
	public int getHighestY(Location l)
	{
		return cache.getHeight(l.getBlockX(), l.getBlockZ());
	}

	@Override
	public int getHighestYUnderwater(Location l)
	{
		return cache.getWaterHeight(l.getBlockX(), l.getBlockZ());
	}
}
