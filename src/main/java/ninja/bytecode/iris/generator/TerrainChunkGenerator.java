package ninja.bytecode.iris.generator;

import java.util.concurrent.locks.ReentrantLock;

import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.World;
import org.bukkit.block.Block;
import org.bukkit.block.data.Bisected;
import org.bukkit.block.data.Bisected.Half;
import org.bukkit.block.data.BlockData;

import lombok.Data;
import lombok.EqualsAndHashCode;
import ninja.bytecode.iris.Iris;
import ninja.bytecode.iris.object.InferredType;
import ninja.bytecode.iris.object.IrisBiome;
import ninja.bytecode.iris.object.IrisBiomeDecorator;
import ninja.bytecode.iris.object.IrisRegion;
import ninja.bytecode.iris.object.atomics.AtomicSliver;
import ninja.bytecode.iris.util.BiomeMap;
import ninja.bytecode.iris.util.BiomeResult;
import ninja.bytecode.iris.util.BlockPosition;
import ninja.bytecode.iris.util.RNG;
import ninja.bytecode.shuriken.collections.KList;
import ninja.bytecode.shuriken.collections.KSet;
import ninja.bytecode.shuriken.logging.L;
import ninja.bytecode.shuriken.math.M;

@Data
@EqualsAndHashCode(callSuper = false)
public abstract class TerrainChunkGenerator extends ParallelChunkGenerator
{
	protected static final BlockData AIR = Material.AIR.createBlockData();
	protected static final BlockData STONE = Material.STONE.createBlockData();
	protected static final BlockData WATER = Material.WATER.createBlockData();
	private KList<BlockPosition> updateBlocks = new KList<>();
	private ReentrantLock relightLock = new ReentrantLock();
	private long lastUpdateRequest = M.ms();
	private long lastChunkLoad = M.ms();

	public TerrainChunkGenerator(String dimensionName, int threads)
	{
		super(dimensionName, threads);
	}

	public void onInit(World world, RNG rng)
	{
		super.onInit(world, rng);
	}

	public void queueUpdate(int x, int y, int z)
	{
		if(M.ms() - lastUpdateRequest > 3000 && M.ms() - lastChunkLoad > 3000)
		{
			updateBlocks.clear();
		}

		updateBlocks.add(new BlockPosition(x, y, z));
		lastUpdateRequest = M.ms();
	}

	public void updateLights()
	{
		if(M.ms() - lastUpdateRequest > 3000 && M.ms() - lastChunkLoad > 3000)
		{
			updateBlocks.clear();
		}
		
		for(BlockPosition i : updateBlocks.copy())
		{
			if(getWorld().isChunkLoaded(i.getChunkX(), i.getChunkZ()))
			{
				updateBlocks.remove(i);
				Block b = getWorld().getBlockAt(i.getX(), i.getY(), i.getZ());
				BlockData bd = b.getBlockData();
				b.setBlockData(AIR, false);
				b.setBlockData(bd, true);
			}
		}

		while(updateBlocks.size() > 5000)
		{
			updateBlocks.remove(0);
		}

		lastChunkLoad = M.ms();
	}

	public void checkUnderwater(int x, int y, int z, BlockData d)
	{
		if(d.getMaterial().equals(Material.SEA_PICKLE) || d.getMaterial().equals(Material.SOUL_SAND) || d.getMaterial().equals(Material.MAGMA_BLOCK))
		{
			queueUpdate(x, y, z);
		}
	}

	public void checkSurface(int x, int y, int z, BlockData d)
	{
		if(d.getMaterial().equals(Material.SEA_PICKLE) || d.getMaterial().equals(Material.TORCH) || d.getMaterial().equals(Material.REDSTONE_TORCH) || d.getMaterial().equals(Material.TORCH))
		{
			queueUpdate(x, y, z);
		}
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

			for(int k = Math.max(height, fluidHeight); k >= 0; k--)
			{
				boolean underwater = k > height && k <= fluidHeight;

				if(biomeMap != null)
				{
					sliver.set(k, biome.getDerivative());
					biomeMap.setBiome(x, z, biome);
				}

				if(underwater)
				{
					block = WATER;
				}

				else
				{
					block = layers.hasIndex(depth) ? layers.get(depth) : STONE;
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
								checkUnderwater(rx, k + 1, rz, d);
							}

							else if(k < fluidHeight - stack)
							{
								for(int l = 0; l < stack; l++)
								{
									sliver.set(k + l + 1, d);
									checkUnderwater(rx, k + l + 1, rz, d);
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

		double wx = getModifiedX(x, z);
		double wz = getModifiedZ(x, z);
		IrisRegion region = sampleRegion(x, z);
		int height = sampleHeight(x, z);
		double sh = region.getShoreHeight(wx, wz);
		IrisBiome current = sampleBiome(x, z).getBiome();

		// Stop shores from spawning on land
		if(current.isShore() && height > sh)
		{
			return glBiome.generateLandData(wx, wz, region);
		}

		// Stop land & shore from spawning underwater
		if(current.isShore() || current.isLand() && height <= getDimension().getFluidHeight())
		{
			return glBiome.generateSeaData(wx, wz, region);
		}

		// Stop oceans from spawning on land
		if(current.isSea() && height > getDimension().getFluidHeight())
		{
			return glBiome.generateLandData(wx, wz, region);
		}

		// Stop land from spawning underwater
		if(height <= getDimension().getFluidHeight())
		{
			return glBiome.generateSeaData(wx, wz, region);
		}

		// Stop land from spawning where shores go
		if(height <= getDimension().getFluidHeight() + sh)
		{
			return glBiome.generateShoreData(wx, wz, region);
		}

		return glBiome.generateRegionData(wx, wz, region);
	}

	@Override
	protected int onSampleColumnHeight(int cx, int cz, int rx, int rz, int x, int z)
	{
		int fluidHeight = getDimension().getFluidHeight();
		double noise = getNoiseHeight(rx, rz);

		return (int) Math.round(noise) + fluidHeight;
	}
}
