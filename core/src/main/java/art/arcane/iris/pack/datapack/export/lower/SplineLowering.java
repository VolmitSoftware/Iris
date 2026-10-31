package art.arcane.iris.pack.datapack.export.lower;

import art.arcane.iris.pack.datapack.export.ir.Field;
import art.arcane.iris.pack.datapack.export.ir.ValueRange;

import java.util.ArrayList;
import java.util.List;

public final class SplineLowering {
    private static final double MUX_FLANK = 0.25;
    private static final double IDENTITY_MARGIN = 1.0;
    private static final double EXACT_IDENTITY_LIMIT = 524_288.0;
    private static final double GATE_CLOSED = Float.MIN_VALUE;
    private static final double GATE_OPEN = Math.nextUp(Float.MIN_VALUE);
    private static final Field ZERO = new Field.Const(0.0);

    private SplineLowering() {
    }

    public static Field.Curve mux(Field.Mux mux) {
        int last = mux.cases().size() - 1;
        List<Field.Knot> knots = new ArrayList<>(mux.cases().size() * 3);
        for (int index = 0; index <= last; index++) {
            if (index > 0) {
                knots.add(new Field.Knot(index - MUX_FLANK, ZERO, 0.0));
            }
            knots.add(new Field.Knot(index, knotValue(mux.cases().get(index)), 0.0));
            if (index < last) {
                knots.add(new Field.Knot(index + MUX_FLANK, ZERO, 0.0));
            }
        }
        return new Field.Curve(mux.index(), knots);
    }

    public static Field.Curve gate(Field.Gate gate) {
        return new Field.Curve(gate.condition(), List.of(new Field.Knot(GATE_CLOSED, ZERO, 0.0), new Field.Knot(GATE_OPEN, knotValue(gate.expr()), 0.0)));
    }

    private static Field knotValue(Field field) {
        return field instanceof Field.Const ? field : identity(field);
    }

    private static Field.Curve identity(Field field) {
        ValueRange range = field.range();
        double bound = identityBound(Math.max(Math.abs(range.min() - IDENTITY_MARGIN), Math.abs(range.max() + IDENTITY_MARGIN)));
        return new Field.Curve(field, List.of(new Field.Knot(-bound, new Field.Const(-bound), 1.0), new Field.Knot(bound, new Field.Const(bound), 1.0)));
    }

    private static double identityBound(double reach) {
        if (!(reach <= EXACT_IDENTITY_LIMIT)) {
            return FieldDomains.CONSTANT_LIMIT;
        }
        double bound = 1.0;
        while (bound < reach) {
            bound *= 2.0;
        }
        return bound;
    }
}
