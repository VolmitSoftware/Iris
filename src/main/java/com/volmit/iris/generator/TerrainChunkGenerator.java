package com.volmit.iris.generator;

import java.util.concurrent.locks.ReentrantLock;

import org.bukkit.Material;
import org.bukkit.World;
import org.bukkit.block.data.Bisected;
import org.bukkit.block.data.Bisected.Half;
import org.bukkit.block.data.BlockData;

import com.volmit.iris.Iris;
import com.volmit.iris.layer.GenLayerCarve;
import com.volmit.iris.layer.GenLayerCave;
import com.volmit.iris.object.DecorationPart;
import com.volmit.iris.object.InferredType;
import com.volmit.iris.object.IrisBiome;
import com.volmit.iris.object.IrisBiomeDecorator;
import com.volmit.iris.object.IrisRegion;
import com.volmit.iris.object.atomics.AtomicSliver;
import com.volmit.iris.util.BiomeMap;
import com.volmit.iris.util.BiomeResult;
import com.volmit.iris.util.BlockDataTools;
import com.volmit.iris.util.CaveResult;
import com.volmit.iris.util.HeightMap;
import com.volmit.iris.util.KList;
import com.volmit.iris.util.M;
import com.volmit.iris.util.RNG;

import lombok.Data;
import lombok.EqualsAndHashCode;

@Data
@EqualsAndHashCode(callSuper = false)
public abstract class TerrainChunkGenerator extends ParallelChunkGenerator
{
	private long lastUpdateRequest = M.ms();
	private long lastChunkLoad = M.ms();
	private GenLayerCave glCave;
	private GenLayerCarve glCarve;
	private RNG rockRandom;
	private int[] cacheHeightMap;
	private IrisBiome[] cacheTrueBiome;
	private ReentrantLock cacheLock;

	public TerrainChunkGenerator(String dimensionName, int threads)
	{
		super(dimensionName, threads);
		cacheHeightMap = new int[256];
		cacheTrueBiome = new IrisBiome[256];
		cachingAllowed = true;
		cacheLock = new ReentrantLock();
	}

	public void onInit(World world, RNG rng)
	{
		super.onInit(world, rng);
		rockRandom = getMasterRandom().nextParallelRNG(2858678);
		glCave = new GenLayerCave(this, rng.nextParallelRNG(238948));
		glCarve = new GenLayerCarve(this, rng.nextParallelRNG(968346576));
	}

	public KList<CaveResult> getCaves(int x, int z)
	{
		return glCave.genCaves(x, z, x & 15, z & 15, null);
	}

	@Override
	protected void onGenerate(RNG random, int x, int z, ChunkData data, BiomeGrid grid)
	{
		super.onGenerate(random, x, z, data, grid);
	}

