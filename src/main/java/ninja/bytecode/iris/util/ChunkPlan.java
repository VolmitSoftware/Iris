package ninja.bytecode.iris.util;

import java.util.function.Supplier;

import ninja.bytecode.iris.generator.biome.IrisBiome;
import ninja.bytecode.shuriken.collections.GMap;

public class ChunkPlan
{
	private final GMap<ChunkedVector, Double> heightCache;
	private final GMap<ChunkedVector, IrisBiome> biomeCache;
	
	public ChunkPlan()
	{
		this.heightCache = new GMap<ChunkedVector, Double>();
		this.biomeCache = new GMap<ChunkedVector, IrisBiome>();
	}
	
	public IrisBiome getBiome(int x, int z)
	{
		return biomeCache.get(new ChunkedVector(x, z));
	}
	
	public void setBiome(int x, int z, IrisBiome cng)
	{
		biomeCache.put(new ChunkedVector(x, z), cng);
	}
	
	public double getHeight(int x, int z, Supplier<Double> realHeight)
	{
		ChunkedVector c = new ChunkedVector(x, z);
		if(hasHeight(c))
		{
			return heightCache.get(c);
		}
		
		double m = realHeight.get();
		setHeight(c, m);
		return m;
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
	
	public void setHeight(int x, int z, double h)
	{
		setHeight(new ChunkedVector(x, z), h);
	}
}
