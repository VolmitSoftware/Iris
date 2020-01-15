package ninja.bytecode.iris.generator;

import java.util.List;
import java.util.Random;

import org.bukkit.Material;
import org.bukkit.World;
import org.bukkit.block.Biome;
import org.bukkit.generator.BlockPopulator;

import net.md_5.bungee.api.ChatColor;
import ninja.bytecode.iris.Iris;
import ninja.bytecode.iris.controller.PackController;
import ninja.bytecode.iris.generator.genobject.GenObjectDecorator;
import ninja.bytecode.iris.generator.genobject.GenObjectGroup;
import ninja.bytecode.iris.generator.layer.GenLayerBiome;
import ninja.bytecode.iris.generator.layer.GenLayerCarving;
import ninja.bytecode.iris.generator.layer.GenLayerCaverns;
import ninja.bytecode.iris.generator.layer.GenLayerCaves;
import ninja.bytecode.iris.generator.layer.GenLayerLayeredNoise;
import ninja.bytecode.iris.generator.layer.GenLayerSnow;
import ninja.bytecode.iris.pack.CompiledDimension;
import ninja.bytecode.iris.pack.IrisBiome;
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
	private RNG rTerrain;
	private CNG lerpf;
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
		glLNoise = new GenLayerLayeredNoise(this, world, random, rTerrain.nextParallelRNG(2));
		glBiome = new GenLayerBiome(this, world, random, rTerrain.nextParallelRNG(4), dim.getBiomes());
		glCaves = new GenLayerCaves(this, world, random, rTerrain.nextParallelRNG(-1));
		glCarving = new GenLayerCarving(this, world, random, rTerrain.nextParallelRNG(-2));
		glCaverns = new GenLayerCaverns(this, world, random, rTerrain.nextParallelRNG(-3));
		glSnow = new GenLayerSnow(this, world, random, rTerrain.nextParallelRNG(5));
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

	@Override
	public Biome genColumn(int wxx, int wzx, int x, int z, ChunkPlan plan)
	{
		//@builder
		int highest = 0;
		int seaLevel = Iris.settings.gen.seaLevel;
		double wx = Math.round((double) wxx * (Iris.settings.gen.horizontalZoom / 1.90476190476));
		double wz = Math.round((double) wzx * (Iris.settings.gen.horizontalZoom / 1.90476190476));
		IrisBiome biome = getBiome(wxx, wzx);
		double hv = IrisInterpolation.getNoise(wxx, wzx, 
				Iris.settings.gen.hermiteSampleRadius,
				(xf, zf) -> getBiomedHeight((int) Math.round(xf), (int) Math.round(zf), plan));
		hv += glLNoise.generateLayer(hv * Iris.settings.gen.roughness * 215, wxx * Iris.settings.gen.roughness * 0.82, wzx * Iris.settings.gen.roughness * 0.82) * (1.6918 * (hv * 2.35)) * 0.725;
		
		int height = (int) Math.round(M.clip(hv, 0D, 1D) * 253);
		int max = Math.max(height, seaLevel);
		IrisBiome override = null;
		//@done

		if(height > 61 && height < 65 + (glLNoise.getHeight(wz, wx) * Iris.settings.gen.beachScale))
		{
			override = biome("Beach");
		}

		else if(height < 63)
		{
			if(height < 36)
			{
				override = biome("Deep Ocean");
			}

			else
			{
				override = biome("Ocean");
			}
		}

		if(override != null)
		{
			biome = override;
		}

		for(int i = 0; i < max; i++)
		{
			MB mb = ROCK.get(scatterInt(wzx, i, wxx, ROCK.size()));
			boolean underwater = i >= height && i < seaLevel;
			boolean underground = i < height;

			if(underwater)
			{
				mb = WATER;
			}

			if(underground && (height - 1) - i < scatterInt(x, i, z, 4) + 2)
			{
				mb = biome.getDirtRNG();
			}

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
						if(j == snowHeight - 1)
						{
							setBlock(x, i + j + 1, z, Material.SNOW, (byte) layers);
						}

						else
						{
							setBlock(x, i + j + 1, z, Material.SNOW_BLOCK);
						}
					}
				}

				else
				{
					MB mbx = biome.getScatterChanceSingle();

					if(!mbx.material.equals(Material.AIR))
					{
						setBlock(x, i + 1, z, mbx.material, mbx.data);
						highest = i > highest ? i : highest;
					}
				}
			}

			if(i == 0)
			{
				mb = BEDROCK;
			}

			if(!Iris.settings.gen.flatBedrock ? i <= 2 : i < scatterInt(x, i, z, 3))
			{
				mb = BEDROCK;
			}

			setBlock(x, i, z, mb.material, mb.data);
			highest = i > highest ? i : highest;
		}

		glCaves.genCaves(wxx, wzx, x, z, height, this);
		glCarving.genCarves(wxx, wzx, x, z, height, this, biome);
		glCaverns.genCaverns(wxx, wzx, x, z, height, this, biome);
		plan.setRealHeight(x, z, highest);
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