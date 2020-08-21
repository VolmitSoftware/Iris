package com.volmit.iris.gen;

import org.bukkit.Material;
import org.bukkit.World;
import org.bukkit.block.Biome;
import org.bukkit.block.data.Bisected;
import org.bukkit.block.data.Bisected.Half;
import org.bukkit.block.data.BlockData;

import com.volmit.iris.Iris;
import com.volmit.iris.gen.atomics.AtomicSliver;
import com.volmit.iris.gen.atomics.AtomicSliverMap;
import com.volmit.iris.gen.layer.GenLayerBiome;
import com.volmit.iris.gen.layer.GenLayerCarve;
import com.volmit.iris.gen.layer.GenLayerCave;
import com.volmit.iris.noise.CNG;
import com.volmit.iris.object.DecorationPart;
import com.volmit.iris.object.InferredType;
import com.volmit.iris.object.IrisBiome;
import com.volmit.iris.object.IrisBiomeDecorator;
import com.volmit.iris.object.IrisBiomeGeneratorLink;
import com.volmit.iris.object.IrisDepositGenerator;
import com.volmit.iris.object.IrisDimension;
import com.volmit.iris.object.IrisGenerator;
import com.volmit.iris.object.IrisRegion;
import com.volmit.iris.util.B;
import com.volmit.iris.util.BiomeMap;
import com.volmit.iris.util.BiomeResult;
import com.volmit.iris.util.CaveResult;
import com.volmit.iris.util.ChronoLatch;
import com.volmit.iris.util.HeightMap;
import com.volmit.iris.util.IrisInterpolation;
import com.volmit.iris.util.IrisLock;
import com.volmit.iris.util.KList;
import com.volmit.iris.util.KMap;
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
	protected GenLayerBiome glBiome;
	private RNG rockRandom;
	protected IrisLock regLock;
	private KMap<String, IrisGenerator> generators;
	protected CNG masterFracture;
	protected ChronoLatch cwarn = new ChronoLatch(1000);

	public TerrainChunkGenerator(String dimensionName, int threads)
	{
		super(dimensionName, threads);
		generators = new KMap<>();
		regLock = new IrisLock("BiomeChunkGenerator");
	}

	@Override
	public void onInit(World world, RNG rng)
	{
		super.onInit(world, rng);
		loadGenerators();
		glBiome = new GenLayerBiome(this, masterRandom.nextParallelRNG(1));
		masterFracture = CNG.signature(rng.nextParallelRNG(13)).scale(0.12);
		rockRandom = getMasterRandom().nextParallelRNG(2858678);
		glCave = new GenLayerCave(this, rng.nextParallelRNG(238948));
		glCarve = new GenLayerCarve(this, rng.nextParallelRNG(968346576));
	}

	public KList<CaveResult> getCaves(int x, int z)
	{
		return glCave.genCaves(x, z, x & 15, z & 15, null);
	}

	@Override
	protected void onGenerateColumn(int cx, int cz, int rx, int rz, int x, int z, AtomicSliver sliver, BiomeMap biomeMap, boolean sampled)
	{
		if(x > 15 || x < 0 || z > 15 || z < 0)
		{
			throw new RuntimeException("Invalid OnGenerate call: x:" + x + " z:" + z);
		}

		try
		{

			BlockData block;
			int fluidHeight = getDimension().getFluidHeight();
			double ox = getModifiedX(rx, rz);
			double oz = getModifiedZ(rx, rz);
			double wx = getZoomed(ox);
			double wz = getZoomed(oz);
			int depth = 0;
			double noise = getTerrainHeight(rx, rz);
			int height = (int) Math.round(noise);
			boolean carvable = getDimension().isCarving() && height > getDimension().getCarvingMin();
			IrisRegion region = sampleRegion(rx, rz);
			BiomeResult biomeResult = sampleTrueBiome(rx, rz, noise);
			IrisBiome biome = biomeResult.getBiome();

			if(biome == null)
			{
				throw new RuntimeException("Null Biome!");
			}

			KList<BlockData> layers = biome.generateLayers(rx, rz, masterRandom, height, height - getFluidHeight());
			KList<BlockData> seaLayers = biome.isSea() || biome.isShore() ? biome.generateSeaLayers(rx, rz, masterRandom, fluidHeight - height) : new KList<>();
			boolean caverning = false;
			KList<Integer> cavernHeights = new KList<>();
			int lastCavernHeight = -1;
			boolean biomeAssigned = false;
			int max = Math.max(height, fluidHeight);
			int biomeMax = Math.min(max + 16, 255);

			// From Height to Bedrock
			for(int k = max; k >= 0; k--)
			{
				boolean cavernSurface = false;
				boolean bedrock = k == 0;
				boolean underwater = k > height && k <= fluidHeight;

				// Bedrock
				if(bedrock)
				{
					if(biomeMap != null)
					{
						sliver.set(k, biome.getDerivative());
					}

					sliver.set(k, BEDROCK);
					continue;
				}

				// Carving
				if(carvable && glCarve.isCarved(rx, k, rz))
				{
					if(biomeMap != null)
					{
						sliver.set(k, biome.getDerivative());
					}

					sliver.set(k, CAVE_AIR);
					caverning = true;
					continue;
				}

				// Carved Surface
				else if(carvable && caverning)
				{
					lastCavernHeight = k;
					cavernSurface = true;
					cavernHeights.add(k);
					caverning = false;
				}

				// Set Biome
				if(!biomeAssigned && biomeMap != null)
				{
					biomeAssigned = true;
					sliver.set(k, biome.getGroundBiome(masterRandom, rz, k, rx));
					biomeMap.setBiome(x, z, biome);

					for(int kv = max; kv < biomeMax; kv++)
					{
						Biome skyBiome = biome.getSkyBiome(masterRandom, rz, kv, rx);
						sliver.set(kv, skyBiome);
					}
				}

				if(k <= Math.max(height, fluidHeight))
				{
					sliver.set(k, biome.getGroundBiome(masterRandom, rz, k, rx));
				}

				// Set Sea Material (water/lava)
				if(underwater)
				{
					block = seaLayers.hasIndex(fluidHeight - k) ? seaLayers.get(depth) : getDimension().getFluid(rockRandom, wx, k, wz);
				}

				// Set Surface Material for cavern layer surfaces
				else if(layers.hasIndex(lastCavernHeight - k))
				{
					block = layers.get(lastCavernHeight - k);
				}

				// Set Surface Material for true surface
				else
				{
					block = layers.hasIndex(depth) ? layers.get(depth) : getDimension().getRock(rockRandom, wx, k, wz);
					depth++;
				}

				// Set block and update heightmaps
				sliver.set(k, block);

				// Decorate underwater surface
				if(!cavernSurface && (k == height && B.isSolid(block.getMaterial()) && k < fluidHeight))
				{
					decorateUnderwater(biome, sliver, wx, k, wz, rx, rz, block);
				}

				// Decorate Cavern surfaces, but not the true surface
				if((carvable && cavernSurface) && !(k == Math.max(height, fluidHeight) && block.getMaterial().isSolid() && k < 255 && k >= fluidHeight))
				{
					decorateLand(biome, sliver, wx, k, wz, rx, rz, block);
				}
			}

			// Carve out biomes
			KList<CaveResult> caveResults = glCave.genCaves(rx, rz, x, z, sliver);
			IrisBiome caveBiome = glBiome.generateData(InferredType.CAVE, wx, wz, rx, rz, region).getBiome();

			// Decorate Cave Biome Height Sections
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

			block = sliver.get(Math.max(height, fluidHeight));

			// Decorate True Surface
			if(block.getMaterial().isSolid())
			{
				decorateLand(biome, sliver, wx, Math.max(height, fluidHeight), wz, rx, rz, block);
			}
		}

		catch(Throwable e)
		{
			fail(e);
		}
	}

	@Override
	protected void onGenerate(RNG random, int x, int z, ChunkData data, BiomeGrid grid)
	{
		super.onGenerate(random, x, z, data, grid);
		RNG ro = random.nextParallelRNG((x * x * x) - z);
		IrisRegion region = sampleRegion((x * 16) + 7, (z * 16) + 7);
		IrisBiome biome = sampleTrueBiome((x * 16) + 7, (z * 16) + 7).getBiome();

		for(IrisDepositGenerator k : getDimension().getDeposits())
		{
			k.generate(data, ro, this, x, z);
		}

		for(IrisDepositGenerator k : region.getDeposits())
		{
			for(int l = 0; l < ro.i(k.getMinPerChunk(), k.getMaxPerChunk()); l++)
			{
				k.generate(data, ro, this, x, z);
			}
		}

		for(IrisDepositGenerator k : biome.getDeposits())
		{
			for(int l = 0; l < ro.i(k.getMinPerChunk(), k.getMaxPerChunk()); l++)
			{
				k.generate(data, ro, this, x, z);
			}
		}
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
			if(i.getPartOf().equals(DecorationPart.SHORE_LINE) && (!touchesSea(rx, rz) || k != getFluidHeight()))
			{
				continue;
			}

			BlockData d = i.getBlockData(getMasterRandom().nextParallelRNG((int) (38888 + biome.getRarity() + biome.getName().length() + j++)), rx, rz);

			if(d != null)
			{
				if(!B.canPlaceOnto(d.getMaterial(), block.getMaterial()))
				{
					continue;
				}

				if(d.getMaterial().equals(Material.CACTUS))
				{
					if(!block.getMaterial().equals(Material.SAND) && !block.getMaterial().equals(Material.RED_SAND))
					{
						sliver.set(k, B.getBlockData("RED_SAND"));
					}
				}

				if(d.getMaterial().equals(Material.WHEAT) || d.getMaterial().equals(Material.CARROTS) || d.getMaterial().equals(Material.POTATOES) || d.getMaterial().equals(Material.MELON_STEM) || d.getMaterial().equals(Material.PUMPKIN_STEM))
				{
					if(!block.getMaterial().equals(Material.FARMLAND))
					{
						sliver.set(k, B.getBlockData("FARMLAND"));
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
					int stack = i.getHeight(getMasterRandom().nextParallelRNG((int) (39456 + (10000 * i.getChance()) + i.getStackMax() + i.getStackMin() + i.getZoom())), rx, rz);

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
			BlockData d = i.getBlockData(getMasterRandom().nextParallelRNG(2333877 + biome.getRarity() + biome.getName().length() + +j++), rx, rz);

			if(d != null)
			{
				if(!B.canPlaceOnto(d.getMaterial(), block.getMaterial()))
				{
					continue;
				}

				if(d.getMaterial().equals(Material.CACTUS))
				{
					if(!block.getMaterial().equals(Material.SAND) && !block.getMaterial().equals(Material.RED_SAND))
					{
						sliver.set(k, B.getBlockData("SAND"));
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
					int stack = i.getHeight(getMasterRandom().nextParallelRNG((int) (39456 + (1000 * i.getChance()) + i.getZoom() * 10)), rx, rz);

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

			BlockData d = i.getBlockData(getMasterRandom().nextParallelRNG(2555 + biome.getRarity() + biome.getName().length() + j++), rx, rz);

			if(d != null)
			{
				int stack = i.getHeight(getMasterRandom().nextParallelRNG((int) (239456 + i.getStackMax() + i.getStackMin() + i.getVerticalZoom() + i.getZoom() + i.getBlockData().size() + j)), rx, rz);

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
	protected void onPostGenerate(RNG random, int x, int z, ChunkData data, BiomeGrid grid, HeightMap height, BiomeMap biomeMap, AtomicSliverMap map)
	{
		onPreParallaxPostGenerate(random, x, z, data, grid, height, biomeMap, map);
	}

	protected void onPreParallaxPostGenerate(RNG random, int x, int z, ChunkData data, BiomeGrid grid, HeightMap height, BiomeMap biomeMap, AtomicSliverMap map)
	{

	}

	protected void onPostParallaxPostGenerate(RNG random, int x, int z, ChunkData data, BiomeGrid grid, HeightMap height, BiomeMap biomeMap, AtomicSliverMap map)
	{

	}

	private double getNoiseHeight(int rx, int rz)
	{
		double h = getBiomeHeight(rx, rz);

		return h;
	}

	public BiomeResult sampleTrueBiomeBase(int x, int z, int height)
	{
		if(!getDimension().getFocus().equals(""))
		{
			return focus();
		}

		double wx = getModifiedX(x, z);
		double wz = getModifiedZ(x, z);
		IrisRegion region = sampleRegion(x, z);
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
		return sampleTrueBiome(x, z, getTerrainHeight(x, z));
	}

	public IrisRegion sampleRegion(int x, int z)
	{
		return getCache().getRegion(x, z, () ->
		{
			double wx = getModifiedX(x, z);
			double wz = getModifiedZ(x, z);
			return glBiome.getRegion(wx, wz);
		});
	}

	public BiomeResult sampleTrueBiome(int x, int z, double noise)
	{
		if(!getDimension().getFocus().equals(""))
		{
			return focus();
		}

		return getCache().getBiome(x, z, () ->
		{
			double wx = getModifiedX(x, z);
			double wz = getModifiedZ(x, z);
			IrisRegion region = sampleRegion(x, z);
			int height = (int) Math.round(noise);
			double sh = region.getShoreHeight(wx, wz);
			BiomeResult res = sampleTrueBiomeBase(x, z, height);
			IrisBiome current = res.getBiome();

			if(current.isSea() && height > getDimension().getFluidHeight() - sh)
			{
				return glBiome.generateData(InferredType.SHORE, wx, wz, x, z, region);
			}

			return res;
		});
	}

	@Override
	protected int onSampleColumnHeight(int cx, int cz, int rx, int rz, int x, int z)
	{
		return (int) Math.round(getTerrainHeight(rx, rz));
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
		return getCache().getHeight(x, z, () -> getNoiseHeight(x, z) + getFluidHeight());
	}

	public double getTerrainWaterHeight(int x, int z)
	{
		return Math.max(getTerrainHeight(x, z), getFluidHeight());
	}

	@Override
	public void onHotload()
	{
		super.onHotload();
		loadGenerators();
		glBiome = new GenLayerBiome(this, masterRandom.nextParallelRNG(1));
	}

	public void registerGenerator(IrisGenerator g, IrisDimension dim)
	{
		KMap<String, IrisGenerator> generators = this.generators;

		regLock.lock();
		if(g.getLoadKey() == null || generators.containsKey(g.getLoadKey()))
		{
			regLock.unlock();
			return;
		}

		regLock.unlock();
		generators.put(g.getLoadKey(), g);
	}

	protected KMap<String, IrisGenerator> getGenerators()
	{
		return generators;
	}

	protected double getBiomeHeight(double rrx, double rrz)
	{
		double rx = rrx;
		double rz = rrz;
		double h = 0;

		for(IrisGenerator i : getGenerators().values())
		{
			h += interpolateGenerator(rx, rz, i);
		}

		return h;
	}

	protected double interpolateGenerator(double rx, double rz, IrisGenerator gen)
	{
		double hi = IrisInterpolation.getNoise(gen.getInterpolationFunction(), (int) Math.round(rx), (int) Math.round(rz), gen.getInterpolationScale(), (xx, zz) ->
		{
			try
			{
				IrisBiome b = sampleBiome((int) xx, (int) zz).getBiome();

				for(IrisBiomeGeneratorLink i : b.getGenerators())
				{
					if(i.getGenerator().equals(gen.getLoadKey()))
					{
						return i.getMax();
					}
				}
			}

			catch(Throwable e)
			{
				Iris.warn("Failed to sample hi biome at " + rx + " " + rz + " using the generator " + gen.getLoadKey());
				fail(e);
			}
			return 0;
		});

		double lo = IrisInterpolation.getNoise(gen.getInterpolationFunction(), (int) Math.round(rx), (int) Math.round(rz), gen.getInterpolationScale(), (xx, zz) ->
		{
			try
			{
				IrisBiome b = sampleBiome((int) xx, (int) zz).getBiome();

				for(IrisBiomeGeneratorLink i : b.getGenerators())
				{
					if(i.getGenerator().equals(gen.getLoadKey()))
					{
						return i.getMin();
					}
				}
			}

			catch(Throwable e)
			{
				Iris.warn("Failed to sample lo biome at " + rx + " " + rz + " using the generator " + gen.getLoadKey());
				fail(e);
			}

			return 0;
		});

		return M.lerp(lo, hi, gen.getHeight(rx, rz, world.getSeed() + 239945));
	}

	protected void loadGenerators()
	{
		generators.clear();
		loadGenerators(getDimension());
	}

	protected void loadGenerators(IrisDimension dim)
	{
		if(dim == null)
		{
			return;
		}

		KList<String> touch = new KList<>();
		KList<String> loadQueue = new KList<>();

		for(String i : dim.getRegions())
		{
			IrisRegion r = loadRegion(i);

			if(r != null)
			{
				loadQueue.addAll(r.getLandBiomes());
				loadQueue.addAll(r.getSeaBiomes());
				loadQueue.addAll(r.getShoreBiomes());
				loadQueue.addAll(r.getRidgeBiomeKeys());
				loadQueue.addAll(r.getSpotBiomeKeys());
			}
		}

		while(!loadQueue.isEmpty())
		{
			String next = loadQueue.pop();

			if(!touch.contains(next))
			{
				touch.add(next);
				IrisBiome biome = loadBiome(next);
				biome.getGenerators().forEach((i) -> registerGenerator(i.getCachedGenerator(this), dim));
				loadQueue.addAll(biome.getChildren());
			}
		}
	}

	public BiomeResult sampleBiome(int x, int z)
	{
		return getCache().getRawBiome(x, z, () ->
		{
			if(!getDimension().getFocus().equals(""))
			{
				IrisBiome biome = loadBiome(getDimension().getFocus());

				for(String i : getDimension().getRegions())
				{
					IrisRegion reg = loadRegion(i);

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
			IrisRegion region = glBiome.getRegion(wx, wz);
			BiomeResult res = glBiome.generateRegionData(wx, wz, x, z, region);

			return res;
		});
	}
}
