package ninja.bytecode.iris.generator;

import org.bukkit.Material;
import org.bukkit.World;
import org.bukkit.block.data.Bisected;
import org.bukkit.block.data.Bisected.Half;
import org.bukkit.block.data.BlockData;

import lombok.Data;
import lombok.EqualsAndHashCode;
import ninja.bytecode.iris.layer.GenLayerCave;
import ninja.bytecode.iris.object.IrisBiome;
import ninja.bytecode.iris.object.IrisBiomeDecorator;
import ninja.bytecode.iris.object.IrisRegion;
import ninja.bytecode.iris.object.atomics.AtomicSliver;
import ninja.bytecode.iris.util.BiomeMap;
import ninja.bytecode.iris.util.BiomeResult;
import ninja.bytecode.iris.util.HeightMap;
import ninja.bytecode.iris.util.RNG;
import ninja.bytecode.shuriken.collections.KList;
import ninja.bytecode.shuriken.math.M;

@Data
@EqualsAndHashCode(callSuper = false)
public abstract class TerrainChunkGenerator extends ParallelChunkGenerator
{
	protected static final BlockData AIR = Material.AIR.createBlockData();
	private long lastUpdateRequest = M.ms();
	private long lastChunkLoad = M.ms();
	private GenLayerCave glCave;
	private RNG rockRandom;

	public TerrainChunkGenerator(String dimensionName, int threads)
	{
		super(dimensionName, threads);
	}

	public void onInit(World world, RNG rng)
	{
		super.onInit(world, rng);
		rockRandom = getMasterRandom().nextParallelRNG(2858678);
		glCave = new GenLayerCave(this, rng.nextParallelRNG(238948));
	}

	@Override
	protected void onGenerateColumn(int cx, int cz, int rx, int rz, int x, int z, AtomicSliver sliver, BiomeMap biomeMap)
	{
		try
		{
			BlockData block;
			int fluidHeight = getDimension().getFluidHeight();
			double ox = getModifiedX(rx, rz);
			double oz = getModifiedZ(rx, rz);
			double wx = getZoomed(ox);
			double wz = getZoomed(oz);
			int depth = 0;
			double noise = getNoiseHeight(rx, rz);
			int height = (int) Math.round(noise) + fluidHeight;
			IrisBiome biome = sampleTrueBiome(rx, rz).getBiome();
			KList<BlockData> layers = biome.generateLayers(wx, wz, masterRandom, height);
			KList<BlockData> seaLayers = biome.isSea() ? biome.generateSeaLayers(wx, wz, masterRandom, fluidHeight - height) : new KList<>();
			cacheBiome(x, z, biome);

			for(int k = Math.max(height, fluidHeight); k < Math.max(height, fluidHeight) + 3; k++)
			{
				if(k < Math.max(height, fluidHeight) + 3)
				{
					if(biomeMap != null)
					{
						sliver.set(k, biome.getGroundBiome(masterRandom, rz, k, rx));
					}
				}
			}

			for(int k = Math.max(height, fluidHeight); k >= 0; k--)
			{
				boolean underwater = k > height && k <= fluidHeight;

				if(biomeMap != null)
				{
					sliver.set(k, biome.getGroundBiome(masterRandom, rz, k, rx));
					biomeMap.setBiome(x, z, biome);
				}

				if(underwater)
				{
					block = seaLayers.hasIndex(fluidHeight - k) ? layers.get(depth) : getDimension().getFluid(rockRandom, wx, k, wz);
				}

				else
				{
					block = layers.hasIndex(depth) ? layers.get(depth) : getDimension().getRock(rockRandom, wx, k, wz);
					depth++;
				}

				sliver.set(k, block);

				if(k == height && block.getMaterial().isSolid() && k < fluidHeight && biome.isSea())
				{
					int j = 0;

					for(IrisBiomeDecorator i : biome.getDecorators())
					{
						BlockData d = i.getBlockData(getMasterRandom().nextParallelRNG(biome.hashCode() + j++), wx, wz);

						if(d != null)
						{
							int stack = i.getHeight(getMasterRandom().nextParallelRNG(39456 + i.hashCode()), wx, wz);

							if(stack == 1)
							{
								sliver.set(k + 1, d);
							}

							else if(k < fluidHeight - stack)
							{
								for(int l = 0; l < stack; l++)
								{
									sliver.set(k + l + 1, d);
								}
							}

							break;
						}
					}
				}

				if(k == Math.max(height, fluidHeight) && block.getMaterial().isSolid() && k < 255 && !biome.isSea())
				{
					int j = 0;

					for(IrisBiomeDecorator i : biome.getDecorators())
					{
						BlockData d = i.getBlockData(getMasterRandom().nextParallelRNG(biome.hashCode() + j++), wx, wz);

						if(d != null)
						{
							if(d instanceof Bisected && k < 254)
							{
								Bisected t = ((Bisected) d.clone());
								t.setHalf(Half.TOP);
								Bisected b = ((Bisected) d.clone());
								b.setHalf(Half.BOTTOM);
								sliver.set(k + 1, b);
								sliver.set(k + 2, t);
							}

							else
							{
								int stack = i.getHeight(getMasterRandom().nextParallelRNG(39456 + i.hashCode()), wx, wz);

								if(stack == 1)
								{
									sliver.set(k + 1, d);
								}

								else if(k < 255 - stack)
								{
									for(int l = 0; l < stack; l++)
									{
										sliver.set(k + l + 1, d);
									}
								}
							}

							break;
						}
					}
				}
			}
		}

		catch(Throwable e)
		{
			fail(e);
		}
	}

