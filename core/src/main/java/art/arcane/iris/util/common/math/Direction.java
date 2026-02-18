package art.arcane.iris.util.common.math;

import art.arcane.volmlib.util.collection.KList;
import art.arcane.volmlib.util.data.Cuboid.CuboidDirection;
import org.bukkit.Axis;
import org.bukkit.block.BlockFace;
import org.bukkit.util.Vector;

public enum Direction {
    U(art.arcane.volmlib.util.math.DirectionBasis.U, CuboidDirection.Up),
    D(art.arcane.volmlib.util.math.DirectionBasis.D, CuboidDirection.Down),
    N(art.arcane.volmlib.util.math.DirectionBasis.N, CuboidDirection.North),
    S(art.arcane.volmlib.util.math.DirectionBasis.S, CuboidDirection.South),
    E(art.arcane.volmlib.util.math.DirectionBasis.E, CuboidDirection.East),
    W(art.arcane.volmlib.util.math.DirectionBasis.W, CuboidDirection.West);

    private final art.arcane.volmlib.util.math.DirectionBasis basis;
    private final CuboidDirection f;

    Direction(art.arcane.volmlib.util.math.DirectionBasis basis, CuboidDirection f) {
        this.basis = basis;
        this.f = f;
    }

    private static Direction fromBasis(art.arcane.volmlib.util.math.DirectionBasis b) {
        if (b == null) {
            return null;
        }

        return switch (b) {
            case U -> U;
            case D -> D;
            case N -> N;
            case S -> S;
            case E -> E;
            case W -> W;
        };
    }

    public static Direction getDirection(BlockFace f) {
        return fromBasis(art.arcane.volmlib.util.math.DirectionBasis.getDirection(f));
    }

    public static Direction closest(Vector v) {
        return fromBasis(art.arcane.volmlib.util.math.DirectionBasis.closest(v));
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
        return fromBasis(art.arcane.volmlib.util.math.DirectionBasis.getDirection(v));
    }

    public static KList<Direction> udnews() {
        return new KList<Direction>().add(U, D, N, E, W, S);
    }

    public static Direction fromByte(byte b) {
        return fromBasis(art.arcane.volmlib.util.math.DirectionBasis.fromByte(b));
    }

    public static void calculatePermutations() {
        art.arcane.volmlib.util.math.DirectionBasis.calculatePermutations();
    }

    @Override
    public String toString() {
        return basis.toString();
    }

    public boolean isVertical() {
        return basis.isVertical();
    }

    public Vector toVector() {
        return basis.toVector();
    }

    public boolean isCrooked(Direction to) {
        if (equals(to.reverse())) {
            return false;
        }

        return !equals(to);
    }

    public Vector angle(Vector initial, Direction d) {
        return basis.angle(initial, d.basis);
    }

    public Direction reverse() {
        return fromBasis(basis.reverse());
    }

    public int x() {
        return basis.x();
    }

    public int y() {
        return basis.y();
    }

    public int z() {
        return basis.z();
    }

    public CuboidDirection f() {
        return f;
    }

    public byte byteValue() {
        return basis.byteValue();
    }

    public BlockFace getFace() {
        return basis.getFace();
    }

    public Axis getAxis() {
        return basis.getAxis();
    }
}
