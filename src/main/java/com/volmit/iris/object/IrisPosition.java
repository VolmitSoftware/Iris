package com.volmit.iris.object;

import com.volmit.iris.util.Desc;
import com.volmit.iris.util.DontObfuscate;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;
import lombok.experimental.Accessors;
import org.bukkit.util.BlockVector;

@Accessors(chain = true)
@NoArgsConstructor
@AllArgsConstructor
@Desc("Represents a position")
@Data
public class IrisPosition
{
	@DontObfuscate
	@Desc("The x position")
	private int x = 0;

	@DontObfuscate
	@Desc("The y position")
	private int y = 0;

	@DontObfuscate
	@Desc("The z position")
	private int z = 0;

	public IrisPosition(BlockVector bv) {
		this(bv.getBlockX(), bv.getBlockY(), bv.getBlockZ());
	}

	public IrisPosition add(IrisPosition relativePosition) {
		return new IrisPosition(relativePosition.x+x, relativePosition.y+y, relativePosition.z + z);
	}

	public IrisPosition sub(IrisPosition relativePosition) {
		return new IrisPosition(x-relativePosition.x, y-relativePosition.y, z-relativePosition.z);
	}
}
