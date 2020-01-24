package ninja.bytecode.iris.generator;

import java.util.List;
import java.util.Random;

import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.TreeSpecies;
import org.bukkit.World;
import org.bukkit.block.Biome;
import org.bukkit.generator.BlockPopulator;
import org.bukkit.material.Leaves;
import org.bukkit.util.NumberConversions;

import mortar.util.text.C;
import ninja.bytecode.iris.Iris;
import ninja.bytecode.iris.IrisMetrics;
import ninja.bytecode.iris.generator.atomics.AtomicChunkData;
import ninja.bytecode.iris.generator.genobject.GenObjectDecorator;
import ninja.bytecode.iris.generator.genobject.PlacedObject;
import ninja.bytecode.iris.generator.layer.GenLayerBiome;
import ninja.bytecode.iris.generator.layer.GenLayerCliffs;
import ninja.bytecode.iris.generator.layer.GenLayerLayeredNoise;
import ninja.bytecode.iris.generator.layer.GenLayerSnow;
import ninja.bytecode.iris.generator.parallax.ParallaxWorldGenerator;
import ninja.bytecode.iris.pack.BiomeType;
import ninja.bytecode.iris.pack.CompiledDimension;
import ninja.bytecode.iris.pack.IrisBiome;
import ninja.bytecode.iris.pack.IrisRegion;
import ninja.bytecode.iris.util.ChunkPlan;
import ninja.bytecode.iris.util.InterpolationMode;
import ninja.bytecode.iris.util.IrisInterpolation;
import ninja.bytecode.iris.util.MB;
import ninja.bytecode.iris.util.ObjectMode;
import ninja.bytecode.iris.util.SChunkVector;
import ninja.bytecode.shuriken.bench.PrecisionStopwatch;
import ninja.bytecode.shuriken.collections.KList;
import ninja.bytecode.shuriken.logging.L;
import ninja.bytecode.shuriken.math.CNG;
import ninja.bytecode.shuriken.math.M;
import ninja.bytecode.shuriken.math.RNG;

public class IrisGenerator extends ParallaxWorldGenerator
{
	//@builder
	public static final KList<MB> ROCK = new KList<MB>().add(new MB[] {
			MB.of(Material.STONE),
			MB.of(Material.STONE),
			MB.of(Material.STONE),
			MB.of(Material.STONE),
			MB.of(Material.STONE),
			MB.of(Material.STONE),
			MB.of(Material.STONE, 5),
			MB.of(Material.STONE, 5),
			MB.of(Material.COBBLESTONE),
			MB.of(Material.COBBLESTONE),
			MB.of(Material.SMOOTH_BRICK),
			MB.of(Material.SMOOTH_BRICK, 1),
			MB.of(Material.SMOOTH_BRICK, 2),
			MB.of(Material.SMOOTH_BRICK, 3),
	});
	//@done
	private boolean disposed;
	private CNG scatter;
	private CNG beach;
	private CNG swirl;
	private MB BEDROCK = new MB(Material.BEDROCK);
	private GenObjectDecorator god;
	private GenLayerLayeredNoise glLNoise;
	private GenLayerBiome glBiome;
	private GenLayerSnow glSnow;
	private GenLayerCliffs glCliffs;
	private RNG rTerrain;
	private CompiledDimension dim;
	private IrisMetrics metrics = new IrisMetrics(0, 512);
	private int objectHits;

	public IrisGenerator()
	{
		this(Iris.pack().getDimension("overworld"));
	}

	public void hitObject()
	{
		objectHits++;
	}

	public IrisGenerator(CompiledDimension dim)
	{
		objectHits = 0;
		CNG.hits = 0;
		CNG.creates = 0;
		this.dim = dim;
		disposed = false;
		L.i("Preparing Dimension: " + dim.getName() + " With " + dim.getBiomes().size() + " Biomes...");
	}

	public int scatterInt(int x, int y, int z, int bound)
	{
		return (int) (scatter(x, y, z) * (double) (bound - 1));
	}

	public double scatter(int x, int y, int z)
	{
		return scatter.noise(x, y, z);
	}

