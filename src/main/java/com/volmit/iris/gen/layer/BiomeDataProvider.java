package com.volmit.iris.gen.layer;

import com.volmit.iris.gen.ContextualChunkGenerator;
import com.volmit.iris.noise.RarityCellGenerator;
import com.volmit.iris.object.InferredType;
import com.volmit.iris.object.IrisBiome;
import com.volmit.iris.object.IrisRegion;
import com.volmit.iris.util.BiomeResult;
import com.volmit.iris.util.RNG;

import lombok.Data;

@Data
public class BiomeDataProvider
{
	private InferredType type;
	private RarityCellGenerator<IrisBiome> generator;
	private GenLayerBiome layer;

	public BiomeDataProvider(GenLayerBiome layer, InferredType type, RNG rng)
	{
		this.type = type;
		this.layer = layer;
		generator = new RarityCellGenerator<IrisBiome>(rng.nextParallelRNG(4645079 + (type.ordinal() * 23845)));
	}

	public BiomeResult generatePureData(ContextualChunkGenerator g, double bx, double bz, int rawX, int rawZ, IrisRegion regionData)
	{
		getGenerator().setShuffle(regionData.getBiomeShuffle());
		return layer.generateBiomeData(bx, bz, regionData, getGenerator(), regionData.getBiomes(g, getType()), getType());
	}

	public BiomeResult generateData(ContextualChunkGenerator g, double bx, double bz, int rawX, int rawZ, IrisRegion regionData)
	{
		return layer.generateImpureData(rawX, rawZ, getType(), regionData, generatePureData(g, bx, bz, rawX, rawZ, regionData));
	}
}
