package art.arcane.iris.engine.object.noise.expression;

import com.dfsek.paralithic.eval.parser.Parser;
import com.dfsek.paralithic.functions.dynamic.DynamicFunction;
import com.dfsek.paralithic.node.Statefulness;
import org.jetbrains.annotations.NotNull;

import java.util.function.ToDoubleFunction;

/** Shaping functions every pack expression can call. */
final class ExpressionHelpers {
    // Simplex noise is exactly 0.5 on its lattice, so a crest at 0.5 would trace the lattice as straight lines.
    private static final double RIDGE_CREST = 0.44;
    private static final double RIDGE_HALF_WIDTH = 1 / 2.2;

    static final String DESCRIPTION = "Helpers: clamp(v, low, high); linearstep(from, to, v) rises 0 to 1 in a straight "
            + "line as v goes from 'from' to 'to'; smoothstep(from, to, v) does the same with soft ends; ridge(n) turns "
            + "noise from 0 to 1 into sharp crests of 1 where n is about 0.44, falling to 0 away from them.";

    private ExpressionHelpers() {
    }

    /** Registers the helpers; a pack function registered afterwards with the same name replaces one. */
    static void registerOn(Parser parser) {
        parser.registerFunction("clamp", new Helper(3, args -> clamp(args[0], args[1], args[2])));
        parser.registerFunction("linearstep", new Helper(3, args -> linearstep(args[0], args[1], args[2])));
        parser.registerFunction("smoothstep", new Helper(3, args -> smoothstep(args[0], args[1], args[2])));
        parser.registerFunction("ridge", new Helper(1, args -> ridge(args[0])));
    }

    static double clamp(double value, double low, double high) {
        return Math.max(low, Math.min(high, value));
    }

    static double linearstep(double from, double to, double value) {
        return clamp((value - from) / (to - from), 0, 1);
    }

    static double smoothstep(double from, double to, double value) {
        double progress = linearstep(from, to, value);
        return progress * progress * (3 - 2 * progress);
    }

    static double ridge(double noise) {
        double crest = 1 - linearstep(0, RIDGE_HALF_WIDTH, Math.abs(noise - RIDGE_CREST));
        return crest * crest;
    }

    private record Helper(int argNumber, ToDoubleFunction<double[]> body) implements DynamicFunction {
        @Override
        public double eval(double... args) {
            return body.applyAsDouble(args);
        }

        @Override
        public int getArgNumber() {
            return argNumber;
        }

        @NotNull
        @Override
        public Statefulness statefulness() {
            return Statefulness.STATELESS;
        }
    }
}
