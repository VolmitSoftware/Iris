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

package art.arcane.iris.generation.noise;

import art.arcane.iris.generation.runtime.Engine;
import art.arcane.iris.generation.runtime.PackRuntime;
import art.arcane.iris.generation.runtime.SeedTable;
import art.arcane.volmlib.util.math.RNG;
import art.arcane.volmlib.util.noise.CNG;
import art.arcane.volmlib.util.stream.ProceduralStream;
import art.arcane.volmlib.util.stream.interpolation.Interpolated;
import com.dfsek.paralithic.Expression;
import com.dfsek.paralithic.functions.dynamic.Context;
import com.dfsek.paralithic.functions.dynamic.DynamicFunction;
import com.dfsek.paralithic.node.Statefulness;
import lombok.NonNull;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.Objects;

/**
 * An {@link IrisExpression} parsed for one runtime scope: its functions and variables read that scope's engine
 * streams and seeded styles, so the parsed expression belongs to the scope and not to the pack object.
 */
public final class ExpressionRuntime {
    private static final int SEED_CAPACITY = 8;
    private static final int MAX_RETAINED_EVALUATION_DEPTH = 8;
    private static final int MAX_RETAINED_ARGUMENTS = 256;
    private static final ThreadLocal<EvaluationScratch> EVALUATION_SCRATCH =
            ThreadLocal.withInitial(EvaluationScratch::new);

    private final VariableRuntime[] variables;
    private final Expression parsed;
    private final SeedTable<ProceduralStream<Double>> streams;

    public ExpressionRuntime(IrisExpression expression, PackRuntime runtime) {
        this.variables = expression.getVariables().stream()
                .map(variable -> new VariableRuntime(variable, runtime))
                .toArray(VariableRuntime[]::new);
        this.parsed = expression.parse(function -> new FunctionRuntime(function, runtime));
        this.streams = new SeedTable<>(SEED_CAPACITY, seed -> {
            RNG streamRng = new RNG(seed);
            return ProceduralStream.of((x, z) -> evaluate(streamRng, x, z),
                    (x, y, z) -> evaluate(streamRng, x, y, z), Interpolated.DOUBLE);
        });
    }

    public ProceduralStream<Double> stream(RNG rng) {
        return streams.get(rng.getSeed());
    }

    public double evaluate(RNG rng, double x, double z) {
        EvaluationScratch scratch = EVALUATION_SCRATCH.get();
        double[] arguments = scratch.acquire(3 + variables.length);
        try {
            int index = 0;
            for (VariableRuntime variable : variables) {
                arguments[index++] = variable.value(rng, x, z);
            }
            arguments[index++] = x;
            arguments[index++] = z;
            arguments[index] = -1;
            return parsed.evaluate(new FunctionContext(rng), arguments);
        } finally {
            scratch.release();
        }
    }

    public double evaluate(RNG rng, double x, double y, double z) {
        EvaluationScratch scratch = EVALUATION_SCRATCH.get();
        double[] arguments = scratch.acquire(3 + variables.length);
        try {
            int index = 0;
            for (VariableRuntime variable : variables) {
                arguments[index++] = variable.value(rng, x, y, z);
            }
            arguments[index++] = x;
            arguments[index++] = y;
            arguments[index] = z;
            return parsed.evaluate(new FunctionContext(rng), arguments);
        } finally {
            scratch.release();
        }
    }

    public record FunctionContext(@NonNull RNG rng) implements Context {
        @Override
        public boolean equals(Object o) {
            if (o == null || getClass() != o.getClass()) return false;
            FunctionContext that = (FunctionContext) o;
            return rng.getSeed() == that.rng.getSeed();
        }

        @Override
        public int hashCode() {
            return Long.hashCode(rng.getSeed());
        }
    }

    private static Engine requireEngine(PackRuntime runtime, String kind, String name) {
        Engine engine = runtime.engine();
        if (engine == null) {
            throw new IllegalStateException("Expression " + kind + " '" + name + "' requires an active Iris engine.");
        }
        return engine;
    }

