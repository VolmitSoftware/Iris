package com.volmit.iris.util;

import com.volmit.iris.object.IrisBiome;

import lombok.Data;

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