package art.arcane.iris.util.project.interpolation;

import org.junit.Test;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

public class IrisInterpolationRangeScaleTest {
    private static final double TOLERANCE = 1e-12;

    @Test
    public void degenerateSourceRangeKeepsIrisArithmetic() {
        assertTrue(Double.isNaN(IrisInterpolation.rangeScale(0, 1, 5, 5, 5)));
        assertTrue(Double.isInfinite(IrisInterpolation.rangeScale(0, 1, 5, 5, 6)));
    }

    @Test
    public void rangeScaleMapsAcrossTheRange() {
        assertEquals(0.25, IrisInterpolation.rangeScale(0, 1, 0, 8, 2), TOLERANCE);
    }

    @Test
    public void inheritedInterpolationStillBlends() {
        assertEquals(2.5, IrisInterpolation.lerp(2, 3, 0.5), TOLERANCE);
        assertEquals(1.5, IrisInterpolation.blerp(1, 2, 1, 2, 0.5, 0.5), TOLERANCE);
        assertEquals(2.5, IrisInterpolation.hermite(1, 2, 3, 4, 0.5, 0, 0), TOLERANCE);
    }
}
