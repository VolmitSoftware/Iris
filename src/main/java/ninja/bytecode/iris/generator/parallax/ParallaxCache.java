package ninja.bytecode.iris.generator.parallax;

import ninja.bytecode.iris.generator.IrisGenerator;
import ninja.bytecode.iris.generator.atomics.AtomicChunkData;
import ninja.bytecode.iris.pack.IrisBiome;
import ninja.bytecode.iris.util.ChunkPlan;
import ninja.bytecode.iris.util.MB;
import ninja.bytecode.iris.util.SMCAVector;
import ninja.bytecode.shuriken.collections.GMap;
import ninja.bytecode.shuriken.collections.GSet;

public class ParallaxCache
{
	private GMap<SMCAVector, ChunkPlan> cachePlan;
	private GMap<SMCAVector, AtomicChunkData> cacheData;
	private GSet<SMCAVector> contains;
	private IrisGenerator gen;

	public ParallaxCache(IrisGenerator gen)
	{
		this.gen = gen;
		cacheData = new GMap<>();
		cachePlan = new GMap<>();
		contains = new GSet<>();
	}

	public MB get(int x, int y, int z)
	{
		SMCAVector s = new SMCAVector(x, z);
		SMCAVector c = new SMCAVector(x >> 4, z >> 4);

		if(contains.contains(s) && cacheData.containsKey(c) && cachePlan.containsKey(c) )
		{
			return cacheData.get(c).getMB(x & 15, y, z & 15);
		}

		createData(x, z, s, c);

		return cacheData.get(c).getMB(x & 15, y, z & 15);
	}

	public IrisBiome getBiome(int x, int z)
	{
		SMCAVector s = new SMCAVector(x, z);
		SMCAVector c = new SMCAVector(x >> 4, z >> 4);

		if(contains.contains(s) && cacheData.containsKey(c) && cachePlan.containsKey(c) )
		{
			return cachePlan.get(c).getBiome(x & 15, z & 15);
		}

		createData(x, z, s, c);

		return cachePlan.get(c).getBiome(x & 15, z & 15);
	}

	public int getWaterHeight(int x, int z)
	{
		SMCAVector s = new SMCAVector(x, z);
		SMCAVector c = new SMCAVector(x >> 4, z >> 4);

		if(contains.contains(s) && cacheData.containsKey(c) && cachePlan.containsKey(c) )
		{
			return cachePlan.get(c).getRealWaterHeight(x & 15, z & 15);
		}

		createData(x, z, s, c);

		return cachePlan.get(c).getRealWaterHeight(x & 15, z & 15);
	}

	public int getHeight(int x, int z)
	{
		SMCAVector s = new SMCAVector(x, z);
		SMCAVector c = new SMCAVector(x >> 4, z >> 4);

		if(contains.contains(s) && cacheData.containsKey(c) && cachePlan.containsKey(c) )
		{
			return cachePlan.get(c).getRealHeight(x & 15, z & 15);
		}

		createData(x, z, s, c);

		return cachePlan.get(c).getRealHeight(x & 15, z & 15);
	}

	private void createData(int x, int z, SMCAVector s, SMCAVector c)
	{
		if(!cacheData.containsKey(c))
		{
			cacheData.put(c, new AtomicChunkData(gen.getWorld()));
		}

		if(!cachePlan.containsKey(c))
		{
			cachePlan.put(c, new ChunkPlan());
		}

		gen.computeAnchor(x, z, cachePlan.get(c), cacheData.get(c));
		contains.add(s);
	}
}
