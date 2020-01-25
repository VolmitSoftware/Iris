package ninja.bytecode.iris.util;

import ninja.bytecode.iris.pack.IrisBiome;
import ninja.bytecode.shuriken.collections.KList;
import ninja.bytecode.shuriken.collections.KMap;

public class ChunkPlan
{
	private final KMap<SChunkVector, Integer> realHeightCache;
	private final KMap<SChunkVector, KList<Integer>> caveHeightCache;
	private final KMap<SChunkVector, Integer> realWaterHeightCache;
	private final KMap<SChunkVector, Double> heightCache;
	private final KMap<SChunkVector, IrisBiome> biomeCache;

	public ChunkPlan()
	{
		this.caveHeightCache = new KMap<>();
		this.realHeightCache = new KMap<>();
		this.realWaterHeightCache = new KMap<>();
		this.heightCache = new KMap<>();
		this.biomeCache = new KMap<>();
	}

	public IrisBiome getBiome(int x, int z)
	{
		return biomeCache.get(new SChunkVector(x, z));
	}

	public void setBiome(int x, int z, IrisBiome cng)
	{
		biomeCache.put(new SChunkVector(x, z), cng);
	}

	public double getHeight(int x, int z)
	{
		SChunkVector c = new SChunkVector(x, z);
		if(hasHeight(c))
		{
			return heightCache.get(c);
		}

		return -1;
	}

	public int getRealHeight(int x, int z)
	{
		SChunkVector c = new SChunkVector(x, z);
		if(realHeightCache.containsKey(c))
		{
			return realHeightCache.get(c);
		}

		return 0;
	}

	public KList<Integer> getCaveHeights(int x, int z)
	{
		SChunkVector c = new SChunkVector(x, z);
		if(caveHeightCache.containsKey(c))
		{
			return caveHeightCache.get(c);
		}

		return null;
	}

	public int getRealWaterHeight(int x, int z)
	{
		SChunkVector c = new SChunkVector(x, z);

		if(realWaterHeightCache.containsKey(c))
		{
			return realWaterHeightCache.get(c);
		}

		return 0;
	}

	public boolean hasHeight(SChunkVector c)
	{
		return heightCache.containsKey(c);
	}

	public boolean hasHeight(int x, int z)
	{
		return hasHeight(new SChunkVector(x, z));
	}

	public void setHeight(SChunkVector c, double h)
	{
		heightCache.put(c, h);
	}

	public void setCaveHeight(SChunkVector c, int h)
	{
		if(!caveHeightCache.containsKey(c))
		{
			caveHeightCache.put(c, new KList<>());
		}

		caveHeightCache.get(c).add(h);
	}

	public void setRealHeight(SChunkVector c, int h)
	{
		realHeightCache.put(c, h);
	}

	public void setRealHeight(int x, int z, int h)
	{
		setRealHeight(new SChunkVector(x, z), h);
	}

	public void setCaveHeight(int x, int z, int h)
	{
		setCaveHeight(new SChunkVector(x, z), h);
	}

	public void setRealWaterHeight(SChunkVector c, int h)
	{
		realWaterHeightCache.put(c, h);
	}

	public void setRealWaterHeight(int x, int z, int h)
	{
		setRealWaterHeight(new SChunkVector(x, z), h);
	}

	public void setHeight(int x, int z, double h)
	{
		setHeight(new SChunkVector(x, z), h);
	}

	public int biomeCacheSize()
	{
		return biomeCache.size();
	}
}
