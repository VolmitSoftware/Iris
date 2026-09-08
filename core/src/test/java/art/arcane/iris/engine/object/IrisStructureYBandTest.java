package art.arcane.iris.engine.object;

import art.arcane.iris.engine.object.annotations.MaxNumber;
import art.arcane.iris.engine.object.annotations.MinNumber;
import org.junit.Test;
import org.junit.experimental.runners.Enclosed;
import org.junit.runner.RunWith;
import org.junit.runners.Parameterized;
import org.junit.runners.Parameterized.Parameters;

import java.lang.reflect.Field;
import java.util.Collection;
import java.util.List;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;

@RunWith(Enclosed.class)
public class IrisStructureYBandTest {
    @RunWith(Parameterized.class)
    public static class Resolution {
        @Parameters(name = "min={0} max={1}")
        public static Collection<Object[]> bands() {
            return List.of(
                    new Object[]{-120, -20, -120, -20},
                    new Object[]{-20, -120, -120, -20},
                    new Object[]{-64, -64, -64, -64}
            );
        }

        private final int min;
        private final int max;
        private final int expectedMin;
        private final int expectedMax;

        public Resolution(int min, int max, int expectedMin, int expectedMax) {
            this.min = min;
            this.max = max;
            this.expectedMin = expectedMin;
            this.expectedMax = expectedMax;
        }

        @Test
        public void authoredBoundsResolveInOrder() {
            IrisStructureYBand band = new IrisStructureYBand().setMin(min).setMax(max);

            assertEquals(expectedMin, band.resolvedMin());
            assertEquals(expectedMax, band.resolvedMax());
        }
    }

    public static class SchemaBounds {
        @Test
        public void numericSchemaBoundsCoverTheWholeBuildRange() throws NoSuchFieldException {
            assertBounds("min");
            assertBounds("max");
        }

        private void assertBounds(String fieldName) throws NoSuchFieldException {
            Field field = IrisStructureYBand.class.getDeclaredField(fieldName);
            MinNumber minimum = field.getAnnotation(MinNumber.class);
            MaxNumber maximum = field.getAnnotation(MaxNumber.class);

            assertNotNull(minimum);
            assertNotNull(maximum);
            assertEquals(-4064D, minimum.value(), 0D);
            assertEquals(4064D, maximum.value(), 0D);
        }
    }
}
