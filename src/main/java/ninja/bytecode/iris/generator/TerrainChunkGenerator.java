package ninja.bytecode.iris.generator;

import org.bukkit.Material;
import org.bukkit.World;
import org.bukkit.block.data.BlockData;

import lombok.Data;
import lombok.EqualsAndHashCode;
import ninja.bytecode.iris.object.IrisBiome;
import ninja.bytecode.iris.object.IrisRegion;
import ninja.bytecode.iris.object.atomics.AtomicSliver;
import ninja.bytecode.iris.util.BiomeResult;
import ninja.bytecode.iris.util.CNG;
import ninja.bytecode.iris.util.RNG;
import ninja.bytecode.shuriken.collections.KList;

@Data
@EqualsAndHashCode(callSuper = false)
public abstract class TerrainChunkGenerator extends ParallelChunkGenerator
{
	protected static final BlockData STONE = Material.STONE.createBlockData();
	protected static final BlockData WATER = Material.WATER.createBlockData();
	protected CNG terrainNoise;

	public TerrainChunkGenerator(String dimensionName, int threads)
	{
		super(dimensionName, threads);
	}

	public void onInit(World world, RNG rng)
	{
		super.onInit(world, rng);
		terrainNoise = CNG.signature(masterRandom.nextParallelRNG(2));
	}

	@Override
	protected void onGenerateColumn(int cx, int cz, int rx, int rz, int x, int z, AtomicSliver sliver)
	{
		BlockData block;
		int fluidHeight = getDimension().getFluidHeight();
		double ox = getModifiedX(rx, rz);
		double oz = getModifiedZ(rx, rz);
		double wx = getZoomed(ox);
		double wz = getZoomed(oz);
		int depth = 0;
		IrisRegion region = glBiome.getRegion(wx, wz);
		BiomeResult biomeResult = glBiome.generateRegionData(wx, wz, region);
		IrisBiome biome = biomeResult.getBiome();
		double lo = interpolateHeight(ox, oz, (b) -> b.getLowHeight());
		double hi = interpolateSurface(ox, oz, (b) -> b.getHighHeight());
		double noise = lo + (terrainNoise.fitDoubleD(0, hi - lo, wx, wz));
		int height = (int) Math.round(noise) + fluidHeight;

		if(height < fluidHeight + 1)
		{
			if(biome.isLand())
			{
				biome = glBiome.generateShoreData(wx, wz, region).getBiome();
			}
		}

		KList<BlockData> layers = biome.generateLayers(wx, wz, masterRandom, height);

		for(int k = Math.max(height, fluidHeight); k >= 0; k--)
		{
			boolean underwater = k > height && k <= fluidHeight;
			sliver.set(k, biome.getDerivative());

			if(underwater)
			{
				block = WATER;
			}

			else
			{
				block = layers.hasIndex(depth) ? layers.get(depth) : STONE;
				depth++;
			}

			sliver.set(k, block);
		}
	}

	@Override
	protected int onSampleColumnHeight(int cx, int cz, int rx, int rz, int x, int z)
	{
		int fluidHeight = getDimension().getFluidHeight();
		double ox = getModifiedX(rx, rz);
		double oz = getModifiedZ(rx, rz);
		double lo = interpolateHeight(ox, oz, (b) -> b.getLowHeight());
		double hi = interpolateSurface(ox, oz, (b) -> b.getHighHeight());
		double noise = lo + (terrainNoise.fitDoubleD(0, hi - lo, getZoomed(ox), getZoomed(oz)));

		return (int) Math.round(noise) + fluidHeight;
	}
}
