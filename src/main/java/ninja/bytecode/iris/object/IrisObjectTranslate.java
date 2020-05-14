package ninja.bytecode.iris.object;

import org.bukkit.util.BlockVector;

import lombok.Data;

@Data
public class IrisObjectTranslate
{
	private int x;
	private int y;
	private int z;

	public IrisObjectTranslate()
	{
		x = 0;
		y = 0;
		z = 0;
	}

	public boolean canTranslate()
	{
		return x != 0 || y != 0 || z != 0;
	}

	public BlockVector translate(BlockVector i)
	{
		if(canTranslate())
		{
			return (BlockVector) i.clone().add(new BlockVector(x, y, z));
		}

		return i;
	}
}