	@Override
	protected void onPostGenerate(RNG random, int x, int z, ChunkData data, BiomeGrid grid, HeightMap height, BiomeMap biomeMap)
	{
		onPreParallaxPostGenerate(random, x, z, data, grid, height, biomeMap);
	}

	protected void onPreParallaxPostGenerate(RNG random, int x, int z, ChunkData data, BiomeGrid grid, HeightMap height, BiomeMap biomeMap)
	{

	}

	protected void onPostParallaxPostGenerate(RNG random, int x, int z, ChunkData data, BiomeGrid grid, HeightMap height, BiomeMap biomeMap)
	{
		for(int i = 0; i < 16; i++)
		{
			for(int j = 0; j < 16; j++)
			{
				glCave.genCaves((x << 4) + i, (z << 4) + j, i, j, data, height);
			}
		}
	}

	protected double getNoiseHeight(int rx, int rz)
	{
		double wx = getZoomed(rx);
		double wz = getZoomed(rz);

		return getBiomeHeight(wx, wz);
	}

	public BiomeResult sampleTrueBiome(int x, int z)
	{
		if(!getDimension().getFocus().equals(""))
		{
			return focus();
		}

		double wx = getModifiedX(x, z);
		double wz = getModifiedZ(x, z);
		IrisRegion region = sampleRegion(x, z);
		int height = sampleHeight(x, z);
		double sh = region.getShoreHeight(wx, wz);
		IrisBiome current = sampleBiome(x, z).getBiome();

		// Stop shores from spawning on land
		if(current.isShore() && height > sh)
		{
			return glBiome.generateLandData(wx, wz, x, z, region);
		}

		// Stop land & shore from spawning underwater
		if(current.isShore() || current.isLand() && height <= getDimension().getFluidHeight())
		{
			return glBiome.generateSeaData(wx, wz, x, z, region);
		}

		// Stop oceans from spawning on land
		if(current.isSea() && height > getDimension().getFluidHeight())
		{
			return glBiome.generateLandData(wx, wz, x, z, region);
		}

		// Stop land from spawning underwater
		if(height <= getDimension().getFluidHeight())
		{
			return glBiome.generateSeaData(wx, wz, x, z, region);
		}

		// Stop land from spawning where shores go
		if(height <= getDimension().getFluidHeight() + sh)
		{
			return glBiome.generateShoreData(wx, wz, x, z, region);
		}

		return glBiome.generateRegionData(wx, wz, x, z, region);
	}

	@Override
	protected int onSampleColumnHeight(int cx, int cz, int rx, int rz, int x, int z)
	{
		int fluidHeight = getDimension().getFluidHeight();
		double noise = getNoiseHeight(rx, rz);

		return (int) Math.round(noise) + fluidHeight;
	}

	public int getFluidHeight()
	{
		return getDimension().getFluidHeight();
	}

	public double getTerrainHeight(int x, int z)
	{
		return getNoiseHeight(x, z) + getFluidHeight();
	}

	public double getTerrainWaterHeight(int x, int z)
	{
		return Math.max(getTerrainHeight(x, z), getFluidHeight());
	}
}
