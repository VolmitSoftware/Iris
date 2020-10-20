package com.volmit.iris.gen;

import org.bukkit.Material;
import org.bukkit.block.Biome;
import org.bukkit.block.data.Bisected;
import org.bukkit.block.data.Bisected.Half;

import com.volmit.iris.Iris;
import com.volmit.iris.gen.atomics.AtomicSliver;
import com.volmit.iris.gen.atomics.AtomicSliverMap;
import com.volmit.iris.gen.layer.GenLayerBiome;
import com.volmit.iris.gen.layer.GenLayerCarve;
import com.volmit.iris.gen.layer.GenLayerCave;
import com.volmit.iris.gen.layer.GenLayerRavine;
import com.volmit.iris.gen.scaffold.GeneratedChunk;
import com.volmit.iris.gen.scaffold.TerrainChunk;
import com.volmit.iris.gen.scaffold.TerrainTarget;
import com.volmit.iris.noise.CNG;
import com.volmit.iris.object.DecorationPart;
import com.volmit.iris.object.InferredType;
import com.volmit.iris.object.IrisBiome;
import com.volmit.iris.object.IrisBiomeDecorator;
import com.volmit.iris.object.IrisCaveFluid;
import com.volmit.iris.object.IrisDepositGenerator;
import com.volmit.iris.object.IrisDimension;
import com.volmit.iris.object.IrisGenerator;
import com.volmit.iris.object.IrisRegion;
import com.volmit.iris.object.IrisShapedGeneratorStyle;
import com.volmit.iris.util.B;
import com.volmit.iris.util.BiomeMap;
import com.volmit.iris.util.CaveResult;
import com.volmit.iris.util.ChronoLatch;
import com.volmit.iris.util.FastBlockData;
import com.volmit.iris.util.HeightMap;
import com.volmit.iris.util.IrisLock;
import com.volmit.iris.util.KList;
import com.volmit.iris.util.KMap;
import com.volmit.iris.util.M;
import com.volmit.iris.util.PrecisionStopwatch;
import com.volmit.iris.util.RNG;

import lombok.Data;
import lombok.EqualsAndHashCode;

@Data
@EqualsAndHashCode(callSuper = false)
public abstract class TopographicTerrainProvider extends ParallelTerrainProvider
{
	private long lastUpdateRequest = M.ms();
	private long lastChunkLoad = M.ms();
	private GenLayerCave glCave;
	private GenLayerCarve glCarve;
	private GenLayerBiome glBiome;
	private GenLayerRavine glRavine;
	private RNG rockRandom;
	private IrisLock regionLock;
	private KMap<String, IrisGenerator> generators;
	private KList<IrisGenerator> generatorList;
	private CNG masterFracture;
	private int generatorSize;
	private ChronoLatch cwarn = new ChronoLatch(1000);

	public TopographicTerrainProvider(TerrainTarget t, String dimensionName, int threads)
	{
		super(t, dimensionName, threads);
		setGeneratorSize(0);
		setGenerators(new KMap<>());
		setGeneratorList(new KList<>());
		setRegionLock(new IrisLock("BiomeChunkGenerator"));
	}

	@Override
	public void onInit(RNG rng)
	{
		generatorSize = 0;
		super.onInit(rng);
		getData().preferFolder(getDimension().getLoadFile().getParentFile().getParentFile().getName());
		buildGenLayers(getMasterRandom());
		loadGenerators();
	}

	private void buildGenLayers(RNG rng)
	{
		setGlBiome(new GenLayerBiome(this, rng.nextParallelRNG(24671)));
		setMasterFracture(CNG.signature(rng.nextParallelRNG(13)).scale(0.12));
		setRockRandom(getMasterRandom().nextParallelRNG(2858678));
		setGlCave(new GenLayerCave(this, rng.nextParallelRNG(238948)));
		setGlCarve(new GenLayerCarve(this, rng.nextParallelRNG(968346576)));
		setGlRavine(new GenLayerRavine(this, rng.nextParallelRNG(-229354923)));
	}

