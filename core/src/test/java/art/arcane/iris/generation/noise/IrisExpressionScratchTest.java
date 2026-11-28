package art.arcane.iris.generation.noise;

import art.arcane.iris.generation.runtime.PackRuntime;
import art.arcane.volmlib.util.collection.KList;
import art.arcane.volmlib.util.math.RNG;
import com.dfsek.paralithic.Expression;
import com.dfsek.paralithic.functions.dynamic.Context;
import com.dfsek.paralithic.functions.dynamic.DynamicFunction;
import com.dfsek.paralithic.node.Statefulness;
import org.junit.Test;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.Function;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotSame;
import static org.junit.Assert.assertSame;
import static org.junit.Assert.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.mock;

public class IrisExpressionScratchTest {
    @Test
    public void sequentialPlanarAndVolumetricSamplesReuseTheArgumentArrayAndKeepRngState() {
        Expression compiled = mock(Expression.class);
        ExpressionRuntime expression = runtimeOf(parsedAs(new IrisExpression(), compiled));
        AtomicReference<double[]> first = new AtomicReference<>();
        doAnswer(invocation -> {
            double[] arguments = (double[]) invocation.getRawArguments()[1];
            if (first.get() == null) {
                first.set(arguments);
            } else {
                assertSame(first.get(), arguments);
            }
            return arguments[0] + 10D * arguments[1] + 100D * arguments[2];
        }).when(compiled).evaluate(any(Context.class), any(double[].class));
        RNG rng = new RNG(17L);
        RNG control = new RNG(17L);

        for (int i = 0; i < 64; i++) {
            assertEquals(i + 30D - 100D, expression.evaluate(rng, i, 3D), 0D);
            assertEquals(i + 70D + 300D, expression.evaluate(rng, i, 7D, 3D), 0D);
        }
        assertEquals(control.nextLong(), rng.nextLong());
    }

    @Test
    public void recursiveEvaluationWithVariablesKeepsOuterArgumentsBeyondTheRetainedDepth() {
        AtomicReference<ExpressionRuntime> expression = new AtomicReference<>();
        IrisExpression config = recursive(new IrisExpression().setExpression("prior(x, y) + value + 17 * x + 31 * y + z")
                .setVariables(new KList<>(new IrisExpressionLoad().setName("value").setStaticValue(-1D))), expression);
        expression.set(runtimeOf(config));
        RNG rng = new RNG(78264193L);
        RNG control = new RNG(78264193L);
        double expected = 7D;
        for (int x = 0; x <= 31; x++) {
            expected = expected + 17D * x + 31D * 3.75D - 2D;
        }

        assertEquals(expected, expression.get().evaluate(rng, 31D, 3.75D), 0D);
        assertEquals(expected, expression.get().evaluate(rng, 31D, 3.75D), 0D);
        assertEquals(control.nextLong(), rng.nextLong());
    }

    @Test
    public void recursiveFunctionEvaluationDoesNotOverwriteTheOuterCoordinates() {
        AtomicReference<ExpressionRuntime> expression = new AtomicReference<>();
        expression.set(runtimeOf(recursive(new IrisExpression().setExpression("prior(x, y) + 17 * x + 31 * y + z"),
                expression)));
        double expected = 7D;
        for (int x = 0; x <= 31; x++) {
            expected = expected + 17D * x + 31D * 3.75D - 1D;
        }

        assertEquals(expected, expression.get().evaluate(new RNG(17L), 31D, 3.75D), 0D);
        assertEquals(expected, expression.get().evaluate(new RNG(17L), 31D, 3.75D), 0D);
    }

    @Test
    public void failedEvaluationUnwindsTheRetainedFrame() {
        Expression compiled = mock(Expression.class);
        ExpressionRuntime expression = runtimeOf(parsedAs(new IrisExpression(), compiled));
        AtomicBoolean fail = new AtomicBoolean(true);
        AtomicReference<double[]> failedArguments = new AtomicReference<>();
        doAnswer(invocation -> {
            double[] arguments = (double[]) invocation.getRawArguments()[1];
            if (fail.getAndSet(false)) {
                failedArguments.set(arguments);
                throw new IllegalStateException("sample failed");
            }
            assertSame(failedArguments.get(), arguments);
            return arguments[0];
        }).when(compiled).evaluate(any(Context.class), any(double[].class));
        RNG rng = new RNG(17L);

        assertThrows(IllegalStateException.class, () -> expression.evaluate(rng, 7D, 9D));
        assertEquals(31D, expression.evaluate(rng, 31D, 9D), 0D);
    }

