package com.volmit.iris.object;

import org.bukkit.util.BlockVector;

import com.volmit.iris.util.Desc;
import com.volmit.iris.util.DontObfuscate;

import lombok.Data;

@Desc("Translate objects")
@Data
public class IrisObjectTranslate
{
	@DontObfuscate
	@Desc("The x shift in blocks")
	private int x;

	@DontObfuscate
	@Desc("The x shift in blocks")
	private int y;

	@DontObfuscate
	@Desc("The x shift in blocks")
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
