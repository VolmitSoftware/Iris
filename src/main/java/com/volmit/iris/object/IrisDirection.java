package com.volmit.iris.object;

import com.volmit.iris.util.Cuboid.CuboidDirection;
import com.volmit.iris.util.*;
import org.bukkit.Axis;
import org.bukkit.block.BlockFace;
import org.bukkit.block.data.type.Jigsaw;
import org.bukkit.util.Vector;

/**
 * Directions
 *
 * @author cyberpwn
 */
@Desc("A direction object")
public enum IrisDirection
{
	UP_POSITIVE_Y(0, 1, 0, CuboidDirection.Up),
	DOWN_NEGATIVE_Y(0, -1, 0, CuboidDirection.Down),
	NORTH_NEGATIVE_Z(0, 0, -1, CuboidDirection.North),
	SOUTH_POSITIVE_Z(0, 0, 1, CuboidDirection.South),
	EAST_POSITIVE_X(1, 0, 0, CuboidDirection.East),
	WEST_NEGATIVE_X(-1, 0, 0, CuboidDirection.West);

	private static KMap<GBiset<IrisDirection, IrisDirection>, DOP> permute = null;

	private int x;
	private int y;
	private int z;
	private CuboidDirection f;

	public static IrisDirection getDirection(BlockFace f)
	{
		switch(f)
		{
			case DOWN:
				return DOWN_NEGATIVE_Y;
			case EAST:
			case EAST_NORTH_EAST:
			case EAST_SOUTH_EAST:
				return EAST_POSITIVE_X;
			case NORTH:
			case NORTH_NORTH_WEST:
			case NORTH_EAST:
			case NORTH_NORTH_EAST:
			case NORTH_WEST:
				return NORTH_NEGATIVE_Z;
			case SELF:
			case UP:
				return UP_POSITIVE_Y;
			case SOUTH:
			case SOUTH_EAST:
			case SOUTH_SOUTH_EAST:
			case SOUTH_SOUTH_WEST:
			case SOUTH_WEST:
				return SOUTH_POSITIVE_Z;
			case WEST:
			case WEST_NORTH_WEST:
			case WEST_SOUTH_WEST:
				return WEST_NEGATIVE_X;
		}

		return DOWN_NEGATIVE_Y;
	}

    public static IrisDirection fromJigsawBlock(String direction) {
		for(IrisDirection i : IrisDirection.values())
		{
			if(i.name().toLowerCase().split("\\Q_\\E")[0]
					.equals(direction.split("\\Q_\\E")[0]))
			{
				return i;
			}
		}

		return null;
    }

	public static IrisDirection getDirection(Jigsaw.Orientation orientation) {
		switch(orientation)
		{
			case DOWN_EAST:
			case UP_EAST:
			case EAST_UP:
				return EAST_POSITIVE_X;
			case DOWN_NORTH:
			case UP_NORTH:
			case NORTH_UP:
				return NORTH_NEGATIVE_Z;
			case DOWN_SOUTH:
			case UP_SOUTH:
			case SOUTH_UP:
				return SOUTH_POSITIVE_Z;
			case DOWN_WEST:
			case UP_WEST:
			case WEST_UP:
				return WEST_NEGATIVE_X;
		}

		return null;
	}

	@Override
	public String toString()
	{
		switch(this)
		{
			case DOWN_NEGATIVE_Y:
				return "Down";
			case EAST_POSITIVE_X:
				return "East";
			case NORTH_NEGATIVE_Z:
				return "North";
			case SOUTH_POSITIVE_Z:
				return "South";
			case UP_POSITIVE_Y:
				return "Up";
			case WEST_NEGATIVE_X:
				return "West";
		}

		return "?";
	}

	public boolean isVertical()
	{
		return equals(DOWN_NEGATIVE_Y) || equals(UP_POSITIVE_Y);
	}

	public static IrisDirection closest(Vector v)
	{
		double m = Double.MAX_VALUE;
		IrisDirection s = null;

		for(IrisDirection i : values())
		{
			Vector x = i.toVector();
			double g = x.distance(v);

			if(g < m)
			{
				m = g;
				s = i;
			}
		}

		return s;
	}

