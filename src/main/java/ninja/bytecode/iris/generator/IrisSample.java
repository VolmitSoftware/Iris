package ninja.bytecode.iris.generator;

import ninja.bytecode.iris.pack.IrisBiome;
import ninja.bytecode.iris.util.MB;

public class IrisSample
{
	public MB surface;
	public int height;
	public IrisBiome biome;

	public MB getSurface()
	{
		return surface;
	}

	public void setSurface(MB surface)
	{
		this.surface = surface;
	}

	public int getHeight()
	{
		return height;
	}

	public void setHeight(int height)
	{
		this.height = height;
	}

	public IrisBiome getBiome()
	{
		return biome;
	}

	public void setBiome(IrisBiome biome)
	{
		this.biome = biome;
	}

	@Override
	public int hashCode()
	{
		final int prime = 31;
		int result = 1;
		result = prime * result + ((biome == null) ? 0 : biome.hashCode());
		result = prime * result + height;
		result = prime * result + ((surface == null) ? 0 : surface.hashCode());
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
		IrisSample other = (IrisSample) obj;
		if(biome == null)
		{
			if(other.biome != null)
				return false;
		}
		else if(!biome.equals(other.biome))
			return false;
		if(height != other.height)
			return false;
		if(surface == null)
		{
			if(other.surface != null)
				return false;
		}
		else if(!surface.equals(other.surface))
			return false;
		return true;
	}
}
