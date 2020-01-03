package ninja.bytecode.iris.generator;

import java.io.File;
import java.util.List;
import java.util.Random;

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
import ninja.bytecode.iris.generator.layer.GenLayerRidge;
import ninja.bytecode.iris.generator.populator.BiomeBiasSchematicPopulator;
import ninja.bytecode.iris.schematic.Schematic;
import ninja.bytecode.iris.util.AtomicChunkData;
import ninja.bytecode.iris.util.ChunkPlan;
import ninja.bytecode.iris.util.IrisInterpolation;
import ninja.bytecode.iris.util.MB;
import ninja.bytecode.iris.util.ParallelChunkGenerator;
import ninja.bytecode.shuriken.collections.GList;
import ninja.bytecode.shuriken.execution.J;
import ninja.bytecode.shuriken.logging.L;
import ninja.bytecode.shuriken.math.M;
import ninja.bytecode.shuriken.math.RNG;

public class IrisGenerator extends ParallelChunkGenerator
{
	private MB WATER = new MB(Material.STATIONARY_WATER);
	private MB BEDROCK = new MB(Material.BEDROCK);
	private GenLayerBase glBase;
	private GenLayerLayeredNoise glLNoise;
	private GenLayerRidge glRidge;
	private GenLayerBiome glBiome;
	private GenLayerCaves glCaves;
	private RNG rTerrain;
	private World world;

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
		int wx = (int) Math.round((double) wxx * Iris.settings.gen.horizontalZoom);
		int wz = (int) Math.round((double) wzx * Iris.settings.gen.horizontalZoom);
		IrisBiome biome = glBiome.getBiome(wx * Iris.settings.gen.biomeScale, wz * Iris.settings.gen.biomeScale);
		double hv = IrisInterpolation.getBicubicNoise(wxx, wzx, (xf, zf) -> getBiomedHeight((int) Math.round(xf), (int) Math.round(zf), plan));
		hv += glLNoise.generateLayer(hv, wxx, wzx);
		hv -= glRidge.generateLayer(hv, wxx, wzx);
		int height = (int) Math.round(M.clip(hv, 0D, 1D) * 253);
		int max = Math.max(height, seaLevel);
		IrisBiome override = null;

		for(int i = 0; i < max; i++)
		{
			MB mb = new MB(Material.STONE);
			boolean underwater = i >= height && i < seaLevel;
			boolean underground = i < height;

			if(underwater)
			{
				mb = WATER;
			}

			if(underground && (height - 1) - i < glBase.scatterInt(x, i, z, 4) + 2)
			{
				mb = biome.getDirt(wx, wz);
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
		int b = 0;
		for(IrisBiome i : IrisBiome.getBiomes())
		{
			b++;
			L.i("Processing Populators for Biome " + i.getName());
			
			for(String j : i.getSchematicGroups().keySet())
			{
				p.add(new BiomeBiasSchematicPopulator(i.getSchematicGroups().get(j), i, loadSchematics(j)));
			}
		}
		
		J.attempt(() -> p.add(new BiomeBiasSchematicPopulator(5, IrisBiome.JUNGLE, loadSchematics(""))));
		
		L.i("Initialized " + b + " Biomes and " + p.size() + " Populators");
		L.flush();
		
		return p;
	}
	
	private Schematic[] loadSchematics(String folder)
	{
		File f = new File(Iris.instance.getDataFolder(), "objects/" + folder);
		GList<Schematic> s = new GList<>();
		
		try
		{
			if(f.exists() && f.isDirectory())
			{
				for(File i : f.listFiles())
				{
					if(i.isFile() && i.getName().endsWith(".ish"))
					{
						s.add(Schematic.load(i));
					}
				}
			}
		}
		
		catch(Throwable e)
		{
			
		}
		
		return s.toArray(new Schematic[s.size()]);
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