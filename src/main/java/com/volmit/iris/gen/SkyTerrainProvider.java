package com.volmit.iris.gen;

import com.volmit.iris.gen.scaffold.GeneratedChunk;
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
	protected GeneratedChunk onGenerate(RNG random, int x, int z, TerrainChunk terrain)
	{
		GeneratedChunk gc = super.onGenerate(random, x, z, terrain);
		return gc;
	}
}