	public boolean scatterChance(int x, int y, int z, double chance)
	{
		return scatter(x, y, z) > chance;
	}

	@Override
	public void onInit(World world, Random random)
	{
		if(disposed)
		{
			return;
		}
		random = new Random(world.getSeed());
		rTerrain = new RNG(world.getSeed());
		swirl = new CNG(rTerrain.nextParallelRNG(0), 40, 1).scale(0.007);
		beach = new CNG(rTerrain.nextParallelRNG(0), 3, 1).scale(0.15);
		glLNoise = new GenLayerLayeredNoise(this, world, random, rTerrain.nextParallelRNG(2));
		glBiome = new GenLayerBiome(this, world, random, rTerrain.nextParallelRNG(4), dim.getBiomes());
		glSnow = new GenLayerSnow(this, world, random, rTerrain.nextParallelRNG(5));
		glCliffs = new GenLayerCliffs(this, world, random, rTerrain.nextParallelRNG(9));
		scatter = new CNG(rTerrain.nextParallelRNG(52), 1, 1).scale(10);

		if(Iris.settings.performance.objectMode.equals(ObjectMode.PARALLAX))
		{
			god = new GenObjectDecorator(this);
		}
	}

	@Override
	public ChunkPlan onInitChunk(World world, int x, int z, Random random)
	{
		return new ChunkPlan();
	}

	public ChunkData generateChunkData(World world, Random random, int x, int z, BiomeGrid biome)
	{
		random = new Random(world.getSeed());
		PrecisionStopwatch s = getMetrics().start();
		ChunkData d = super.generateChunkData(world, random, x, z, biome);
		getMetrics().stop("chunk:ms", s);
		getMetrics().put("noise-hits", CNG.hits);
		metrics.setGenerators((int) CNG.creates);
		CNG.hits = 0;
		return d;
	}

	public IrisBiome biome(String name)
	{
		return getDimension().getBiomeByName(name);
	}

	public double getOffsetX(double x, double z)
	{
		return Math.round((double) x * (Iris.settings.gen.horizontalZoom / 1.90476190476)) + swirl.noise(x, z);
	}

	public double getOffsetZ(double x, double z)
	{
		return Math.round((double) z * (Iris.settings.gen.horizontalZoom / 1.90476190476)) - swirl.noise(z, x);
	}

	public IrisMetrics getMetrics()
	{
		return metrics;
	}

	public IrisBiome getBeach(IrisBiome biome)
	{
		IrisBiome beach = null;
		IrisRegion region = glBiome.getRegion(biome.getRegionID());

		if(region != null)
		{
			beach = region.getBeach();
		}

		if(beach == null)
		{
			beach = biome("Beach");
		}

		return beach;
	}

	public int computeHeight(int x, int z, ChunkPlan plan, IrisBiome biome)
	{
		return (int) Math.round(M.clip(getANoise((int) x, (int) z, plan, biome), 0D, 1D) * 253);
	}

	public double getInterpolation(int x, int z, ChunkPlan plan)
	{
		PrecisionStopwatch s = getMetrics().start();
		double d = 0;
		InterpolationMode m = Iris.settings.gen.interpolationMode;
		if(m.equals(InterpolationMode.BILINEAR))
		{
			d = IrisInterpolation.getBilinearNoise(x, z, Iris.settings.gen.interpolationRadius, (xf, zf) -> getBiomedHeight((int) Math.round(xf), (int) Math.round(zf), plan));
		}

		else if(m.equals(InterpolationMode.BICUBIC))
		{
			d = IrisInterpolation.getBicubicNoise(x, z, Iris.settings.gen.interpolationRadius, (xf, zf) -> getBiomedHeight((int) Math.round(xf), (int) Math.round(zf), plan));
		}

		else if(m.equals(InterpolationMode.HERMITE_BICUBIC))
		{
			d = IrisInterpolation.getHermiteNoise(x, z, Iris.settings.gen.interpolationRadius, (xf, zf) -> getBiomedHeight((int) Math.round(xf), (int) Math.round(zf), plan));
		}

		else
		{
			d = getBiomedHeight((int) Math.round(x), (int) Math.round(z), plan);
		}

		getMetrics().stop("interpolation:ms:x256:/biome:.", s);

		return d;
	}

