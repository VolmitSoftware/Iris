/*
 * Iris is a World Generator for Minecraft Bukkit Servers
 * Copyright (c) 2022 Arcane Arts (Volmit Software)
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License
 * along with this program.  If not, see <https://www.gnu.org/licenses/>.
 */

package com.volmit.iris.util.math;

import com.volmit.iris.util.collection.GBiset;
import com.volmit.iris.util.collection.KList;
import com.volmit.iris.util.collection.KMap;
import com.volmit.iris.util.data.Cuboid.CuboidDirection;
import org.bukkit.Axis;
import org.bukkit.block.BlockFace;
import org.bukkit.util.Vector;

import java.util.Map;

/**
 * Directions
 *
 * @author cyberpwn
 */
public enum Direction {
    U(0, 1, 0, CuboidDirection.Up),
    D(0, -1, 0, CuboidDirection.Down),
    N(0, 0, -1, CuboidDirection.North),
    S(0, 0, 1, CuboidDirection.South),
    E(1, 0, 0, CuboidDirection.East),
    W(-1, 0, 0, CuboidDirection.West);

    private static KMap<GBiset<Direction, Direction>, DOP> permute = null;

    private final int x;
    private final int y;
    private final int z;
    private final CuboidDirection f;

    Direction(int x, int y, int z, CuboidDirection f) {
        this.x = x;
        this.y = y;
        this.z = z;
        this.f = f;
    }

    public static Direction getDirection(BlockFace f) {
        return switch (f) {
            case DOWN -> D;
            case EAST, EAST_SOUTH_EAST, EAST_NORTH_EAST -> E;
            case NORTH, NORTH_WEST, NORTH_NORTH_WEST, NORTH_NORTH_EAST, NORTH_EAST -> N;
            case SELF, UP -> U;
            case SOUTH, SOUTH_WEST, SOUTH_SOUTH_WEST, SOUTH_SOUTH_EAST, SOUTH_EAST -> S;
            case WEST, WEST_SOUTH_WEST, WEST_NORTH_WEST -> W;
        };

    }

    public static Direction closest(Vector v) {
        double m = Double.MAX_VALUE;
        Direction s = null;

        for (Direction i : values()) {
            Vector x = i.toVector();
            double g = x.dot(v);

            if (g < m) {
                m = g;
                s = i;
            }
        }

        return s;
    }

    public static Direction closest(Vector v, Direction... d) {
        double m = Double.MAX_VALUE;
        Direction s = null;

        for (Direction i : d) {
            Vector x = i.toVector();
            double g = x.distance(v);

            if (g < m) {
                m = g;
                s = i;
            }
        }

        return s;
    }

    public static Direction closest(Vector v, KList<Direction> d) {
        double m = Double.MAX_VALUE;
        Direction s = null;

        for (Direction i : d) {
            Vector x = i.toVector();
            double g = x.distance(v);

            if (g < m) {
                m = g;
                s = i;
            }
        }

        return s;
    }

    public static KList<Direction> news() {
        return new KList<Direction>().add(N, E, W, S);
    }

    public static Direction getDirection(Vector v) {
        Vector k = VectorMath.triNormalize(v.clone().normalize());

        for (Direction i : udnews()) {
            if (i.x == k.getBlockX() && i.y == k.getBlockY() && i.z == k.getBlockZ()) {
                return i;
            }
        }

        return Direction.N;
    }

    public static KList<Direction> udnews() {
        return new KList<Direction>().add(U, D, N, E, W, S);
    }

    /**
     * Get the directional value from the given byte from common directional blocks
     * (MUST BE BETWEEN 0 and 5 INCLUSIVE)
     *
     * @param b the byte
     * @return the direction or null if the byte is outside of the inclusive range
     * 0-5
     */
    public static Direction fromByte(byte b) {
        if (b > 5 || b < 0) {
            return null;
        }

        if (b == 0) {
            return D;
        } else if (b == 1) {
            return U;
        } else if (b == 2) {
            return N;
        } else if (b == 3) {
            return S;
        } else if (b == 4) {
            return W;
        } else {
            return E;
        }
    }

