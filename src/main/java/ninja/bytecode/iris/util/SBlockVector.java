package ninja.bytecode.iris.util;

import org.bukkit.configuration.serialization.SerializableAs;
import org.bukkit.util.BlockVector;
import org.bukkit.util.Vector;

/**
 * A vector with a hash function that floors the X, Y, Z components, a la
 * BlockVector in WorldEdit. BlockVectors can be used in hash sets and hash
 * maps. Be aware that BlockVectors are mutable, but it is important that
 * BlockVectors are never changed once put into a hash set or hash map.
 */
@SerializableAs("BlockVector")
public class SBlockVector
{
	private short x;
	private short y;
	private short z;

	/**
	 * Construct the vector with all components as 0.
	 */
	public SBlockVector()
	{
		this.x = 0;
		this.y = 0;
		this.z = 0;
	}

	/**
	 * Construct the vector with another vector.
	 *
	 * @param vec
	 *            The other vector.
	 */
	public SBlockVector(Vector vec)
	{
		this.x = (short) vec.getX();
		this.y = (short) vec.getY();
		this.z = (short) vec.getZ();
	}

	/**
	 * Construct the vector with provided integer components.
	 *
	 * @param x
	 *            X component
	 * @param y
	 *            Y component
	 * @param z
	 *            Z component
	 */
	public SBlockVector(int x, int y, int z)
	{
		this.x = (short) x;
		this.y = (short) y;
		this.z = (short) z;
	}

	/**
	 * Construct the vector with provided double components.
	 *
	 * @param x
	 *            X component
	 * @param y
	 *            Y component
	 * @param z
	 *            Z component
	 */
	public SBlockVector(double x, double y, double z)
	{
		this.x = (short) x;
		this.y = (short) y;
		this.z = (short) z;
	}

	/**
	 * Construct the vector with provided float components.
	 *
	 * @param x
	 *            X component
	 * @param y
	 *            Y component
	 * @param z
	 *            Z component
	 */
	public SBlockVector(float x, float y, float z)
	{
		this.x = (short) x;
		this.y = (short) y;
		this.z = (short) z;
	}

	/**
	 * Get a new block vector.
	 *
	 * @return vector
	 */
	@Override
	public SBlockVector clone()
	{
		return new SBlockVector(x, y, z);
	}

	public double getX()
	{
		return x;
	}

	public void setX(double x)
	{
		this.x = (short) x;
	}

	public double getY()
	{
		return y;
	}

	public void setY(double y)
	{
		this.y = (short) y;
	}

	public double getZ()
	{
		return z;
	}

	public void setZ(double z)
	{
		this.z = (short) z;
	}

	@Override
	public int hashCode()
	{
		final int prime = 31;
		int result = 1;
		result = prime * result + x;
		result = prime * result + y;
		result = prime * result + z;
		return result;
	}

	@Override
	public boolean equals(Object obj)
	{
		if(this == obj)
			return true;
		if(obj == null)
			return false;
		if(getClass() != obj.getClass())
			return false;
		SBlockVector other = (SBlockVector) obj;
		if(x != other.x)
			return false;
		if(y != other.y)
			return false;
		if(z != other.z)
			return false;
		return true;
	}

	public BlockVector toBlockVector()
	{
		return new BlockVector(x, y, z);
	}
}
