package ninja.bytecode.iris.generator;

import org.bukkit.Material;
import org.bukkit.World;
import org.bukkit.block.data.BlockData;

import lombok.Data;
import lombok.EqualsAndHashCode;
import ninja.bytecode.iris.object.IrisBiome;
import ninja.bytecode.iris.object.IrisRegion;
import ninja.bytecode.iris.object.atomics.AtomicSliver;
import ninja.bytecode.iris.util.BiomeMap;
import ninja.bytecode.iris.util.BiomeResult;
import ninja.bytecode.iris.util.RNG;
import ninja.bytecode.shuriken.collections.KList;

@Data
@EqualsAndHashCode(callSuper = false)
public abstract class TerrainChunkGenerator extends ParallelChunkGenerator
{
	protected static final BlockData AIR = Material.AIR.createBlockData();
	protected static final BlockData STONE = Material.STONE.createBlockData();
	protected static final BlockData WATER = Material.WATER.createBlockData();

	public TerrainChunkGenerator(String dimensionName, int threads)
	{
		super(dimensionName, threads);
	}

	public void onInit(World world, RNG rng)
	{
		super.onInit(world, rng);
	}

	@Override
	protected void onGenerateColumn(int cx, int cz, int rx, int rz, int x, int z, AtomicSliver sliver, BiomeMap biomeMap)
	{
		BlockData block;
		int fluidHeight = getDimension().getFluidHeight();
		double ox = getModifiedX(rx, rz);
		double oz = getModifiedZ(rx, rz);
		double wx = getZoomed(ox);
		double wz = getZoomed(oz);
		int depth = 0;
		double noise = getNoiseHeight(rx, rz);
		int height = (int) Math.round(noise) + fluidHeight;
		IrisBiome biome = sampleTrueBiome(rx, rz).getBiome();

		KList<BlockData> layers = biome.generateLayers(wx, wz, masterRandom, height);

		for(int k = Math.max(height, fluidHeight); k >= 0; k--)
		{
			boolean underwater = k > height && k <= fluidHeight;

			if(biomeMap != null)
			{
				sliver.set(k, biome.getDerivative());
				biomeMap.setBiome(x, z, biome);
			}

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

	protected double getNoiseHeight(int rx, int rz)
	{
		double wx = getZoomed(rx);
		double wz = getZoomed(rz);

		return getBiomeHeight(wx, wz);
	}

	public BiomeResult sampleTrueBiome(int x, int z)
	{
		double wx = getModifiedX(x, z);
		double wz = getModifiedZ(x, z);
		IrisRegion region = sampleRegion(x, z);
		int height = sampleHeight(x, z);
		double sh = region.getShoreHeight(wx, wz);
		IrisBiome current = sampleBiome(x, z).getBiome();

		// Stop shores from spawning on land
		if(current.isShore() && height > sh)
		{
			return glBiome.generateLandData(wx, wz, region);
		}

		// Stop oceans from spawning on mountains
		if(current.isShore() && height <= getDimension().getFluidHeight())
		{
			return glBiome.generateSeaData(wx, wz, region);
		}

		// Stop oceans from spawning on land
		if(current.isSea() && height > getDimension().getFluidHeight())
		{
			return glBiome.generateLandData(wx, wz, region);
		}

		// Stop land from spawning underwater
		if(height <= getDimension().getFluidHeight())
		{
			return glBiome.generateSeaData(wx, wz, region);
		}

		// Stop land from spawning where shores go
		if(height <= getDimension().getFluidHeight() + sh)
		{
			return glBiome.generateShoreData(wx, wz, region);
		}

		return glBiome.generateRegionData(wx, wz, region);
	}

	@Override
	protected int onSampleColumnHeight(int cx, int cz, int rx, int rz, int x, int z)
	{
		int fluidHeight = getDimension().getFluidHeight();
		double noise = getNoiseHeight(rx, rz);

		return (int) Math.round(noise) + fluidHeight;
	}
}
