package ninja.bytecode.iris;

import java.util.List;
import java.util.Random;
import java.util.function.Function;

import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.World;
import org.bukkit.block.data.BlockData;
import org.bukkit.generator.BlockPopulator;
import org.bukkit.generator.ChunkGenerator;

import ninja.bytecode.iris.layer.GenLayerBiome;
import ninja.bytecode.iris.object.IrisBiome;
import ninja.bytecode.iris.object.IrisDimension;
import ninja.bytecode.iris.util.BiomeResult;
import ninja.bytecode.iris.util.CNG;
import ninja.bytecode.iris.util.IrisInterpolation;
import ninja.bytecode.iris.util.KList;
import ninja.bytecode.iris.util.RNG;

public class IrisGenerator extends ChunkGenerator
{
	// TODO REMOVE OR FIND A BETTER PLACE
	private BlockData STONE = Material.STONE.createBlockData();
	private String dimensionName;
	private GenLayerBiome glBiome;
	private CNG terrainNoise;

	private boolean initialized = false;

	public IrisGenerator(String dimensionName)
	{
		this.dimensionName = dimensionName;
	}

	public IrisDimension getDimension()
	{
		return Iris.data.getDimensionLoader().load(dimensionName);
	}

	public void onInit(World world, RNG rng)
	{
		if(initialized)
		{
			return;
		}

		initialized = true;
		glBiome = new GenLayerBiome(this, rng.nextParallelRNG(1));
		terrainNoise = CNG.signature(rng.nextParallelRNG(2));
	}

	@Override
	public boolean canSpawn(World world, int x, int z)
	{
		return super.canSpawn(world, x, z);
	}

	@Override
	public ChunkData generateChunkData(World world, Random no, int x, int z, BiomeGrid biomeGrid)
	{
		Iris.hotloader.check();
		int i, j, k, height, depth;
		double wx, wz, rx, rz, heightLow, heightHigh, heightExponent;
		int fluidHeight = getDimension().getFluidHeight();
		BiomeResult biomeResult;
		IrisBiome biome;
		RNG random = new RNG(world.getSeed());
		onInit(world, random.nextParallelRNG(0));
		ChunkData data = Bukkit.createChunkData(world);

		for(i = 0; i < 16; i++)
		{
			rx = (x * 16) + i;
			wx = ((double) (x * 16) + i) / getDimension().getTerrainZoom();
			for(j = 0; j < 16; j++)
			{
				rz = (z * 16) + j;
				wz = ((double) (z * 16) + j) / getDimension().getTerrainZoom();
				depth = 0;
				biomeResult = glBiome.generateData(wx, wz);
				biome = biomeResult.getBiome();
				heightLow = interpolate(rx, rz, (b) -> b.getLowHeight());
				heightHigh = interpolate(rx, rz, (b) -> b.getHighHeight());
				heightExponent = interpolate(rx, rz, (b) -> b.getHeightExponent());
				height = (int) Math.round(terrainNoise.fitDoubleExponent(heightLow, heightHigh, heightExponent, wx, wz)) + fluidHeight;
				KList<BlockData> layers = biome.generateLayers(wx, wz, random, height);

				for(k = Math.max(height, fluidHeight); k >= 0; k--)
				{
					biomeGrid.setBiome(i, k, j, biome.getDerivative());
					data.setBlock(i, k, j, layers.hasIndex(depth) ? layers.get(depth) : STONE);
					depth++;
				}
			}
		}

		return data;
	}

	public double interpolate(double rx, double rz, Function<IrisBiome, Double> property)
	{
		return IrisInterpolation.getNoise(getDimension().getInterpolationFunction(), (int) Math.round(rx), (int) Math.round(rz), getDimension().getInterpolationScale(), (xx, zz) ->
		{
			BiomeResult neighborResult = glBiome.generateData(xx / getDimension().getTerrainZoom(), zz / getDimension().getTerrainZoom());
			return property.apply(neighborResult.getBiome());
		});
	}

	@Override
	public List<BlockPopulator> getDefaultPopulators(World world)
	{
		return super.getDefaultPopulators(world);
	}

	@Override
	public Location getFixedSpawnLocation(World world, Random random)
	{
		return super.getFixedSpawnLocation(world, random);
	}

	@Override
	public boolean isParallelCapable()
	{
		return true;
	}
}
