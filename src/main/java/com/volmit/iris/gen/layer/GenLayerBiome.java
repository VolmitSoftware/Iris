package com.volmit.iris.gen.layer;

import com.volmit.iris.Iris;
import com.volmit.iris.gen.DimensionChunkGenerator;
import com.volmit.iris.object.InferredType;
import com.volmit.iris.object.IrisBiome;
import com.volmit.iris.object.IrisRegion;
import com.volmit.iris.object.IrisRegionRidge;
import com.volmit.iris.object.IrisRegionSpot;
import com.volmit.iris.util.BiomeRarityCellGenerator;
import com.volmit.iris.util.BiomeResult;
import com.volmit.iris.util.CellGenerator;
import com.volmit.iris.util.GenLayer;
import com.volmit.iris.util.KList;
import com.volmit.iris.util.RNG;

import lombok.Data;
import lombok.EqualsAndHashCode;

@Data
@EqualsAndHashCode(callSuper = false)
public class GenLayerBiome extends GenLayer
{
	private CellGenerator regionGenerator;
	private CellGenerator bridgeGenerator;
	private BiomeDataProvider seaProvider;
	private BiomeDataProvider landProvider;
	private BiomeDataProvider shoreProvider;
	private BiomeDataProvider caveProvider;
	private BiomeDataProvider islandProvider;
	private BiomeDataProvider skylandProvider;
	private DimensionChunkGenerator iris;

	public GenLayerBiome(DimensionChunkGenerator iris, RNG rng)
	{
		super(iris, rng);
		this.iris = iris;
		seaProvider = new BiomeDataProvider(this, InferredType.SEA, rng);
		landProvider = new BiomeDataProvider(this, InferredType.LAND, rng);
		shoreProvider = new BiomeDataProvider(this, InferredType.SHORE, rng);
		caveProvider = new BiomeDataProvider(this, InferredType.CAVE, rng);
		islandProvider = new BiomeDataProvider(this, InferredType.ISLAND, rng);
		skylandProvider = new BiomeDataProvider(this, InferredType.SKYLAND, rng);
		regionGenerator = new CellGenerator(rng.nextParallelRNG(1188519));
		bridgeGenerator = new CellGenerator(rng.nextParallelRNG(1541462));
	}

	public IrisRegion getRegion(double bx, double bz)
	{
		if(iris.getDimension().getRegions().isEmpty())
		{
			Iris.error("NO REGIONS!");
			return null;
		}

		regionGenerator.setShuffle(8);
		regionGenerator.setCellScale(0.33 / iris.getDimension().getRegionZoom());
		double x = bx / iris.getDimension().getBiomeZoom();
		double z = bz / iris.getDimension().getBiomeZoom();
		String regionId = iris.getDimension().getRegions().get(regionGenerator.getIndex(x, z, iris.getDimension().getRegions().size()));

		return Iris.data.getRegionLoader().load(regionId);
	}

	public BiomeResult generateData(double bx, double bz, int rawX, int rawZ)
	{
		return generateRegionData(bx, bz, rawX, rawZ, getRegion(bx, bz));
	}

	public BiomeResult generateData(InferredType type, double bx, double bz, int rawX, int rawZ, IrisRegion regionData)
	{
		return getProvider(type).generateData(bx, bz, rawX, rawZ, regionData);
	}

	public BiomeDataProvider getProvider(InferredType type)
	{
		if(type.equals(InferredType.SEA))
		{
			return seaProvider;
		}

		else if(type.equals(InferredType.LAND))
		{
			return landProvider;
		}

		else if(type.equals(InferredType.SHORE))
		{
			return shoreProvider;
		}

		else if(type.equals(InferredType.CAVE))
		{
			return caveProvider;
		}

		else if(type.equals(InferredType.ISLAND))
		{
			return islandProvider;
		}

		else if(type.equals(InferredType.SKYLAND))
		{
			return skylandProvider;
		}

		else
		{
			Iris.error("Cannot find a BiomeDataProvider for type " + type.name());
		}

		return null;
	}

	public BiomeResult generateRegionData(double bx, double bz, int rawX, int rawZ, IrisRegion regionData)
	{
		return generateData(getType(bx, bz, regionData), bx, bz, rawX, rawZ, regionData);
	}

	public InferredType getType(double bx, double bz, IrisRegion regionData)
	{
		bridgeGenerator.setShuffle(0);
		bridgeGenerator.setCellScale(0.33 / iris.getDimension().getContinentZoom());
		double x = bx / iris.getDimension().getBiomeZoom();
		double z = bz / iris.getDimension().getBiomeZoom();
		return bridgeGenerator.getIndex(x, z, 5) == 1 ? InferredType.SEA : InferredType.LAND;
	}

	public BiomeResult generateBiomeData(double bx, double bz, IrisRegion regionData, BiomeRarityCellGenerator cell, KList<IrisBiome> biomes, InferredType inferredType)
	{
		if(biomes.isEmpty())
		{
			return new BiomeResult(null, 0);
		}

		double x = bx / iris.getDimension().getBiomeZoom();
		double z = bz / iris.getDimension().getBiomeZoom();
		IrisBiome biome = cell.get(x, z, biomes);
		biome.setInferredType(inferredType);

		return implode(bx, bz, regionData, cell, new BiomeResult(biome, cell.getDistance(x, z)));
	}

	public BiomeResult generateImpureData(int rawX, int rawZ, InferredType type, IrisRegion regionData, BiomeResult pureResult)
	{
		for(IrisRegionRidge i : regionData.getRidgeBiomes())
		{
			if(i.getType().equals(type) && i.isRidge(rng, rawX, rawZ))
			{
				return new BiomeResult(Iris.data.getBiomeLoader().load(i.getBiome()).infer(i.getAs(), type), 0.5);
			}
		}

		for(IrisRegionSpot i : regionData.getSpotBiomes())
		{
			if(i.getType().equals(type) && i.isSpot(rng, rawX, rawZ))
			{
				return new BiomeResult(Iris.data.getBiomeLoader().load(i.getBiome()).infer(i.getAs(), type), 0.5);
			}
		}

		return pureResult;
	}

	public BiomeResult implode(double bx, double bz, IrisRegion regionData, BiomeRarityCellGenerator parentCell, BiomeResult parent)
	{
		return implode(bx, bz, regionData, parentCell, parent, 1);
	}

	public BiomeResult implode(double bx, double bz, IrisRegion regionData, BiomeRarityCellGenerator parentCell, BiomeResult parent, int hits)
	{
		if(hits > 9)
		{
			return parent;
		}

		double x = bx / iris.getDimension().getBiomeZoom();
		double z = bz / iris.getDimension().getBiomeZoom();

		if(parent.getDistance() > regionData.getBiomeImplosionRatio())
		{
			if(!parent.getBiome().getRealChildren().isEmpty())
			{
				BiomeRarityCellGenerator childCell = parent.getBiome().getChildrenGenerator(rng, 123, parentCell.getCellScale() * parent.getBiome().getChildShrinkFactor());
				KList<IrisBiome> chx = parent.getBiome().getRealChildren().copy();
				chx.add(parent.getBiome());
				IrisBiome biome = childCell.get(x, z, chx);
				biome.setInferredType(parent.getBiome().getInferredType());

				return implode(bx, bz, regionData, childCell, new BiomeResult(biome, childCell.getDistance(x, z)), hits + 1);
			}
		}

		return parent;
	}

	@Override
	public double generate(double x, double z)
	{
		return 0;
	}
}