	public double getANoise(int x, int z, ChunkPlan plan, IrisBiome biome)
	{
		double hv = getInterpolation(x, z, plan);
		hv += glLNoise.generateLayer(hv * Iris.settings.gen.roughness * 215, (double) x * Iris.settings.gen.roughness * 0.82, (double) z * Iris.settings.gen.roughness * 0.82) * (1.6918 * (hv * 2.35));

		if(biome.hasCliffs())
		{
			hv = glCliffs.generateLayer(hv, x, z, biome.getCliffScale(), biome.getCliffChance());
		}

		return hv;
	}

	public IrisRegion getRegion(IrisBiome biome)
	{
		return glBiome.getRegion(biome.getRegionID());
	}

	@Override
	public List<BlockPopulator> getDefaultPopulators(World world)
	{
		KList<BlockPopulator> p = new KList<>();

		if(Iris.settings.performance.objectMode.equals(ObjectMode.QUICK_N_DIRTY) || Iris.settings.performance.objectMode.equals(ObjectMode.LIGHTING))
		{
			p.add(god = new GenObjectDecorator(this));
		}

		return p;
	}

	@Override
	public void onGenParallax(int x, int z, Random random)
	{
		try
		{
			PrecisionStopwatch s = getMetrics().start();
			god.populateParallax(x, z, random);
			String xx = "x" + getParallaxSize().getX() * getParallaxSize().getZ();
			getMetrics().stop("object:" + xx + ":.:ms:/parallax", s);
		}

		catch(Throwable e)
		{
			e.printStackTrace();
		}
	}

	private double getObjectHits()
	{
		int hits = objectHits;
		objectHits = 0;
		return hits;
	}

	public IrisBiome getBiome(int x, int z)
	{
		IrisBiome biome = glBiome.getBiome(x, z);
		int height = computeHeight((int) x, (int) z, new ChunkPlan(), biome);
		biome = getBiome((int) x, height, (int) z);

		return biome;
	}

	private IrisBiome getBiome(int x, int y, int z)
	{
		int seaLevel = Iris.settings.gen.seaLevel;
		boolean land = y >= seaLevel;
		int beachHeight = land ? 1 + (int) Math.round(seaLevel + beach.noise(x, z)) : seaLevel;
		boolean beach = y <= beachHeight && land;
		IrisBiome biome = glBiome.getBiome(x, z);
		IrisBiome realBiome = glBiome.getBiome(x, z, true);
		boolean nearAquatic = glBiome.isNearAquatic(x, z);
		IrisRegion region = getRegion(realBiome);

		// Remove Oceans from biomes above sea level
		if(land && biome.getType().equals(BiomeType.FLUID))
		{
			biome = realBiome;
		}

		// Add Beaches & Shores
		if(beach && biome.getType().equals(BiomeType.LAND))
		{
			biome = nearAquatic ? region.getBeach() : region.getShore();
		}

		// // Replace biomes under sea level with lakes
		if(!land && biome.getType().equals(BiomeType.LAND))
		{
			biome = region.getLake();
		}

		return biome;
	}

