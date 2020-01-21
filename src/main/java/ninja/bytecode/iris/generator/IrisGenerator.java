package ninja.bytecode.iris.generator;

import java.util.List;
import java.util.Random;

import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.World;
import org.bukkit.block.Biome;
import org.bukkit.generator.BlockPopulator;
import org.bukkit.util.NumberConversions;

import mortar.util.text.C;
import ninja.bytecode.iris.Iris;
import ninja.bytecode.iris.IrisMetrics;
import ninja.bytecode.iris.controller.PackController;
import ninja.bytecode.iris.generator.atomics.AtomicChunkData;
import ninja.bytecode.iris.generator.genobject.GenObjectDecorator;
import ninja.bytecode.iris.generator.genobject.PlacedObject;
import ninja.bytecode.iris.generator.layer.GenLayerBiome;
import ninja.bytecode.iris.generator.layer.GenLayerCarving;
import ninja.bytecode.iris.generator.layer.GenLayerCaverns;
import ninja.bytecode.iris.generator.layer.GenLayerCaves;
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
import ninja.bytecode.shuriken.collections.GList;
import ninja.bytecode.shuriken.logging.L;
import ninja.bytecode.shuriken.math.CNG;
import ninja.bytecode.shuriken.math.M;
import ninja.bytecode.shuriken.math.RNG;