    @Test
    public void largeArgumentListsRemainSupportedWithoutRetainingTheirArrays() {
        KList<IrisExpressionLoad> variables = new KList<>();
        for (int i = 0; i < 300; i++) {
            variables.add(new IrisExpressionLoad().setName("value" + i).setStaticValue(i));
        }
        Expression compiled = mock(Expression.class);
        ExpressionRuntime expression = runtimeOf(parsedAs(new IrisExpression().setVariables(variables), compiled));
        AtomicReference<double[]> first = new AtomicReference<>();
        doAnswer(invocation -> {
            double[] arguments = (double[]) invocation.getRawArguments()[1];
            if (first.get() == null) {
                first.set(arguments);
            } else {
                assertNotSame(first.get(), arguments);
            }
            for (int i = 0; i < 300; i++) {
                assertEquals(i, arguments[i], 0D);
            }
            return arguments[300] + arguments[301] + arguments[302];
        }).when(compiled).evaluate(any(Context.class), any(double[].class));

        assertEquals(11D, expression.evaluate(new RNG(17L), 7D, 5D), 0D);
        assertEquals(31D, expression.evaluate(new RNG(17L), 7D, 19D, 5D), 0D);
    }

    @Test(timeout = 15000)
    public void sharedExpressionKeepsExactSamplesAcrossThreadsAndDimensions() throws Exception {
        ExpressionRuntime expression = runtimeOf(new IrisExpression().setExpression("sin(x) + cos(y) + z / 7"));
        RNG control = new RNG(17L);
        long[] expected = new long[128];
        for (int i = 0; i < expected.length; i++) {
            expected[i] = Double.doubleToRawLongBits((i & 1) == 0
                    ? expression.evaluate(control, i * 0.17D, i * -0.31D)
                    : expression.evaluate(control, i * 0.17D, i * 0.23D, i * -0.31D));
        }
        ExecutorService executor = Executors.newFixedThreadPool(4);
        try {
            List<Future<?>> tasks = new ArrayList<>();
            for (int task = 0; task < 16; task++) {
                tasks.add(executor.submit(() -> {
                    RNG rng = new RNG(17L);
                    for (int i = 0; i < expected.length; i++) {
                        double actual = (i & 1) == 0
                                ? expression.evaluate(rng, i * 0.17D, i * -0.31D)
                                : expression.evaluate(rng, i * 0.17D, i * 0.23D, i * -0.31D);
                        assertEquals(expected[i], Double.doubleToRawLongBits(actual));
                    }
                }));
            }
            for (Future<?> task : tasks) {
                task.get();
            }
        } finally {
            executor.shutdownNow();
            executor.awaitTermination(5, TimeUnit.SECONDS);
        }
    }

    private static ExpressionRuntime runtimeOf(IrisExpression expression) {
        return PackRuntime.standalone(null).expression(expression);
    }

    private static IrisExpression parsedAs(IrisExpression template, Expression compiled) {
        return new IrisExpression() {
            @Override
            public Expression parse(Function<IrisExpressionFunction, DynamicFunction> functionRuntime) {
                return compiled;
            }
        }.setVariables(template.getVariables());
    }

    private static IrisExpression recursive(IrisExpression template, AtomicReference<ExpressionRuntime> expression) {
        IrisExpression config = new IrisExpression() {
            @Override
            public Expression parse(Function<IrisExpressionFunction, DynamicFunction> functionRuntime) {
                return super.parse(function -> new PriorFunction(expression));
            }
        };
        config.setExpression(template.getExpression());
        config.setVariables(template.getVariables());
        config.setFunctions(new KList<>(new IrisExpressionFunction().setName("prior")
                .setStyleValue(new IrisGeneratorStyle())));
        return config;
    }

    private record PriorFunction(AtomicReference<ExpressionRuntime> expression) implements DynamicFunction {
        @Override
        public int getArgNumber() {
            return 2;
        }

        @Override
        public Statefulness statefulness() {
            return Statefulness.STATEFUL;
        }

        @Override
        public double eval(double... coordinates) {
            return 0D;
        }

        @Override
        public double eval(Context context, double... coordinates) {
            RNG rng = ((ExpressionRuntime.FunctionContext) context).rng();
            return coordinates[0] <= 0D ? 7D : expression.get().evaluate(rng, coordinates[0] - 1D, coordinates[1]);
        }
    }
}
