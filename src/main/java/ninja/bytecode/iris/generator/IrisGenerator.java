package ninja.bytecode.iris.generator;

import java.io.File;
import java.io.IOException;
import java.util.List;
import java.util.Random;

import org.bukkit.Bukkit;
import org.bukkit.Chunk;
import org.bukkit.Material;
import org.bukkit.World;
import org.bukkit.block.Biome;
import org.bukkit.generator.BlockPopulator;

import ninja.bytecode.iris.Iris;
import ninja.bytecode.iris.generator.biome.IrisBiome;
import ninja.bytecode.iris.generator.layer.GenLayerBase;
import ninja.bytecode.iris.generator.layer.GenLayerBiome;
import ninja.bytecode.iris.generator.layer.GenLayerCaves;
import ninja.bytecode.iris.generator.layer.GenLayerLayeredNoise;
import ninja.bytecode.iris.generator.layer.GenLayerOreCoal;
import ninja.bytecode.iris.generator.layer.GenLayerOreDiamond;
import ninja.bytecode.iris.generator.layer.GenLayerOreEmerald;
import ninja.bytecode.iris.generator.layer.GenLayerOreGold;
import ninja.bytecode.iris.generator.layer.GenLayerOreIron;
import ninja.bytecode.iris.generator.layer.GenLayerOreLapis;
import ninja.bytecode.iris.generator.layer.GenLayerRidge;
import ninja.bytecode.iris.generator.populator.BiomeBiasSchematicPopulator;
import ninja.bytecode.iris.schematic.Schematic;
import ninja.bytecode.iris.schematic.SchematicGroup;
import ninja.bytecode.iris.util.AtomicChunkData;
import ninja.bytecode.iris.util.ChunkPlan;
import ninja.bytecode.iris.util.IrisInterpolation;
import ninja.bytecode.iris.util.MB;
import ninja.bytecode.iris.util.ParallelChunkGenerator;
import ninja.bytecode.shuriken.collections.GList;
import ninja.bytecode.shuriken.collections.GMap;
import ninja.bytecode.shuriken.execution.J;
import ninja.bytecode.shuriken.io.IO;
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
	//@done
	private MB WATER = new MB(Material.STATIONARY_WATER);
	private MB BEDROCK = new MB(Material.BEDROCK);
	private GenLayerBase glBase;
	private GenLayerLayeredNoise glLNoise;
	private GenLayerRidge glRidge;
	private GenLayerBiome glBiome;
	private GenLayerCaves glCaves;
	private GenLayerOreIron glOreIron;
	private GenLayerOreCoal glOreCoal;
	private GenLayerOreLapis glOreLapis;
	private GenLayerOreGold glOreGold;
	private GenLayerOreEmerald glOreEmerald;
	private GenLayerOreDiamond glOreDiamond;
	private RNG rTerrain;
	private World world;
	private GMap<String, SchematicGroup> schematicCache = new GMap<>();

	@Override
	public void onInit(World world, Random random)
	{
		this.world = world;
		rTerrain = new RNG(world.getSeed() + 1024);
		glBase = new GenLayerBase(this, world, random, rTerrain.nextParallelRNG(1));
		glLNoise = new GenLayerLayeredNoise(this, world, random, rTerrain.nextParallelRNG(2));
		glRidge = new GenLayerRidge(this, world, random, rTerrain.nextParallelRNG(3));
		glBiome = new GenLayerBiome(this, world, random, rTerrain.nextParallelRNG(4));
		glCaves = new GenLayerCaves(this, world, random, rTerrain.nextParallelRNG(-1));
		glOreIron = new GenLayerOreIron(this, world, random, rTerrain.nextParallelRNG(-500), 10);
		glOreLapis = new GenLayerOreLapis(this, world, random, rTerrain.nextParallelRNG(-501), 15);
		glOreCoal = new GenLayerOreCoal(this, world, random, rTerrain.nextParallelRNG(-502), 20);
		glOreGold = new GenLayerOreGold(this, world, random, rTerrain.nextParallelRNG(-503), 25);
		glOreEmerald = new GenLayerOreEmerald(this, world, random, rTerrain.nextParallelRNG(-504), 30);
		glOreDiamond = new GenLayerOreDiamond(this, world, random, rTerrain.nextParallelRNG(-505), 35);
	}

	@Override
	public ChunkPlan onInitChunk(World world, int x, int z, Random random)
	{
		return new ChunkPlan();
	}

	@Override
	public Biome genColumn(int wxx, int wzx, int x, int z, ChunkPlan plan)
	{
		int seaLevel = Iris.settings.gen.seaLevel;
		double wx = Math.round((double) wxx * Iris.settings.gen.horizontalZoom);
		double wz = Math.round((double) wzx * Iris.settings.gen.horizontalZoom);
		IrisBiome biome = glBiome.getBiome(wx * Iris.settings.gen.biomeScale, wz * Iris.settings.gen.biomeScale);
		double hv = IrisInterpolation.getBicubicNoise(wxx, wzx, (xf, zf) -> getBiomedHeight((int) Math.round(xf), (int) Math.round(zf), plan));
		hv += glLNoise.generateLayer(hv, wxx, wzx);
		hv -= glRidge.generateLayer(hv, wxx, wzx);
		int height = (int) Math.round(M.clip(hv, 0D, 1D) * 253);
		int max = Math.max(height, seaLevel);
		IrisBiome override = null;

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
				if(height > 61 && height < glBase.scatterInt(x, i, z, 4) + 65)
				{
					override = IrisBiome.BEACH;
				}

				else if(height < 63)
				{
					if(i < 36)
					{
						override = IrisBiome.DEEP_OCEAN;
					}

					else if(i < 50)
					{
						override = IrisBiome.OCEAN;
					}

					else
					{
						override = IrisBiome.LAKE;
					}
				}

				if(override != null)
				{
					biome = override;
				}

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

			if(Iris.settings.gen.flatBedrock ? i == 1 : i < glBase.scatterInt(x, i, z, 3))
			{
				mb = BEDROCK;
			}

			setBlock(x, i, z, mb.material, mb.data);
		}

		glCaves.genCaves(wxx, wzx, x, z, height, this);
		glOreIron.genOre(wxx, wzx, x, z, height, this, biome);
		glOreLapis.genOre(wxx, wzx, x, z, height, this, biome);
		glOreCoal.genOre(wxx, wzx, x, z, height, this, biome);
		glOreGold.genOre(wxx, wzx, x, z, height, this, biome);
		glOreEmerald.genOre(wxx, wzx, x, z, height, this, biome);
		glOreDiamond.genOre(wxx, wzx, x, z, height, this, biome);

		if(override != null)
		{
			return override.getRealBiome();
		}

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
			int b = 0;
			int sch = 0;
			for(IrisBiome i : IrisBiome.getAllBiomes())
			{
				b++;
				L.i("Processing Populators for Biome " + i.getName());

				for(String j : i.getSchematicGroups().keySet())
				{
					SchematicGroup gs = loadSchematics(j);
					sch += gs.size();
					p.add(new BiomeBiasSchematicPopulator(i.getSchematicGroups().get(j), i, gs.getSchematics().toArray(new Schematic[gs.size()])));
				}
			}

			p.add(new BlockPopulator()
			{
				@SuppressWarnings("deprecation")
				@Override
				public void populate(World world, Random random, Chunk source)
				{
					Bukkit.getScheduler().scheduleSyncDelayedTask(Iris.instance, () -> world.refreshChunk(source.getX(), source.getZ()), 50);
				}
			});

			L.i("Initialized " + b + " Biomes with " + p.size() + " Populators using " + sch + " Schematics");
		}

		return p;
	}

	private SchematicGroup loadSchematics(String folder)
	{
		if(schematicCache.containsKey(folder))
		{
			return schematicCache.get(folder);
		}

		File f = new File(Iris.instance.getDataFolder(), "objects/" + folder);
		GList<Schematic> s = new GList<>();
		GList<String> flags = new GList<>();

		if(f.exists() && f.isDirectory())
		{
			for(File i : f.listFiles())
			{
				if(i.isFile() && i.getName().endsWith(".ifl"))
				{
					try
					{
						flags.add(IO.readAll(i).split("\\Q\n\\E"));
					}

					catch(IOException e)
					{
						e.printStackTrace();
					}
				}

				if(i.isFile() && i.getName().endsWith(".ish"))
				{
					J.attempt(() ->
					{
						Schematic sc = Schematic.load(i);
						s.add(sc);
					});
				}
			}
		}

		for(String i : flags)
		{
			String flag = i.trim().toLowerCase();

			if(flag.equals("center"))
			{
				for(Schematic j : s)
				{
					j.setCenteredHeight();
				}

				L.i("Centered " + s.size() + " Schematics");
			}
		}

		L.i("Loaded " + s.size() + " Schematics in " + folder);
		SchematicGroup g = new SchematicGroup(folder);
		g.setSchematics(s);
		g.setFlags(flags);

		for(String i : flags)
		{
			if(i.startsWith("priority="))
			{
				J.attempt(() -> g.setPriority(Integer.valueOf(i.split("\\Q=\\E")[1]).intValue()));
			}
		}
		
		schematicCache.put(folder, g);
		return g;
	}

	private double getBiomedHeight(int x, int z, ChunkPlan plan)
	{
		return plan.getHeight(x, z, () ->
		{
			int wx = (int) Math.round((double) x * Iris.settings.gen.horizontalZoom);
			int wz = (int) Math.round((double) z * Iris.settings.gen.horizontalZoom);
			IrisBiome biome = glBiome.getBiome(wx * Iris.settings.gen.biomeScale, wz * Iris.settings.gen.biomeScale);
			double h = Iris.settings.gen.baseHeight + biome.getHeight();
			h += (glBase.getHeight(wx, wz) * biome.getAmp()) - (0.33 * biome.getAmp());

			return h;
		});
	}

	public World getWorld()
	{
		return world;
	}
}