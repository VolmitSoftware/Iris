package com.volmit.iris.gen;

import org.bukkit.Material;
import org.bukkit.block.data.BlockData;

import com.volmit.iris.Iris;
import com.volmit.iris.object.InferredType;
import com.volmit.iris.object.IrisBiome;
import com.volmit.iris.object.IrisDimension;
import com.volmit.iris.object.IrisRegion;
import com.volmit.iris.util.BiomeResult;

import lombok.Data;
import lombok.EqualsAndHashCode;

@Data
@EqualsAndHashCode(callSuper = false)
public abstract class DimensionChunkGenerator extends ContextualChunkGenerator
{
	protected final String dimensionName;
	protected static final BlockData AIR = Material.AIR.createBlockData();
	protected static final BlockData CAVE_AIR = Material.CAVE_AIR.createBlockData();
	protected static final BlockData BEDROCK = Material.BEDROCK.createBlockData();
	protected LitChunkGenerator thisLight;

	public DimensionChunkGenerator(String dimensionName)
	{
		super();
		this.dimensionName = dimensionName;
		thisLight = (LitChunkGenerator) this;
	}

	protected void lit(int x, int y, int z, BlockData d)
	{
		thisLight.queueLight(x, y, z, d);
	}

	public IrisDimension getDimension()
	{
		IrisDimension d = Iris.data.getDimensionLoader().load(dimensionName);

		if(d == null)
		{
			Iris.error("Can't find dimension: " + dimensionName);
		}

		return d;
	}

	protected BiomeResult focus()
	{
		IrisBiome biome = Iris.data.getBiomeLoader().load(getDimension().getFocus());

		for(String i : getDimension().getRegions())
		{
			IrisRegion reg = Iris.data.getRegionLoader().load(i);

			if(reg.getLandBiomes().contains(biome.getLoadKey()))
			{
				biome.setInferredType(InferredType.LAND);
				break;
			}

			if(reg.getSeaBiomes().contains(biome.getLoadKey()))
			{
				biome.setInferredType(InferredType.SEA);
				break;
			}

			if(reg.getShoreBiomes().contains(biome.getLoadKey()))
			{
				biome.setInferredType(InferredType.SHORE);
				break;
			}
		}

		return new BiomeResult(biome, 0);
	}

	public double getModifiedX(int rx, int rz)
	{
		return (getDimension().cosRotate() * rx) + (-getDimension().sinRotate() * rz) +

				getDimension().getCoordFracture(masterRandom, 39392).fitDoubleD(-getDimension().getCoordFractureDistance() / 2, getDimension().getCoordFractureDistance() / 2, rx, rz);
	}

	public double getModifiedZ(int rx, int rz)
	{
		return (getDimension().sinRotate() * rx) + (getDimension().cosRotate() * rz) +

				getDimension().getCoordFracture(masterRandom, 39392).fitDoubleD(-getDimension().getCoordFractureDistance() / 2, getDimension().getCoordFractureDistance() / 2, rx, rz);
	}

	public double getZoomed(double modified)
	{
		return (double) (modified) / getDimension().getTerrainZoom();
	}

	public double getUnzoomed(double modified)
	{
		return (double) (modified) * getDimension().getTerrainZoom();
	}
}
