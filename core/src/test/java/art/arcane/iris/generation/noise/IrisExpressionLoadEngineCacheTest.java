package art.arcane.iris.generation.noise;

import art.arcane.iris.generation.runtime.IrisEngineStreamType;
import art.arcane.iris.generation.runtime.IrisEngineValueType;

import art.arcane.iris.pack.loading.IrisData;
import art.arcane.iris.generation.runtime.IrisComplex;
import art.arcane.iris.generation.runtime.Engine;
import art.arcane.volmlib.util.stream.ProceduralStream;
import art.arcane.volmlib.util.math.RNG;
import org.junit.Test;

import java.util.concurrent.atomic.AtomicReference;

import static org.junit.Assert.assertEquals;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

public class IrisExpressionLoadEngineCacheTest {
    @Test
    public void engineValuesAreCachedPerEngineIdentity() {
        Engine first = mock(Engine.class);
        Engine second = mock(Engine.class);
        when(first.getHeight()).thenReturn(128);
        when(second.getHeight()).thenReturn(320);
        AtomicReference<Engine> active = new AtomicReference<>(first);
        IrisData data = data(active);
        IrisExpressionLoad load = new IrisExpressionLoad()
                .setName("height")
                .setEngineValue(IrisEngineValueType.ENGINE_HEIGHT);

        assertEquals(128D, load.getValue(new RNG(1L), data, 0D, 0D), 0D);
        active.set(second);
        assertEquals(320D, load.getValue(new RNG(1L), data, 0D, 0D), 0D);
    }

    @Test
    public void engineStreamsAreCachedPerEngineIdentity() {
        Engine first = streamEngine(12D);
        Engine second = streamEngine(27D);
        AtomicReference<Engine> active = new AtomicReference<>(first);
        IrisData data = data(active);
        IrisExpressionLoad load = new IrisExpressionLoad()
                .setName("height")
                .setEngineStreamValue(IrisEngineStreamType.HEIGHT);

        assertEquals(12D, load.getValue(new RNG(1L), data, 4D, 8D), 0D);
        active.set(second);
        assertEquals(27D, load.getValue(new RNG(1L), data, 4D, 8D), 0D);
    }

    @Test(expected = IllegalStateException.class)
    public void engineBackedValueFailsWithoutUnambiguousEngine() {
        IrisData data = mock(IrisData.class);
        IrisExpressionLoad load = new IrisExpressionLoad()
                .setName("height")
                .setEngineValue(IrisEngineValueType.ENGINE_HEIGHT);

        load.getValue(new RNG(1L), data, 0D, 0D);
    }

    private IrisData data(AtomicReference<Engine> active) {
        IrisData data = mock(IrisData.class);
        when(data.getEngine()).thenAnswer(ignored -> active.get());
        return data;
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
