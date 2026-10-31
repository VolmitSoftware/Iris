package art.arcane.iris.pack.datapack.export.lower;

import art.arcane.iris.pack.datapack.export.ir.Field;
import art.arcane.iris.pack.datapack.export.ir.ValueRange;

public final class FieldDomains {
    public static final double POSITIVE_FLOOR = Float.MIN_NORMAL;
    public static final double CONSTANT_LIMIT = 1.0e6;

    private FieldDomains() {
    }

    public static Field clampToDomain(Field input, double floor) {
        ValueRange range = input.range();
        if (range.min() >= floor) {
            return input;
        }
        return new Field.Clamp(input, floor, Math.max(floor, Math.min(range.max(), CONSTANT_LIMIT)));
    }

    public static Field unaryInput(Field.Unary unary) {
        return switch (unary.op()) {
            case SQRT -> clampToDomain(unary.input(), 0.0);
            case LOG -> clampToDomain(unary.input(), POSITIVE_FLOOR);
            default -> unary.input();
        };
    }

    public static Field clampPowBase(Field base, Field exponent) {
        ValueRange exponentRange = exponent.range();
        if (exponentRange.min() == exponentRange.max() && exponentRange.min() == Math.rint(exponentRange.min())) {
            return base;
        }
        return clampToDomain(base, exponentRange.min() < 0.0 ? POSITIVE_FLOOR : 0.0);
    }
}
