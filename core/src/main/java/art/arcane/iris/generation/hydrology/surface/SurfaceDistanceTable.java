package art.arcane.iris.generation.hydrology.surface;

import java.util.Objects;
import java.util.function.DoubleBinaryOperator;

final class SurfaceDistanceTable {
    static final SurfaceDistanceTable SHARED = new SurfaceDistanceTable(256, StrictMath::hypot);

    private final int maximumResidual;
    private final int width;
    private final double[] integers;
    private final double[] diagonals;
    private final DoubleBinaryOperator calculator;

    SurfaceDistanceTable(int maximumResidual, DoubleBinaryOperator calculator) {
        if (maximumResidual < 0 || maximumResidual > 256) {
            throw new IllegalArgumentException("Surface distance table residual must be between zero and 256.");
        }
        this.maximumResidual = maximumResidual;
        this.width = maximumResidual + 1;
        this.calculator = Objects.requireNonNull(calculator, "calculator");
        this.integers = new double[width * width];
        this.diagonals = new double[maximumResidual];
        for (int x = 0; x <= maximumResidual; x++) {
            for (int z = 0; z <= maximumResidual; z++) {
                integers[x * width + z] = calculator.applyAsDouble(x, z);
            }
        }
        for (int offset = 0; offset < maximumResidual; offset++) {
            diagonals[offset] = calculator.applyAsDouble(offset + 0.5D, offset + 0.5D);
        }
    }

    double hypot(double x, double z) {
        double positiveX = Math.abs(x);
        double positiveZ = Math.abs(z);
        int integerX = (int) positiveX;
        int integerZ = (int) positiveZ;
        if (integerX <= maximumResidual && integerZ <= maximumResidual
                && positiveX == integerX && positiveZ == integerZ) {
            return integers[integerX * width + integerZ];
        }
        if (positiveX == positiveZ && positiveX < maximumResidual && positiveX == integerX + 0.5D) {
            return diagonals[integerX];
        }
        return calculator.applyAsDouble(x, z);
    }
}
