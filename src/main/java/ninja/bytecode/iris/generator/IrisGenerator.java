package ninja.bytecode.iris.generator;

import java.util.List;
import java.util.Random;

import org.bukkit.Material;
import org.bukkit.World;
import org.bukkit.block.Biome;
import org.bukkit.generator.BlockPopulator;

import ninja.bytecode.iris.Iris;
import ninja.bytecode.iris.controller.PackController;
import ninja.bytecode.iris.generator.genobject.GenObjectDecorator;
import ninja.bytecode.iris.generator.layer.BiomeNoiseGenerator;
import ninja.bytecode.iris.generator.layer.GenLayerBiome;
import ninja.bytecode.iris.generator.layer.GenLayerCarving;
import ninja.bytecode.iris.generator.layer.GenLayerCaverns;
import ninja.bytecode.iris.generator.layer.GenLayerCaves;
import ninja.bytecode.iris.generator.layer.GenLayerCliffs;
import ninja.bytecode.iris.generator.layer.GenLayerLayeredNoise;
import ninja.bytecode.iris.generator.layer.GenLayerSnow;
import ninja.bytecode.iris.pack.BiomeType;
import ninja.bytecode.iris.pack.CompiledDimension;
import ninja.bytecode.iris.pack.IrisBiome;
import ninja.bytecode.iris.pack.IrisRegion;
import ninja.bytecode.iris.util.AtomicChunkData;
import ninja.bytecode.iris.util.ChunkPlan;
import ninja.bytecode.iris.util.IrisInterpolation;
import ninja.bytecode.iris.util.MB;
import ninja.bytecode.iris.util.ParallelChunkGenerator;
import ninja.bytecode.shuriken.collections.GList;
import ninja.bytecode.shuriken.logging.L;
import ninja.bytecode.shuriken.math.CNG;
import ninja.bytecode.shuriken.math.M;
import ninja.bytecode.shuriken.math.RNG;

public class IrisGenerator extends ParallelChunkGenerator
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

	private double[][][] scatterCache;
	private CNG scatter;
	private MB ICE = new MB(Material.ICE);
	private MB PACKED_ICE = new MB(Material.PACKED_ICE);
	private MB WATER = new MB(Material.STATIONARY_WATER);
	private MB BEDROCK = new MB(Material.BEDROCK);
	private GenLayerLayeredNoise glLNoise;
	private GenLayerBiome glBiome;
	private GenLayerCaves glCaves;
	private GenLayerCarving glCarving;
	private GenLayerCaverns glCaverns;
	private GenLayerSnow glSnow;
	private BiomeNoiseGenerator glBase;
	private GenLayerCliffs glCliffs;
	private RNG rTerrain;
	private CompiledDimension dim;
	private World world;

	public IrisGenerator()
	{
		this(Iris.getController(PackController.class).getDimension("overworld"));
	}

	public IrisGenerator(CompiledDimension dim)
	{
		this.dim = dim;
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
		this.world = world;
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

		int m = 0;

		for(IrisBiome i : getDimension().getBiomes())
		{
			i.seal(getRTerrain().nextParallelRNG(3922 - m++));
		}
	}

	@Override
	public ChunkPlan onInitChunk(World world, int x, int z, Random random)
	{
		return new ChunkPlan();
	}

	public IrisBiome getBiome(int wxx, int wzx)
	{
		return glBiome.getBiome(wxx, wzx);
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

	public double getANoise(int x, int z, ChunkPlan plan, IrisBiome biome)
	{
		double hv = Iris.settings.performance.interpolation ? IrisInterpolation.getNoise(x, z, Iris.settings.gen.hermiteSampleRadius, (xf, zf) -> getBiomedHeight((int) Math.round(xf), (int) Math.round(zf), plan)) : getBiomedHeight((int) Math.round(x), (int) Math.round(z), plan);
		hv += Iris.settings.performance.surfaceNoise ? glLNoise.generateLayer(hv * Iris.settings.gen.roughness * 215, (double) x * Iris.settings.gen.roughness * 0.82, (double) z * Iris.settings.gen.roughness * 0.82) * (1.6918 * (hv * 2.35)) : 0;

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
	public Biome genColumn(int wxxf, int wzxf, int x, int z, ChunkPlan plan)
	{
		double wx = getOffsetX(wxxf);
		double wz = getOffsetZ(wzxf);
		int wxx = (int) wx;
		int wzx = (int) wz;
		int highest = 0;
		int seaLevel = Iris.settings.gen.seaLevel;
		IrisBiome biome = getBiome(wxx, wzx);
		boolean frozen = getRegion(biome) != null ? getRegion(biome).isFrozen() : false;
		int height = computeHeight(wxx, wzx, plan, biome);
		int max = Math.max(height, seaLevel);
		IrisBiome nbiome = height < 63 ? getOcean(biome, height) : biome;
		biome = nbiome;
		biome = height > 61 && height < 65 ? frozen ? biome : getBeach(biome) : biome;
		biome = height > 63 && biome.getType().equals(BiomeType.FLUID) ? getBeach(biome) : biome;

		for(int i = 0; i < max; i++)
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
						setBlock(x, i + j + 1, z, j == snowHeight - 1 ? Material.SNOW : Material.SNOW_BLOCK, j == snowHeight - 1 ? (byte) layers : (byte) 0);
					}
				}

				else
				{
					MB mbx = biome.getScatterChanceSingle();

					if(!mbx.material.equals(Material.AIR))
					{
						highest = i > highest ? i : highest;
						setBlock(x, i + 1, z, mbx.material, mbx.data);
					}
				}
			}

			highest = i > highest ? i : highest;
			setBlock(x, i, z, mb.material, mb.data);
		}

		glCaves.genCaves(wxx, wzx, x, z, height, this);
		glCarving.genCarves(wxx, wzx, x, z, height, this, biome);
		glCaverns.genCaverns(wxx, wzx, x, z, height, this, biome);
		int hw = 0;
		int hl = 0;

		for(int i = highest; i > 0; i--)
		{
			Material t = getType(x, i, z);
			hw = i > seaLevel && hw == 0 && (t.equals(Material.WATER) || t.equals(Material.STATIONARY_WATER)) ? i : hw;
			hl = hl == 0 && !t.equals(Material.AIR) ? i : hl;
		}

		plan.setRealHeight(x, z, hl);
		plan.setRealWaterHeight(x, z, hw == 0 ? seaLevel : hw);
		plan.setBiome(x, z, biome);

		return biome.getRealBiome();
	}

	@Override
	public void decorateColumn(int wx, int wz, int x, int z, ChunkPlan plan)
	{

	}

	@Override
	public void onPostChunk(World world, int x, int z, Random random, AtomicChunkData data, ChunkPlan plan)
	{

	}

	@Override
	public List<BlockPopulator> getDefaultPopulators(World world)
	{
		GList<BlockPopulator> p = new GList<>();

		if(Iris.settings.gen.genObjects)
		{
			p.add(new GenObjectDecorator(this));
		}

		return p;
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

	public World getWorld()
	{
		return world;
	}

	public RNG getRTerrain()
	{
		return rTerrain;
	}

	public CompiledDimension getDimension()
	{
		return dim;
	}
}