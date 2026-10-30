package art.arcane.iris.pack.datapack.export.compile.cave;

import art.arcane.iris.pack.datapack.export.ir.Field;

import java.util.ArrayList;
import java.util.List;

final class VolumeLift {
    private static final String SHARED_SUFFIX = "/volume";

    private VolumeLift() {
    }

    static Field lift(Field field) {
        return switch (field) {
            case Field.Const constant -> constant;
            case Field.Coord coord -> coord;
            case Field.Ref ref -> ref;
            case Field.TopSurface topSurface -> topSurface;
            case Field.Slice slice -> slice;
            case Field.Noise noise -> noise(noise);
            case Field.Binary binary -> new Field.Binary(binary.op(), lift(binary.left()), lift(binary.right()));
            case Field.Unary unary -> new Field.Unary(unary.op(), lift(unary.input()));
            case Field.Clamp clamp -> new Field.Clamp(lift(clamp.input()), clamp.min(), clamp.max());
            case Field.Lerp lerp -> new Field.Lerp(lift(lerp.alpha()), lift(lerp.first()), lift(lerp.second()));
            case Field.Round round -> new Field.Round(round.mode(), lift(round.input()), lift(round.multiple()));
            case Field.Curve curve -> curve(curve);
            case Field.Partition partition -> new Field.Partition(lift(partition.input()), partition.thresholds(), partition.leaves());
            case Field.Mux mux -> new Field.Mux(lift(mux.index()), liftAll(mux.cases()));
            case Field.Gate gate -> new Field.Gate(lift(gate.condition()), lift(gate.expr()));
            case Field.RangeIf rangeIf -> new Field.RangeIf(lift(rangeIf.input()), rangeIf.min(), rangeIf.max(), lift(rangeIf.inRange()), lift(rangeIf.outOfRange()));
            case Field.Grid grid -> new Field.Grid(lift(grid.expr()), grid.cellXZ(), grid.cellY());
            case Field.Shared shared -> shared(shared);
        };
    }

    private static Field noise(Field.Noise noise) {
        double yScale = noise.yScale() == 0.0 ? noise.xzScale() : noise.yScale();
        return new Field.Noise(noise.noiseId(), noise.xzScale(), yScale, liftShift(noise.shiftX()), liftShift(noise.shiftY()), liftShift(noise.shiftZ()));
    }

    private static Field liftShift(Field shift) {
        return shift == null ? null : lift(shift);
    }

    private static Field.Curve curve(Field.Curve curve) {
        List<Field.Knot> knots = new ArrayList<>(curve.knots().size());
        for (Field.Knot knot : curve.knots()) {
            Field value = knot.value() instanceof Field.Curve nested ? curve(nested) : knot.value();
            knots.add(new Field.Knot(knot.location(), value, knot.derivative()));
        }
        return new Field.Curve(lift(curve.input()), knots);
    }

    private static List<Field> liftAll(List<Field> fields) {
        List<Field> lifted = new ArrayList<>(fields.size());
        for (Field field : fields) {
            lifted.add(lift(field));
        }
        return lifted;
    }

    private static Field shared(Field.Shared shared) {
        Field body = lift(shared.expr());
        return body.equals(shared.expr()) ? shared : new Field.Shared(shared.name() + SHARED_SUFFIX, body);
    }
}