	@Override
	protected void onGenerateColumn(int cx, int cz, int rx, int rz, int x, int z, AtomicSliver sliver, BiomeMap biomeMap, int onlyY)
	{
		if(x > 15 || x < 0 || z > 15 || z < 0)
		{
			throw new RuntimeException("Invalid OnGenerate call: x:" + x + " z:" + z);
		}

		try
		{
			int highestPlaced = 0;
			BlockData block;
			int fluidHeight = getDimension().getFluidHeight();
			double ox = getModifiedX(rx, rz);
			double oz = getModifiedZ(rx, rz);
			double wx = getZoomed(ox);
			double wz = getZoomed(oz);
			int depth = 0;
			double noise = getNoiseHeight(rx, rz);
			int height = (int) Math.round(noise) + fluidHeight;
			boolean carvable = getDimension().isCarving() && height > getDimension().getCarvingMin();
			IrisRegion region = sampleRegion(rx, rz);
			IrisBiome biome = sampleTrueBiome(rx, rz).getBiome();

			if(biome == null)
			{
				throw new RuntimeException("Null Biome!");
			}

			if(cachingAllowed)
			{
				try
				{
					cacheTrueBiome[(z << 4) | x] = biome;
					cacheHeightMap[(z << 4) | x] = height;
				}

				catch(Throwable e)
				{
					Iris.error("Failed to write cache at " + x + " " + z + " in chunk " + cx + " " + cz);
				}
			}

			KList<BlockData> layers = biome.generateLayers(wx, wz, masterRandom, height, height - getFluidHeight());
			KList<BlockData> seaLayers = biome.isSea() ? biome.generateSeaLayers(wx, wz, masterRandom, fluidHeight - height) : new KList<>();
			cacheInternalBiome(x, z, biome);
			boolean caverning = false;
			KList<Integer> cavernHeights = new KList<>();
			int lastCavernHeight = -1;

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
				boolean cavernSurface = false;

				if(k == 0)
				{
					if(biomeMap != null)
					{
						sliver.set(k, biome.getDerivative());
						biomeMap.setBiome(x, z, biome);
					}

					sliver.set(k, BEDROCK);
					continue;
				}

				if(carvable && glCarve.isCarved(rx, k, rz))
				{
					if(biomeMap != null)
					{
						sliver.set(k, biome.getDerivative());
						biomeMap.setBiome(x, z, biome);
					}

					sliver.set(k, CAVE_AIR);
					caverning = true;
					continue;
				}

				else if(carvable && caverning)
				{
					lastCavernHeight = k;
					cavernSurface = true;
					cavernHeights.add(k);
					caverning = false;
				}

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

				else if(layers.hasIndex(lastCavernHeight - k))
				{
					block = layers.get(lastCavernHeight - k);
				}

				else
				{
					block = layers.hasIndex(depth) ? layers.get(depth) : getDimension().getRock(rockRandom, wx, k, wz);
					depth++;
				}

				sliver.set(k, block);
				highestPlaced = Math.max(highestPlaced, k);

				if(!cavernSurface && (k == height && block.getMaterial().isSolid() && k < fluidHeight))
				{
					decorateUnderwater(biome, sliver, wx, k, wz, rx, rz, block);
				}

				if((carvable && cavernSurface) || (k == Math.max(height, fluidHeight) && block.getMaterial().isSolid() && k < 255 && k > fluidHeight))
				{
					decorateLand(biome, sliver, wx, k, wz, rx, rz, block);
				}
			}

			KList<CaveResult> caveResults = glCave.genCaves(rx, rz, x, z, sliver);
			IrisBiome caveBiome = glBiome.generateData(InferredType.CAVE, wx, wz, rx, rz, region).getBiome();

			if(caveBiome != null)
			{
				for(CaveResult i : caveResults)
				{
					for(int j = i.getFloor(); j <= i.getCeiling(); j++)
					{
						sliver.set(j, caveBiome);
						sliver.set(j, caveBiome.getGroundBiome(masterRandom, rz, j, rx));
					}

					KList<BlockData> floor = caveBiome.generateLayers(wx, wz, rockRandom, i.getFloor() - 2, i.getFloor() - 2);
					KList<BlockData> ceiling = caveBiome.generateLayers(wx + 256, wz + 256, rockRandom, height - i.getCeiling() - 2, height - i.getCeiling() - 2);
					BlockData blockc = null;
					for(int j = 0; j < floor.size(); j++)
					{
						if(j == 0)
						{
							blockc = floor.get(j);
						}

						sliver.set(i.getFloor() - j, floor.get(j));
					}

					for(int j = ceiling.size() - 1; j > 0; j--)
					{
						sliver.set(i.getCeiling() + j, ceiling.get(j));
					}

					if(blockc != null && !sliver.isSolid(i.getFloor() + 1))
					{
						decorateCave(caveBiome, sliver, wx, i.getFloor(), wz, rx, rz, blockc);
					}
				}
			}

			if(cachingAllowed && highestPlaced < height)
			{
				cacheHeightMap[(z << 4) | x] = highestPlaced;
			}
		}

