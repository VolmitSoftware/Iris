package com.volmit.iris.gen.layer;

import com.volmit.iris.Iris;
import com.volmit.iris.IrisSettings;
import com.volmit.iris.gen.DimensionChunkGenerator;
import com.volmit.iris.noise.CNG;
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
	private CNG regionGenerator;
	private CNG bridgeGenerator;
	private BiomeDataProvider seaProvider;
	private BiomeDataProvider landProvider;
	private BiomeDataProvider shoreProvider;
	private BiomeDataProvider caveProvider;
	private DimensionChunkGenerator iris;

	public GenLayerBiome(DimensionChunkGenerator iris, RNG rng)
	{
		super(iris, rng);
		this.iris = iris;
		seaProvider = new BiomeDataProvider(this, InferredType.SEA, rng);
		landProvider = new BiomeDataProvider(this, InferredType.LAND, rng);
		shoreProvider = new BiomeDataProvider(this, InferredType.SHORE, rng);
		caveProvider = new BiomeDataProvider(this, InferredType.CAVE, rng);
		regionGenerator = iris.getDimension().getRegionStyle().create(rng.nextParallelRNG(1188519)).bake().scale(1D / iris.getDimension().getRegionZoom());
		bridgeGenerator = iris.getDimension().getContinentalStyle().create(rng.nextParallelRNG(1541462)).bake().scale(1D / iris.getDimension().getContinentZoom());
	}

	public IrisRegion getRegion(double bx, double bz)
	{
		if(iris.getDimension().getRegions().isEmpty())
		{
			Iris.error("NO REGIONS!");
			return null;
		}

		double x = bx;
		double z = bz;

		return regionGenerator.fitRarity(iris.getDimension().getAllRegions(iris), x, z);
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
		double x = bx;
		double z = bz;
		double c = iris.getDimension().getLandChance();
		InferredType bridge;
		if(c >= 1)
		{
			bridge = InferredType.LAND;
		}

		if(c <= 0)
		{
			bridge = InferredType.SEA;
		}

		bridge = bridgeGenerator.fitDouble(0, 1, x, z) < c ? InferredType.LAND : InferredType.SEA;

		return bridge;
	}

	public BiomeResult generateBiomeData(double bx, double bz, IrisRegion regionData, CNG cell, KList<IrisBiome> biomes, InferredType inferredType, int rx, int rz)
	{
		for(IrisRegionRidge i : regionData.getRidgeBiomes())
		{
			if(i.getType().equals(inferredType) && i.isRidge(rng, rx, rz))
			{
				return new BiomeResult(iris.loadBiome(i.getBiome()).infer(i.getAs(), inferredType), 0.5);
			}
		}

		for(IrisRegionSpot i : regionData.getSpotBiomes())
		{
			if(i.getType().equals(inferredType) && i.isSpot(rng, rx, rz))
			{
				return new BiomeResult(iris.loadBiome(i.getBiome()).infer(i.getAs(), inferredType), 0.5);
			}
		}

		if(biomes.isEmpty())
		{
			return new BiomeResult(null, 0);
		}

		double x = bx / (iris.getDimension().getBiomeZoom() * regionData.getBiomeZoom(inferredType));
		double z = bz / (iris.getDimension().getBiomeZoom() * regionData.getBiomeZoom(inferredType));
		IrisBiome biome = cell.fitRarity(biomes, x, z);
		biome.setInferredType(inferredType);

		return implode(bx, bz, regionData, cell, new BiomeResult(biome, 1));
	}

	public BiomeResult generateImpureData(int rawX, int rawZ, InferredType type, IrisRegion regionData, BiomeResult pureResult)
	{
		return pureResult;
	}

	public BiomeResult implode(double bx, double bz, IrisRegion regionData, CNG parentCell, BiomeResult parent)
	{
		return implode(bx, bz, regionData, parentCell, parent, 1);
	}

	public BiomeResult implode(double bx, double bz, IrisRegion regionData, CNG parentCell, BiomeResult parent, int hits)
	{
		if(hits > IrisSettings.get().maxBiomeChildDepth)
		{
			return parent;
		}

		double x = bx / iris.getDimension().getBiomeZoom();
		double z = bz / iris.getDimension().getBiomeZoom();

		if(!parent.getBiome().getRealChildren(iris).isEmpty())
		{
			CNG childCell = parent.getBiome().getChildrenGenerator(rng, 123, parent.getBiome().getChildShrinkFactor());
			KList<IrisBiome> chx = parent.getBiome().getRealChildren(iris).copy(); // TODO Cache
			chx.add(parent.getBiome());
			IrisBiome biome = childCell.fitRarity(chx, x, z);
			biome.setInferredType(parent.getBiome().getInferredType());

			return implode(bx, bz, regionData, childCell, new BiomeResult(biome, 0), hits + 1);
		}

		return parent;
	}

	@Override
	public double generate(double x, double z)
	{
		return 0;
	}
}
