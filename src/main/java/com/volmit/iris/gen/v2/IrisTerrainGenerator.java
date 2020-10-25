package com.volmit.iris.gen.v2;

import org.bukkit.block.Biome;
import org.bukkit.block.data.BlockData;

import com.volmit.iris.gen.v2.scaffold.Hunk;
import com.volmit.iris.manager.IrisDataManager;
import com.volmit.iris.object.IrisDimension;

public class IrisTerrainGenerator
{
	private long seed;
	private IrisDataManager data;
	private IrisDimension dimension;
	private IrisComplex complex;

	public IrisTerrainGenerator(long seed, IrisDimension dimension, IrisDataManager data)
	{
		this.seed = seed;
		complex = new IrisComplex();
		this.data = data;
		this.dimension = dimension;
		flash();
	}

	public void flash()
	{
		complex.flash(seed, dimension, data);
	}

	public void generate(int x, int z, Hunk<BlockData> blocks, Hunk<Biome> biomes)
	{
		// RNG rng = new RNG((((long) x) << 32) | (z & 0xffffffffL));
		complex.getHeightStream().fill2D(blocks, x * 16, z * 16, complex.getTerrainStream());
	}
}
