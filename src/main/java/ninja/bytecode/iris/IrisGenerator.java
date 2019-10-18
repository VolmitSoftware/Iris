package ninja.bytecode.iris;

import java.awt.Polygon;
import java.awt.geom.Point2D;
import java.awt.geom.Point2D.Double;
import java.util.List;
import java.util.Random;

import org.apache.logging.log4j.core.layout.GelfLayout;
import org.bukkit.Material;
import org.bukkit.World;
import org.bukkit.block.Biome;
import org.bukkit.generator.BlockPopulator;
import org.bukkit.util.Vector;
import org.bukkit.util.noise.PerlinNoiseGenerator;

import net.minecraft.server.v1_12_R1.GenLayer;
import net.minecraft.server.v1_12_R1.WorldProviderNormal;
import ninja.bytecode.iris.gen.GenLayerBase;
import ninja.bytecode.iris.gen.GenLayerSuperSample;
import ninja.bytecode.iris.gen.IGenLayer;
import ninja.bytecode.shuriken.collections.GList;
import ninja.bytecode.shuriken.math.CNG;
import ninja.bytecode.shuriken.math.M;
import ninja.bytecode.shuriken.math.RNG;

public class IrisGenerator extends ParallelChunkGenerator
{
	private MB AIR = new MB(Material.AIR);
	private MB WATER = new MB(Material.STATIONARY_WATER);
	private MB SAND = new MB(Material.SAND);
	private MB BEDROCK = new MB(Material.BEDROCK);
	private GList<IGenLayer> genLayers;
	private GenLayerBase glBase;
	private GenLayerSuperSample glSuperSample;
	private int waterLevel = 127;
	private GList<Vector> updates = new GList<>();
	private String wf;

	@Override
	public void onInit(World world, Random random)
	{
		wf = world.getName();
		updates = new GList<>();
		genLayers = new GList<>();
		RNG rng = new RNG(world.getSeed());
		genLayers.add(glBase = new GenLayerBase(this, world, random, rng.nextRNG()));
		genLayers.add(glSuperSample = new GenLayerSuperSample(this, world, random, rng.nextRNG()));
	}

	public int getHeight(double dx, double dz)
	{
		double height = M.clip(glSuperSample.getSuperSampledHeight(dx, dz), 0D, 1D);

		return (int) (height * 253);
	}

	public double getRawHeight(double dx, double dz)
	{
		double noise = 0 + Iris.settings.gen.baseHeight;

		for(IGenLayer i : genLayers)
		{
			noise = i.generateLayer(noise, dx, dz);
		}

		return M.clip(noise, 0D, 1D);
	}

	@Override
	public Biome genColumn(int wxx, int wzx, int x, int z)
	{
		int wx = (int) Math.round((double) wxx * Iris.settings.gen.horizontalZoom);
		int wz = (int) Math.round((double) wzx * Iris.settings.gen.horizontalZoom);
		int height = getHeight(wx, wz);
		
		for(int i = 0; i < height; i++)
		{
			MB mb = new MB(Material.STONE);

			setBlock(x, i, z, mb.material, mb.data);
		}

		return Biome.PLAINS;
	}

	@Override
	public List<BlockPopulator> getDefaultPopulators(World world)
	{
		GList<BlockPopulator> p = new GList<BlockPopulator>();

		return p;
	}

	public int pick(int max, double noise)
	{
		return (int) (noise * max);
	}

	public MB pick(MB[] array, double noise)
	{
		return array[pick(array.length, noise)];
	}

	@Override
	public void onInitChunk(World world, int x, int z, Random random)
	{
		
	}
}