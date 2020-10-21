package com.volmit.iris.gen;

import java.util.function.Function;

import org.bukkit.Chunk;
import org.bukkit.Material;
import org.bukkit.block.Biome;
import org.bukkit.block.data.BlockData;
import org.bukkit.entity.Player;
import org.bukkit.event.block.BlockDropItemEvent;
import org.bukkit.event.entity.EntitySpawnEvent;
import org.bukkit.generator.ChunkGenerator.BiomeGrid;
import org.bukkit.generator.ChunkGenerator.ChunkData;
import org.bukkit.material.MaterialData;
import org.bukkit.util.BlockVector;

import com.volmit.iris.Iris;
import com.volmit.iris.gen.scaffold.GeneratedChunk;
import com.volmit.iris.gen.scaffold.TerrainChunk;
import com.volmit.iris.gen.scaffold.TerrainTarget;
import com.volmit.iris.util.RNG;

public abstract class SkyTerrainProvider extends PostBlockTerrainProvider
{
	private IrisTerrainProvider actualSky;

	public SkyTerrainProvider(TerrainTarget t, String dimensionName, int threads)
	{
		super(t, dimensionName, threads);
	}

	public void skyHotload()
	{
		if(actualSky != null)
		{
			actualSky.onHotloaded();
		}
	}

	@Override
	protected GeneratedChunk onGenerate(RNG random, int x, int z, TerrainChunk terrain)
	{
		GeneratedChunk gc = super.onGenerate(random, x, z, terrain);

		if(getDimension().hasSky())
		{
			getDimension().getSky().setSkyDimension(true);
			if(actualSky == null)
			{
				actualSky = new IrisTerrainProvider(getTarget(), "...", getThreads())
				{
					@Override
					public boolean shouldGenerateVanillaStructures()
					{
						return false;
					}

					@Override
					public boolean shouldGenerateMobs()
					{
						return false;
					}

					@Override
					public boolean shouldGenerateDecorations()
					{
						return false;
					}

					@Override
					public boolean shouldGenerateCaves()
					{
						return getDimension().getSky().isVanillaCaves();
					}

					@Override
					public BlockVector computeSpawn(Function<BlockVector, Boolean> allowed)
					{
						return null;
					}

					@Override
					public boolean canSpawn(int x, int z)
					{
						return false;
					}

					@Override
					protected void onSpawn(EntitySpawnEvent e)
					{

					}

					@Override
					protected void onPlayerJoin(Player p)
					{

					}

					@Override
					protected void onFailure(Throwable e)
					{

					}

					@Override
					protected void onChunkUnloaded(Chunk c)
					{

					}

					@Override
					protected void onChunkLoaded(Chunk c)
					{

					}

					@Override
					protected void handleDrops(BlockDropItemEvent e)
					{

					}
				};

				actualSky.changeThreadCount(getThreads());
				actualSky.forceDimension(getDimension().getSky());
				actualSky.setMasterRandom(new RNG(getMasterRandom().nextParallelRNG(299455).nextLong()));
				Iris.info("Created Sky Dimension Provider");
			}

			try
			{
				actualSky.onGenerate(random, x, z, new TerrainChunk()
				{
					@Override
					public void setRegion(int xMin, int yMin, int zMin, int xMax, int yMax, int zMax, BlockData blockData)
					{

					}

					@Override
					public void setRegion(int xMin, int yMin, int zMin, int xMax, int yMax, int zMax, MaterialData material)
					{

					}

					@Override
					public void setRegion(int xMin, int yMin, int zMin, int xMax, int yMax, int zMax, Material material)
					{

					}

					@Override
					public void setBlock(int x, int y, int z, MaterialData material)
					{
						gc.getTerrain().setBlock(x, 255 - y, z, material);
					}

					@Override
					public void setBlock(int x, int y, int z, Material material)
					{
						gc.getTerrain().setBlock(x, 255 - y, z, material);
					}

					@Override
					public MaterialData getTypeAndData(int x, int y, int z)
					{
						return gc.getTerrain().getTypeAndData(x, 255 - y, z);
					}

					@Override
					public Material getType(int x, int y, int z)
					{
						return gc.getTerrain().getType(x, 255 - y, z);
					}

					@Override
					public byte getData(int x, int y, int z)
					{
						return 0;
					}

					@Override
					public void setRaw(ChunkData data)
					{

					}

					@Override
					public void setBlock(int x, int y, int z, BlockData blockData)
					{
						gc.getTerrain().setBlock(x, 255 - y, z, blockData);
					}

					@Override
					public void setBiome(int x, int y, int z, Biome bio)
					{
						gc.getTerrain().setBiome(x, 255 - y, z, bio);
					}

					@Override
					public void setBiome(int x, int z, Biome bio)
					{

					}

					@Override
					public void inject(BiomeGrid biome)
					{

					}

					@Override
					public ChunkData getRaw()
					{
						return null;
					}

					@Override
					public int getMaxHeight()
					{
						return gc.getTerrain().getMaxHeight();
					}

					@Override
					public BlockData getBlockData(int x, int y, int z)
					{
						return gc.getTerrain().getBlockData(x, 255 - y, z);
					}

					@Override
					public Biome getBiome(int x, int y, int z)
					{
						return gc.getTerrain().getBiome(x, 255 - y, z);
					}

					@Override
					public Biome getBiome(int x, int z)
					{
						return Biome.THE_VOID;
					}
				});
			}

			catch(Throwable e)
			{

			}
		}

		return gc;
	}
}
