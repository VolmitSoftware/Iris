package com.volmit.iris.util;

import org.bukkit.Material;
import org.bukkit.block.data.BlockData;
import org.bukkit.block.data.Levelled;
import org.bukkit.block.data.Waterlogged;
import org.bukkit.block.data.type.Slab;

import com.volmit.iris.generator.PostBlockChunkGenerator;

import lombok.Data;

@Data
public abstract class IrisPostBlockFilter implements IPostBlockAccess
{
	public PostBlockChunkGenerator gen;
	private int phase;
	private KList<Runnable> queue;

	public IrisPostBlockFilter(PostBlockChunkGenerator gen, int phase)
	{
		this.gen = gen;
		this.phase = phase;
		queue = new KList<>();
	}

	public IrisPostBlockFilter(PostBlockChunkGenerator gen)
	{
		this(gen, 0);
	}

	public abstract void onPost(int x, int z);

	@Override
	public BlockData getPostBlock(int x, int y, int z)
	{
		return gen.getPostBlock(x, y, z);
	}

	@Override
	public void setPostBlock(int x, int y, int z, BlockData d)
	{
		gen.setPostBlock(x, y, z, d);
	}

	@Override
	public int highestTerrainOrFluidBlock(int x, int z)
	{
		return gen.highestTerrainOrFluidBlock(x, z);
	}

	@Override
	public int highestTerrainBlock(int x, int z)
	{
		return gen.highestTerrainBlock(x, z);
	}

	public boolean isAir(int x, int y, int z)
	{
		BlockData d = getPostBlock(x, y, z);
		return d.getMaterial().equals(Material.AIR) || d.getMaterial().equals(Material.CAVE_AIR);
	}

	public boolean isSlab(int x, int y, int z)
	{
		BlockData d = getPostBlock(x, y, z);
		return d instanceof Slab;
	}

	public boolean isWater(int x, int y, int z)
	{
		BlockData d = getPostBlock(x, y, z);
		return d.getMaterial().equals(Material.WATER);
	}

	public boolean isWaterOrWaterlogged(int x, int y, int z)
	{
		BlockData d = getPostBlock(x, y, z);
		return d.getMaterial().equals(Material.WATER) || (d instanceof Waterlogged && ((Waterlogged) d).isWaterlogged());
	}

	public boolean isLiquid(int x, int y, int z)
	{
		BlockData d = getPostBlock(x, y, z);
		return d instanceof Levelled;
	}

	@Override
	public KList<CaveResult> caveFloors(int x, int z)
	{
		return gen.caveFloors(x, z);
	}

	public void queue(Runnable a)
	{
		queue.add(a);
	}

	@Override
	public void updateHeight(int x, int z, int h)
	{
		gen.updateHeight(x, z, h);
	}
}
