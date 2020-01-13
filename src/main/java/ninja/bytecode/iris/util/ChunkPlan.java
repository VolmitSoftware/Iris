package ninja.bytecode.iris.util;

import ninja.bytecode.iris.pack.IrisBiome;
import ninja.bytecode.shuriken.collections.GMap;

public class ChunkPlan
{
	private final GMap<ChunkedVector, Integer> realHeightCache;
	private final GMap<ChunkedVector, Double> heightCache;
	private final GMap<ChunkedVector, IrisBiome> biomeCache;

	public ChunkPlan()
	{
		this.realHeightCache = new GMap<>();
		this.heightCache = new GMap<>();
		this.biomeCache = new GMap<>();
	}

	public IrisBiome getBiome(int x, int z)
	{
		return biomeCache.get(new ChunkedVector(x, z));
	}

	public void setBiome(int x, int z, IrisBiome cng)
	{
		biomeCache.put(new ChunkedVector(x, z), cng);
	}

	public double getHeight(int x, int z)
	{
		ChunkedVector c = new ChunkedVector(x, z);
		if(hasHeight(c))
		{
			return heightCache.get(c);
		}

		return -1;
	}

	public int getRealHeight(int x, int z)
	{
		ChunkedVector c = new ChunkedVector(x, z);
		if(realHeightCache.containsKey(c))
		{
			return realHeightCache.get(c);
		}

		return 0;
	}

	public boolean hasHeight(ChunkedVector c)
	{
		return heightCache.containsKey(c);
	}

	public boolean hasHeight(int x, int z)
	{
		return hasHeight(new ChunkedVector(x, z));
	}

	public void setHeight(ChunkedVector c, double h)
	{
		heightCache.put(c, h);
	}

	public void setRealHeight(ChunkedVector c, int h)
	{
		realHeightCache.put(c, h);
	}

	public void setHeight(int x, int z, double h)
	{
		setHeight(new ChunkedVector(x, z), h);
	}

	public void setRealHeight(int x, int z, int h)
	{
		setRealHeight(new ChunkedVector(x, z), h);
	}
}
