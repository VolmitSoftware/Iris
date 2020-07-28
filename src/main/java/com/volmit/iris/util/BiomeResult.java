package ninja.bytecode.iris.util;

import lombok.Data;
import ninja.bytecode.iris.object.IrisBiome;

@Data
public class BiomeResult
{
	private IrisBiome biome;
	private double distance;

	public BiomeResult(IrisBiome biome, double distance)
	{
		this.biome = biome;
		this.distance = distance;
	}

	public boolean is(BiomeResult r)
	{
		return biome.getName().equals(r.biome.getName());
	}
}