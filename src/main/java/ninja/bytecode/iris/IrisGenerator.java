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
import ninja.bytecode.iris.object.IrisRegion;
import ninja.bytecode.iris.util.BiomeResult;
import ninja.bytecode.iris.util.CNG;
import ninja.bytecode.iris.util.ChronoLatch;
import ninja.bytecode.iris.util.IrisInterpolation;
import ninja.bytecode.iris.util.KList;
import ninja.bytecode.iris.util.RNG;

public class IrisGenerator extends ChunkGenerator implements IrisContext
{
	// TODO REMOVE OR FIND A BETTER PLACE
	private BlockData STONE = Material.STONE.createBlockData();
	private BlockData WATER = Material.WATER.createBlockData();
	private String dimensionName;
	private GenLayerBiome glBiome;
	private CNG terrainNoise;
	private IrisMetrics metrics;
	private World world;
	private ChronoLatch pushLatch;

	private boolean initialized = false;

	public IrisGenerator(String dimensionName)
	{
		this.dimensionName = dimensionName;
		pushLatch = new ChronoLatch(3000);
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

		this.world = world;
		metrics = new IrisMetrics(1024);
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
		if(pushLatch.flip())
		{
			Iris.hotloader.check();
			IrisContext.pushContext(this);
		}
		
		int i, j, k, height, depth;
		double wx, wz, rx, rz, noise, ox, oz;
		boolean underwater;
		BlockData block;
		int fluidHeight = getDimension().getFluidHeight();
		BiomeResult biomeResult;
		IrisBiome biome;
		IrisRegion region;
		RNG random = new RNG(world.getSeed());
		onInit(world, random.nextParallelRNG(0));
		ChunkData data = Bukkit.createChunkData(world);

		for(i = 0; i < 16; i++)
		{
			for(j = 0; j < 16; j++)
			{
				rx = (x * 16) + i;
				rz = (z * 16) + j;
				ox = (getDimension().cosRotate() * rx) + (-getDimension().sinRotate() * rz) + getDimension().getCoordFracture(random, 39392).fitDoubleD(-getDimension().getCoordFractureDistance() / 2, getDimension().getCoordFractureDistance() / 2, rx, rz);
				oz = (getDimension().sinRotate() * rx) + (getDimension().cosRotate() * rz) + getDimension().getCoordFracture(random, 39392).fitDoubleD(-getDimension().getCoordFractureDistance() / 2, getDimension().getCoordFractureDistance() / 2, rx, rz);
				wx = (double) (ox) / getDimension().getTerrainZoom();
				wz = (double) (oz) / getDimension().getTerrainZoom();
				depth = 0;
				region = glBiome.getRegion(wx, wz);
				biomeResult = glBiome.generateRegionData(wx, wz, region);
				biome = biomeResult.getBiome();
				double lo = interpolateHeight(ox, oz, (b) -> b.getLowHeight());
				double hi = interpolateSurface(ox, oz, (b) -> b.getHighHeight());
				noise = lo + (terrainNoise.fitDoubleD(0, hi - lo, wx, wz));
				height = (int) Math.round(noise) + fluidHeight;

				// Remove Land biome surfaces from underwater
				if(height < fluidHeight + 1)
				{
					if(biome.isLand())
					{
						biome = glBiome.generateShoreData(wx, wz, region).getBiome();
					}
				}

				KList<BlockData> layers = biome.generateLayers(wx, wz, random, height);

				for(k = Math.max(height, fluidHeight); k >= 0; k--)
				{
					underwater = k > height && k <= fluidHeight;
					biomeGrid.setBiome(i, k, j, biome.getDerivative());

					if(underwater)
					{
						block = WATER;
					}

					else
					{
						block = layers.hasIndex(depth) ? layers.get(depth) : STONE;
						depth++;
					}

					data.setBlock(i, k, j, block);
				}
			}
		}

		Iris.verbose("Generated " + x + " " + z);
		return data;
	}

	public double interpolateHeight(double rx, double rz, Function<IrisBiome, Double> property)
	{
		return IrisInterpolation.getNoise(getDimension().getInterpolationFunction(), (int) Math.round(rx), (int) Math.round(rz), getDimension().getInterpolationScale(), (xx, zz) ->
		{
			BiomeResult neighborResult = glBiome.generateData(xx / getDimension().getTerrainZoom(), zz / getDimension().getTerrainZoom());
			return property.apply(neighborResult.getBiome());
		});
	}

	public double interpolateSurface(double rx, double rz, Function<IrisBiome, Double> property)
	{
		return IrisInterpolation.getNoise(getDimension().getInterpolationSurfaceFunction(), (int) Math.round(rx), (int) Math.round(rz), getDimension().getInterpolationSurfaceScale(), (xx, zz) ->
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
	public BiomeResult getBiome(int x, int z)
	{
		return null;
	}

	@Override
	public boolean isParallelCapable()
	{
		return true;
	}

	@Override
	public IrisMetrics getMetrics()
	{
		return metrics;
	}

	@Override
	public World getWorld()
	{
		return world;
	}
}
