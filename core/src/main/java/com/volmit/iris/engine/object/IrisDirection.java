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

package com.volmit.iris.engine.object;

import com.volmit.iris.engine.object.annotations.Desc;
import com.volmit.iris.util.collection.GBiset;
import com.volmit.iris.util.collection.KList;
import com.volmit.iris.util.collection.KMap;
import com.volmit.iris.util.data.Cuboid.CuboidDirection;
import com.volmit.iris.util.math.DOP;
import com.volmit.iris.util.math.VectorMath;
import org.bukkit.Axis;
import org.bukkit.block.BlockFace;
import org.bukkit.block.data.type.Jigsaw;
import org.bukkit.util.Vector;

import java.util.Map;

/**
 * Directions
 *
 * @author cyberpwn
 */
@Desc("A direction object")
public enum IrisDirection {
    @Desc("0, 1, 0")
    UP_POSITIVE_Y(0, 1, 0, CuboidDirection.Up),
    @Desc("0, -1, 0")
    DOWN_NEGATIVE_Y(0, -1, 0, CuboidDirection.Down),
    @Desc("0, 0, -1")
    NORTH_NEGATIVE_Z(0, 0, -1, CuboidDirection.North),
    @Desc("0, 0, 1")
    SOUTH_POSITIVE_Z(0, 0, 1, CuboidDirection.South),
    @Desc("1, 0, 0")
    EAST_POSITIVE_X(1, 0, 0, CuboidDirection.East),
    @Desc("-1, 0, 0")
    WEST_NEGATIVE_X(-1, 0, 0, CuboidDirection.West);

    private static KMap<GBiset<IrisDirection, IrisDirection>, DOP> permute = null;

    private final int x;
    private final int y;
    private final int z;
    private final CuboidDirection f;

    IrisDirection(int x, int y, int z, CuboidDirection f) {
        this.x = x;
        this.y = y;
        this.z = z;
        this.f = f;
    }

    public static IrisDirection getDirection(BlockFace f) {
        return switch (f) {
            case DOWN -> DOWN_NEGATIVE_Y;
            case EAST, EAST_NORTH_EAST, EAST_SOUTH_EAST -> EAST_POSITIVE_X;
            case NORTH, NORTH_NORTH_WEST, NORTH_EAST, NORTH_NORTH_EAST, NORTH_WEST -> NORTH_NEGATIVE_Z;
            case SELF, UP -> UP_POSITIVE_Y;
            case SOUTH, SOUTH_EAST, SOUTH_SOUTH_EAST, SOUTH_SOUTH_WEST, SOUTH_WEST -> SOUTH_POSITIVE_Z;
            case WEST, WEST_NORTH_WEST, WEST_SOUTH_WEST -> WEST_NEGATIVE_X;
        };

    }

    public static IrisDirection fromJigsawBlock(String direction) {
        for (IrisDirection i : IrisDirection.values()) {
            if (i.name().toLowerCase().split("\\Q_\\E")[0]
                    .equals(direction.split("\\Q_\\E")[0])) {
                return i;
            }
        }

        return null;
    }

    public static IrisDirection getDirection(Jigsaw.Orientation orientation) {
        return switch (orientation) {
            case DOWN_EAST, UP_EAST, EAST_UP -> EAST_POSITIVE_X;
            case DOWN_NORTH, UP_NORTH, NORTH_UP -> NORTH_NEGATIVE_Z;
            case DOWN_SOUTH, UP_SOUTH, SOUTH_UP -> SOUTH_POSITIVE_Z;
            case DOWN_WEST, UP_WEST, WEST_UP -> WEST_NEGATIVE_X;
        };

    }

    public static IrisDirection closest(Vector v) {
        double m = Double.MAX_VALUE;
        IrisDirection s = null;

        for (IrisDirection i : values()) {
            Vector x = i.toVector();
            double g = x.distance(v);

            if (g < m) {
                m = g;
                s = i;
            }
        }

        return s;
    }

    public static IrisDirection closest(Vector v, IrisDirection... d) {
        double m = Double.MAX_VALUE;
        IrisDirection s = null;

        for (IrisDirection i : d) {
            Vector x = i.toVector();
            double g = x.distance(v);

            if (g < m) {
                m = g;
                s = i;
            }
        }

        return s;
    }

