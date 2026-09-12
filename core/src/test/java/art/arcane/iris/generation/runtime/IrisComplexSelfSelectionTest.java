package art.arcane.iris.generation.runtime;

import art.arcane.iris.generation.terrain.InferredType;
import art.arcane.iris.generation.biome.IrisBiome;
import art.arcane.iris.generation.noise.IrisGeneratorStyle;
import art.arcane.volmlib.util.noise.CNG;
import art.arcane.volmlib.util.collection.KList;
import art.arcane.volmlib.util.math.RNG;
import org.junit.Test;

import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.util.IdentityHashMap;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertSame;
import static org.mockito.Answers.CALLS_REAL_METHODS;
import static org.mockito.Mockito.mock;

public class IrisComplexSelfSelectionTest {
    @Test
    public void parentSelectionStopsAfterOneIdenticalNoiseSample() throws Exception {
        Fixture fixture = new Fixture();
        CountingBiome parent = new CountingBiome();
        fixture.select(parent, parent);

        assertSame(parent, fixture.implode(parent, 3));
        assertEquals(1, parent.noise.calls);
    }

    @Test
    public void cyclicChildrenRetainDepthLimit() throws Exception {
        Fixture fixture = new Fixture();
        CountingBiome first = new CountingBiome();
        CountingBiome second = new CountingBiome();
        fixture.select(first, second);
        fixture.select(second, first);

        assertSame(first, fixture.implode(first, 3));
        assertEquals(2, first.noise.calls);
        assertEquals(2, second.noise.calls);
    }

    @Test
    public void exhaustedDepthAndLeafDoNotSelectChildren() throws Exception {
        Fixture fixture = new Fixture();
        CountingBiome parent = new CountingBiome();
        fixture.select(parent, parent);
        assertSame(parent, fixture.implode(parent, -1));
        assertEquals(0, parent.noise.calls);

        parent.getChildren().clear();
        assertSame(parent, fixture.implode(parent, 3));
        assertEquals(0, parent.noise.calls);
    }

    @Test
    public void expressionsRetainRepeatedEvaluationIncludingFractureLayers() throws Exception {
        for (int fractureDepth = 0; fractureDepth < 3; fractureDepth++) {
            Fixture fixture = new Fixture();
            CountingBiome parent = new CountingBiome();
            IrisGeneratorStyle style = parent.getChildStyle();
            for (int level = 0; level < fractureDepth; level++) {
                IrisGeneratorStyle fracture = new IrisGeneratorStyle();
                style.setFracture(fracture);
                style = fracture;
            }
            style.setExpression("contextual-expression");
            fixture.select(parent, parent);

            assertSame(parent, fixture.implode(parent, 3));
            assertEquals(4, parent.noise.calls);
        }
    }

    private static final class Fixture {
        private final IrisComplex complex = mock(IrisComplex.class, CALLS_REAL_METHODS);
        private final IdentityHashMap<IrisBiome, IrisComplex.ChildSelectionPlan> plans = new IdentityHashMap<>();
        private final Method implode;

        private Fixture() throws Exception {
            complex.setRng(new RNG(7331L));
            Field field = IrisComplex.class.getDeclaredField("childSelectionPlans");
            field.setAccessible(true);
            field.set(complex, plans);
            implode = IrisComplex.class.getDeclaredMethod("implode", IrisBiome.class, Double.class, Double.class, int.class);
            implode.setAccessible(true);
        }

        private void select(IrisBiome parent, IrisBiome selected) {
            IrisBiome alternative = new IrisBiome();
            plans.put(parent, IrisComplex.ChildSelectionPlan.create(new KList<>(alternative, selected)));
        }

        private IrisBiome implode(IrisBiome parent, int depth) throws Exception {
            return (IrisBiome) implode.invoke(complex, parent, -17.25D, 8.5D, depth);
        }
    }

    private static final class CountingBiome extends IrisBiome {
        private final CountingNoise noise = new CountingNoise();

        private CountingBiome() {
            setChildren(new KList<>("child"));
            setInferredType(InferredType.LAND);
        }

        @Override
        public CNG getChildrenGenerator(RNG rng, int signature, double scale) {
            return noise;
        }
    }

    private static final class CountingNoise extends CNG {
        private int calls;

        private CountingNoise() {
            super(new RNG(7331L));
        }

        @Override
        public int fit2D(int minimum, int maximum, double x, double z) {
            calls++;
            return 0;
        }
    }
}
