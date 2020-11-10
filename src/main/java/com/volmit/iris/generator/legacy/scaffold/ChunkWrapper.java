package com.volmit.iris.generator.legacy.scaffold;

import org.bukkit.Chunk;
import org.bukkit.Material;
import org.bukkit.block.data.BlockData;
import org.bukkit.generator.ChunkGenerator.ChunkData;
import org.bukkit.material.MaterialData;

@SuppressWarnings("deprecation")
public class ChunkWrapper implements ChunkData
{
	private final Chunk chunk;

	public ChunkWrapper(Chunk chunk)
	{
		this.chunk = chunk;
	}

	@Override
	public int getMaxHeight()
	{
		return chunk.getWorld().getMaxHeight();
	}

	@Override
	public void setBlock(int x, int y, int z, Material material)
	{
		chunk.getBlock(x, y, z).setType(material, false);
	}

	@Override
	public void setBlock(int x, int y, int z, MaterialData material)
	{
		throw new UnsupportedOperationException();
	}

	@Override
	public void setBlock(int x, int y, int z, BlockData blockData)
	{
		chunk.getBlock(x, y, z).setBlockData(blockData, false);
	}

	@Override
	public void setRegion(int xMin, int yMin, int zMin, int xMax, int yMax, int zMax, Material material)
	{
		throw new UnsupportedOperationException();
	}

	@Override
	public void setRegion(int xMin, int yMin, int zMin, int xMax, int yMax, int zMax, MaterialData material)
	{
		throw new UnsupportedOperationException();
	}

	@Override
	public void setRegion(int xMin, int yMin, int zMin, int xMax, int yMax, int zMax, BlockData blockData)
	{
		throw new UnsupportedOperationException();
	}

	@Override
	public Material getType(int x, int y, int z)
	{
		return chunk.getBlock(x, y, z).getType();
	}

	@Override
	public MaterialData getTypeAndData(int x, int y, int z)
	{
		throw new UnsupportedOperationException();
	}

	@Override
	public BlockData getBlockData(int x, int y, int z)
	{
		return chunk.getBlock(x, y, z).getBlockData();
	}

	@Override
	public byte getData(int x, int y, int z)
	{
		throw new UnsupportedOperationException();
	}
}
