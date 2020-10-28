package com.volmit.iris.util;

import org.bukkit.Material;
import org.bukkit.block.data.BlockData;
import org.bukkit.block.data.Levelled;
import org.bukkit.block.data.Waterlogged;
import org.bukkit.block.data.type.Slab;
import org.bukkit.generator.ChunkGenerator.ChunkData;

import com.volmit.iris.gen.PostBlockTerrainProvider;

import lombok.Data;

@Data
public abstract class IrisPostBlockFilter implements IPostBlockAccess
{
	public PostBlockTerrainProvider gen;
	private int phase;

	@DontObfuscate
	public IrisPostBlockFilter(PostBlockTerrainProvider gen, int phase)
	{
		this.gen = gen;
		this.phase = phase;
	}

	@DontObfuscate
	public IrisPostBlockFilter(PostBlockTerrainProvider gen)
	{
		this(gen, 0);
	}

	public abstract void onPost(int x, int z, int currentPostX, int currentPostZ, ChunkData currentData, KList<Runnable> q);

	@Override
	public BlockData getPostBlock(int x, int y, int z, int currentPostX, int currentPostZ, ChunkData currentData)
	{
		return gen.getPostBlock(x, y, z, currentPostX, currentPostZ, currentData);
	}

	@Override
	public void setPostBlock(int x, int y, int z, BlockData d, int currentPostX, int currentPostZ, ChunkData currentData)
	{
		gen.setPostBlock(x, y, z, d, currentPostX, currentPostZ, currentData);
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

	public int highestTerrainOrCarvingBlock(int x, int z)
	{
		return gen.getCarvedHeight(x, z, true);
	}

	public boolean isAir(int x, int y, int z, int currentPostX, int currentPostZ, ChunkData currentData)
	{
		BlockData d = getPostBlock(x, y, z, currentPostX, currentPostZ, currentData);
		return d.getMaterial().equals(Material.AIR) || d.getMaterial().equals(Material.CAVE_AIR);
	}

	public boolean hasGravity(int x, int y, int z, int currentPostX, int currentPostZ, ChunkData currentData)
	{
		BlockData d = getPostBlock(x, y, z, currentPostX, currentPostZ, currentData);
		return d.getMaterial().equals(Material.SAND) || d.getMaterial().equals(Material.RED_SAND) || d.getMaterial().equals(Material.BLACK_CONCRETE_POWDER) || d.getMaterial().equals(Material.BLUE_CONCRETE_POWDER) || d.getMaterial().equals(Material.BROWN_CONCRETE_POWDER) || d.getMaterial().equals(Material.CYAN_CONCRETE_POWDER) || d.getMaterial().equals(Material.GRAY_CONCRETE_POWDER) || d.getMaterial().equals(Material.GREEN_CONCRETE_POWDER) || d.getMaterial().equals(Material.LIGHT_BLUE_CONCRETE_POWDER) || d.getMaterial().equals(Material.LIGHT_GRAY_CONCRETE_POWDER) || d.getMaterial().equals(Material.LIME_CONCRETE_POWDER) || d.getMaterial().equals(Material.MAGENTA_CONCRETE_POWDER) || d.getMaterial().equals(Material.ORANGE_CONCRETE_POWDER) || d.getMaterial().equals(Material.PINK_CONCRETE_POWDER) || d.getMaterial().equals(Material.PURPLE_CONCRETE_POWDER) || d.getMaterial().equals(Material.RED_CONCRETE_POWDER) || d.getMaterial().equals(Material.WHITE_CONCRETE_POWDER) || d.getMaterial().equals(Material.YELLOW_CONCRETE_POWDER);
	}

	public boolean isSolid(int x, int y, int z, int currentPostX, int currentPostZ, ChunkData currentData)
	{
		BlockData d = getPostBlock(x, y, z, currentPostX, currentPostZ, currentData);
		return d.getMaterial().isSolid();
	}

	public boolean isSolidNonSlab(int x, int y, int z, int currentPostX, int currentPostZ, ChunkData currentData)
	{
		BlockData d = getPostBlock(x, y, z, currentPostX, currentPostZ, currentData);
		return d.getMaterial().isSolid() && !(d instanceof Slab);
	}

	public boolean isAirOrWater(int x, int y, int z, int currentPostX, int currentPostZ, ChunkData currentData)
	{
		BlockData d = getPostBlock(x, y, z, currentPostX, currentPostZ, currentData);
		return d.getMaterial().equals(Material.WATER) || d.getMaterial().equals(Material.AIR) || d.getMaterial().equals(Material.CAVE_AIR);
	}

	public boolean isSlab(int x, int y, int z, int currentPostX, int currentPostZ, ChunkData currentData)
	{
		BlockData d = getPostBlock(x, y, z, currentPostX, currentPostZ, currentData);
		return d instanceof Slab;
	}

	public boolean isSnowLayer(int x, int y, int z, int currentPostX, int currentPostZ, ChunkData currentData)
	{
		BlockData d = getPostBlock(x, y, z, currentPostX, currentPostZ, currentData);
		return d.getMaterial().equals(Material.SNOW);
	}

	public boolean isWater(int x, int y, int z, int currentPostX, int currentPostZ, ChunkData currentData)
	{
		BlockData d = getPostBlock(x, y, z, currentPostX, currentPostZ, currentData);
		return d.getMaterial().equals(Material.WATER);
	}

	public boolean isWaterOrWaterlogged(int x, int y, int z, int currentPostX, int currentPostZ, ChunkData currentData)
	{
		BlockData d = getPostBlock(x, y, z, currentPostX, currentPostZ, currentData);
		return d.getMaterial().equals(Material.WATER) || (d instanceof Waterlogged && ((Waterlogged) d).isWaterlogged());
	}

	public boolean isLiquid(int x, int y, int z, int currentPostX, int currentPostZ, ChunkData currentData)
	{
		BlockData d = getPostBlock(x, y, z, currentPostX, currentPostZ, currentData);
		return d instanceof Levelled;
	}

	@Override
	public KList<CaveResult> caveFloors(int x, int z)
	{
		return gen.caveFloors(x, z);
	}

	@Override
	public void updateHeight(int x, int z, int h)
	{
		gen.updateHeight(x, z, h);
	}
}
