package com.volmit.iris.object;

import com.volmit.iris.util.Desc;
import com.volmit.iris.util.DontObfuscate;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;
import lombok.experimental.Accessors;
import org.bukkit.Location;
import org.bukkit.World;
import org.bukkit.util.BlockVector;
import org.bukkit.util.Vector;

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

	public IrisPosition(Location l) {
		this(l.getBlockX(), l.getBlockY(), l.getBlockZ());
	}

	public IrisPosition(Vector v) {
		this(v.getBlockX(), v.getBlockY(), v.getBlockZ());
	}

	public IrisPosition add(IrisPosition relativePosition) {
		return new IrisPosition(relativePosition.x+x, relativePosition.y+y, relativePosition.z + z);
	}

	public IrisPosition sub(IrisPosition relativePosition) {
		return new IrisPosition(x-relativePosition.x, y-relativePosition.y, z-relativePosition.z);
	}

	public Location toLocation(World world) {
		return new Location(world, x,y,z);
	}

    public IrisPosition copy() {
		return new IrisPosition(x,y,z);
    }
}
