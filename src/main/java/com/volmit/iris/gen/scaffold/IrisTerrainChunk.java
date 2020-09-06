package com.volmit.iris.gen.scaffold;

import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.block.Biome;
import org.bukkit.block.data.BlockData;
import org.bukkit.generator.ChunkGenerator.BiomeGrid;
import org.bukkit.generator.ChunkGenerator.ChunkData;
import org.bukkit.material.MaterialData;

import com.volmit.iris.Iris;

@SuppressWarnings("deprecation")
public class IrisTerrainChunk implements TerrainChunk
{
	private final Biome[] biome2D;
	private final IrisBiomeStorage biome3D;
	private final ChunkData rawChunkData;
	private final BiomeGrid storage;

	public IrisTerrainChunk(int maxHeight)
	{
		this(null, maxHeight);
	}

	public IrisTerrainChunk(BiomeGrid storage, int maxHeight)
	{
		this.storage = storage;
		rawChunkData = createChunkData(maxHeight);
		biome2D = storage != null ? null : Iris.biome3d ? null : new Biome[256];
		biome3D = storage != null ? null : Iris.biome3d ? new IrisBiomeStorage() : null;
	}

	private ChunkData createChunkData(int maxHeight)
	{
		try
		{
			return Bukkit.createChunkData(new HeightedFakeWorld(maxHeight));
		}

		catch(Throwable e)
		{
			e.printStackTrace();
		}

		return null;
	}

	@Override
	public Biome getBiome(int x, int z)
	{
		if(storage != null)
		{
			return storage.getBiome(x, z);
		}

		if(biome2D != null)
		{
			return biome2D[(z << 4) | x];
		}

		return biome3D.getBiome(x, 0, z);
	}

	@Override
	public Biome getBiome(int x, int y, int z)
	{
		if(storage != null)
		{
			return storage.getBiome(x, y, z);
		}

		if(biome2D != null)
		{
			return biome2D[(z << 4) | x];
		}

		return biome3D.getBiome(x, y, z);
	}

	@Override
	public void setBiome(int x, int z, Biome bio)
	{
		if(storage != null)
		{
			storage.setBiome(x, z, bio);
			return;
		}

		if(biome2D != null)
		{
			biome2D[(z << 4) | x] = bio;
			return;
		}

		biome3D.setBiome(x, 0, z, bio);
	}

	@Override
	public void setBiome(int x, int y, int z, Biome bio)
	{
		if(storage != null)
		{
			storage.setBiome(x, y, z, bio);
			return;
		}

		if(biome2D != null)
		{
			biome2D[(z << 4) | x] = bio;
			return;
		}

		biome3D.setBiome(x, y, z, bio);
	}

	@Override
	public int getMaxHeight()
	{
		return rawChunkData.getMaxHeight();
	}

	@Override
	public void setBlock(int x, int y, int z, BlockData blockData)
	{
		rawChunkData.setBlock(x, y, z, blockData);
	}

	@Override
	public BlockData getBlockData(int x, int y, int z)
	{
		return rawChunkData.getBlockData(x, y, z);
	}

	@Deprecated
	@Override
	public void setBlock(int x, int y, int z, Material material)
	{
		rawChunkData.setBlock(x, y, z, material);
	}

	@Deprecated
	@Override
	public void setBlock(int x, int y, int z, MaterialData material)
	{
		rawChunkData.setBlock(x, y, z, material);
	}

	@Deprecated
	@Override
	public void setRegion(int xMin, int yMin, int zMin, int xMax, int yMax, int zMax, Material material)
	{
		rawChunkData.setRegion(xMin, yMin, zMin, xMax, yMax, zMax, material);
	}

	@Deprecated
	@Override
	public void setRegion(int xMin, int yMin, int zMin, int xMax, int yMax, int zMax, MaterialData material)
	{
		rawChunkData.setRegion(xMin, yMin, zMin, xMax, yMax, zMax, material);
	}

	@Override
	public void setRegion(int xMin, int yMin, int zMin, int xMax, int yMax, int zMax, BlockData blockData)
	{
		rawChunkData.setRegion(xMin, yMin, zMin, xMax, yMax, zMax, blockData);
	}

	@Deprecated
	@Override
	public Material getType(int x, int y, int z)
	{
		return rawChunkData.getType(x, y, z);
	}

	@Deprecated
	@Override
	public MaterialData getTypeAndData(int x, int y, int z)
	{
		return rawChunkData.getTypeAndData(x, y, z);
	}

	@Deprecated
	@Override
	public byte getData(int x, int y, int z)
	{
		return rawChunkData.getData(x, y, z);
	}

	@Override
	public ChunkData getRaw()
	{
		return rawChunkData;
	}
}
