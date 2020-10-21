package com.volmit.iris.generator.atomics;

import org.bukkit.Material;
import org.bukkit.block.Biome;
import org.bukkit.block.data.BlockData;
import org.bukkit.generator.ChunkGenerator.BiomeGrid;
import org.bukkit.generator.ChunkGenerator.ChunkData;
import org.bukkit.material.MaterialData;

@SuppressWarnings("deprecation")
public class TerrainHunk extends Hunk<TerrainNode> implements BiomeGrid, ChunkData
{
	public TerrainHunk(int w, int h, int d)
	{
		super(w, h, d);
	}

	@Override
	public int getMaxHeight()
	{
		return getH();
	}

	private void set(int x, int y, int z, BlockData block)
	{
		TerrainNode n = get(x, y, z);

		if(n == null)
		{
			n = new TerrainNode(Biome.THE_VOID, block);
		}

		else
		{
			n = n.setBlockData(block);
		}

		set(x, y, z, n);
	}

	private void set(int x, int y, int z, Biome biome)
	{
		TerrainNode n = get(x, y, z);

		if(n == null)
		{
			n = new TerrainNode(biome, Material.AIR.createBlockData());
		}

		else
		{
			n = n.setBiome(biome);
		}

		set(x, y, z, n);
	}

	@Override
	public void setBlock(int x, int y, int z, Material material)
	{
		set(x, y, z, material.createBlockData());
	}

	@Override
	public void setBlock(int x, int y, int z, MaterialData material)
	{
		set(x, y, z, material.getItemType().createBlockData());
	}

	@Override
	public void setBlock(int x, int y, int z, BlockData blockData)
	{
		set(x, y, z, blockData);
	}

	@Override
	public void setRegion(int xMin, int yMin, int zMin, int xMax, int yMax, int zMax, Material material)
	{
		throw new RuntimeException("Not Supported");
	}

	@Override
	public void setRegion(int xMin, int yMin, int zMin, int xMax, int yMax, int zMax, MaterialData material)
	{
		throw new RuntimeException("Not Supported");
	}

	@Override
	public void setRegion(int xMin, int yMin, int zMin, int xMax, int yMax, int zMax, BlockData blockData)
	{
		throw new RuntimeException("Not Supported");
	}

	@Override
	public Material getType(int x, int y, int z)
	{
		return getBlockData(x, y, z).getMaterial();
	}

	@Override
	public MaterialData getTypeAndData(int x, int y, int z)
	{
		return new MaterialData(getBlockData(x, y, z).getMaterial());
	}

	@Override
	public BlockData getBlockData(int x, int y, int z)
	{
		TerrainNode n = get(x, y, z);

		if(n == null)
		{
			return Material.STONE.createBlockData();
		}

		return n.getBlockData();
	}

	@Override
	public byte getData(int x, int y, int z)
	{
		throw new RuntimeException("Not Supported");
	}

	@Override
	public Biome getBiome(int x, int z)
	{
		throw new RuntimeException("Not Supported");
	}

	@Override
	public Biome getBiome(int x, int y, int z)
	{
		TerrainNode n = get(x, y, z);

		if(n == null)
		{
			return Biome.THE_VOID;
		}

		return n.getBiome();
	}

	@Override
	public void setBiome(int x, int z, Biome bio)
	{
		throw new RuntimeException("Not Supported");
	}

	@Override
	public void setBiome(int x, int y, int z, Biome bio)
	{
		set(x, y, z, bio);
	}

	@SafeVarargs
	public static TerrainHunk combined(TerrainNode defaultNode, TerrainHunk... hunks)
	{
		int w = 0;
		int h = 0;
		int d = 0;

		for(TerrainHunk i : hunks)
		{
			w = Math.max(w, i.getW());
			h = Math.max(h, i.getH());
			d = Math.max(d, i.getD());
		}

		TerrainHunk b = new TerrainHunk(w, h, d);
		b.fill(defaultNode);

		for(TerrainHunk i : hunks)
		{
			b.insert(i);
		}

		return b;
	}

	@SafeVarargs
	public static TerrainHunk combined(TerrainHunk... hunks)
	{
		int w = 0;
		int h = 0;
		int d = 0;

		for(TerrainHunk i : hunks)
		{
			w = Math.max(w, i.getW());
			h = Math.max(h, i.getH());
			d = Math.max(d, i.getD());
		}

		TerrainHunk b = new TerrainHunk(w, h, d);

		for(TerrainHunk i : hunks)
		{
			b.insert(i);
		}

		return b;
	}
}
