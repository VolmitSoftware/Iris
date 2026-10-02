package art.arcane.iris.generation.noise;

import art.arcane.iris.generation.runtime.Engine;
import art.arcane.iris.generation.runtime.IrisComplex;
import art.arcane.iris.generation.runtime.IrisEngineStreamType;
import art.arcane.iris.pack.loading.IrisData;
import art.arcane.volmlib.util.collection.KList;
import art.arcane.volmlib.util.math.RNG;
import art.arcane.volmlib.util.stream.ProceduralStream;
import org.junit.Test;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicReference;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertSame;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

public class IrisExpressionSeedCohesionTest {
    @Test
    public void expressionStreamsSeparateSeedsAndInitializationOrder() {
        IrisData data = mock(IrisData.class);
        IrisExpression expression = expression(data);
        double first = expression(data).evaluate(new RNG(17L), 23.5D, -94.75D);
        double second = expression(data).evaluate(new RNG(53L), 23.5D, -94.75D);
        ProceduralStream<Double> secondStream = expression.stream(new RNG(53L));
        ProceduralStream<Double> firstStream = expression.stream(new RNG(17L));
        assertEquals(second, secondStream.get(23.5D, -94.75D), 0D);
        assertEquals(first, firstStream.get(23.5D, -94.75D), 0D);
        assertSame(firstStream, expression.stream(new RNG(17L)));
    }

    @Test
    public void expressionFunctionTracksEngineIdentity() {
        Engine firstEngine = engine(17D);
        Engine secondEngine = engine(53D);
        AtomicReference<Engine> active = new AtomicReference<>(firstEngine);
        IrisData data = mock(IrisData.class);
        when(data.getEngine()).thenAnswer(ignored -> active.get());
        IrisExpressionFunction function = new IrisExpressionFunction()
                .setName("height")
                .setData(data)
                .setEngineStreamValue(IrisEngineStreamType.HEIGHT);
        IrisExpressionFunction.FunctionContext context = new IrisExpressionFunction.FunctionContext(new RNG(91L));
        assertEquals(17D, function.eval(context, 4D, -8D), 0D);
        active.set(secondEngine);
        assertEquals(53D, function.eval(context, 4D, -8D), 0D);
        active.set(firstEngine);
        assertEquals(17D, function.eval(context, 4D, -8D), 0D);
    }

    @Test(timeout = 15000)
    public void concurrentFunctionInitializationAndEvictionRetainSeedSamples() throws Exception {
        IrisData data = mock(IrisData.class);
        IrisExpressionFunction shared = function(data);
        ExecutorService workers = Executors.newFixedThreadPool(4);
        try {
            List<Future<Double>> results = new ArrayList<>(32);
            double[] expected = new double[32];
            for (int index = 0; index < 32; index++) {
                long seed = index;
                expected[index] = function(data).eval(new IrisExpressionFunction.FunctionContext(new RNG(seed)), 23.5D, -94.75D);
                results.add(workers.submit(() -> shared.eval(
                        new IrisExpressionFunction.FunctionContext(new RNG(seed)), 23.5D, -94.75D)));
            }
            for (int index = 0; index < results.size(); index++) {
                assertEquals(expected[index], results.get(index).get(5L, TimeUnit.SECONDS), 0D);
                assertEquals(expected[index], shared.eval(new IrisExpressionFunction.FunctionContext(new RNG(index)), 23.5D, -94.75D), 0D);
            }
        } finally {
            workers.shutdownNow();
        }
    }

    private static IrisExpression expression(IrisData data) {
        IrisExpression expression = new IrisExpression()
                .setExpression("field(x, z) + value")
                .setFunctions(new KList<>(function(data)))
                .setVariables(new KList<>(new IrisExpressionLoad().setName("value").setStyleValue(NoiseStyle.PERLIN.style())));
        expression.setLoader(data);
        return expression;
    }

    private static IrisExpressionFunction function(IrisData data) {
        return new IrisExpressionFunction().setName("field").setStyleValue(NoiseStyle.SIMPLEX.style()).setData(data);
    }

    private static Engine engine(double height) {
        IrisComplex complex = mock(IrisComplex.class);
        when(complex.getHeightStream()).thenReturn(ProceduralStream.ofDouble((x, z) -> height));
        Engine engine = mock(Engine.class);
        when(engine.getComplex()).thenReturn(complex);
        return engine;
    }
}
