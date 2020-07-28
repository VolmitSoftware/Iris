package ninja.bytecode.iris.util;

import lombok.Data;

@Data
public class BlockPosition
{
	private int x;
	private int y;
	private int z;

	public BlockPosition(int x, int y, int z)
	{
		this.x = x;
		this.y = y;
		this.z = z;
	}

	public int getChunkX()
	{
		return x >> 4;
	}

	public int getChunkZ()
	{
		return z >> 4;
	}
}