		catch(Throwable e)
		{
			fail(e);
		}
	}

	protected boolean canPlace(Material mat, Material onto)
	{
		if(onto.equals(Material.GRASS_PATH))
		{
			if(!mat.isSolid())
			{
				return false;
			}
		}

		if(onto.equals(Material.STONE) || onto.equals(Material.ANDESITE) || onto.equals(Material.GRANITE) || onto.equals(Material.DIORITE) || onto.equals(Material.BLACKSTONE) || onto.equals(Material.BASALT))
		{
			if(mat.equals(Material.POPPY) || mat.equals(Material.DANDELION) || mat.equals(Material.CORNFLOWER) || mat.equals(Material.ORANGE_TULIP) || mat.equals(Material.PINK_TULIP) || mat.equals(Material.RED_TULIP) || mat.equals(Material.WHITE_TULIP) || mat.equals(Material.FERN) || mat.equals(Material.LARGE_FERN) || mat.equals(Material.GRASS) || mat.equals(Material.TALL_GRASS))
			{
				return false;
			}
		}

		if(onto.equals(Material.ACACIA_LEAVES) || onto.equals(Material.BIRCH_LEAVES) || onto.equals(Material.DARK_OAK_LEAVES) || onto.equals(Material.JUNGLE_LEAVES) || onto.equals(Material.OAK_LEAVES) || onto.equals(Material.SPRUCE_LEAVES))
		{
			if(!mat.isSolid())
			{
				return false;
			}
		}

		return true;
	}

	private void decorateLand(IrisBiome biome, AtomicSliver sliver, double wx, int k, double wz, int rx, int rz, BlockData block)
	{
		if(!getDimension().isDecorate())
		{
			return;
		}

		int j = 0;

		for(IrisBiomeDecorator i : biome.getDecorators())
		{
			if(i.getPartOf().equals(DecorationPart.SHORE_LINE) && !touchesSea(rx, rz))
			{
				continue;
			}

			BlockData d = i.getBlockData(getMasterRandom().nextParallelRNG((int) (38888 + biome.getRarity() + biome.getName().length() + j++)), wx, wz);

			if(d != null)
			{
				if(!canPlace(d.getMaterial(), block.getMaterial()))
				{
					continue;
				}

				if(d.getMaterial().equals(Material.CACTUS))
				{
					if(!block.getMaterial().equals(Material.SAND) && !block.getMaterial().equals(Material.RED_SAND))
					{
						sliver.set(k, BlockDataTools.getBlockData("RED_SAND"));
					}
				}

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
					int stack = i.getHeight(getMasterRandom().nextParallelRNG((int) (39456 + (10000 * i.getChance()) + i.getStackMax() + i.getStackMin() + i.getZoom())), wx, wz);

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

	private void decorateCave(IrisBiome biome, AtomicSliver sliver, double wx, int k, double wz, int rx, int rz, BlockData block)
	{
		if(!getDimension().isDecorate())
		{
			return;
		}

		int j = 0;

		for(IrisBiomeDecorator i : biome.getDecorators())
		{
			BlockData d = i.getBlockData(getMasterRandom().nextParallelRNG(2333877 + biome.getRarity() + biome.getName().length() + +j++), wx, wz);

			if(d != null)
			{
				if(!canPlace(d.getMaterial(), block.getMaterial()))
				{
					continue;
				}

				if(d.getMaterial().equals(Material.CACTUS))
				{
					if(!block.getMaterial().equals(Material.SAND) && !block.getMaterial().equals(Material.RED_SAND))
					{
						sliver.set(k, BlockDataTools.getBlockData("SAND"));
					}
				}

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
					int stack = i.getHeight(getMasterRandom().nextParallelRNG((int) (39456 + (1000 * i.getChance()) + i.getZoom() * 10)), wx, wz);

					if(stack == 1)
					{
						sliver.set(k + 1, d);
					}

					else if(k < 255 - stack)
					{
						for(int l = 0; l < stack; l++)
						{
							if(sliver.isSolid(k + l + 1))
							{
								break;
							}

							sliver.set(k + l + 1, d);
						}
					}
				}

				break;
			}
		}
	}

	private void decorateUnderwater(IrisBiome biome, AtomicSliver sliver, double wx, int y, double wz, int rx, int rz, BlockData block)
	{
		if(!getDimension().isDecorate())
		{
			return;
		}

		int j = 0;

		for(IrisBiomeDecorator i : biome.getDecorators())
		{
			if(biome.getInferredType().equals(InferredType.SHORE))
			{
				continue;
			}

			BlockData d = i.getBlockData(getMasterRandom().nextParallelRNG(2555 + biome.getRarity() + biome.getName().length() + j++), wx, wz);

			if(d != null)
			{
				int stack = i.getHeight(getMasterRandom().nextParallelRNG((int) (239456 + i.getStackMax() + i.getStackMin() + i.getVerticalZoom() + i.getZoom() + i.getBlockData().size() + j)), wx, wz);

				if(stack == 1)
				{
					sliver.set(i.getPartOf().equals(DecorationPart.SEA_SURFACE) ? (getFluidHeight() + 1) : (y + 1), d);
				}

				else if(y < getFluidHeight() - stack)
				{
					for(int l = 0; l < stack; l++)
					{
						sliver.set(i.getPartOf().equals(DecorationPart.SEA_SURFACE) ? (getFluidHeight() + 1 + l) : (y + l + 1), d);
					}
				}

				break;
			}
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

	}

	protected double getNoiseHeight(int rx, int rz)
	{
		double wx = getZoomed(rx);
		double wz = getZoomed(rz);

		return getBiomeHeight(wx, wz);
	}

	public BiomeResult sampleTrueBiomeBase(int x, int z)
	{
		if(!getDimension().getFocus().equals(""))
		{
			return focus();
		}

		double wx = getModifiedX(x, z);
		double wz = getModifiedZ(x, z);
		IrisRegion region = sampleRegion(x, z);
		int height = (int) Math.round(getTerrainHeight(x, z));
		double sh = region.getShoreHeight(wx, wz);
		IrisBiome current = sampleBiome(x, z).getBiome();

		if(current.isShore() && height > sh)
		{
			return glBiome.generateData(InferredType.LAND, wx, wz, x, z, region);
		}

		if(current.isShore() || current.isLand() && height <= getDimension().getFluidHeight())
		{
			return glBiome.generateData(InferredType.SEA, wx, wz, x, z, region);
		}

		if(current.isSea() && height > getDimension().getFluidHeight())
		{
			return glBiome.generateData(InferredType.LAND, wx, wz, x, z, region);
		}

		if(height <= getDimension().getFluidHeight())
		{
			return glBiome.generateData(InferredType.SEA, wx, wz, x, z, region);
		}

		if(height <= getDimension().getFluidHeight() + sh)
		{
			return glBiome.generateData(InferredType.SHORE, wx, wz, x, z, region);
		}

		return glBiome.generateRegionData(wx, wz, x, z, region);
	}

	public BiomeResult sampleCaveBiome(int x, int z)
	{
		double wx = getModifiedX(x, z);
		double wz = getModifiedZ(x, z);
		return glBiome.generateData(InferredType.CAVE, wx, wz, x, z, sampleRegion(x, z));
	}

	public BiomeResult sampleTrueBiome(int x, int y, int z)
	{
		if(y < getTerrainHeight(x, z))
		{
			double wx = getModifiedX(x, z);
			double wz = getModifiedZ(x, z);
			BiomeResult r = glBiome.generateData(InferredType.CAVE, wx, wz, x, z, sampleRegion(x, z));

			if(r.getBiome() != null)
			{
				return r;
			}
		}

		return sampleTrueBiome(x, z);
	}

	public BiomeResult sampleTrueBiome(int x, int z)
	{
		if(!getDimension().getFocus().equals(""))
		{
			return focus();
		}

		if(isSafe() && x >> 4 == cacheX && z >> 4 == cacheZ)
		{
			return new BiomeResult(cacheTrueBiome[((z & 15) << 4) | (x & 15)], 0);
		}

		double wx = getModifiedX(x, z);
		double wz = getModifiedZ(x, z);
		IrisRegion region = sampleRegion(x, z);
		int height = sampleHeight(x, z);
		double sh = region.getShoreHeight(wx, wz);
		BiomeResult res = sampleTrueBiomeBase(x, z);
		IrisBiome current = res.getBiome();

		if(current.isSea() && height > getDimension().getFluidHeight() - sh)
		{
			return glBiome.generateData(InferredType.SHORE, wx, wz, x, z, region);
		}

		return res;
	}

	@Override
	protected int onSampleColumnHeight(int cx, int cz, int rx, int rz, int x, int z)
	{
		int fluidHeight = getDimension().getFluidHeight();
		double noise = getNoiseHeight(rx, rz);

		return (int) Math.round(noise) + fluidHeight;
	}

	private boolean touchesSea(int rx, int rz)
	{
		return isFluidAtHeight(rx + 1, rz) || isFluidAtHeight(rx - 1, rz) || isFluidAtHeight(rx, rz - 1) || isFluidAtHeight(rx, rz + 1);
	}

	public boolean isUnderwater(int x, int z)
	{
		return isFluidAtHeight(x, z);
	}

	public boolean isFluidAtHeight(int x, int z)
	{
		return Math.round(getTerrainHeight(x, z)) < getFluidHeight();
	}

	public int getFluidHeight()
	{
		return getDimension().getFluidHeight();
	}

	public double getTerrainHeight(int x, int z)
	{
		if(isSafe() && x >> 4 == cacheX && z >> 4 == cacheZ)
		{
			return cacheHeightMap[((z & 15) << 4) | (x & 15)];
		}

		return getNoiseHeight(x, z) + getFluidHeight();
	}

	public double getTerrainWaterHeight(int x, int z)
	{
		return Math.max(getTerrainHeight(x, z), getFluidHeight());
	}
}
