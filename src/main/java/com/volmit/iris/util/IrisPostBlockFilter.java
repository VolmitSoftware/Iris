package com.volmit.iris.util;

import org.bukkit.Material;
import org.bukkit.block.data.BlockData;
import org.bukkit.block.data.Levelled;
import org.bukkit.block.data.Waterlogged;
import org.bukkit.block.data.type.Slab;

import com.volmit.iris.gen.PostBlockChunkGenerator;
import com.volmit.iris.gen.post.Post;

import lombok.Data;

@Data
public abstract class IrisPostBlockFilter implements IPostBlockAccess
{
	public PostBlockChunkGenerator gen;
	private int phase;
	private String key;
	private KList<Runnable> queue = new KList<>();

	public IrisPostBlockFilter(PostBlockChunkGenerator gen, int phase)
	{
		this.gen = gen;
		this.phase = phase;
		queue = new KList<>();
		key = getClass().getDeclaredAnnotation(Post.class).value();
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

	public boolean hasGravity(int x, int y, int z)
	{
		BlockData d = getPostBlock(x, y, z);
		return d.getMaterial().equals(Material.SAND) || d.getMaterial().equals(Material.RED_SAND) || d.getMaterial().equals(Material.BLACK_CONCRETE_POWDER) || d.getMaterial().equals(Material.BLUE_CONCRETE_POWDER) || d.getMaterial().equals(Material.BROWN_CONCRETE_POWDER) || d.getMaterial().equals(Material.CYAN_CONCRETE_POWDER) || d.getMaterial().equals(Material.GRAY_CONCRETE_POWDER) || d.getMaterial().equals(Material.GREEN_CONCRETE_POWDER) || d.getMaterial().equals(Material.LIGHT_BLUE_CONCRETE_POWDER) || d.getMaterial().equals(Material.LIGHT_GRAY_CONCRETE_POWDER) || d.getMaterial().equals(Material.LIME_CONCRETE_POWDER) || d.getMaterial().equals(Material.MAGENTA_CONCRETE_POWDER) || d.getMaterial().equals(Material.ORANGE_CONCRETE_POWDER) || d.getMaterial().equals(Material.PINK_CONCRETE_POWDER) || d.getMaterial().equals(Material.PURPLE_CONCRETE_POWDER) || d.getMaterial().equals(Material.RED_CONCRETE_POWDER) || d.getMaterial().equals(Material.WHITE_CONCRETE_POWDER) || d.getMaterial().equals(Material.YELLOW_CONCRETE_POWDER);
	}

	public boolean isSolid(int x, int y, int z)
	{
		BlockData d = getPostBlock(x, y, z);
		return d.getMaterial().isSolid();
	}

	public boolean isAirOrWater(int x, int y, int z)
	{
		BlockData d = getPostBlock(x, y, z);
		return d.getMaterial().equals(Material.WATER) || d.getMaterial().equals(Material.AIR) || d.getMaterial().equals(Material.CAVE_AIR);
	}

	public boolean isSlab(int x, int y, int z)
	{
		BlockData d = getPostBlock(x, y, z);
		return d instanceof Slab;
	}

	public boolean isSnowLayer(int x, int y, int z)
	{
		BlockData d = getPostBlock(x, y, z);
		return d.getMaterial().equals(Material.SNOW);
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
