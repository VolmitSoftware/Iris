package ninja.bytecode.iris.util;

import java.util.function.Supplier;

import org.bukkit.util.BlockVector;

import ninja.bytecode.iris.schematic.Schematic;
import ninja.bytecode.iris.spec.IrisBiome;
import ninja.bytecode.shuriken.collections.GMap;

public class ChunkPlan
{
	private final GMap<ChunkedVector, Double> heightCache;
	private final GMap<ChunkedVector, IrisBiome> biomeCache;
	private final GMap<BlockVector, Schematic> schematics;
	
	public ChunkPlan()
	{
		this.schematics = new GMap<>();
		this.heightCache = new GMap<>();
		this.biomeCache = new GMap<>();
	}
	
	public void planSchematic(BlockVector b, Schematic s)
	{
		schematics.put(b, s);
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