    public static IrisDirection closest(Vector v, KList<IrisDirection> d) {
        double m = Double.MAX_VALUE;
        IrisDirection s = null;

        for (IrisDirection i : d) {
            Vector x = i.toVector();
            double g = x.distance(v);

            if (g < m) {
                m = g;
                s = i;
            }
        }

        return s;
    }

    public static KList<IrisDirection> news() {
        return new KList<IrisDirection>().add(NORTH_NEGATIVE_Z, EAST_POSITIVE_X, WEST_NEGATIVE_X, SOUTH_POSITIVE_Z);
    }

    public static IrisDirection getDirection(Vector v) {
        Vector k = VectorMath.triNormalize(v.clone().normalize());

        for (IrisDirection i : udnews()) {
            if (i.x == k.getBlockX() && i.y == k.getBlockY() && i.z == k.getBlockZ()) {
                return i;
            }
        }

        return IrisDirection.NORTH_NEGATIVE_Z;
    }

    public static KList<IrisDirection> udnews() {
        return new KList<IrisDirection>().add(UP_POSITIVE_Y, DOWN_NEGATIVE_Y, NORTH_NEGATIVE_Z, EAST_POSITIVE_X, WEST_NEGATIVE_X, SOUTH_POSITIVE_Z);
    }

    /**
     * Get the directional value from the given byte from common directional blocks
     * (MUST BE BETWEEN 0 and 5 INCLUSIVE)
     *
     * @param b the byte
     * @return the direction or null if the byte is outside of the inclusive range
     * 0-5
     */
    public static IrisDirection fromByte(byte b) {
        if (b > 5 || b < 0) {
            return null;
        }

        if (b == 0) {
            return DOWN_NEGATIVE_Y;
        } else if (b == 1) {
            return UP_POSITIVE_Y;
        } else if (b == 2) {
            return NORTH_NEGATIVE_Z;
        } else if (b == 3) {
            return SOUTH_POSITIVE_Z;
        } else if (b == 4) {
            return WEST_NEGATIVE_X;
        } else {
            return EAST_POSITIVE_X;
        }
    }

    public static void calculatePermutations() {
        if (permute != null) {
            return;
        }

        permute = new KMap<>();

        for (IrisDirection i : udnews()) {
            for (IrisDirection j : udnews()) {
                GBiset<IrisDirection, IrisDirection> b = new GBiset<>(i, j);

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
            case DOWN_NEGATIVE_Y -> "Down";
            case EAST_POSITIVE_X -> "East";
            case NORTH_NEGATIVE_Z -> "North";
            case SOUTH_POSITIVE_Z -> "South";
            case UP_POSITIVE_Y -> "Up";
            case WEST_NEGATIVE_X -> "West";
        };

    }

    public boolean isVertical() {
        return equals(DOWN_NEGATIVE_Y) || equals(UP_POSITIVE_Y);
    }

    public Vector toVector() {
        return new Vector(x, y, z);
    }

    public boolean isCrooked(IrisDirection to) {
        if (equals(to.reverse())) {
            return false;
        }

        return !equals(to);
    }

    public Vector angle(Vector initial, IrisDirection d) {
        calculatePermutations();

        for (Map.Entry<GBiset<IrisDirection, IrisDirection>, DOP> entry : permute.entrySet()) {
            GBiset<IrisDirection, IrisDirection> i = entry.getKey();
            if (i.getA().equals(this) && i.getB().equals(d)) {
                return entry.getValue().op(initial);
            }
        }

        return initial;
    }

    public IrisDirection reverse() {
        switch (this) {
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

    public BlockFace getFace() {
        return switch (this) {
            case DOWN_NEGATIVE_Y -> BlockFace.DOWN;
            case EAST_POSITIVE_X -> BlockFace.EAST;
            case NORTH_NEGATIVE_Z -> BlockFace.NORTH;
            case SOUTH_POSITIVE_Z -> BlockFace.SOUTH;
            case UP_POSITIVE_Y -> BlockFace.UP;
            case WEST_NEGATIVE_X -> BlockFace.WEST;
        };

    }

    public Axis getAxis() {
        return switch (this) {
            case DOWN_NEGATIVE_Y, UP_POSITIVE_Y -> Axis.Y;
            case EAST_POSITIVE_X, WEST_NEGATIVE_X -> Axis.X;
            case NORTH_NEGATIVE_Z, SOUTH_POSITIVE_Z -> Axis.Z;
        };

    }
}
