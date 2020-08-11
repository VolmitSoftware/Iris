package com.volmit.iris.gen.layer;

import com.volmit.iris.Iris;
import com.volmit.iris.gen.DimensionChunkGenerator;
import com.volmit.iris.noise.CellGenerator;
import com.volmit.iris.noise.RarityCellGenerator;
import com.volmit.iris.object.InferredType;
import com.volmit.iris.object.IrisBiome;
import com.volmit.iris.object.IrisRegion;
import com.volmit.iris.object.IrisRegionRidge;
import com.volmit.iris.object.IrisRegionSpot;
import com.volmit.iris.util.BiomeResult;
import com.volmit.iris.util.GenLayer;
import com.volmit.iris.util.KList;
import com.volmit.iris.util.RNG;

import lombok.Data;
import lombok.EqualsAndHashCode;

@Data
@EqualsAndHashCode(callSuper = false)
public class GenLayerBiome extends GenLayer
{
	private RarityCellGenerator<IrisRegion> regionGenerator;
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
		regionGenerator = new RarityCellGenerator<IrisRegion>(rng.nextParallelRNG(1188519));
		bridgeGenerator = new CellGenerator(rng.nextParallelRNG(1541462));
	}

	public IrisRegion getRegion(double bx, double bz)
	{
		if(iris.getDimension().getRegions().isEmpty())
		{
			Iris.error("NO REGIONS!");
			return null;
		}

		regionGenerator.setShuffle(iris.getDimension().getRegionShuffle());
		regionGenerator.setCellScale(0.35);
		double x = bx / iris.getDimension().getRegionZoom();
		double z = bz / iris.getDimension().getRegionZoom();

		return regionGenerator.get(x, z, iris.getDimension().getAllRegions(iris));
	}

	public BiomeResult generateData(double bx, double bz, int rawX, int rawZ)
	{
		return generateRegionData(bx, bz, rawX, rawZ, getRegion(bx, bz));
	}

	public BiomeResult generateData(InferredType type, double bx, double bz, int rawX, int rawZ, IrisRegion regionData)
	{
		return getProvider(type).generateData(iris, bx, bz, rawX, rawZ, regionData);
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
		bridgeGenerator.setShuffle(iris.getDimension().getContinentalShuffle());
		bridgeGenerator.setCellScale(0.33);
		double x = bx / iris.getDimension().getContinentZoom();
		double z = bz / iris.getDimension().getContinentZoom();
		return bridgeGenerator.getIndex(x, z, 2) == 1 ? InferredType.LAND : InferredType.SEA;
	}

	public BiomeResult generateBiomeData(double bx, double bz, IrisRegion regionData, RarityCellGenerator<IrisBiome> cell, KList<IrisBiome> biomes, InferredType inferredType)
	{
		if(biomes.isEmpty())
		{
			return new BiomeResult(null, 0);
		}

		double x = bx / (iris.getDimension().getBiomeZoom() * regionData.getBiomeZoom(inferredType));
		double z = bz / (iris.getDimension().getBiomeZoom() * regionData.getBiomeZoom(inferredType));
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
				return new BiomeResult(iris.loadBiome(i.getBiome()).infer(i.getAs(), type), 0.5);
			}
		}

		for(IrisRegionSpot i : regionData.getSpotBiomes())
		{
			if(i.getType().equals(type) && i.isSpot(rng, rawX, rawZ))
			{
				return new BiomeResult(iris.loadBiome(i.getBiome()).infer(i.getAs(), type), 0.5);
			}
		}

		return pureResult;
	}

	public BiomeResult implode(double bx, double bz, IrisRegion regionData, RarityCellGenerator<IrisBiome> parentCell, BiomeResult parent)
	{
		return implode(bx, bz, regionData, parentCell, parent, 1);
	}

	public BiomeResult implode(double bx, double bz, IrisRegion regionData, RarityCellGenerator<IrisBiome> parentCell, BiomeResult parent, int hits)
	{
		if(hits > 9)
		{
			return parent;
		}

		double x = bx / iris.getDimension().getBiomeZoom();
		double z = bz / iris.getDimension().getBiomeZoom();

		if(parent.getDistance() > regionData.getBiomeImplosionRatio())
		{
			if(!parent.getBiome().getRealChildren(iris).isEmpty())
			{
				RarityCellGenerator<IrisBiome> childCell = parent.getBiome().getChildrenGenerator(rng, 123, parentCell.getCellScale() * parent.getBiome().getChildShrinkFactor());
				KList<IrisBiome> chx = parent.getBiome().getRealChildren(iris).copy();
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
