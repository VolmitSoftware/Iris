package com.volmit.iris.gen.layer;

import com.volmit.iris.Iris;
import com.volmit.iris.gen.ContextualTerrainProvider;
import com.volmit.iris.noise.CNG;
import com.volmit.iris.object.InferredType;
import com.volmit.iris.object.IrisBiome;
import com.volmit.iris.object.IrisGeneratorStyle;
import com.volmit.iris.object.IrisRegion;
import com.volmit.iris.util.RNG;

import lombok.Data;
import lombok.NonNull;

@Data
public class BiomeDataProvider
{
	private InferredType type;
	private CNG generator;
	private GenLayerBiome layer;

	public BiomeDataProvider(@NonNull GenLayerBiome layer, @NonNull InferredType type, @NonNull RNG rng)
	{
		this.type = type;
		this.layer = layer;

		IrisGeneratorStyle b = layer.getIris().getDimension().getBiomeStyle(type);

		if(b == null)
		{
			Iris.error("BIOME STYLE IS NULL FOR " + type);
		}

		generator = b.create(rng.nextParallelRNG(4645079 + (type.ordinal() * 23845)));
	}

	public IrisBiome generatePureData(ContextualTerrainProvider g, double bx, double bz, int rawX, int rawZ, IrisRegion regionData)
	{
		return layer.generateBiomeData(bx, bz, regionData, getGenerator(), regionData.getBiomes(g, getType()), getType(), rawX, rawZ, true);
	}

	public IrisBiome generateData(ContextualTerrainProvider g, double bx, double bz, int rawX, int rawZ, IrisRegion regionData)
	{
		return layer.generateBiomeData(bx, bz, regionData, getGenerator(), regionData.getBiomes(g, getType()), getType(), rawX, rawZ, false);
	}
}