	public int getCarvedHeight(int x, int z, boolean ignoreFluid)
	{
		if(ignoreFluid)
		{
			return getCache().getCarvedHeightIgnoreWater(x, z);
		}

		return getCache().getCarvedHeight(x, z);
	}

	public int getCarvedHeight(int x, int z)
	{
		return getCarvedHeight(x, z, false);
	}

	public int getCarvedWaterHeight(int x, int z)
	{
		return getCarvedHeight(x, z, true);
	}

	public KList<CaveResult> getCaves(int x, int z)
	{
		return glCave.genCaves(x, z, x & 15, z & 15, null);
	}

	@Override
	protected void onPreGenerate(RNG random, int x, int z, TerrainChunk terrain, HeightMap height, BiomeMap biomeMap, AtomicSliverMap map)
	{

	}

	@Override
	protected int onGenerateColumn(int cx, int cz, int rx, int rz, int x, int z, AtomicSliver sliver, BiomeMap biomeMap, boolean sampled)
	{
		if(x > 15 || x < 0 || z > 15 || z < 0)
		{
			throw new RuntimeException("Invalid OnGenerate call: x:" + x + " z:" + z);
		}

		RNG crand = getMasterRandom().nextParallelRNG(rx).nextParallelRNG(rz);
		FastBlockData block;
		int fluidHeight = getDimension().getFluidHeight();
		double ox = getModifiedX(rx, rz);
		double oz = getModifiedZ(rx, rz);
		double wx = getZoomed(ox);
		double wz = getZoomed(oz);
		int depth = 0;
		double noise = getTerrainHeight(rx, rz);
		int height = (int) Math.round(noise);
		boolean carvable = getGlCarve().couldCarveBelow(height);
		IrisRegion region = sampleRegion(rx, rz);
		IrisBiome biome = sampleTrueBiome(rx, rz);
		IrisBiome carveBiome = null;
		Biome onlyBiome = Iris.biome3d ? null : biome.getGroundBiome(getMasterRandom(), rz, getDimension().getFluidHeight(), rx);
		IrisCaveFluid forceFluid = getDimension().getForceFluid().hasFluid(getData()) ? getDimension().getForceFluid() : null;

		if(biome == null)
		{
			throw new RuntimeException("Null Biome!");
		}

		KList<FastBlockData> layers = biome.generateLayers(rx, rz, getMasterRandom(), height, height - getFluidHeight(), getData());
		KList<FastBlockData> cavernLayers = null;
		KList<FastBlockData> seaLayers = biome.isAquatic() || biome.isShore() ? biome.generateSeaLayers(rx, rz, getMasterRandom(), fluidHeight - height, getData()) : new KList<>();
		FastBlockData biomeFluid = biome.getFluidType().isEmpty() ? null : B.get(biome.getFluidType());

		boolean caverning = false;
		KList<Integer> cavernHeights = new KList<>();
		int lastCavernHeight = -1;
		boolean biomeAssigned = false;
		int max = Math.max(height, fluidHeight);
		int biomeMax = Math.min(max + 32, 255);

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
			if(carvable && getGlCarve().isCarved(rx, k, rz))
			{
				if(biomeMap != null)
				{
					if(carveBiome == null)
					{
						carveBiome = biome.getRealCarvingBiome(getData());
					}

					sliver.set(k, carveBiome.getDerivative());
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
			if(!biomeAssigned && biomeMap != null && k < max)
			{
				biomeAssigned = true;

				if(Iris.biome3d)
				{
					sliver.set(k, biome.getGroundBiome(getMasterRandom(), rz, k, rx));

					for(int kv = max; kv <= biomeMax; kv++)
					{
						sliver.set(kv, biome.getSkyBiome(getMasterRandom(), rz, kv, rx));
					}
				}

				else
				{
					sliver.set(getFluidHeight(), onlyBiome);
				}

				biomeMap.setBiome(x, z, biome);
			}

			if(Iris.biome3d && k <= Math.max(height, fluidHeight))
			{
				sliver.set(k, biome.getGroundBiome(getMasterRandom(), rz, k, rx));
			}

			// Set Sea Material (water/lava)
			if(underwater)
			{
				block = seaLayers.hasIndex(fluidHeight - k) ? seaLayers.get(depth) : biomeFluid != null ? biomeFluid : getDimension().getFluidPalette().get(rockRandom, wx, k, wz, getData());
			}

			// Set Surface Material for cavern layer surfaces
			else if(carvable && cavernHeights.isNotEmpty() && lastCavernHeight - k >= 0 && lastCavernHeight - k < 5)
			{
				if(carveBiome == null)
				{
					carveBiome = biome.getRealCarvingBiome(getData());
				}

				if(cavernLayers == null)
				{
					cavernLayers = carveBiome.generateLayers(rx, rz, getMasterRandom(), 5, height - getFluidHeight(), getData());
				}

				block = cavernLayers.hasIndex(lastCavernHeight - k) ? cavernLayers.get(lastCavernHeight - k) : cavernLayers.get(0);
			}

			// Set Surface Material for true surface
			else
			{
				block = layers.hasIndex(depth) ? layers.get(depth) : getDimension().getRockPalette().get(rockRandom, wx, k, wz, getData());
				depth++;
			}

			// Set block and update heightmaps
			sliver.set(k, block);

			// Decorate underwater surface
			if(!cavernSurface && (k == height && B.isSolid(block.getMaterial()) && k < fluidHeight))
			{
				decorateUnderwater(crand, biome, sliver, k, rx, rz);
			}

			// Decorate Cavern surfaces, but not the true surface
			if((carvable && cavernSurface) && !(k == Math.max(height, fluidHeight) && block.getMaterial().isSolid() && k < 255 && k >= fluidHeight))
			{
				if(carveBiome == null)
				{
					carveBiome = biome.getRealCarvingBiome(getData());
				}

				decorateLand(crand, carveBiome, sliver, k, rx, rz, block);
			}

			if(forceFluid != null && B.isAir(block) && (forceFluid.isInverseHeight() ? k >= forceFluid.getFluidHeight() : k <= forceFluid.getFluidHeight()))
			{
				sliver.set(k, forceFluid.getFluid(getData()));
			}
		}

		// Carve out biomes
		KList<CaveResult> caveResults = glCave.genCaves(rx, rz, x, z, sliver);

		IrisBiome caveBiome = glBiome.generateData(InferredType.CAVE, wx, wz, rx, rz, region);

		// Decorate Cave Biome Height Sections
		if(caveBiome != null)
		{
			for(CaveResult i : caveResults)
			{
				if(i.getFloor() < 0 || i.getFloor() > 255 || i.getCeiling() > 255 || i.getCeiling() < 0)
				{
					continue;
				}

				if(Iris.biome3d)
				{

					for(int j = i.getFloor(); j <= i.getCeiling(); j++)
					{
						sliver.set(j, caveBiome.getGroundBiome(getMasterRandom(), rz, j, rx));
					}
				}

				KList<FastBlockData> floor = caveBiome.generateLayers(wx, wz, rockRandom, i.getFloor() - 2, i.getFloor() - 2, getData());
				KList<FastBlockData> ceiling = caveBiome.generateLayers(wx + 256, wz + 256, rockRandom, (carvable ? getCarvedWaterHeight(rx, rz) : height) - i.getCeiling() - 2, (carvable ? getCarvedWaterHeight(rx, rz) : height) - i.getCeiling() - 2, getData());
				FastBlockData blockc = null;
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
					decorateCave(crand, caveBiome, sliver, i.getFloor(), rx, rz, blockc);
				}
			}
		}

		block = sliver.get(Math.max(height, fluidHeight));

		// Decorate True Surface
		if(block.getMaterial().isSolid())
		{
			decorateLand(crand, biome, sliver, Math.max(height, fluidHeight), rx, rz, block);
		}

		return height;
	}

