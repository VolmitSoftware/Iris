package art.arcane.iris.util.common.math;

import org.bukkit.util.Vector;

public class VectorMath extends art.arcane.volmlib.util.math.VectorMath {
    public static Vector rotate(Direction current, Direction to, Vector v) {
        if (current.equals(to)) {
            return v;
        } else if (current.equals(to.reverse())) {
            if (current.isVertical()) {
                return new Vector(v.getX(), -v.getY(), v.getZ());
            } else {
                return new Vector(-v.getX(), v.getY(), -v.getZ());
            }
        } else {
            Vector c = current.toVector().clone().add(to.toVector());

            if (c.getX() == 0) {
                if (c.getY() != c.getZ()) {
                    return rotate90CX(v);
                }

                return rotate90CCX(v);
            } else if (c.getY() == 0) {
                if (c.getX() != c.getZ()) {
                    return rotate90CY(v);
                }

                return rotate90CCY(v);
            } else if (c.getZ() == 0) {
                if (c.getX() != c.getY()) {
                    return rotate90CZ(v);
                }

                return rotate90CCZ(v);
            }
        }

        return v;
    }

    public static Vector rotate(Direction current, Direction to, Vector v, int w, int h, int d) {
        if (current.equals(to)) {
            return v;
        } else if (current.equals(to.reverse())) {
            if (current.isVertical()) {
                return new Vector(v.getX(), -v.getY() + h, v.getZ());
            } else {
                return new Vector(-v.getX() + w, v.getY(), -v.getZ() + d);
            }
        } else {
            Vector c = current.toVector().clone().add(to.toVector());

            if (c.getX() == 0) {
                if (c.getY() != c.getZ()) {
                    return rotate90CX(v, d);
                }

                return rotate90CCX(v, h);
            } else if (c.getY() == 0) {
                if (c.getX() != c.getZ()) {
                    return rotate90CY(v, d);
                }

                return rotate90CCY(v, w);
            } else if (c.getZ() == 0) {
                if (c.getX() != c.getY()) {
                    return rotate90CZ(v, w);
                }

                return rotate90CCZ(v, h);
            }
        }

        return v;
    }

    public static Vector getAxis(Direction current, Direction to) {
        if (current.equals(Direction.U) || current.equals(Direction.D)) {
            if (to.equals(Direction.U) || to.equals(Direction.D)) {
                return new Vector(1, 0, 0);
            } else {
                if (current.equals(Direction.N) || current.equals(Direction.S)) {
                    return Direction.E.toVector();
                } else {
                    return Direction.S.toVector();
                }
            }
        }

        return new Vector(0, 1, 0);
    }
}
