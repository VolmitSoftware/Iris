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
		optimize();
	}

	private FastBlockData(Material m)
	{
		this.type = m;
		this.blockData = null;
	}

	public Material getMaterial()
	{
		return type != null ? type : blockData.getMaterial();
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
			BlockData f = getDefaultBlockData(getMaterial());

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

	public BlockData createBlockData()
	{
		if(blockData != null)
		{
			return blockData;
		}

		return type.createBlockData();
	}

	public BlockData getBlockData()
	{
		if(blockData == null)
		{
			blockData = createBlockData();
		}

		return blockData;
	}

	@Override
	public int hashCode()
	{
		if(hasBlockData())
		{
			return getBlockData().hashCode();
		}

		return getType().hashCode();
	}

	@Override
	public boolean equals(Object obj)
	{
		if(this == obj)
		{
			return true;
		}
		if(obj == null)
		{
			return false;
		}
		if(getClass() != obj.getClass())
		{
			return false;
		}
		FastBlockData other = (FastBlockData) obj;

		if(other.hashCode() == hashCode())
		{
			return true;
		}

		return false;
	}

	public FastBlockData clone()
	{
		return hasBlockData() ? new FastBlockData(blockData.clone()) : new FastBlockData(getType());
	}

	public boolean matches(FastBlockData data)
	{
		return (data.hasBlockData() && hasBlockData() && getBlockData().matches(data.getBlockData())) || (!data.hasBlockData() && !hasBlockData() && getType().equals(data.getType()));
	}
}
