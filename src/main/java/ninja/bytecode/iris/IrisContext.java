package ninja.bytecode.iris;

import org.bukkit.World;

import ninja.bytecode.iris.object.IrisDimension;
import ninja.bytecode.iris.object.IrisRegion;
import ninja.bytecode.iris.util.BiomeResult;
import ninja.bytecode.shuriken.collections.KMap;

public interface IrisContext
{
	static KMap<World, IrisContext> contexts = new KMap<>();

	public static void pushContext(IrisContext context)
	{
		contexts.put(context.getWorld(), context);
	}

	public static IrisContext of(World world)
	{
		return contexts.get(world);
	}

	public BiomeResult getBiome(int x, int z);

	public IrisDimension getDimension();

	public IrisRegion getRegion(int x, int z);

	public IrisMetrics getMetrics();

	public int getHeight(int x, int z);

	public World getWorld();
}
