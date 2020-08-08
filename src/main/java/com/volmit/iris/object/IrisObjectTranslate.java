package com.volmit.iris.object;

import org.bukkit.util.BlockVector;

import com.volmit.iris.util.Desc;
import com.volmit.iris.util.DontObfuscate;
import com.volmit.iris.util.MaxNumber;
import com.volmit.iris.util.MinNumber;
import com.volmit.iris.util.Required;

import lombok.Data;

@Desc("Translate objects")
@Data
public class IrisObjectTranslate
{
	@MinNumber(-8)
	@MaxNumber(8)
	@DontObfuscate
	@Desc("The x shift in blocks")
	private int x = 0;

	@Required
	@MinNumber(-256)
	@MaxNumber(256)
	@DontObfuscate
	@Desc("The x shift in blocks")
	private int y = 0;

	@MinNumber(-8)
	@MaxNumber(8)
	@DontObfuscate
	@Desc("The x shift in blocks")
	private int z = 0;

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
