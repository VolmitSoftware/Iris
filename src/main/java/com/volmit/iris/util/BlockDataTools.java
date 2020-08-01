package com.volmit.iris.util;

import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.block.data.BlockData;

import com.volmit.iris.Iris;

public class BlockDataTools
{
	private static final KMap<String, BlockData> bdc = new KMap<>();
	private static final KList<String> nulls = new KList<>();

	public static BlockData getBlockData(String bd)
	{
		if(bdc.containsKey(bd))
		{
			return bdc.get(bd).clone();
		}

		try
		{
			BlockData bdx = parseBlockData(bd);

			if(bdx == null)
			{
				Iris.warn("Unknown Block Data '" + bd + "'");
				nulls.add(bd);
				return bdx;
			}

			bdc.put(bd, bdx);

			return bdx;
		}

		catch(Throwable e)
		{
			Iris.warn("Unknown Block Data '" + bd + "'");
		}

		return null;
	}

	public static BlockData parseBlockData(String ix)
	{
		try
		{
			BlockData bx = Bukkit.createBlockData(ix);

			if(bx != null)
			{
				return bx;
			}
		}

		catch(Throwable e)
		{

		}

		String i = ix.toUpperCase().trim();
		i = i.equals("WOOL") ? "WHITE_WOOL" : i;
		i = i.equals("CONCRETE") ? "WHITE_CONCRETE" : i;

		try
		{
			Material m = Material.valueOf(i);

			return m.createBlockData();
		}

		catch(Throwable e)
		{

		}

		return null;
	}

	public static boolean canPlaceOnto(Material mat, Material onto)
	{
		if(onto.equals(Material.GRASS_BLOCK) && mat.equals(Material.DEAD_BUSH))
		{
			return false;
		}

		if(onto.equals(Material.GRASS_PATH))
		{
			if(!mat.isSolid())
			{
				return false;
			}
		}

		if(onto.equals(Material.STONE) || onto.equals(Material.GRAVEL) || onto.equals(Material.GRAVEL) || onto.equals(Material.ANDESITE) || onto.equals(Material.GRANITE) || onto.equals(Material.DIORITE) || onto.equals(Material.BLACKSTONE) || onto.equals(Material.BASALT))
		{
			if(mat.equals(Material.POPPY) || mat.equals(Material.DANDELION) || mat.equals(Material.CORNFLOWER) || mat.equals(Material.ORANGE_TULIP) || mat.equals(Material.PINK_TULIP) || mat.equals(Material.RED_TULIP) || mat.equals(Material.WHITE_TULIP) || mat.equals(Material.FERN) || mat.equals(Material.LARGE_FERN) || mat.equals(Material.GRASS) || mat.equals(Material.TALL_GRASS))
			{
				return false;
			}
		}

		if(onto.equals(Material.ACACIA_LEAVES) || onto.equals(Material.BIRCH_LEAVES) || onto.equals(Material.DARK_OAK_LEAVES) || onto.equals(Material.JUNGLE_LEAVES) || onto.equals(Material.OAK_LEAVES) || onto.equals(Material.SPRUCE_LEAVES))
		{
			if(!mat.isSolid())
			{
				return false;
			}
		}

		return true;
	}
}
