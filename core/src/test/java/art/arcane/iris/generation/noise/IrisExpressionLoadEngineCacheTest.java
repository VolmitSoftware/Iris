package art.arcane.iris.generation.noise;

import art.arcane.iris.generation.runtime.Engine;
import art.arcane.iris.generation.runtime.IrisComplex;
import art.arcane.iris.generation.runtime.IrisEngineStreamType;
import art.arcane.iris.generation.runtime.IrisEngineValueType;
import art.arcane.iris.generation.runtime.PackRuntime;
import art.arcane.iris.pack.loading.IrisData;
import art.arcane.volmlib.util.collection.KList;
import art.arcane.volmlib.util.math.RNG;
import art.arcane.volmlib.util.stream.ProceduralStream;
import org.junit.Test;

import static org.junit.Assert.assertEquals;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

public class IrisExpressionLoadEngineCacheTest {
    @Test
    public void engineValuesBelongToTheEngineRuntime() {
        Engine first = mock(Engine.class);
        Engine second = mock(Engine.class);
        when(first.getHeight()).thenReturn(128);
        when(second.getHeight()).thenReturn(320);
        IrisExpression expression = expression(new IrisExpressionLoad()
                .setName("height")
                .setEngineValue(IrisEngineValueType.ENGINE_HEIGHT));
        IrisData data = mock(IrisData.class);

        assertEquals(128D, PackRuntime.forEngine(first, data).expression(expression).evaluate(new RNG(1L), 0D, 0D), 0D);
        assertEquals(320D, PackRuntime.forEngine(second, data).expression(expression).evaluate(new RNG(1L), 0D, 0D), 0D);
    }

    @Test
    public void engineStreamsBelongToTheEngineRuntime() {
        IrisExpression expression = expression(new IrisExpressionLoad()
                .setName("height")
                .setEngineStreamValue(IrisEngineStreamType.HEIGHT));
        IrisData data = mock(IrisData.class);

        assertEquals(12D, PackRuntime.forEngine(streamEngine(12D), data).expression(expression)
                .evaluate(new RNG(1L), 4D, 8D), 0D);
        assertEquals(27D, PackRuntime.forEngine(streamEngine(27D), data).expression(expression)
                .evaluate(new RNG(1L), 4D, 8D), 0D);
    }

    @Test(expected = IllegalStateException.class)
    public void engineBackedValueFailsWithoutAnEngine() {
        IrisExpression expression = expression(new IrisExpressionLoad()
                .setName("height")
                .setEngineValue(IrisEngineValueType.ENGINE_HEIGHT));

        PackRuntime.standalone(mock(IrisData.class)).expression(expression).evaluate(new RNG(1L), 0D, 0D);
    }

    private static IrisExpression expression(IrisExpressionLoad variable) {
        return new IrisExpression().setExpression("height").setVariables(new KList<>(variable));
    }

    private Engine streamEngine(double value) {
        ProceduralStream<Double> stream = ProceduralStream.ofDouble((x, z) -> value);
        IrisComplex complex = mock(IrisComplex.class);
        when(complex.getHeightStream()).thenReturn(stream);
        Engine engine = mock(Engine.class);
        when(engine.getComplex()).thenReturn(complex);
        return engine;
    }
}
