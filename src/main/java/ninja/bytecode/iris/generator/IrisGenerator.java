package ninja.bytecode.iris.generator;

import java.util.List;
import java.util.Random;
import java.util.concurrent.locks.ReentrantLock;

import org.bukkit.Material;
import org.bukkit.World;
import org.bukkit.block.Biome;
import org.bukkit.generator.BlockPopulator;

import net.md_5.bungee.api.ChatColor;
import ninja.bytecode.iris.Iris;
import ninja.bytecode.iris.controller.PackController;
import ninja.bytecode.iris.generator.genobject.GenObjectDecorator;
import ninja.bytecode.iris.generator.genobject.GenObjectGroup;
import ninja.bytecode.iris.generator.layer.GenLayerBase;
import ninja.bytecode.iris.generator.layer.GenLayerBiome;
import ninja.bytecode.iris.generator.layer.GenLayerCarving;
import ninja.bytecode.iris.generator.layer.GenLayerCaverns;
import ninja.bytecode.iris.generator.layer.GenLayerCaves;
import ninja.bytecode.iris.generator.layer.GenLayerCliffs;
import ninja.bytecode.iris.generator.layer.GenLayerLayeredNoise;
import ninja.bytecode.iris.generator.layer.GenLayerSnow;
import ninja.bytecode.iris.pack.CompiledDimension;
import ninja.bytecode.iris.pack.IrisBiome;
import ninja.bytecode.iris.pack.IrisRegion;
import ninja.bytecode.iris.util.AtomicChunkData;
import ninja.bytecode.iris.util.ChunkPlan;
import ninja.bytecode.iris.util.IrisInterpolation;
import ninja.bytecode.iris.util.MB;
import ninja.bytecode.iris.util.ParallelChunkGenerator;
import ninja.bytecode.shuriken.collections.GList;
import ninja.bytecode.shuriken.collections.GMap;
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
	public GMap<String, IrisBiome> biomeCache = new GMap<>();
	private MB WATER = new MB(Material.STATIONARY_WATER);
	private MB BEDROCK = new MB(Material.BEDROCK);
	private GList<IrisBiome> internal;
	private GenLayerLayeredNoise glLNoise;
	private GenLayerBiome glBiome;
	private GenLayerCaves glCaves;
	private GenLayerCarving glCarving;
	private GenLayerCaverns glCaverns;
	private GenLayerSnow glSnow;
	private GenLayerBase glBase;
	private GenLayerCliffs glCliffs;
	private RNG rTerrain;
	private CompiledDimension dim;
	private World world;
	private GMap<String, GenObjectGroup> schematicCache = new GMap<>();

	public IrisGenerator()
	{
		this(Iris.getController(PackController.class).getDimension("overworld"));
	}

	public IrisGenerator(CompiledDimension dim)
	{
		this.dim = dim;
		L.i("Preparing Dimension: " + dim.getName() + " With " + dim.getBiomes().size() + " Biomes...");
		internal = IrisBiome.getAllBiomes();

		for(IrisBiome i : dim.getBiomes())
		{
			for(IrisBiome j : internal.copy())
			{
				if(j.getName().equals(i.getName()))
				{
					internal.remove(j);
					L.i(ChatColor.LIGHT_PURPLE + "Internal Biome: " + ChatColor.WHITE + j.getName() + ChatColor.LIGHT_PURPLE + " overwritten by dimension " + ChatColor.WHITE + dim.getName());
				}
			}
		}

		internal.addAll(dim.getBiomes());

		for(IrisBiome i : internal)
		{
			biomeCache.put(i.getName(), i);
		}
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

	public GList<IrisBiome> getLoadedBiomes()
	{
		return internal;
	}

	@Override
	public void onInit(World world, Random random)
	{
		this.world = world;
		rTerrain = new RNG(world.getSeed() + 1024);
		glBase = new GenLayerBase(this, world, random, rTerrain.nextParallelRNG(1));
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
	}

	@Override
	public ChunkPlan onInitChunk(World world, int x, int z, Random random)
	{
		return new ChunkPlan();
	}

	public IrisBiome getBiome(int wxx, int wzx)
	{
		double wx = Math.round((double) wxx * (Iris.settings.gen.horizontalZoom / 1.90476190476));
		double wz = Math.round((double) wzx * (Iris.settings.gen.horizontalZoom / 1.90476190476));
		return glBiome.getBiome(wx * Iris.settings.gen.biomeScale, wz * Iris.settings.gen.biomeScale);
	}

	public IrisBiome biome(String name)
	{
		return biomeCache.get(name);
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
		double hv = IrisInterpolation.getNoise(x, z, Iris.settings.gen.hermiteSampleRadius, (xf, zf) -> getBiomedHeight((int) Math.round(xf), (int) Math.round(zf), plan));
		hv += glLNoise.generateLayer(hv * Iris.settings.gen.roughness * 215, (double) x * Iris.settings.gen.roughness * 0.82, (double) z * Iris.settings.gen.roughness * 0.82) * (1.6918 * (hv * 2.35));

		if(biome.hasCliffs())
		{
			hv = glCliffs.generateLayer(hv, x, z, biome.getCliffScale(), biome.getCliffChance());
		}

		return hv;
	}

	@Override
	public Biome genColumn(int wxx, int wzx, int x, int z, ChunkPlan plan)
	{
		int highest = 0;
		int seaLevel = Iris.settings.gen.seaLevel;
		double wx = getOffsetX(wxx);
		double wz = getOffsetZ(wzx);
		IrisBiome biome = getBiome(wxx, wzx);
		int height = computeHeight(wxx, wzx, plan, biome);
		int max = Math.max(height, seaLevel);
		biome = height > 61 && height < 65 ? getBeach(biome) : biome;
		biome = height < 63 ? getOcean(biome, height) : biome;

		for(int i = 0; i < max; i++)
		{
			MB mb = ROCK.get(scatterInt(wzx, i, wxx, ROCK.size()));
			boolean underwater = i >= height && i < seaLevel;
			boolean underground = i < height;
			int dheight = biome.getDirtDepth();
			int rheight = biome.getRockDepth();
			boolean dirt = (height - 1) - i < (dheight > 0 ? scatterInt(x, i, z, 4) : 0) + dheight;
			boolean rocky = i > height - rheight && !dirt;
			boolean bedrock = i == 0 || !Iris.settings.gen.flatBedrock ? i <= 2 : i < scatterInt(x, i, z, 3);
			mb = underwater ? WATER : mb;
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
			int wx = (int) Math.round((double) x * (Iris.settings.gen.horizontalZoom / 1.90476190476));
			int wz = (int) Math.round((double) z * (Iris.settings.gen.horizontalZoom / 1.90476190476));
			IrisBiome biome = glBiome.getBiome(wx * Iris.settings.gen.biomeScale, wz * Iris.settings.gen.biomeScale);
			double h = Iris.settings.gen.baseHeight + biome.getHeight();
			h += (glBase.getHeight(wx, wz) * 0.5) - (0.33 * 0.5);
			plan.setHeight(x, z, h);
			return h;
		}

		return xh;
	}

	public World getWorld()
	{
		return world;
	}

	public GMap<String, GenObjectGroup> getSchematicCache()
	{
		return schematicCache;
	}

	public void setSchematicCache(GMap<String, GenObjectGroup> schematicCache)
	{
		this.schematicCache = schematicCache;
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