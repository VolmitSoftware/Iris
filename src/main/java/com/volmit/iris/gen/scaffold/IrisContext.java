package com.volmit.iris.gen.scaffold;

import com.volmit.iris.object.IrisBiome;
import com.volmit.iris.object.IrisDimension;
import com.volmit.iris.object.IrisRegion;
import com.volmit.iris.util.KMap;

public interface IrisContext
{
	static KMap<TerrainTarget, IrisContext> contexts = new KMap<>();

	public static void pushContext(IrisContext context)
	{
		contexts.put(context.getTarget(), context);
	}

	public static IrisContext of(TerrainTarget world)
	{
		return contexts.get(world);
	}
	
	public TerrainTarget getTarget();

	public IrisBiome getBiome(int x, int z);

	public IrisDimension getDimension();

	public IrisRegion getRegion(int x, int z);

	public IrisMetrics getMetrics();

	public int getHeight(int x, int z);

	public void onHotloaded();
}
