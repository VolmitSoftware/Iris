package com.volmit.iris.generator.legacy.layer;

import com.volmit.iris.Iris;
import com.volmit.iris.generator.legacy.ContextualTerrainProvider;
import com.volmit.iris.generator.noise.CNG;
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
	private double offx = 0;
	private double offz = 0;

	public BiomeDataProvider(@NonNull GenLayerBiome layer, @NonNull InferredType type, @NonNull RNG rng)
	{
		this.type = type;
		this.layer = layer;

		IrisGeneratorStyle b = layer.getIris().getDimension().getBiomeStyle(type);

		if(b == null)
		{
			Iris.error("BIOME STYLE IS NULL FOR " + type);
		}

		generator = b.create(rng.nextParallelRNG((layer.getIris().getDimension().isAggressiveBiomeReshuffle() ? (177 + type.ordinal() + rng.nextParallelRNG(229 - type.ordinal()).nextInt()) : 4645079) + (type.ordinal() * 23845)));

		if(layer.getIris().getDimension().isAggressiveBiomeReshuffle())
		{
			offx += generator.fitDouble(-1000, 1000, 10000, -10000);
			offz += generator.fitDouble(-1000, 1000, -10000, 10000);
		}
	}

	public IrisBiome generatePureData(ContextualTerrainProvider g, double bx, double bz, int rawX, int rawZ, IrisRegion regionData)
	{
		return layer.generateBiomeData(bx + offx, bz + offz, regionData, getGenerator(), regionData.getBiomes(g, getType()), getType(), (int) (rawX + offx), (int) (rawZ + offz), true);
	}

	public IrisBiome generateData(ContextualTerrainProvider g, double bx, double bz, int rawX, int rawZ, IrisRegion regionData)
	{
		return layer.generateBiomeData(bx + offx, bz + offz, regionData, getGenerator(), regionData.getBiomes(g, getType()), getType(), (int) (rawX + offx), (int) (rawZ + offz), false);
	}
}
