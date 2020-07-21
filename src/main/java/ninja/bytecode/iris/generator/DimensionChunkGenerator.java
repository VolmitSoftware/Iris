package ninja.bytecode.iris.generator;

import lombok.Data;
import lombok.EqualsAndHashCode;
import ninja.bytecode.iris.Iris;
import ninja.bytecode.iris.object.InferredType;
import ninja.bytecode.iris.object.IrisBiome;
import ninja.bytecode.iris.object.IrisDimension;
import ninja.bytecode.iris.object.IrisRegion;
import ninja.bytecode.iris.util.BiomeResult;

@Data
@EqualsAndHashCode(callSuper = false)
public abstract class DimensionChunkGenerator extends ContextualChunkGenerator
{
	protected final String dimensionName;

	public DimensionChunkGenerator(String dimensionName)
	{
		super();
		this.dimensionName = dimensionName;
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
