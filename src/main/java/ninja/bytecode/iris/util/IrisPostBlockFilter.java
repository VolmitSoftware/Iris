package ninja.bytecode.iris.util;

import org.bukkit.block.data.BlockData;

import ninja.bytecode.iris.generator.PostBlockChunkGenerator;

public abstract class IrisPostBlockFilter implements IPostBlockAccess
{
	public PostBlockChunkGenerator gen;

	public IrisPostBlockFilter(PostBlockChunkGenerator gen)
	{
		this.gen = gen;
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
}