	public static IrisDirection closest(Vector v, IrisDirection... d)
	{
		double m = Double.MAX_VALUE;
		IrisDirection s = null;

		for(IrisDirection i : d)
		{
			Vector x = i.toVector();
			double g = x.distance(v);

			if(g < m)
			{
				m = g;
				s = i;
			}
		}

		return s;
	}

	public static IrisDirection closest(Vector v, KList<IrisDirection> d)
	{
		double m = Double.MAX_VALUE;
		IrisDirection s = null;

		for(IrisDirection i : d)
		{
			Vector x = i.toVector();
			double g = x.distance(v);

			if(g < m)
			{
				m = g;
				s = i;
			}
		}

		return s;
	}

	public Vector toVector()
	{
		return new Vector(x, y, z);
	}

	public boolean isCrooked(IrisDirection to)
	{
		if(equals(to.reverse()))
		{
			return false;
		}

		return !equals(to);
	}

	private IrisDirection(int x, int y, int z, CuboidDirection f)
	{
		this.x = x;
		this.y = y;
		this.z = z;
		this.f = f;
	}

	public Vector angle(Vector initial, IrisDirection d)
	{
		calculatePermutations();

		for(GBiset<IrisDirection, IrisDirection> i : permute.keySet())
		{
			if(i.getA().equals(this) && i.getB().equals(d))
			{
				return permute.get(i).op(initial);
			}
		}

		return initial;
	}

	public IrisDirection reverse()
	{
		switch(this)
		{
			case DOWN_NEGATIVE_Y:
				return UP_POSITIVE_Y;
			case EAST_POSITIVE_X:
				return WEST_NEGATIVE_X;
			case NORTH_NEGATIVE_Z:
				return SOUTH_POSITIVE_Z;
			case SOUTH_POSITIVE_Z:
				return NORTH_NEGATIVE_Z;
			case UP_POSITIVE_Y:
				return DOWN_NEGATIVE_Y;
			case WEST_NEGATIVE_X:
				return EAST_POSITIVE_X;
			default:
				break;
		}

		return EAST_POSITIVE_X;
	}

	public int x()
	{
		return x;
	}

	public int y()
	{
		return y;
	}

	public int z()
	{
		return z;
	}

	public CuboidDirection f()
	{
		return f;
	}

	public static KList<IrisDirection> news()
	{
		return new KList<IrisDirection>().add(NORTH_NEGATIVE_Z, EAST_POSITIVE_X, WEST_NEGATIVE_X, SOUTH_POSITIVE_Z);
	}

	public static IrisDirection getDirection(Vector v)
	{
		Vector k = VectorMath.triNormalize(v.clone().normalize());

		for(IrisDirection i : udnews())
		{
			if(i.x == k.getBlockX() && i.y == k.getBlockY() && i.z == k.getBlockZ())
			{
				return i;
			}
		}

		return IrisDirection.NORTH_NEGATIVE_Z;
	}

	public static KList<IrisDirection> udnews()
	{
		return new KList<IrisDirection>().add(UP_POSITIVE_Y, DOWN_NEGATIVE_Y, NORTH_NEGATIVE_Z, EAST_POSITIVE_X, WEST_NEGATIVE_X, SOUTH_POSITIVE_Z);
	}

	/**
	 * Get the directional value from the given byte from common directional blocks
	 * (MUST BE BETWEEN 0 and 5 INCLUSIVE)
	 *
	 * @param b
	 *            the byte
	 * @return the direction or null if the byte is outside of the inclusive range
	 *         0-5
	 */
	public static IrisDirection fromByte(byte b)
	{
		if(b > 5 || b < 0)
		{
			return null;
		}

		if(b == 0)
		{
			return DOWN_NEGATIVE_Y;
		}

		else if(b == 1)
		{
			return UP_POSITIVE_Y;
		}

		else if(b == 2)
		{
			return NORTH_NEGATIVE_Z;
		}

		else if(b == 3)
		{
			return SOUTH_POSITIVE_Z;
		}

		else if(b == 4)
		{
			return WEST_NEGATIVE_X;
		}

		else
		{
			return EAST_POSITIVE_X;
		}
	}

