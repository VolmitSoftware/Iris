package com.volmit.iris.gen.v2;

import org.bukkit.block.Biome;
import org.bukkit.block.data.BlockData;

import com.volmit.iris.gen.v2.scaffold.Hunk;
import com.volmit.iris.manager.IrisDataManager;
import com.volmit.iris.object.IrisDimension;
import com.volmit.iris.util.RNG;

public class IrisGenerator
{
	private long seed;
	private IrisDataManager data;
	private IrisDimension dimension;
	private IrisComplex complex;

	public IrisGenerator(long seed, IrisDimension dimension, IrisDataManager data)
	{
		this.seed = seed;
		flash();
	}

	public void flash()
	{
		complex.flash(seed, dimension, data);
	}

	public void generate(int x, int z, Hunk<BlockData> blocks, Hunk<Biome> biomes)
	{
		RNG rng = new RNG((((long) x) << 32) | (z & 0xffffffffL));

	}
}
