package art.arcane.iris.pack.datapack.export.emit;

import art.arcane.iris.pack.datapack.DataVersion;
import art.arcane.iris.pack.datapack.export.WorldgenExportTargets;
import art.arcane.iris.pack.datapack.export.lower.FieldDomains;
import com.google.gson.JsonPrimitive;

import java.math.BigDecimal;

public final class JsonNumbers {
    private JsonNumbers() {
    }

    public static JsonPrimitive constant(double value, DataVersion target) {
        if (!Double.isFinite(value) || value < -FieldDomains.CONSTANT_LIMIT || value > FieldDomains.CONSTANT_LIMIT) {
            throw new IllegalArgumentException("Density function constant " + value + " is outside [-1e6, 1e6]");
        }
        double normalized = value == 0.0 ? 0.0 : value;
        return switch (target) {
            case V26_3 -> new JsonPrimitive(shortestDecimal((float) normalized));
            default -> throw WorldgenExportTargets.unsupported(target.getVersion());
        };
    }

    public static JsonPrimitive unbounded(double value) {
        if (!Double.isFinite(value)) {
            throw new IllegalArgumentException("Density function value must be finite, got " + value);
        }
        return new JsonPrimitive(value == 0.0 ? 0.0 : value);
    }

    private static BigDecimal shortestDecimal(float value) {
        BigDecimal decimal = new BigDecimal(Float.toString(value)).stripTrailingZeros();
        return decimal.scale() < 1 ? decimal.setScale(1) : decimal;
    }
}
