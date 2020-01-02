package ninja.bytecode.iris.generator;

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
import ninja.bytecode.iris.generator.layer.GenLayerLayeredNoise;
import ninja.bytecode.iris.generator.layer.GenLayerRidge;
import ninja.bytecode.iris.generator.populator.PopulatorTrees;
import ninja.bytecode.iris.util.AtomicChunkData;
import ninja.bytecode.iris.util.ChunkPlan;
import ninja.bytecode.iris.util.IrisInterpolation;
import ninja.bytecode.iris.util.MB;
import ninja.bytecode.iris.util.ParallelChunkGenerator;
import ninja.bytecode.shuriken.collections.GList;
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
	private RNG rTerrain;
	private RNG rScatter;
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
	}

	public World getWorld()
	{
		return world;
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

		for(int i = 0; i < Math.max(height, seaLevel); i++)
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
				mb = biome.getSurface(wx, wz, rTerrain);
			}

			if(Iris.settings.gen.flatBedrock ? i == 0 : i < glBase.scatterInt(x, i, z, 3))
			{
				mb = BEDROCK;
			}

			setBlock(x, i, z, mb.material, mb.data);
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

	private double getBiomedHeight(int x, int z, ChunkPlan plan)
	{
		return plan.getHeight(x, z, () -> {
			int wx = (int) Math.round((double) x * Iris.settings.gen.horizontalZoom);
			int wz = (int) Math.round((double) z * Iris.settings.gen.horizontalZoom);
			IrisBiome biome = glBiome.getBiome(wx * Iris.settings.gen.biomeScale, wz * Iris.settings.gen.biomeScale);
			double h = Iris.settings.gen.baseHeight + biome.getHeight();
			h += (glBase.getHeight(wx, wz) * biome.getAmp()) - (0.33 * biome.getAmp());
			
			return h;
		});
	}

	@Override
	public List<BlockPopulator> getDefaultPopulators(World world)
	{
		GList<BlockPopulator> p = new GList<BlockPopulator>();
		p.add(new PopulatorTrees());
		return p;
	}
}