	/**
	 * Get the byte value represented in some directional blocks
	 *
	 * @return the byte value
	 */
	public byte byteValue()
	{
		switch(this)
		{
			case DOWN_NEGATIVE_Y:
				return 0;
			case EAST_POSITIVE_X:
				return 5;
			case NORTH_NEGATIVE_Z:
				return 2;
			case SOUTH_POSITIVE_Z:
				return 3;
			case UP_POSITIVE_Y:
				return 1;
			case WEST_NEGATIVE_X:
				return 4;
			default:
				break;
		}

		return -1;
	}

	public static void calculatePermutations()
	{
		if(permute != null)
		{
			return;
		}

		permute = new KMap<GBiset<IrisDirection, IrisDirection>, DOP>();

		for(IrisDirection i : udnews())
		{
			for(IrisDirection j : udnews())
			{
				GBiset<IrisDirection, IrisDirection> b = new GBiset<IrisDirection, IrisDirection>(i, j);

				if(i.equals(j))
				{
					permute.put(b, new DOP("DIRECT")
					{
						@Override
						public Vector op(Vector v)
						{
							return v;
						}
					});
				}

				else if(i.reverse().equals(j))
				{
					if(i.isVertical())
					{
						permute.put(b, new DOP("R180CCZ")
						{
							@Override
							public Vector op(Vector v)
							{
								return VectorMath.rotate90CCZ(VectorMath.rotate90CCZ(v));
							}
						});
					}

					else
					{
						permute.put(b, new DOP("R180CCY")
						{
							@Override
							public Vector op(Vector v)
							{
								return VectorMath.rotate90CCY(VectorMath.rotate90CCY(v));
							}
						});
					}
				}

				else if(getDirection(VectorMath.rotate90CX(i.toVector())).equals(j))
				{
					permute.put(b, new DOP("R90CX")
					{
						@Override
						public Vector op(Vector v)
						{
							return VectorMath.rotate90CX(v);
						}
					});
				}

				else if(getDirection(VectorMath.rotate90CCX(i.toVector())).equals(j))
				{
					permute.put(b, new DOP("R90CCX")
					{
						@Override
						public Vector op(Vector v)
						{
							return VectorMath.rotate90CCX(v);
						}
					});
				}

				else if(getDirection(VectorMath.rotate90CY(i.toVector())).equals(j))
				{
					permute.put(b, new DOP("R90CY")
					{
						@Override
						public Vector op(Vector v)
						{
							return VectorMath.rotate90CY(v);
						}
					});
				}

				else if(getDirection(VectorMath.rotate90CCY(i.toVector())).equals(j))
				{
					permute.put(b, new DOP("R90CCY")
					{
						@Override
						public Vector op(Vector v)
						{
							return VectorMath.rotate90CCY(v);
						}
					});
				}

				else if(getDirection(VectorMath.rotate90CZ(i.toVector())).equals(j))
				{
					permute.put(b, new DOP("R90CZ")
					{
						@Override
						public Vector op(Vector v)
						{
							return VectorMath.rotate90CZ(v);
						}
					});
				}

				else if(getDirection(VectorMath.rotate90CCZ(i.toVector())).equals(j))
				{
					permute.put(b, new DOP("R90CCZ")
					{
						@Override
						public Vector op(Vector v)
						{
							return VectorMath.rotate90CCZ(v);
						}
					});
				}

				else
				{
					permute.put(b, new DOP("FAIL")
					{
						@Override
						public Vector op(Vector v)
						{
							return v;
						}
					});
				}
			}
		}
	}

	public BlockFace getFace()
	{
		switch(this)
		{
			case DOWN_NEGATIVE_Y:
				return BlockFace.DOWN;
			case EAST_POSITIVE_X:
				return BlockFace.EAST;
			case NORTH_NEGATIVE_Z:
				return BlockFace.NORTH;
			case SOUTH_POSITIVE_Z:
				return BlockFace.SOUTH;
			case UP_POSITIVE_Y:
				return BlockFace.UP;
			case WEST_NEGATIVE_X:
				return BlockFace.WEST;
		}

		return null;
	}

	public Axis getAxis()
	{
		switch(this)
		{
			case DOWN_NEGATIVE_Y:
			case UP_POSITIVE_Y:
				return Axis.Y;
			case EAST_POSITIVE_X:
			case WEST_NEGATIVE_X:
				return Axis.X;
			case NORTH_NEGATIVE_Z:
			case SOUTH_POSITIVE_Z:
				return Axis.Z;
		}

		return null;
	}
}
