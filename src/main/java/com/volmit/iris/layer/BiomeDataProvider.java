package com.volmit.iris.layer;

import com.volmit.iris.object.InferredType;
import com.volmit.iris.object.IrisRegion;
import com.volmit.iris.util.BiomeResult;
import com.volmit.iris.util.CellGenerator;
import com.volmit.iris.util.RNG;

import lombok.Data;

@Data
public class BiomeDataProvider
{
	private InferredType type;
	private CellGenerator generator;
	private GenLayerBiome layer;

	public BiomeDataProvider(GenLayerBiome layer, InferredType type, RNG rng)
	{
		this.type = type;
		this.layer = layer;
		generator = new CellGenerator(rng.nextParallelRNG(4645079 + (type.ordinal() * 23845)));
	}

	public BiomeResult generatePureData(double bx, double bz, int rawX, int rawZ, IrisRegion regionData)
	{
		getGenerator().setShuffle(12);
		double zoom = (layer.getIris().getDimension().getBiomeZoom() * regionData.getBiomeZoom(getType())) * 3.15;
		getGenerator().setCellScale(1D / zoom);
		return layer.generateBiomeData(bx, bz, regionData, getGenerator(), regionData.getBiomes(getType()), getType());
	}

	public BiomeResult generateData(double bx, double bz, int rawX, int rawZ, IrisRegion regionData)
	{
		return layer.generateImpureData(rawX, rawZ, getType(), regionData, generatePureData(bx, bz, rawX, rawZ, regionData));
	}
}