    public static void calculatePermutations() {
        if (permute != null) {
            return;
        }

        permute = new KMap<>();

        for (Direction i : udnews()) {
            for (Direction j : udnews()) {
                GBiset<Direction, Direction> b = new GBiset<>(i, j);

                if (i.equals(j)) {
                    permute.put(b, new DOP("DIRECT") {
                        @Override
                        public Vector op(Vector v) {
                            return v;
                        }
                    });
                } else if (i.reverse().equals(j)) {
                    if (i.isVertical()) {
                        permute.put(b, new DOP("R180CCZ") {
                            @Override
                            public Vector op(Vector v) {
                                return VectorMath.rotate90CCZ(VectorMath.rotate90CCZ(v));
                            }
                        });
                    } else {
                        permute.put(b, new DOP("R180CCY") {
                            @Override
                            public Vector op(Vector v) {
                                return VectorMath.rotate90CCY(VectorMath.rotate90CCY(v));
                            }
                        });
                    }
                } else if (getDirection(VectorMath.rotate90CX(i.toVector())).equals(j)) {
                    permute.put(b, new DOP("R90CX") {
                        @Override
                        public Vector op(Vector v) {
                            return VectorMath.rotate90CX(v);
                        }
                    });
                } else if (getDirection(VectorMath.rotate90CCX(i.toVector())).equals(j)) {
                    permute.put(b, new DOP("R90CCX") {
                        @Override
                        public Vector op(Vector v) {
                            return VectorMath.rotate90CCX(v);
                        }
                    });
                } else if (getDirection(VectorMath.rotate90CY(i.toVector())).equals(j)) {
                    permute.put(b, new DOP("R90CY") {
                        @Override
                        public Vector op(Vector v) {
                            return VectorMath.rotate90CY(v);
                        }
                    });
                } else if (getDirection(VectorMath.rotate90CCY(i.toVector())).equals(j)) {
                    permute.put(b, new DOP("R90CCY") {
                        @Override
                        public Vector op(Vector v) {
                            return VectorMath.rotate90CCY(v);
                        }
                    });
                } else if (getDirection(VectorMath.rotate90CZ(i.toVector())).equals(j)) {
                    permute.put(b, new DOP("R90CZ") {
                        @Override
                        public Vector op(Vector v) {
                            return VectorMath.rotate90CZ(v);
                        }
                    });
                } else if (getDirection(VectorMath.rotate90CCZ(i.toVector())).equals(j)) {
                    permute.put(b, new DOP("R90CCZ") {
                        @Override
                        public Vector op(Vector v) {
                            return VectorMath.rotate90CCZ(v);
                        }
                    });
                } else {
                    permute.put(b, new DOP("FAIL") {
                        @Override
                        public Vector op(Vector v) {
                            return v;
                        }
                    });
                }
            }
        }
    }

    @Override
    public String toString() {
        return switch (this) {
            case D -> "Down";
            case E -> "East";
            case N -> "North";
            case S -> "South";
            case U -> "Up";
            case W -> "West";
        };

    }

    public boolean isVertical() {
        return equals(D) || equals(U);
    }

    public Vector toVector() {
        return new Vector(x, y, z);
    }

    public boolean isCrooked(Direction to) {
        if (equals(to.reverse())) {
            return false;
        }

        return !equals(to);
    }

    public Vector angle(Vector initial, Direction d) {
        calculatePermutations();

        for (Map.Entry<GBiset<Direction, Direction>, DOP> entry : permute.entrySet()) {
            GBiset<Direction, Direction> i = entry.getKey();
            if (i.getA().equals(this) && i.getB().equals(d)) {
                return entry.getValue().op(initial);
            }
        }

        return initial;
    }

    public Direction reverse() {
        switch (this) {
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

    public int x() {
        return x;
    }

    public int y() {
        return y;
    }

    public int z() {
        return z;
    }

    public CuboidDirection f() {
        return f;
    }

    /**
     * Get the byte value represented in some directional blocks
     *
     * @return the byte value
     */
    public byte byteValue() {
        switch (this) {
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

    public BlockFace getFace() {
        return switch (this) {
            case D -> BlockFace.DOWN;
            case E -> BlockFace.EAST;
            case N -> BlockFace.NORTH;
            case S -> BlockFace.SOUTH;
            case U -> BlockFace.UP;
            case W -> BlockFace.WEST;
        };

    }

    public Axis getAxis() {
        return switch (this) {
            case D, U -> Axis.Y;
            case E, W -> Axis.X;
            case N, S -> Axis.Z;
        };

    }
}
