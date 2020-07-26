package ninja.bytecode.iris.layer;

import ninja.bytecode.iris.Iris;
import ninja.bytecode.iris.generator.DimensionChunkGenerator;
import ninja.bytecode.iris.object.InferredType;
import ninja.bytecode.iris.object.IrisBiome;
import ninja.bytecode.iris.object.IrisRegion;
import ninja.bytecode.iris.object.IrisRegionRidge;
import ninja.bytecode.iris.object.IrisRegionSpot;
import ninja.bytecode.iris.util.BiomeResult;
import ninja.bytecode.iris.util.CellGenerator;
import ninja.bytecode.iris.util.GenLayer;
import ninja.bytecode.iris.util.RNG;
import ninja.bytecode.shuriken.collections.KList;

public class GenLayerBiome extends GenLayer
{
	private CellGenerator region;
	private CellGenerator bridge;
	private CellGenerator land;
	private CellGenerator shore;
	private CellGenerator sea;
	private CellGenerator cave;
	private DimensionChunkGenerator iris;
	private IrisBiome defaultCave;

	public GenLayerBiome(DimensionChunkGenerator iris, RNG rng)
	{
		super(iris, rng);
		this.iris = iris;
		region = new CellGenerator(rng.nextParallelRNG(1188519));
		bridge = new CellGenerator(rng.nextParallelRNG(1541462));
		land = new CellGenerator(rng.nextParallelRNG(9045162));
		shore = new CellGenerator(rng.nextParallelRNG(2342812));
		sea = new CellGenerator(rng.nextParallelRNG(6135621));
		cave = new CellGenerator(rng.nextParallelRNG(9985621));
		defaultCave = new IrisBiome();
		defaultCave.getLayers().clear();
		defaultCave.setLoadKey("default");
	}

	public IrisRegion getRegion(double bx, double bz)
	{
		region.setShuffle(8);
		region.setCellScale(0.33 / iris.getDimension().getRegionZoom());
		double x = bx / iris.getDimension().getBiomeZoom();
		double z = bz / iris.getDimension().getBiomeZoom();
		String regionId = iris.getDimension().getRegions().get(region.getIndex(x, z, iris.getDimension().getRegions().size()));

		return Iris.data.getRegionLoader().load(regionId);
	}

	public BiomeResult generateData(double bx, double bz, int rawX, int rawZ)
	{
		return generateRegionData(bx, bz, rawX, rawZ, getRegion(bx, bz));
	}

	public boolean isSea(double bx, double bz, IrisRegion regionData)
	{
		bridge.setShuffle(0);
		bridge.setCellScale(0.33 / iris.getDimension().getContinentZoom());
		double x = bx / iris.getDimension().getBiomeZoom();
		double z = bz / iris.getDimension().getBiomeZoom();
		return bridge.getIndex(x, z, 5) == 1;
	}

	public BiomeResult generateRegionData(double bx, double bz, int rawX, int rawZ, IrisRegion regionData)
	{
		if(isSea(bx, bz, regionData))
		{
			return generateSeaData(bx, bz, rawX, rawZ, regionData);
		}

		else
		{
			return generateLandData(bx, bz, rawX, rawZ, regionData);
		}
	}

	public BiomeResult generateBiomeData(double bx, double bz, IrisRegion regionData, CellGenerator cell, KList<String> biomes, InferredType inferredType)
	{
		double x = bx / iris.getDimension().getBiomeZoom();
		double z = bz / iris.getDimension().getBiomeZoom();
		IrisBiome biome = Iris.data.getBiomeLoader().load(biomes.get(sea.getIndex(x, z, biomes.size())));
		biome.setInferredType(inferredType);

		return implode(bx, bz, regionData, cell, new BiomeResult(biome, cell.getDistance(x, z)));
	}

	public BiomeResult generatePureSeaData(double bx, double bz, int rawX, int rawZ, IrisRegion regionData)
	{
		sea.setShuffle(42);
		sea.setCellScale(0.56 / iris.getDimension().getSeaZoom());
		return generateBiomeData(bx, bz, regionData, sea, regionData.getSeaBiomes(), InferredType.SEA);
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

	public BiomeResult generateSeaData(double bx, double bz, int rawX, int rawZ, IrisRegion regionData)
	{
		return generateImpureData(rawX, rawZ, InferredType.SEA, regionData, generatePureSeaData(bx, bz, rawX, rawZ, regionData));
	}

	public BiomeResult generatePureLandData(double bx, double bz, int rawX, int rawZ, IrisRegion regionData)
	{
		land.setShuffle(12);
		land.setCellScale(0.6 / iris.getDimension().getLandZoom());
		return generateBiomeData(bx, bz, regionData, land, regionData.getLandBiomes(), InferredType.LAND);
	}

	public BiomeResult generateLandData(double bx, double bz, int rawX, int rawZ, IrisRegion regionData)
	{
		return generateImpureData(rawX, rawZ, InferredType.LAND, regionData, generatePureLandData(bx, bz, rawX, rawZ, regionData));
	}

	public BiomeResult generatePureShoreData(double bx, double bz, int rawX, int rawZ, IrisRegion regionData)
	{
		shore.setShuffle(4);
		shore.setCellScale(0.8 / iris.getDimension().getShoreZoom());
		return generateBiomeData(bx, bz, regionData, shore, regionData.getShoreBiomes(), InferredType.SHORE);
	}

	public BiomeResult generateShoreData(double bx, double bz, int rawX, int rawZ, IrisRegion regionData)
	{
		return generateImpureData(rawX, rawZ, InferredType.SHORE, regionData, generatePureShoreData(bx, bz, rawX, rawZ, regionData));
	}

	public BiomeResult generateCaveData(double bx, double bz, int rawX, int rawZ, IrisRegion regionData)
	{
		if(regionData.getCaveBiomes().isEmpty())
		{
			return new BiomeResult(defaultCave, 0);
		}

		cave.setShuffle(12);
		cave.setCellScale(0.6 / iris.getDimension().getCaveBiomeZoom());
		return generateBiomeData(bx, bz, regionData, cave, regionData.getCaveBiomes(), InferredType.CAVE);
	}

	public BiomeResult implode(double bx, double bz, IrisRegion regionData, CellGenerator parentCell, BiomeResult parent)
	{
		return implode(bx, bz, regionData, parentCell, parent, 1);
	}

	public BiomeResult implode(double bx, double bz, IrisRegion regionData, CellGenerator parentCell, BiomeResult parent, int hits)
	{
		if(hits > 9)
		{
			return parent;
		}

		double x = bx / iris.getDimension().getBiomeZoom();
		double z = bz / iris.getDimension().getBiomeZoom();
		if(parent.getDistance() > regionData.getBiomeImplosionRatio())
		{
			if(!parent.getBiome().getChildren().isEmpty())
			{
				CellGenerator childCell = parent.getBiome().getChildrenGenerator(rng, 123, parentCell.getCellScale() * parent.getBiome().getChildShrinkFactor());
				int r = childCell.getIndex(x, z, parent.getBiome().getChildren().size() + 1);

				if(r == parent.getBiome().getChildren().size())
				{
					return new BiomeResult(parent.getBiome(), childCell.getDistance(x, z));
				}

				IrisBiome biome = Iris.data.getBiomeLoader().load(parent.getBiome().getChildren().get(r));
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
