package com.volmit.iris.util;

import com.volmit.iris.object.IrisBiome;
import com.volmit.iris.object.IrisBiomePaletteLayer;

import lombok.Data;

@Data
public class BiomeResult
{
	private IrisBiome biome;
	private IrisBiomePaletteLayer air;
	private double heightOffset;
	private double distance;

	public BiomeResult(IrisBiome biome, double distance)
	{
		this.biome = biome;
		this.distance = distance;
		this.heightOffset = 0;
	}

	public BiomeResult(IrisBiome biome, double distance, double height, IrisBiomePaletteLayer air)
	{
		this.biome = biome;
		this.distance = distance;
		this.heightOffset = height;
		this.air = air;
	}

	public boolean is(BiomeResult r)
	{
		return biome.getName().equals(r.biome.getName());
	}
}