public class IrisGenerator extends ParallaxWorldGenerator
{
	//@builder
	public static final GList<MB> ROCK = new GList<MB>().add(new MB[] {
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
	private double[][][] scatterCache;
	private CNG scatter;
	private MB ICE = new MB(Material.ICE);
	private MB PACKED_ICE = new MB(Material.PACKED_ICE);
	private MB WATER = new MB(Material.STATIONARY_WATER);
	private MB BEDROCK = new MB(Material.BEDROCK);
	private GenObjectDecorator god;
	private GenLayerLayeredNoise glLNoise;
	private GenLayerBiome glBiome;
	private GenLayerCaves glCaves;
	private GenLayerCarving glCarving;
	private GenLayerCaverns glCaverns;
	private GenLayerSnow glSnow;
	private GenLayerCliffs glCliffs;
	private RNG rTerrain;
	private CompiledDimension dim;
	private IrisMetrics metrics = new IrisMetrics(0, 512);
	private int objectHits;

	public IrisGenerator()
	{
		this(Iris.getController(PackController.class).getDimension("overworld"));
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
		return scatterCache[Math.abs(x) % 16][Math.abs(y) % 16][Math.abs(z) % 16];
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

		//@builder
		rTerrain = new RNG(world.getSeed());
		glLNoise = new GenLayerLayeredNoise(this, world, random, rTerrain.nextParallelRNG(2));
		glBiome = new GenLayerBiome(this, world, random, rTerrain.nextParallelRNG(4), dim.getBiomes());
		glCaves = new GenLayerCaves(this, world, random, rTerrain.nextParallelRNG(-1));
		glCarving = new GenLayerCarving(this, world, random, rTerrain.nextParallelRNG(-2));
		glCaverns = new GenLayerCaverns(this, world, random, rTerrain.nextParallelRNG(-3));
		glSnow = new GenLayerSnow(this, world, random, rTerrain.nextParallelRNG(5));
		glCliffs = new GenLayerCliffs(this, world, random, rTerrain.nextParallelRNG(9));
		scatterCache = new double[16][][];
		scatter = new CNG(rTerrain.nextParallelRNG(52), 1, 1).scale(10);
		
		if(Iris.settings.performance.objectMode.equals(ObjectMode.PARALLAX))
		{
			god = new GenObjectDecorator(this);
		}
		//@done
		for(int i = 0; i < 16; i++)
		{
			scatterCache[i] = new double[16][];

			for(int j = 0; j < 16; j++)
			{
				scatterCache[i][j] = new double[16];

				for(int k = 0; k < 16; k++)
				{
					scatterCache[i][j][k] = scatter.noise(i, j, k);
				}
			}
		}
	}

	@Override
	public ChunkPlan onInitChunk(World world, int x, int z, Random random)
	{
		return new ChunkPlan();
	}

	public IrisBiome getBiome(int wxx, int wzx)
	{
		PrecisionStopwatch c = getMetrics().start();
		IrisBiome biome = glBiome.getBiome(wxx, wzx);
		IrisBiome real = glBiome.getBiome(wxx, wzx, true);
		boolean frozen = getRegion(biome) != null ? getRegion(biome).isFrozen() : false;
		int height = computeHeight(wxx, wzx, new ChunkPlan(), biome);
		IrisBiome nbiome = height < 63 ? getOcean(real, height) : biome;
		biome = nbiome;
		int beach = 65;
		biome = height > 61 && height < 65 ? frozen ? biome : getBeach(real) : biome;
		biome = height > 63 && biome.getType().equals(BiomeType.FLUID) ? getBeach(real) : biome;
		biome = height >= beach && !biome.getType().equals(BiomeType.LAND) ? real : biome;
		getMetrics().stop("biome:ms:x256:/terrain:..", c);

		return biome;
	}

	public ChunkData generateChunkData(World world, Random random, int x, int z, BiomeGrid biome)
	{
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

	public double getOffsetX(double x)
	{
		return Math.round((double) x * (Iris.settings.gen.horizontalZoom / 1.90476190476));
	}

	public double getOffsetZ(double z)
	{
		return Math.round((double) z * (Iris.settings.gen.horizontalZoom / 1.90476190476));
	}

	public IrisMetrics getMetrics()
	{
		return metrics;
	}

	public IrisBiome getOcean(IrisBiome biome, int height)
	{
		IrisRegion region = glBiome.getRegion(biome.getRegion());
		if(region != null)
		{
			if(region.isFrozen())
			{
				return biome("Frozen Ocean");
			}
		}

		if(height < 36)
		{
			return biome("Deep Ocean");
		}

		else
		{
			return biome("Ocean");
		}
	}

	public IrisBiome getBeach(IrisBiome biome)
	{
		IrisBiome beach = null;
		IrisRegion region = glBiome.getRegion(biome.getRegion());

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
		double hv = !Iris.settings.performance.fastMode ? getInterpolation(x, z, plan) : getBiomedHeight((int) Math.round(x), (int) Math.round(z), plan);
		hv += glLNoise.generateLayer(hv * Iris.settings.gen.roughness * 215, (double) x * Iris.settings.gen.roughness * 0.82, (double) z * Iris.settings.gen.roughness * 0.82) * (1.6918 * (hv * 2.35));

		if(biome.hasCliffs())
		{
			hv = glCliffs.generateLayer(hv, x, z, biome.getCliffScale(), biome.getCliffChance());
		}

		return hv;
	}

	public IrisRegion getRegion(IrisBiome biome)
	{
		return glBiome.getRegion(biome.getRegion());
	}

	@Override
	public List<BlockPopulator> getDefaultPopulators(World world)
	{
		GList<BlockPopulator> p = new GList<>();

		if(Iris.settings.performance.objectMode.equals(ObjectMode.FAST_LIGHTING) || Iris.settings.performance.objectMode.equals(ObjectMode.LIGHTING))
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

	@Override
	public Biome onGenColumn(int wxxf, int wzxf, int x, int z, ChunkPlan plan, AtomicChunkData data, boolean surfaceOnly)
	{
		PrecisionStopwatch s = getMetrics().start();
		if(disposed)
		{
			data.setBlock(x, 0, z, Material.MAGENTA_GLAZED_TERRACOTTA);
			return Biome.VOID;
		}

		double wx = getOffsetX(wxxf);
		double wz = getOffsetZ(wzxf);
		int wxx = (int) wx;
		int wzx = (int) wz;
		int highest = 0;
		int seaLevel = Iris.settings.gen.seaLevel;
		IrisBiome biome = getBiome(wxx, wzx);
		IrisRegion r = getRegion(biome);
		boolean frozen = r != null && r.isFrozen();
		int height = computeHeight(wxx, wzx, plan, biome);
		int max = Math.max(height, seaLevel);

		for(int i = surfaceOnly ? max > seaLevel ? max - 2 : height - 2 : 0; i < max; i++)
		{
			MB mb = ROCK.get(scatterInt(wzx, i, wxx, ROCK.size()));
			boolean underwater = i >= height && i < seaLevel;
			boolean someunderwater = i >= height && i < seaLevel - (1 + scatterInt(x, i, z, 1));
			boolean wayunderwater = i >= height && i < seaLevel - (3 + scatterInt(x, i, z, 2));
			boolean underground = i < height;
			int dheight = biome.getDirtDepth();
			int rheight = biome.getRockDepth();
			boolean dirt = (height - 1) - i < (dheight > 0 ? scatterInt(x, i, z, 4) : 0) + dheight;
			boolean rocky = i > height - rheight && !dirt;
			boolean bedrock = i == 0 || !Iris.settings.gen.flatBedrock ? i <= 2 : i < scatterInt(x, i, z, 3);
			mb = underwater ? frozen ? PACKED_ICE : WATER : mb;
			mb = someunderwater ? frozen ? ICE : WATER : mb;
			mb = wayunderwater ? WATER : mb;
			mb = underground && dirt ? biome.getSubSurface(wxx, i, wzx, rTerrain) : mb;
			mb = underground && rocky ? biome.getRock(wxx, i, wzx, rTerrain) : mb;
			mb = bedrock ? BEDROCK : mb;

			if(i == height - 1)
			{
				mb = biome.getSurface(wx, wz, rTerrain);

				if(biome.getSnow() > 0)
				{
					double level = glSnow.getHeight(wx, wz) * biome.getSnow();
					int blocks = (int) level;
					level -= blocks;
					int layers = (int) (level * 7D);
					int snowHeight = blocks + (layers > 0 ? 1 : 0);

					for(int j = 0; j < snowHeight; j++)
					{
						highest = j == snowHeight - 1 ? highest < j ? j : highest : highest < j + 1 ? j + 1 : highest;
						data.setBlock(x, i + j + 1, z, j == snowHeight - 1 ? Material.SNOW : Material.SNOW_BLOCK, j == snowHeight - 1 ? (byte) layers : (byte) 0);
					}
				}

				else
				{
					MB mbx = biome.getScatterChanceSingle();

					if(!mbx.material.equals(Material.AIR))
					{
						highest = i > highest ? i : highest;
						data.setBlock(x, i + 1, z, mbx.material, mbx.data);
					}
				}
			}

			highest = i > highest ? i : highest;
			data.setBlock(x, i, z, mb.material, mb.data);
		}

		getMetrics().stop("terrain:ms:x256:/chunk:..", s);

		if(!surfaceOnly)
		{
			PrecisionStopwatch c = getMetrics().start();
			glCaves.genCaves(wxx, wzx, x, z, height, this, data);
			getMetrics().stop("caves:ms:x256:/terrain:..", c);
			PrecisionStopwatch v = getMetrics().start();
			glCaverns.genCaverns(wxx, wzx, x, z, height, this, biome, data);
			getMetrics().stop("caverns:ms:x256:/terrain:..", v);
		}

		PrecisionStopwatch c = getMetrics().start();
		glCarving.genCarves(wxx, wzx, x, z, height, this, biome, data);
		getMetrics().stop("carving:ms:x256:/terrain:..", c);

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
	public void onPostChunk(World world, int x, int z, Random random, AtomicChunkData data, ChunkPlan plan)
	{

	}

	private double getBiomedHeight(int x, int z, ChunkPlan plan)
	{
		double xh = plan.getHeight(x, z);

		if(xh == -1)
		{
			IrisBiome biome = glBiome.getBiome(x, z);
			double h = Iris.settings.gen.baseHeight + biome.getHeight();
			h += biome.getGenerator().getHeight(x, z);
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
		glCaves = null;
		glCarving = null;
		glCaverns = null;
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
}