package art.arcane.iris.generation.runtime;

import art.arcane.iris.generation.noise.IrisGenerator;
import art.arcane.iris.generation.noise.IrisGeneratorStyle;
import art.arcane.iris.generation.noise.IrisNoiseGenerator;
import art.arcane.iris.generation.noise.IrisShapedGeneratorStyle;
import art.arcane.iris.generation.noise.NoiseStyle;
import art.arcane.iris.generation.terrain.IrisDimension;
import art.arcane.volmlib.util.collection.KList;
import org.junit.Test;

import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.util.Map;

import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;
import static org.mockito.Mockito.CALLS_REAL_METHODS;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

public class IrisComplexHeightBatchTest {
    @Test
    public void standardSurfaceGraphsCanBatchAndCoarseSamplingKeepsScalarPath() throws Exception {
        IrisDimension dimension = new IrisDimension();
        IrisGenerator generator = new IrisGenerator().setSurfaceDetail(0.5D)
                .setComposite(new KList<>(new IrisNoiseGenerator().setStyle(NoiseStyle.FRACTAL_SMOKE.style())
                        .setFracture(new KList<>(new IrisNoiseGenerator().setStyle(NoiseStyle.IRIS.style())))));
        IrisComplex complex = complex(dimension, generator);
        assertTrue(eligible(complex));
        for (int step : new int[]{2, 4, 8}) {
            dimension.setTerrainSamplingStep(step);
            assertFalse(eligible(complex));
        }
        dimension.setTerrainSamplingStep(1);
        assertTrue(eligible(complex));
        generator.getComposite().getFirst().setStyle(new IrisGeneratorStyle().setExpression("height"));
        assertFalse(eligible(complex));
    }

    @Test
    public void statefulBiomeAndOverlaySourcesKeepScalarPath() throws Exception {
        IrisDimension dimension = new IrisDimension();
        IrisComplex complex = complex(dimension, new IrisGenerator()
                .setComposite(new KList<>(new IrisNoiseGenerator().setStyle(NoiseStyle.SIMPLEX.style()))));
        set(complex, "reuseNaturalBaseBiome", false);
        assertFalse(eligible(complex));
        set(complex, "reuseNaturalBaseBiome", true);
        dimension.setOverlayNoise(new KList<>(new IrisShapedGeneratorStyle()
                .setGenerator(new IrisGeneratorStyle() {})));
        assertFalse(eligible(complex));
        dimension.getOverlayNoise().getFirst().setGenerator(NoiseStyle.SIMPLEX.style());
        assertTrue(eligible(complex));
    }

    private static IrisComplex complex(IrisDimension dimension, IrisGenerator generator) throws Exception {
        Engine engine = mock(Engine.class);
        when(engine.getDimension()).thenReturn(dimension);
        IrisComplex complex = mock(IrisComplex.class, CALLS_REAL_METHODS);
        set(complex, "terrainEngine", engine);
        set(complex, "biomeBuffetGenerators", Map.of());
        set(complex, "reuseNaturalBaseBiome", true);
        set(complex, "frozenGenerators", new IrisGenerator[][]{{generator}});
        return complex;
    }

    private static boolean eligible(IrisComplex complex) throws Exception {
        Method method = IrisComplex.class.getDeclaredMethod("supportsHeightBatch");
        method.setAccessible(true);
        return (boolean) method.invoke(complex);
    }

    private static void set(IrisComplex complex, String name, Object value) throws Exception {
        Field field = IrisComplex.class.getDeclaredField(name);
        field.setAccessible(true);
        field.set(complex, value);
    }
}
