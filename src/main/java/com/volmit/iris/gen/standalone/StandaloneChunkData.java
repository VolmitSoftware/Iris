package com.volmit.iris.gen.standalone;

import org.bukkit.Material;
import org.bukkit.block.data.BlockData;
import org.bukkit.generator.ChunkGenerator.ChunkData;
import org.bukkit.material.MaterialData;

import com.volmit.iris.gen.atomics.AtomicSliverMap;

@SuppressWarnings("deprecation")
public class StandaloneChunkData extends AtomicSliverMap implements ChunkData
{
	@Override
	public BlockData getBlockData(int x, int y, int z)
	{
		return getSliver(x, z).get(y);
	}

	@Override
	public byte getData(int x, int y, int z)
	{
		throw new UnsupportedOperationException("Use getBlockData");
	}

	@Override
	public int getMaxHeight()
	{
		return 256;
	}

	@Override
	public Material getType(int x, int y, int z)
	{
		return getBlockData(x, y, z).getMaterial();
	}

	@Deprecated
	@Override
	public MaterialData getTypeAndData(int x, int y, int z)
	{
		throw new UnsupportedOperationException("Use GetBlockData");
	}

	@Override
	public void setBlock(int x, int y, int z, Material arg3)
	{
		setBlock(x, y, z, arg3.createBlockData());
	}

	@Deprecated
	@Override
	public void setBlock(int arg0, int arg1, int arg2, MaterialData arg3)
	{
		throw new UnsupportedOperationException("Use SetBlock (bd)");
	}

	@Override
	public void setBlock(int x, int y, int z, BlockData b)
	{
		getSliver(x, z).set(y, b);
	}

	@Override
	public void setRegion(int arg0, int arg1, int arg2, int arg3, int arg4, int arg5, Material arg6)
	{
		throw new UnsupportedOperationException("Use SetBlock (bd)");
	}

	@Deprecated
	@Override
	public void setRegion(int arg0, int arg1, int arg2, int arg3, int arg4, int arg5, MaterialData arg6)
	{
		throw new UnsupportedOperationException("Use SetBlock (bd)");
	}

	@Override
	public void setRegion(int arg0, int arg1, int arg2, int arg3, int arg4, int arg5, BlockData arg6)
	{
		throw new UnsupportedOperationException("Use SetBlock (bd)");
	}
}