	@Override
	public Biome onGenColumn(int wxxf, int wzxf, int x, int z, ChunkPlan plan, AtomicChunkData data, boolean surfaceOnly)
	{
		PrecisionStopwatch s = getMetrics().start();
		if(disposed)
		{
			data.setBlock(x, 0, z, Material.MAGENTA_GLAZED_TERRACOTTA);
			return Biome.VOID;
		}

		double wx = getOffsetX(wxxf, wzxf);
		double wz = getOffsetZ(wxxf, wzxf);
		int wxx = (int) wx;
		int wzx = (int) wz;
		int highest = 0;
		int seaLevel = Iris.settings.gen.seaLevel;
		IrisBiome biome = glBiome.getBiome(wxx, wzx);
		int height = computeHeight(wxx, wzx, plan, biome);
		int max = Math.max(height, seaLevel);
		biome = getBiome(wxx, height, wzx);
		MB FLUID = biome.getFluid();

		for(int i = surfaceOnly ? max > seaLevel ? max - 2 : height - 2 : 0; i < max; i++)
		{
			MB mb = ROCK.get(scatterInt(wzx, i, wxx, ROCK.size()));
			boolean underwater = i >= height && i < seaLevel;
			boolean underground = i < height;
			int dheight = biome.getDirtDepth();
			int rheight = biome.getRockDepth();
			boolean dirt = (height - 1) - i < (dheight > 0 ? scatterInt(x, i, z, 4) : 0) + dheight;
			boolean rocky = i > height - rheight && !dirt;
			boolean bedrock = i == 0 || !Iris.settings.gen.flatBedrock ? i <= 2 : i < scatterInt(x, i, z, 3);
			mb = underwater ? FLUID : mb;
			mb = underground && dirt ? biome.getSubSurface(wxx, i, wzx, rTerrain) : mb;
			mb = underground && rocky ? biome.getRock(wxx, i, wzx, rTerrain) : mb;
			mb = bedrock ? BEDROCK : mb;

			if(i == height - 1)
			{
				mb = biome.getSurface(wx, wz, rTerrain);
			}

			highest = i > highest ? i : highest;
			data.setBlock(x, i, z, mb.material, mb.data);
		}

		getMetrics().stop("terrain:ms:x256:/chunk:..", s);

		int hw = 0;
		int hl = 0;

		for(int i = highest; i > 0; i--)
		{
			Material t = data.getType(x, i, z);
			hw = i > seaLevel && hw == 0 && (t.equals(Material.WATER) || t.equals(Material.STATIONARY_WATER)) ? i : hw;
			hl = hl == 0 && !t.equals(Material.AIR) ? i : hl;
		}

		plan.setRealHeight(x, z, hl);
		plan.setRealWaterHeight(x, z, hw == 0 ? seaLevel : hw);
		plan.setBiome(x, z, biome);
		double time = s.getMilliseconds() * 256D;
		double atime = getMetrics().get("chunk:ms").getAverage();
		getMetrics().setParScale(time / atime);
		getMetrics().put("objects:,:/parallax", getObjectHits());

		return biome.getRealBiome();
	}

	@Override
	protected void onDecorateChunk(World world, int cx, int cz, AtomicChunkData data, ChunkPlan plan)
	{
		int x = 0;
		int z = 0;
		int h = 0;
		int v = 0;
		int border = 0;
		int above = 0;
		int below = 0;

		for(int f = 0; f < Iris.settings.gen.blockSmoothing; f++)
		{
			for(x = 0; x < 16; x++)
			{
				for(z = 0; z < 16; z++)
				{
					h = plan.getRealHeight(x, z);
					border = 0;

					if(x == 0 || x == 15)
					{
						border++;
					}

					if(z == 0 || z == 15)
					{
						border++;
					}

					if(h > Iris.settings.gen.seaLevel)
					{
						above = 0;
						below = 0;

						if(x + 1 <= 15)
						{
							v = plan.getRealHeight(x + 1, z);

							if(v > h)
							{
								above++;
							}

							else if(v < h)
							{
								below++;
							}
						}

						if(x - 1 >= 0)
						{
							v = plan.getRealHeight(x - 1, z);

							if(v > h)
							{
								above++;
							}

							else if(v < h)
							{
								below++;
							}
						}

						if(z + 1 <= 15)
						{
							v = plan.getRealHeight(x, z + 1);

							if(v > h)
							{
								above++;
							}

							else if(v < h)
							{
								below++;
							}
						}

						if(z - 1 >= 0)
						{
							v = plan.getRealHeight(x, z - 1);

							if(v > h)
							{
								above++;
							}

							else if(v < h)
							{
								below++;
							}
						}

						// Patch Hole
						if(above >= 4 - border)
						{
							data.setBlock(x, h + 1, z, data.getMB(x, h, z));
							plan.setRealHeight(x, z, h + 1);
						}

						// Remove Nipple
						else if(below >= 4 - border)
						{
							data.setBlock(x, h - 1, z, data.getMB(x, h, z));
							data.setBlock(x, h, z, Material.AIR);
							plan.setRealHeight(x, z, h - 1);
						}
					}
				}
			}
		}
	}

