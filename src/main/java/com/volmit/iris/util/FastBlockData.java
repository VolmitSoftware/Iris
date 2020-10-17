package com.volmit.iris.util;

import org.bukkit.Material;
import org.bukkit.block.data.BlockData;

public class FastBlockData
{
	private static final KMap<Material, BlockData> defaultBlockData = new KMap<>();
	private BlockData blockData;
	private Material type;

	private FastBlockData(BlockData d)
	{
		this.blockData = d;
		this.type = null;
	}

	private FastBlockData(Material m)
	{
		this.type = m;
		this.blockData = null;
	}

	public static FastBlockData of(Material type)
	{
		return new FastBlockData(type);
	}

	public static FastBlockData of(BlockData type)
	{
		return new FastBlockData(type);
	}

	public Material getType()
	{
		return type != null ? type : blockData.getMaterial();
	}

	public FastBlockData optimize()
	{
		if(hasBlockData())
		{
			BlockData f = getDefaultBlockData(type);

			if(f.hashCode() == getBlockData().hashCode())
			{
				type = getBlockData().getMaterial();
				blockData = null;
				return this;
			}
		}

		return this;
	}

	private static BlockData getDefaultBlockData(Material type)
	{
		return defaultBlockData.compute(type, (k, v) -> v != null ? v : type.createBlockData());
	}

	public boolean hasBlockData()
	{
		return blockData != null;
	}

	public BlockData getBlockData()
	{
		if(blockData != null)
		{
			return blockData;
		}

		return type.createBlockData();
	}
}