    private static final class FunctionRuntime implements DynamicFunction {
        private final IrisExpressionFunction function;
        private final PackRuntime runtime;
        private final SeedTable<Provider> providers;

        private FunctionRuntime(IrisExpressionFunction function, PackRuntime runtime) {
            this.function = function;
            this.runtime = runtime;
            this.providers = new SeedTable<>(SEED_CAPACITY, this::createProvider);
        }

        @Override
        public int getArgNumber() {
            return function.argumentCount();
        }

        @NotNull
        @Override
        public Statefulness statefulness() {
            return Statefulness.STATEFUL;
        }

        @Override
        public double eval(double... doubles) {
            return 0;
        }

        @Override
        public double eval(@Nullable Context raw, double... args) {
            FunctionContext context = Objects.requireNonNull((FunctionContext) raw, "Expression context");
            return providers.get(context.rng().getSeed()).eval(args);
        }

        private Provider createProvider(long seed) {
            if (function.getEngineStreamValue() != null) {
                Engine engine = requireEngine(runtime, "function", function.getName());
                ProceduralStream<Double> stream = function.getEngineStreamValue().get(engine);
                return coordinates -> stream.get(coordinates[0], coordinates[1]);
            }
            if (function.getStyleValue() != null) {
                return function.getStyleValue().create(seed, runtime)::noise;
            }
            return coordinates -> Double.NaN;
        }
    }

    @FunctionalInterface
    private interface Provider {
        double eval(double... args);
    }

    private static final class VariableRuntime {
        private final IrisExpressionLoad variable;
        private final PackRuntime runtime;
        private final SeedTable<CNG> styles;
        private volatile ProceduralStream<Double> engineStream;
        private volatile Double engineValue;

        private VariableRuntime(IrisExpressionLoad variable, PackRuntime runtime) {
            this.variable = variable;
            this.runtime = runtime;
            this.styles = new SeedTable<>(SEED_CAPACITY,
                    seed -> variable.getStyleValue().createNoCache(new RNG(seed), runtime));
        }

        private double value(RNG rng, double x, double z) {
            if (variable.getEngineValue() != null) {
                return engineValue();
            }
            if (variable.getEngineStreamValue() != null) {
                return engineStream().get(x, z);
            }
            if (variable.getStyleValue() != null) {
                return styles.get(rng.getSeed()).noise(x, z);
            }
            return variable.getStaticValue();
        }

        private double value(RNG rng, double x, double y, double z) {
            if (variable.getEngineValue() != null) {
                return engineValue();
            }
            if (variable.getEngineStreamValue() != null) {
                return engineStream().get(x, z);
            }
            if (variable.getStyleValue() != null) {
                return styles.get(rng.getSeed()).noise(x, y, z);
            }
            return variable.getStaticValue();
        }

        private double engineValue() {
            Double value = engineValue;
            if (value == null) {
                value = variable.getEngineValue().get(requireEngine(runtime, "variable", variable.getName()));
                engineValue = value;
            }
            return value;
        }

        private ProceduralStream<Double> engineStream() {
            ProceduralStream<Double> stream = engineStream;
            if (stream == null) {
                stream = variable.getEngineStreamValue().get(requireEngine(runtime, "variable", variable.getName()));
                engineStream = stream;
            }
            return stream;
        }
    }

    private static final class EvaluationScratch {
        private final double[][] retained = new double[MAX_RETAINED_EVALUATION_DEPTH][];
        private int depth;

        private double[] acquire(int length) {
            double[] arguments;
            if (depth >= retained.length || length > MAX_RETAINED_ARGUMENTS) {
                arguments = new double[length];
            } else {
                arguments = retained[depth];
                if (arguments == null || arguments.length != length) {
                    arguments = new double[length];
                    retained[depth] = arguments;
                }
            }
            depth++;
            return arguments;
        }

        private void release() {
            depth--;
        }
    }
}