	@Override
	protected GeneratedChunk onGenerate(RNG random, int x, int z, TerrainChunk terrain)
	{
		GeneratedChunk map = super.onGenerate(random, x, z, terrain);

		if(!getDimension().isVanillaCaves())
		{
			generateDeposits(random.nextParallelRNG(x * ((z * 39) + 10000)).nextParallelRNG(z + z - x), terrain, x, z);
		}

		return map;
	}

	private void decorateLand(RNG rng, IrisBiome biome, AtomicSliver sliver, int k, int rx, int rz, FastBlockData block)
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

			FastBlockData d = i.getBlockData(biome, rng.nextParallelRNG(38888 + biome.getRarity() + biome.getName().length() + j++), rx, rz, getData());

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

				if(d.getBlockData() instanceof Bisected && k < 254)
				{
					FastBlockData bb = d.clone();
					Bisected t = ((Bisected) d.getBlockData());
					t.setHalf(Half.TOP);
					Bisected b = ((Bisected) bb.getBlockData());
					b.setHalf(Half.BOTTOM);
					sliver.set(k + 1, bb);
					sliver.set(k + 2, d);
				}

				else
				{
					int stack = i.getHeight(rng.nextParallelRNG((int) (39456 + (10000 * i.getChance()) + i.getStackMax() + i.getStackMin() + i.getZoom())), rx, rz, getData());

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

	private void decorateCave(RNG rng, IrisBiome biome, AtomicSliver sliver, int k, int rx, int rz, FastBlockData block)
	{
		if(!getDimension().isDecorate())
		{
			return;
		}

		int j = 0;

		for(IrisBiomeDecorator i : biome.getDecorators())
		{
			FastBlockData d = i.getBlockData(biome, rng.nextParallelRNG(2333877 + biome.getRarity() + biome.getName().length() + +j++), rx, rz, getData());

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

				if(d.getBlockData() instanceof Bisected && k < 254)
				{
					FastBlockData bb = d.clone();
					Bisected t = ((Bisected) d.getBlockData());
					t.setHalf(Half.TOP);
					Bisected b = ((Bisected) bb.getBlockData());
					b.setHalf(Half.BOTTOM);
					sliver.set(k + 1, bb);
					sliver.set(k + 2, d);
				}

				else
				{
					int stack = i.getHeight(rng.nextParallelRNG((int) (39456 + (1000 * i.getChance()) + i.getZoom() * 10)), rx, rz, getData());

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

	private void decorateUnderwater(RNG random, IrisBiome biome, AtomicSliver sliver, int y, int rx, int rz)
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

			FastBlockData d = i.getBlockData(biome, getMasterRandom().nextParallelRNG(2555 + biome.getRarity() + biome.getName().length() + j++), rx, rz, getData());

			if(d != null)
			{
				int stack = i.getHeight(random.nextParallelRNG((int) (239456 + i.getStackMax() + i.getStackMin() + i.getVerticalZoom() + i.getZoom() + i.getBlockData(getData()).size() + j)), rx, rz, getData());

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
	protected void onPostGenerate(RNG random, int x, int z, TerrainChunk terrain, HeightMap height, BiomeMap biomeMap, AtomicSliverMap map)
	{
		onPreParallaxPostGenerate(random, x, z, terrain, height, biomeMap, map);
	}

	protected void onPreParallaxPostGenerate(RNG random, int x, int z, TerrainChunk terrain, HeightMap height, BiomeMap biomeMap, AtomicSliverMap map)
	{
		if(getDimension().isRavines())
		{
			getGlRavine().generateRavines(random.nextParallelRNG(x - 283845).nextParallelRNG(z + 23845868), x, z, terrain, height, biomeMap, map);
		}
	}

	public void generateDeposits(RNG rx, TerrainChunk terrain, int x, int z)
	{
		PrecisionStopwatch p = PrecisionStopwatch.start();
		RNG ro = rx.nextParallelRNG(x * x).nextParallelRNG(z * z);
		IrisRegion region = sampleRegion((x * 16) + 7, (z * 16) + 7);
		IrisBiome biome = sampleTrueBiome((x * 16) + 7, (z * 16) + 7);

		for(IrisDepositGenerator k : getDimension().getDeposits())
		{
			k.generate(terrain, ro, this, x, z, false);
		}

		for(IrisDepositGenerator k : region.getDeposits())
		{
			for(int l = 0; l < ro.i(k.getMinPerChunk(), k.getMaxPerChunk()); l++)
			{
				k.generate(terrain, ro, this, x, z, false);
			}
		}

		for(IrisDepositGenerator k : biome.getDeposits())
		{
			for(int l = 0; l < ro.i(k.getMinPerChunk(), k.getMaxPerChunk()); l++)
			{
				k.generate(terrain, ro, this, x, z, false);
			}
		}
		p.end();
		getMetrics().getDeposits().put(p.getMilliseconds());
	}

	protected void onPostParallaxPostGenerate(RNG random, int x, int z, TerrainChunk terrain, HeightMap height, BiomeMap biomeMap, AtomicSliverMap map)
	{

	}

	public double getNoiseHeight(int rx, int rz)
	{
		return getBiomeHeight(rx, rz);
	}

	public IrisBiome sampleTrueBiomeBase(int x, int z)
	{
		if(!getDimension().getFocus().equals(""))
		{
			return focus();
		}

		int height = (int) Math.round(getTerrainHeight(x, z));
		double wx = getModifiedX(x, z);
		double wz = getModifiedZ(x, z);
		IrisRegion region = sampleRegion(x, z);
		double sh = region.getShoreHeight(wx, wz);
		double shMax = getFluidHeight() + (sh / 2);
		double shMin = getFluidHeight() - (sh / 2);
		IrisBiome current = sampleBiome(x, z);
		InferredType aquaticType = current.isAquatic() ? (current.isSea() ? InferredType.SEA : current.isRiver() ? InferredType.RIVER : InferredType.LAKE) : InferredType.SEA;
		boolean sea = height <= getFluidHeight();
		boolean shore = height >= shMin && height <= shMax;
		boolean land = height > getFluidHeight();

		// Remove rivers, lakes & sea from land
		if(current.isAquatic() && land)
		{
			current = glBiome.generateData(InferredType.LAND, wx, wz, x, z, region);
		}

		// Remove land from underwater
		if(current.isLand() && sea)
		{
			current = glBiome.generateData(aquaticType, wx, wz, x, z, region);
		}

		// Add shores to land
		if(shore)
		{
			current = glBiome.generateData(InferredType.SHORE, wx, wz, x, z, region);
		}

		// Impure Remove rivers, lakes & sea from land
		if(current.isAquatic() && land)
		{
			current = glBiome.generatePureData(InferredType.LAND, wx, wz, x, z, region);
		}

		// Impure Remove land from underwater
		if(current.isLand() && sea)
		{
			current = glBiome.generatePureData(aquaticType, wx, wz, x, z, region);
		}

		// Impure Add shores to land
		if(shore)
		{
			current = glBiome.generatePureData(InferredType.SHORE, wx, wz, x, z, region);
		}

		return current;
	}

	public IrisBiome sampleCaveBiome(int x, int z)
	{
		double wx = getModifiedX(x, z);
		double wz = getModifiedZ(x, z);
		return glBiome.generateData(InferredType.CAVE, wx, wz, x, z, sampleRegion(x, z));
	}

	public IrisBiome sampleTrueBiome(int x, int y, int z)
	{
		if(y < getTerrainHeight(x, z))
		{
			double wx = getModifiedX(x, z);
			double wz = getModifiedZ(x, z);
			IrisBiome r = glBiome.generateData(InferredType.CAVE, wx, wz, x, z, sampleRegion(x, z));

			if(r != null)
			{
				return r;
			}
		}

		return sampleTrueBiome(x, z);
	}

	public IrisRegion sampleRegion(int x, int z)
	{
		return getCache().getRegion(x, z);
	}

	public IrisBiome sampleTrueBiome(int x, int z)
	{
		if(!getDimension().getFocus().equals(""))
		{
			return focus();
		}

		return getCache().getBiome(x, z);
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
		return getCache().getHeight(x, z);
	}

	public double getTerrainWaterHeight(int x, int z)
	{
		return Math.max(getTerrainHeight(x, z), getFluidHeight());
	}

	@Override
	public void onHotload()
	{
		generatorSize = 0;
		super.onHotload();
		getData().preferFolder(getDimension().getLoadFile().getParentFile().getParentFile().getName());
		loadGenerators();
		buildGenLayers(getMasterRandom());
	}

	public void registerGenerator(IrisGenerator g)
	{
		KMap<String, IrisGenerator> generators = this.generators;

		getRegionLock().lock();
		if(g.getLoadKey() == null || generators.containsKey(g.getLoadKey()))
		{
			getRegionLock().unlock();
			return;
		}

		generatorList.add(g);
		getRegionLock().unlock();
		generators.put(g.getLoadKey(), g);
	}

	protected KMap<String, IrisGenerator> getGenerators()
	{
		return generators;
	}

	protected double getRawBiomeHeight(double rrx, double rrz)
	{
		double h = 0;

		for(int j = 0; j < generatorSize; j++)
		{
			try
			{
				h += interpolateGenerator(rrx, rrz, generatorList.get(j));
			}

			catch(Throwable e)
			{

			}
		}

		return h;
	}

	protected double getBiomeHeight(double rrx, double rrz)
	{
		double h = getRawBiomeHeight(rrx, rrz);

		for(IrisShapedGeneratorStyle i : getDimension().getOverlayNoise())
		{
			h += i.get(getMasterRandom(), rrx, rrz);
		}

		return h;
	}

	protected double interpolateGenerator(double rx, double rz, IrisGenerator gen)
	{
		double hi = gen.getInterpolator().interpolate(rx, rz, (xx, zz) ->
		{
			try
			{
				return sampleBiome((int) xx, (int) zz).getGenLinkMax(gen.getLoadKey());
			}

			catch(Throwable e)
			{
				Iris.warn("Failed to sample hi biome at " + rx + " " + rz + " using the generator " + gen.getLoadKey());
				fail(e);
			}
			return 0;
		});

		double lo = gen.getInterpolator().interpolate(rx, rz, (xx, zz) ->
		{
			try
			{
				return sampleBiome((int) xx, (int) zz).getGenLinkMin(gen.getLoadKey());
			}

			catch(Throwable e)
			{
				Iris.warn("Failed to sample lo biome at " + rx + " " + rz + " using the generator " + gen.getLoadKey());
				fail(e);
			}

			return 0;
		});

		return M.lerp(lo, hi, gen.getHeight(rx, rz, getTarget().getSeed() + 239945));
	}

	protected void loadGenerators()
	{
		generatorList.clear();
		generators.clear();
		loadGenerators(getDimension());
		generatorSize = generatorList.size();
	}

	protected void loadGenerators(IrisDimension dim)
	{
		if(dim == null)
		{
			Iris.warn("Cannot load generators, Dimension is null!");
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
				biome.getGenerators().forEach((i) -> registerGenerator(i.getCachedGenerator(this)));
				loadQueue.addAll(biome.getChildren());
			}
		}

		Iris.info("Loaded " + generators.size() + " Generators");
	}

	public IrisBiome computeRawBiome(int x, int z)
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

			return biome;
		}

		double wx = getModifiedX(x, z);
		double wz = getModifiedZ(x, z);
		IrisRegion region = glBiome.getRegion(wx, wz);

		return glBiome.generateRegionData(wx, wz, x, z, region);
	}

	public IrisBiome sampleBiome(int x, int z)
	{
		return getCache().getRawBiome(x, z);
	}
}
