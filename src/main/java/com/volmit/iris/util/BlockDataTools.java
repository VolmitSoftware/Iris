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
}
