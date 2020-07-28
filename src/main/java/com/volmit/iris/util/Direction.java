
package com.volmit.iris.util;

import org.bukkit.Axis;
import org.bukkit.block.BlockFace;
import org.bukkit.util.Vector;

import com.volmit.iris.util.Cuboid.CuboidDirection;

/**
 * Directions
 *
 * @author cyberpwn
 */
public enum Direction
{
	U(0, 1, 0, CuboidDirection.Up),
	D(0, -1, 0, CuboidDirection.Down),
	N(0, 0, -1, CuboidDirection.North),
	S(0, 0, 1, CuboidDirection.South),
	E(1, 0, 0, CuboidDirection.East),
	W(-1, 0, 0, CuboidDirection.West);

	private static KMap<GBiset<Direction, Direction>, DOP> permute = null;

	private int x;
	private int y;
	private int z;
	private CuboidDirection f;

	public static Direction getDirection(BlockFace f)
	{
		switch(f)
		{
			case DOWN:
				return D;
			case EAST:
				return E;
			case EAST_NORTH_EAST:
				return E;
			case EAST_SOUTH_EAST:
				return E;
			case NORTH:
				return N;
			case NORTH_EAST:
				return N;
			case NORTH_NORTH_EAST:
				return N;
			case NORTH_NORTH_WEST:
				return N;
			case NORTH_WEST:
				return N;
			case SELF:
				return U;
			case SOUTH:
				return S;
			case SOUTH_EAST:
				return S;
			case SOUTH_SOUTH_EAST:
				return S;
			case SOUTH_SOUTH_WEST:
				return S;
			case SOUTH_WEST:
				return S;
			case UP:
				return U;
			case WEST:
				return W;
			case WEST_NORTH_WEST:
				return W;
			case WEST_SOUTH_WEST:
				return W;
		}

		return D;
	}

	@Override
	public String toString()
	{
		switch(this)
		{
			case D:
				return "Down";
			case E:
				return "East";
			case N:
				return "North";
			case S:
				return "South";
			case U:
				return "Up";
			case W:
				return "West";
		}

		return "?";
	}

	public boolean isVertical()
	{
		return equals(D) || equals(U);
	}

	public static Direction closest(Vector v)
	{
		double m = Double.MAX_VALUE;
		Direction s = null;

		for(Direction i : values())
		{
			Vector x = i.toVector();
			double g = x.dot(v);

			if(g < m)
			{
				m = g;
				s = i;
			}
		}

		return s;
	}

	public static Direction closest(Vector v, Direction... d)
	{
		double m = Double.MAX_VALUE;
		Direction s = null;

		for(Direction i : d)
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

	public static Direction closest(Vector v, KList<Direction> d)
	{
		double m = Double.MAX_VALUE;
		Direction s = null;

		for(Direction i : d)
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

	public boolean isCrooked(Direction to)
	{
		if(equals(to.reverse()))
		{
			return false;
		}

		if(equals(to))
		{
			return false;
		}

		return true;
	}

	private Direction(int x, int y, int z, CuboidDirection f)
	{
		this.x = x;
		this.y = y;
		this.z = z;
		this.f = f;
	}

	public Vector angle(Vector initial, Direction d)
	{
		calculatePermutations();

		for(GBiset<Direction, Direction> i : permute.keySet())
		{
			if(i.getA().equals(this) && i.getB().equals(d))
			{
				return permute.get(i).op(initial);
			}
		}

		return initial;
	}

	public Direction reverse()
	{
		switch(this)
		{
			case D:
				return U;
			case E:
				return W;
			case N:
				return S;
			case S:
				return N;
			case U:
				return D;
			case W:
				return E;
			default:
				break;
		}

		return null;
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

	public static KList<Direction> news()
	{
		return new KList<Direction>().add(N, E, W, S);
	}

	public static Direction getDirection(Vector v)
	{
		Vector k = VectorMath.triNormalize(v.clone().normalize());

		for(Direction i : udnews())
		{
			if(i.x == k.getBlockX() && i.y == k.getBlockY() && i.z == k.getBlockZ())
			{
				return i;
			}
		}

		return Direction.N;
	}

	public static KList<Direction> udnews()
	{
		return new KList<Direction>().add(U, D, N, E, W, S);
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
	public static Direction fromByte(byte b)
	{
		if(b > 5 || b < 0)
		{
			return null;
		}

		if(b == 0)
		{
			return D;
		}

		else if(b == 1)
		{
			return U;
		}

		else if(b == 2)
		{
			return N;
		}

		else if(b == 3)
		{
			return S;
		}

		else if(b == 4)
		{
			return W;
		}

		else
		{
			return E;
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
			case D:
				return 0;
			case E:
				return 5;
			case N:
				return 2;
			case S:
				return 3;
			case U:
				return 1;
			case W:
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

		permute = new KMap<GBiset<Direction, Direction>, DOP>();

		for(Direction i : udnews())
		{
			for(Direction j : udnews())
			{
				GBiset<Direction, Direction> b = new GBiset<Direction, Direction>(i, j);

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
			case D:
				return BlockFace.DOWN;
			case E:
				return BlockFace.EAST;
			case N:
				return BlockFace.NORTH;
			case S:
				return BlockFace.SOUTH;
			case U:
				return BlockFace.UP;
			case W:
				return BlockFace.WEST;
		}

		return null;
	}

	public Axis getAxis()
	{
		switch(this)
		{
			case D:
				return Axis.Y;
			case E:
				return Axis.X;
			case N:
				return Axis.Z;
			case S:
				return Axis.Z;
			case U:
				return Axis.Y;
			case W:
				return Axis.X;
		}

		return null;
	}
}
