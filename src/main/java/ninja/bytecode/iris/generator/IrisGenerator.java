package ninja.bytecode.iris.generator;

import java.util.List;
import java.util.Random;

import org.bukkit.Material;
import org.bukkit.World;
import org.bukkit.block.Biome;
import org.bukkit.generator.BlockPopulator;

import ninja.bytecode.iris.Iris;
import ninja.bytecode.iris.generator.layer.GenLayerBase;
import ninja.bytecode.iris.generator.layer.GenLayerBiome;
import ninja.bytecode.iris.generator.layer.GenLayerCaves;
import ninja.bytecode.iris.generator.layer.GenLayerLayeredNoise;
import ninja.bytecode.iris.generator.layer.GenLayerRidge;
import ninja.bytecode.iris.generator.populator.ObjectPopulator;
import ninja.bytecode.iris.schematic.SchematicGroup;
import ninja.bytecode.iris.spec.IrisBiome;
import ninja.bytecode.iris.spec.IrisDimension;
import ninja.bytecode.iris.util.AtomicChunkData;
import ninja.bytecode.iris.util.ChunkPlan;
import ninja.bytecode.iris.util.IrisInterpolation;
import ninja.bytecode.iris.util.MB;
import ninja.bytecode.iris.util.ParallelChunkGenerator;
import ninja.bytecode.shuriken.collections.GList;
import ninja.bytecode.shuriken.collections.GMap;
import ninja.bytecode.shuriken.logging.L;
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
	public GMap<String, IrisBiome> biomeCache = new GMap<>();
	//@done
	private MB WATER = new MB(Material.STATIONARY_WATER);
	private MB BEDROCK = new MB(Material.BEDROCK);
	private GList<IrisBiome> internal;
	private GenLayerBase glBase;
	private GenLayerLayeredNoise glLNoise;
	private GenLayerRidge glRidge;
	private GenLayerBiome glBiome;
	private GenLayerCaves glCaves;
	private RNG rTerrain;
	private IrisDimension dim;
	private World world;
	private GMap<String, SchematicGroup> schematicCache = new GMap<>();

	public IrisGenerator()
	{
		this(Iris.dimensions.get("overworld"));
	}

	public GList<IrisBiome> getLoadedBiomes()
	{
		return internal;
	}

	public IrisGenerator(IrisDimension dim)
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
					L.i("Internal Biome: " + j.getName() + " overwritten by dimension " + dim.getName());
				}
			}
		}

		internal.addAll(dim.getBiomes());

		for(IrisBiome i : internal)
		{
			biomeCache.put(i.getName(), i);
		}
	}

	@Override
	public void onInit(World world, Random random)
	{
		this.world = world;
		rTerrain = new RNG(world.getSeed() + 1024);
		glBase = new GenLayerBase(this, world, random, rTerrain.nextParallelRNG(1));
		glLNoise = new GenLayerLayeredNoise(this, world, random, rTerrain.nextParallelRNG(2));
		glRidge = new GenLayerRidge(this, world, random, rTerrain.nextParallelRNG(3));
		glBiome = new GenLayerBiome(this, world, random, rTerrain.nextParallelRNG(4), dim.getBiomes());
		glCaves = new GenLayerCaves(this, world, random, rTerrain.nextParallelRNG(-1));
	}

	@Override
	public ChunkPlan onInitChunk(World world, int x, int z, Random random)
	{
		return new ChunkPlan();
	}

	public IrisBiome getBiome(int wxx, int wzx)
	{
		double wx = Math.round((double) wxx * Iris.settings.gen.horizontalZoom);
		double wz = Math.round((double) wzx * Iris.settings.gen.horizontalZoom);
		return glBiome.getBiome(wx * Iris.settings.gen.biomeScale, wz * Iris.settings.gen.biomeScale);
	}

	public IrisBiome biome(String name)
	{
		return biomeCache.get(name);
	}

	@Override
	public Biome genColumn(int wxx, int wzx, int x, int z, ChunkPlan plan)
	{
		int seaLevel = Iris.settings.gen.seaLevel;
		double wx = Math.round((double) wxx * Iris.settings.gen.horizontalZoom);
		double wz = Math.round((double) wzx * Iris.settings.gen.horizontalZoom);
		IrisBiome biome = getBiome(wxx, wzx);
		double hv = IrisInterpolation.getBicubicNoise(wxx, wzx, (xf, zf) -> getBiomedHeight((int) Math.round(xf), (int) Math.round(zf), plan));
		hv += glLNoise.generateLayer(hv * Iris.settings.gen.roughness * 215, wxx * Iris.settings.gen.roughness * 0.82, wzx * Iris.settings.gen.roughness * 0.82) * (1.6918 * (hv * 2.35));
		hv -= glRidge.generateLayer(hv, wxx, wzx);
		int height = (int) Math.round(M.clip(hv, 0D, 1D) * 253);
		int max = Math.max(height, seaLevel);
		IrisBiome override = null;

		if(height > 61 && height < 65)
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
			MB mb = ROCK.get(glBase.scatterInt(wzx, i, wxx, ROCK.size()));
			boolean underwater = i >= height && i < seaLevel;
			boolean underground = i < height;

			if(underwater)
			{
				mb = WATER;
			}

			if(underground && (height - 1) - i < glBase.scatterInt(x, i, z, 4) + 2)
			{
				mb = biome.getDirtRNG();
			}

			if(i == height - 1)
			{
				mb = biome.getSurface(wx, wz, rTerrain);
				MB mbx = biome.getScatterChanceSingle();

				if(!mbx.material.equals(Material.AIR))
				{
					setBlock(x, i + 1, z, mbx.material, mbx.data);
				}
			}

			if(i == 0)
			{
				mb = BEDROCK;
			}

			if(!Iris.settings.gen.flatBedrock ? i <= 2 : i < glBase.scatterInt(x, i, z, 3))
			{
				mb = BEDROCK;
			}

			setBlock(x, i, z, mb.material, mb.data);
		}

		plan.caveMs(glCaves.genCaves(wxx, wzx, x, z, height, this));
		
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

		if(Iris.settings.gen.doSchematics)
		{
			p.add(new ObjectPopulator(this));
		}

		return p;
	}

	public SchematicGroup loadSchematics(String folder)
	{
		return Iris.schematics.get(folder);
	}

	private double getBiomedHeight(int x, int z, ChunkPlan plan)
	{
		return plan.getHeight(x, z, () ->
		{
			int wx = (int) Math.round((double) x * Iris.settings.gen.horizontalZoom);
			int wz = (int) Math.round((double) z * Iris.settings.gen.horizontalZoom);
			IrisBiome biome = glBiome.getBiome(wx * Iris.settings.gen.biomeScale, wz * Iris.settings.gen.biomeScale);
			double h = Iris.settings.gen.baseHeight + biome.getHeight();
			h += (glBase.getHeight(wx, wz) * 0.5) - (0.33 * 0.5);

			return h;
		});
	}

	public World getWorld()
	{
		return world;
	}

	public GMap<String, SchematicGroup> getSchematicCache()
	{
		return schematicCache;
	}

	public void setSchematicCache(GMap<String, SchematicGroup> schematicCache)
	{
		this.schematicCache = schematicCache;
	}
}