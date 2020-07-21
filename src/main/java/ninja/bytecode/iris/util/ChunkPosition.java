package ninja.bytecode.iris.util;

import lombok.Data;

@Data
public class ChunkPosition
{
	private int x;
	private int z;
	
	public ChunkPosition(int x, int z)
	{
		this.x = x;
		this.z = z;
	}
}
