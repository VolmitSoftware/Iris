package ninja.bytecode.iris.object;

import org.bukkit.block.data.BlockData;
import org.bukkit.util.BlockVector;

import ninja.bytecode.shuriken.collections.KMap;
import ninja.bytecode.shuriken.collections.KSet;

public class IrisObject
{
	private String name;
	private KMap<BlockVector, BlockData> blocks;
	private KSet<BlockVector> mount;
	private int w;
	private int d;
	private int h;
	private transient BlockVector center;

	public IrisObject(String name, int w, int h, int d)
	{
		blocks = new KMap<>();
		mount = new KSet<>();
		this.w = w;
		this.h = h;
		this.d = d;
		this.name = name;
		center = new BlockVector(w / 2, h / 2, d / 2);
	}

	public void setUnsigned(int x, int y, int z, BlockData block)
	{
		if(x >= w || y >= h || z >= d)
		{
			throw new RuntimeException(x + " " + y + " " + z + " exceeds limit of " + w + " " + h + " " + d);
		}
		
		BlockVector v = new BlockVector(x, y, z).subtract(center).toBlockVector();
		
		if(block == null)
		{
			blocks.remove(v);
		}
		
		else
		{
			blocks.put(v, block);
		}
	}
}