	@SuppressWarnings("deprecation")
	@Override
	protected void onDecorateColumn(World world, int x, int z, int wx, int wz, AtomicChunkData data, ChunkPlan plan)
	{
		int h = plan.getRealHeight(x, z);

		if(h < 63)
		{
			return;
		}

		IrisBiome biome = plan.getBiome(x, z);

		if(biome == null)
		{
			return;
		}

		if(biome.getSnow() > 0)
		{
			double level = glSnow.getHeight(wx, wz) * biome.getSnow();
			int blocks = (int) level;
			level -= blocks;
			int layers = (int) (level * 7D);
			int snowHeight = blocks + (layers > 0 ? 1 : 0);

			for(int j = 0; j < snowHeight; j++)
			{
				data.setBlock(x, h + j + 1, z, j == snowHeight - 1 ? Material.SNOW : Material.SNOW_BLOCK, j == snowHeight - 1 ? (byte) layers : (byte) 0);
			}
		}

		if(biome.getLush() > 0.33)
		{
			double cnd = (1D - biome.getLush() > 1 ? 1 : biome.getLush()) / 3.5D;
			double g = glSnow.getHeight(wz, wx);

			if(g > cnd)
			{
				double gx = glSnow.getHeight(wx * 2.25, wz * 2.25);
				Leaves l = new Leaves(TreeSpecies.values()[(int) (gx * (TreeSpecies.values().length - 1))]);
				l.setDecaying(false);
				l.setDecayable(false);
				data.setBlock(x, h - 1, z, data.getMB(x, h, z));
				data.setBlock(x, h, z, l.getItemType(), l.getData());
			}
		}

		else
		{
			MB mbx = biome.getScatterChanceSingle(scatter(wx, h, wz));

			if(!mbx.material.equals(Material.AIR))
			{
				data.setBlock(x, h + 1, z, mbx.material, mbx.data);
			}
		}
	}

	@Override
	public void onPostChunk(World world, int cx, int cz, Random random, AtomicChunkData data, ChunkPlan plan)
	{

	}

	private double getBiomedHeight(int x, int z, ChunkPlan plan)
	{
		double xh = plan.getHeight(x, z);

		if(xh == -1)
		{
			IrisBiome biome = glBiome.getBiome(x, z);
			double h = Iris.settings.gen.baseHeight + biome.getHeight();
			h += biome.getGenerator().getHeight(x, z) / 2D;
			plan.setHeight(x, z, h);
			return h;
		}

		return xh;
	}

	public RNG getRTerrain()
	{
		return rTerrain;
	}

	public CompiledDimension getDimension()
	{
		return dim;
	}

	public void dispose()
	{
		if(disposed)
		{
			return;
		}
		L.w(C.YELLOW + "Disposed Iris World " + C.RED + getWorld().getName());
		disposed = true;
		dim = null;
		glLNoise = null;
		glSnow = null;
		glCliffs = null;
		god.dispose();
	}

	public boolean isDisposed()
	{
		return disposed;
	}

	public PlacedObject nearest(Location o, int i)
	{
		PlacedObject f = null;
		double d = Integer.MAX_VALUE;
		if(god != null)
		{
			for(PlacedObject j : god.getHistory())
			{
				double dx = Math.abs(NumberConversions.square(j.getX() - o.getX()) + NumberConversions.square(j.getY() - o.getY()) + NumberConversions.square(j.getZ() - o.getZ()));

				if(dx < d)
				{
					d = dx;
					f = j;
				}
			}
		}

		return f;
	}

	public PlacedObject randomObject(String string)
	{
		return god.randomObject(string);
	}

	@Override
	protected SChunkVector getParallaxSize()
	{
		return dim.getMaxChunkSize();
	}

	@Override
	protected void onUnload()
	{
		dispose();
	}

	public void inject(CompiledDimension dimension)
	{
		this.dim = dimension;
		onInit(getWorld(), rTerrain);
	}
}