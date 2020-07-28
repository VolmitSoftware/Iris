package com.volmit.iris;

import org.bukkit.World;

import com.volmit.iris.object.IrisDimension;
import com.volmit.iris.object.IrisRegion;
import com.volmit.iris.util.BiomeResult;
import com.volmit.iris.util.KMap;

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

	public void onHotloaded();
}
