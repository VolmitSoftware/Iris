package com.volmit.iris.gen.layer;

import com.volmit.iris.gen.ContextualChunkGenerator;
import com.volmit.iris.noise.CNG;
import com.volmit.iris.object.InferredType;
import com.volmit.iris.object.IrisBiome;
import com.volmit.iris.object.IrisRegion;
import com.volmit.iris.util.RNG;

import lombok.Data;

@Data
public class BiomeDataProvider
{
	private InferredType type;
	private CNG generator;
	private GenLayerBiome layer;

	public BiomeDataProvider(GenLayerBiome layer, InferredType type, RNG rng)
	{
		this.type = type;
		this.layer = layer;
		generator = layer.getIris().getDimension().getBiomeStyle(type).create(rng.nextParallelRNG(4645079 + (type.ordinal() * 23845)));
	}

	public IrisBiome generatePureData(ContextualChunkGenerator g, double bx, double bz, int rawX, int rawZ, IrisRegion regionData)
	{
		return layer.generateBiomeData(bx, bz, regionData, getGenerator(), regionData.getBiomes(g, getType()), getType(), rawX, rawZ);
	}

	public IrisBiome generateData(ContextualChunkGenerator g, double bx, double bz, int rawX, int rawZ, IrisRegion regionData)
	{
		return layer.generateImpureData(rawX, rawZ, getType(), regionData, generatePureData(g, bx, bz, rawX, rawZ, regionData));
	}
}
