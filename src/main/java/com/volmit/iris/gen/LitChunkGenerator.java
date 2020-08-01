package com.volmit.iris.gen;

import org.bukkit.Chunk;
import org.bukkit.block.Block;
import org.bukkit.block.data.BlockData;

import com.volmit.iris.Iris;
import com.volmit.iris.util.BlockDataTools;
import com.volmit.iris.util.BlockPosition;
import com.volmit.iris.util.KList;

public abstract class LitChunkGenerator extends PostBlockChunkGenerator
{
	private KList<BlockPosition> lit;

	public LitChunkGenerator(String dimensionName, int threads)
	{
		super(dimensionName, threads);
		lit = new KList<>();
	}

	protected void queueLight(int x, int y, int z, BlockData d)
	{
		if(BlockDataTools.isLit(d))
		{
			lit.add(new BlockPosition(x, y, z));
		}
	}

	@Override
	protected void onChunkLoaded(Chunk c)
	{
		for(BlockPosition i : lit.copy())
		{
			if(i.getChunkX() == c.getX() && i.getChunkZ() == c.getZ())
			{
				Block b = getWorld().getBlockAt(i.getX(), i.getY(), i.getZ());
				// BlockData d = b.getBlockData();
				b.getState().update(true, true);
				lit.remove(i);
			}
		}

		Iris.info("Lit: " + lit.size());
	}
}
