package ninja.bytecode.iris.pack;

import ninja.bytecode.iris.util.MaxingGenerator.EnumMaxingGenerator;
import ninja.bytecode.shuriken.collections.GList;

public class IrisRegion
{
	private String name;
	private GList<IrisBiome> biomes;
	private EnumMaxingGenerator<IrisBiome> gen;

	public IrisRegion(String name)
	{
		this.name = name;
		this.biomes = new GList<>();
	}

	public EnumMaxingGenerator<IrisBiome> getGen()
	{
		return gen;
	}

	public void setGen(EnumMaxingGenerator<IrisBiome> gen)
	{
		this.gen = gen;
	}

	public String getName()
	{
		return name;
	}

	public void setName(String name)
	{
		this.name = name;
	}

	public GList<IrisBiome> getBiomes()
	{
		return biomes;
	}

	public void setBiomes(GList<IrisBiome> biomes)
	{
		this.biomes = biomes;
	}

	@Override
	public int hashCode()
	{
		final int prime = 31;
		int result = 1;
		result = prime * result + ((biomes == null) ? 0 : biomes.hashCode());
		result = prime * result + ((name == null) ? 0 : name.hashCode());
		return result;
	}

	@Override
	public boolean equals(Object obj)
	{
		if(this == obj)
			return true;
		if(obj == null)
			return false;
		if(getClass() != obj.getClass())
			return false;
		IrisRegion other = (IrisRegion) obj;
		if(biomes == null)
		{
			if(other.biomes != null)
				return false;
		}
		else if(!biomes.equals(other.biomes))
			return false;
		if(name == null)
		{
			if(other.name != null)
				return false;
		}
		else if(!name.equals(other.name))
			return false;
		return true;
	}
}
