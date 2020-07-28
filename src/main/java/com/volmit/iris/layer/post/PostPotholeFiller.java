package ninja.bytecode.iris.layer.post;

import ninja.bytecode.iris.generator.PostBlockChunkGenerator;
import ninja.bytecode.iris.util.IrisPostBlockFilter;

public class PostPotholeFiller extends IrisPostBlockFilter
{
	public PostPotholeFiller(PostBlockChunkGenerator gen)
	{
		super(gen);
	}

	@Override
	public void onPost(int x, int z)
	{
		int h = highestTerrainBlock(x, z);
		int ha = highestTerrainBlock(x + 1, z);
		int hb = highestTerrainBlock(x, z + 1);
		int hc = highestTerrainBlock(x - 1, z);
		int hd = highestTerrainBlock(x, z - 1);

		if(ha == h + 1 && hb == h + 1 && hc == h + 1 && hd == h + 1)
		{
			setPostBlock(x, h + 1, z, getPostBlock(x, h, z));
		}
	}
}
