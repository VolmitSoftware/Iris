package ninja.bytecode.iris.generator;

import java.util.function.Function;

import org.bukkit.World;

import lombok.Data;
import lombok.EqualsAndHashCode;
import ninja.bytecode.iris.layer.GenLayerBiome;
import ninja.bytecode.iris.object.IrisBiome;
import ninja.bytecode.iris.object.IrisRegion;
import ninja.bytecode.iris.util.BiomeResult;
import ninja.bytecode.iris.util.IrisInterpolation;
import ninja.bytecode.iris.util.RNG;

@Data
@EqualsAndHashCode(callSuper = false)
public abstract class BiomeChunkGenerator extends DimensionChunkGenerator
{
	protected GenLayerBiome glBiome;

	public BiomeChunkGenerator(String dimensionName)
	{
		super(dimensionName);
	}

	public void onInit(World world, RNG rng)
	{
		glBiome = new GenLayerBiome(this, masterRandom.nextParallelRNG(1));
	}

	public IrisRegion sampleRegion(int x, int z)
	{
		double wx = getModifiedX(x, z);
		double wz = getModifiedZ(x, z);
		return glBiome.getRegion(wx, wz);
	}

	public BiomeResult sampleBiome(int x, int z)
	{
		double wx = getModifiedX(x, z);
		double wz = getModifiedZ(x, z);
		IrisRegion region = glBiome.getRegion(wx, wz);
		return glBiome.generateRegionData(wx, wz, region);
	}

	protected double interpolateAuxiliaryHeight(double rx, double rz)
	{
		return IrisInterpolation.getNoise(getDimension().getInterpolationAuxiliaryFunction(), (int) Math.round(rx), (int) Math.round(rz), getDimension().getInterpolationAuxiliaryScale(), (xx, zz) ->
		{
			double xv = xx / getDimension().getTerrainZoom();
			double zv = zz / getDimension().getTerrainZoom();
			BiomeResult neighborResult = glBiome.generateData(xv, zv);
			return neighborResult.getBiome().getAuxiliaryHeight(xv, zv, getWorld().getSeed() * 3923);
		});
	}

	protected double interpolateHeight(double rx, double rz, Function<IrisBiome, Double> property)
	{
		return IrisInterpolation.getNoise(getDimension().getInterpolationFunction(), (int) Math.round(rx), (int) Math.round(rz), getDimension().getInterpolationScale(), (xx, zz) ->
		{
			BiomeResult neighborResult = glBiome.generateData(xx / getDimension().getTerrainZoom(), zz / getDimension().getTerrainZoom());
			return property.apply(neighborResult.getBiome());
		});
	}

	protected double interpolateSurface(double rx, double rz, Function<IrisBiome, Double> property)
	{
		return IrisInterpolation.getNoise(getDimension().getInterpolationSurfaceFunction(), (int) Math.round(rx), (int) Math.round(rz), getDimension().getInterpolationSurfaceScale(), (xx, zz) ->
		{
			BiomeResult neighborResult = glBiome.generateData(xx / getDimension().getTerrainZoom(), zz / getDimension().getTerrainZoom());
			return property.apply(neighborResult.getBiome());
		});
	}
}
