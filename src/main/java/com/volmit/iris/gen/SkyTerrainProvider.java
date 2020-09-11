package com.volmit.iris.gen;

import com.volmit.iris.gen.scaffold.TerrainChunk;
import com.volmit.iris.gen.scaffold.TerrainTarget;
import com.volmit.iris.util.RNG;

public abstract class SkyTerrainProvider extends PostBlockTerrainProvider
{
	public SkyTerrainProvider(TerrainTarget t, String dimensionName, int threads)
	{
		super(t, dimensionName, threads);
	}

	@Override
	protected void onGenerate(RNG random, int x, int z, TerrainChunk terrain)
	{
		super.onGenerate(random, x, z, terrain);
	}
}
