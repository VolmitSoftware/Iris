package com.volmit.iris.gen.layer;

import com.volmit.iris.Iris;
import com.volmit.iris.IrisSettings;
import com.volmit.iris.gen.DimensionalTerrainProvider;
import com.volmit.iris.gen.TopographicTerrainProvider;
import com.volmit.iris.noise.CNG;
import com.volmit.iris.object.InferredType;
import com.volmit.iris.object.IrisBiome;
import com.volmit.iris.object.IrisRegion;
import com.volmit.iris.object.IrisRegionRidge;
import com.volmit.iris.object.IrisRegionSpot;
import com.volmit.iris.util.GenLayer;
import com.volmit.iris.util.KList;
import com.volmit.iris.util.RNG;

import lombok.Data;
import lombok.EqualsAndHashCode;
import lombok.NonNull;

@Data
@EqualsAndHashCode(callSuper = false)
public class GenLayerBiome extends GenLayer
{
	private CNG regionGenerator;
	private CNG bridgeGenerator;
	private RNG lakeRandom;
	private RNG riverRandom;
	private BiomeDataProvider seaProvider;
	private BiomeDataProvider landProvider;
	private BiomeDataProvider shoreProvider;
	private BiomeDataProvider caveProvider;
	private BiomeDataProvider riverProvider;
	private BiomeDataProvider lakeProvider;
	private DimensionalTerrainProvider iris;

	public GenLayerBiome(@NonNull TopographicTerrainProvider iris, @NonNull RNG rng)
	{
		super(iris, rng);
		this.iris = iris;
		riverRandom = iris.getMasterRandom().nextParallelRNG(-324778);
		lakeRandom = iris.getMasterRandom().nextParallelRNG(-868778);
		seaProvider = new BiomeDataProvider(this, InferredType.SEA, rng);
		landProvider = new BiomeDataProvider(this, InferredType.LAND, rng);
		shoreProvider = new BiomeDataProvider(this, InferredType.SHORE, rng);
		caveProvider = new BiomeDataProvider(this, InferredType.CAVE, rng);
		riverProvider = new BiomeDataProvider(this, InferredType.RIVER, rng);
		lakeProvider = new BiomeDataProvider(this, InferredType.LAKE, rng);
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

	public IrisBiome generateData(double bx, double bz, int rawX, int rawZ)
	{
		return generateRegionData(bx, bz, rawX, rawZ, getRegion(bx, bz));
	}

	public IrisBiome generateData(InferredType type, double bx, double bz, int rawX, int rawZ, IrisRegion regionData)
	{
		return getProvider(type).generateData(iris, bx, bz, rawX, rawZ, regionData);
	}

	public IrisBiome generatePureData(InferredType type, double bx, double bz, int rawX, int rawZ, IrisRegion regionData)
	{
		return getProvider(type).generatePureData(iris, bx, bz, rawX, rawZ, regionData);
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

		else if(type.equals(InferredType.RIVER))
		{
			return riverProvider;
		}

		else if(type.equals(InferredType.LAKE))
		{
			return lakeProvider;
		}

		else
		{
			Iris.error("Cannot find a BiomeDataProvider for type " + type.name());
		}

		return null;
	}

	public IrisBiome generateRegionData(double bx, double bz, int rawX, int rawZ, IrisRegion regionData)
	{
		return generateData(getType(bx, bz, regionData), bx, bz, rawX, rawZ, regionData);
	}

	public InferredType getType(double bx, double bz, IrisRegion region)
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

		if(bridge.equals(InferredType.LAND) && region.isLake(lakeRandom, x, z))
		{
			bridge = InferredType.LAKE;
		}

		if(bridge.equals(InferredType.LAND) && region.isRiver(riverRandom, x, z))
		{
			bridge = InferredType.RIVER;
		}

		return bridge;
	}

	public IrisBiome generateBiomeData(double bx, double bz, IrisRegion regionData, CNG cell, KList<IrisBiome> biomes, InferredType inferredType, int rx, int rz)
	{
		return generateBiomeData(bx, bz, regionData, cell, biomes, inferredType, rx, rz, false);
	}

	public IrisBiome generateBiomeData(double bx, double bz, IrisRegion regionData, CNG cell, KList<IrisBiome> biomes, InferredType inferredType, int rx, int rz, boolean pure)
	{
		if(!pure)
		{
			for(IrisRegionRidge i : regionData.getRidgeBiomes())
			{
				if(i.getType().equals(inferredType) && i.isRidge(rng, rx, rz))
				{
					return iris.loadBiome(i.getBiome()).infer(i.getAs(), inferredType);
				}
			}

			for(IrisRegionSpot i : regionData.getSpotBiomes())
			{
				if(i.getType().equals(inferredType) && i.isSpot(rng, rx, rz))
				{
					return iris.loadBiome(i.getBiome()).infer(i.getAs(), inferredType);
				}
			}
		}

		if(biomes.isEmpty())
		{
			return null;
		}

		double x = bx / (iris.getDimension().getBiomeZoom() * regionData.getBiomeZoom(inferredType));
		double z = bz / (iris.getDimension().getBiomeZoom() * regionData.getBiomeZoom(inferredType));
		IrisBiome biome = cell.fitRarity(biomes, x, z);
		biome.setInferredType(inferredType);

		return implode(bx, bz, regionData, cell, biome);
	}

	public IrisBiome generateImpureData(int rawX, int rawZ, InferredType type, IrisRegion regionData, IrisBiome pureResult)
	{
		return pureResult;
	}

	public IrisBiome implode(double bx, double bz, IrisRegion regionData, CNG parentCell, IrisBiome parent)
	{
		return implode(bx, bz, regionData, parentCell, parent, 1);
	}

	public IrisBiome implode(double bx, double bz, IrisRegion regionData, CNG parentCell, IrisBiome parent, int hits)
	{
		if(hits > IrisSettings.get().maxBiomeChildDepth)
		{
			return parent;
		}

		double x = bx / iris.getDimension().getBiomeZoom();
		double z = bz / iris.getDimension().getBiomeZoom();

		if(parent.getRealChildren(iris).isNotEmpty())
		{
			CNG childCell = parent.getChildrenGenerator(rng, 123, parent.getChildShrinkFactor());
			KList<IrisBiome> chx = parent.getRealChildren(iris).copy();
			chx.add(parent);
			IrisBiome biome = childCell.fitRarity(chx, x, z);
			biome.setInferredType(parent.getInferredType());

			return implode(bx, bz, regionData, childCell, biome, hits + 1);
		}

		return parent;
	}

	@Override
	public double generate(double x, double z)
	{
		return 0;
	}
}
