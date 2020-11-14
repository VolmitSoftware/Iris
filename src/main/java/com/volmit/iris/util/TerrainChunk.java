package com.volmit.iris.util;

import org.bukkit.World;
import org.bukkit.block.Biome;
import org.bukkit.block.data.BlockData;
import org.bukkit.generator.ChunkGenerator.BiomeGrid;
import org.bukkit.generator.ChunkGenerator.ChunkData;

public interface TerrainChunk extends BiomeGrid, ChunkData
{
	public static TerrainChunk create(World world)
	{
		return create(world.getMaxHeight());
	}

	public static TerrainChunk create(int maxHeight)
	{
		return new LinkedTerrainChunk(maxHeight);
	}

	public static TerrainChunk create(World world, BiomeGrid grid)
	{
		return create(world.getMaxHeight(), grid);
	}

	public static TerrainChunk create(ChunkData raw, BiomeGrid grid)
	{
		return new LinkedTerrainChunk(grid, raw);
	}

	public static TerrainChunk create(int maxHeight, BiomeGrid grid)
	{
		return new LinkedTerrainChunk(grid, maxHeight);
	}

	public void setRaw(ChunkData data);

	/**
	 * Get biome at x, z within chunk being generated
	 *
	 * @param x
	 *            - 0-15
	 * @param z
	 *            - 0-15
	 * @return Biome value
	 * @deprecated biomes are now 3-dimensional
	 */
	@Deprecated
	Biome getBiome(int x, int z);

	/**
	 * Get biome at x, z within chunk being generated
	 *
	 * @param x
	 *            - 0-15
	 * @param y
	 *            - 0-255
	 * @param z
	 *            - 0-15
	 * @return Biome value
	 */
	Biome getBiome(int x, int y, int z);

	/**
	 * Set biome at x, z within chunk being generated
	 *
	 * @param x
	 *            - 0-15
	 * @param z
	 *            - 0-15
	 * @param bio
	 *            - Biome value
	 * @deprecated biomes are now 3-dimensional
	 */
	@Deprecated
	void setBiome(int x, int z, Biome bio);

	/**
	 * Set biome at x, z within chunk being generated
	 *
	 * @param x
	 *            - 0-15
	 * @param y
	 *            - 0-255
	 * @param z
	 *            - 0-15
	 * @param bio
	 *            - Biome value
	 */
	void setBiome(int x, int y, int z, Biome bio);

	/**
	 * Get the maximum height for the chunk.
	 *
	 * Setting blocks at or above this height will do nothing.
	 *
	 * @return the maximum height
	 */
	public int getMaxHeight();

	/**
	 * Set the block at x,y,z in the chunk data to material.
	 *
	 * Setting blocks outside the chunk's bounds does nothing.
	 *
	 * @param x
	 *            the x location in the chunk from 0-15 inclusive
	 * @param y
	 *            the y location in the chunk from 0 (inclusive) - maxHeight
	 *            (exclusive)
	 * @param z
	 *            the z location in the chunk from 0-15 inclusive
	 * @param blockData
	 *            the type to set the block to
	 */
	public void setBlock(int x, int y, int z, BlockData blockData);

	/**
	 * Get the type and data of the block at x, y, z.
	 *
	 * Getting blocks outside the chunk's bounds returns air.
	 *
	 * @param x
	 *            the x location in the chunk from 0-15 inclusive
	 * @param y
	 *            the y location in the chunk from 0 (inclusive) - maxHeight
	 *            (exclusive)
	 * @param z
	 *            the z location in the chunk from 0-15 inclusive
	 * @return the data of the block or the BlockData for air if x, y or z are
	 *         outside the chunk's bounds
	 */
	public BlockData getBlockData(int x, int y, int z);

	public ChunkData getRaw();

	public void inject(BiomeGrid biome);
}
