package com.volmit.iris.util;

import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.block.data.BlockData;

import com.volmit.iris.Iris;

public class BlockDataTools
{
	private static final BlockData AIR = Material.AIR.createBlockData();
	private static final KMap<String, BlockData> bdc = new KMap<>();
	private static final KList<String> nulls = new KList<>();

	public static BlockData getBlockData(String bd)
	{
		try
		{
			if(bdc.containsKey(bd))
			{
				return bdc.get(bd).clone();
			}

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

		return AIR;
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

		return AIR;
	}

	public static boolean isLit(BlockData mat)
	{
		return isLit(mat.getMaterial());
	}

	public static boolean isLit(Material mat)
	{
		if(mat.equals(Material.GLOWSTONE) || mat.equals(Material.TORCH) || mat.equals(Material.REDSTONE_TORCH) || mat.equals(Material.SOUL_TORCH) || mat.equals(Material.REDSTONE_WALL_TORCH) || mat.equals(Material.WALL_TORCH) || mat.equals(Material.SOUL_WALL_TORCH) || mat.equals(Material.LANTERN) || mat.equals(Material.JACK_O_LANTERN) || mat.equals(Material.REDSTONE_LAMP) || mat.equals(Material.MAGMA_BLOCK) || mat.equals(Material.SEA_LANTERN) || mat.equals(Material.SOUL_LANTERN) || mat.equals(Material.FIRE) || mat.equals(Material.SOUL_FIRE) || mat.equals(Material.SEA_PICKLE) || mat.equals(Material.BREWING_STAND) || mat.equals(Material.REDSTONE_ORE))
		{
			return true;
		}

		return false;
	}

	public static boolean canPlaceOnto(Material mat, Material onto)
	{
		if(onto.equals(Material.AIR) || onto.equals(Material.CAVE_AIR))
		{
			return false;
		}

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

	public static boolean isDecorant(Material m)
	{
		//@builder
		return m.equals(Material.GRASS) 
				|| m.equals(Material.TALL_GRASS)
				|| m.equals(Material.CORNFLOWER)
				|| m.equals(Material.SUNFLOWER)
				|| m.equals(Material.CHORUS_FLOWER)
				|| m.equals(Material.POPPY)
				|| m.equals(Material.DANDELION)
				|| m.equals(Material.OXEYE_DAISY)
				|| m.equals(Material.ORANGE_TULIP)
				|| m.equals(Material.PINK_TULIP)
				|| m.equals(Material.RED_TULIP)
				|| m.equals(Material.WHITE_TULIP)
				|| m.equals(Material.LILAC)
				|| m.equals(Material.DEAD_BUSH)
				|| m.equals(Material.SWEET_BERRY_BUSH)
				|| m.equals(Material.ROSE_BUSH)
				|| m.equals(Material.WITHER_ROSE)
				|| m.equals(Material.ALLIUM)
				|| m.equals(Material.BLUE_ORCHID)
				|| m.equals(Material.LILY_OF_THE_VALLEY)
				|| m.equals(Material.CRIMSON_FUNGUS)
				|| m.equals(Material.WARPED_FUNGUS)
				|| m.equals(Material.RED_MUSHROOM)
				|| m.equals(Material.BROWN_MUSHROOM)
				|| m.equals(Material.CRIMSON_ROOTS)
				|| m.equals(Material.AZURE_BLUET)
				|| m.equals(Material.WEEPING_VINES)
				|| m.equals(Material.WEEPING_VINES_PLANT)
				|| m.equals(Material.WARPED_ROOTS)
				|| m.equals(Material.NETHER_SPROUTS)
				|| m.equals(Material.TWISTING_VINES)
				|| m.equals(Material.TWISTING_VINES_PLANT)
				|| m.equals(Material.SUGAR_CANE)
				|| m.equals(Material.WHEAT)
				|| m.equals(Material.POTATOES)
				|| m.equals(Material.CARROTS)
				|| m.equals(Material.BEETROOTS)
				|| m.equals(Material.NETHER_WART)
				|| m.equals(Material.SEA_PICKLE)
				|| m.equals(Material.SEAGRASS)
				|| m.equals(Material.ACACIA_BUTTON)
				|| m.equals(Material.BIRCH_BUTTON)
				|| m.equals(Material.CRIMSON_BUTTON)
				|| m.equals(Material.DARK_OAK_BUTTON)
				|| m.equals(Material.JUNGLE_BUTTON)
				|| m.equals(Material.OAK_BUTTON)
				|| m.equals(Material.POLISHED_BLACKSTONE_BUTTON)
				|| m.equals(Material.SPRUCE_BUTTON)
				|| m.equals(Material.STONE_BUTTON)
				|| m.equals(Material.WARPED_BUTTON)
				|| m.equals(Material.TORCH)
				|| m.equals(Material.SOUL_TORCH);
		//@done
	}
}
