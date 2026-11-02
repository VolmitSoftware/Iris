package art.arcane.iris.api.terrain;

import org.junit.Test;

import java.util.concurrent.atomic.AtomicReference;

import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertSame;
import static org.junit.Assert.assertTrue;
import static org.junit.Assert.fail;

public class IrisColumnSampleTest {
    @Test
    public void unavailableFieldsHaveUnambiguousSentinels() {
        IrisColumnSample sample = sample(
                IrisColumnSample.UNAVAILABLE_HEIGHT,
                IrisColumnSample.UNAVAILABLE_HEIGHT,
                IrisSurfaceKind.UNKNOWN,
                null,
                IrisRiverState.NONE,
                IrisColumnSample.UNAVAILABLE_RIVER_DISTANCE,
                IrisColumnSample.UNAVAILABLE_RIVER_FLOW,
                IrisColumnSample.UNAVAILABLE_HEIGHT
        );

        assertFalse(sample.hasSurfaceHeight());
        assertFalse(sample.hasNaturalHeight());
        assertFalse(sample.hasSurfaceKind());
        assertFalse(sample.hasBiomeKey());
        assertFalse(sample.hasRiverState());
        assertFalse(sample.hasRiverDistance());
        assertFalse(sample.hasRiverFlow());
        assertFalse(sample.hasRiverWaterSurfaceY());
    }

    @Test
    public void negativeWorldHeightsRemainAvailableValues() {
        IrisColumnSample sample = sample(
                -1,
                -64,
                IrisSurfaceKind.DRY_CHANNEL,
                "test:river",
                IrisRiverState.DRY,
                0D,
                0,
                -1
        );

        assertTrue(sample.hasSurfaceHeight());
        assertTrue(sample.hasNaturalHeight());
        assertTrue(sample.hasSurfaceKind());
        assertTrue(sample.hasBiomeKey());
        assertTrue(sample.hasRiverState());
        assertTrue(sample.hasRiverDistance());
        assertTrue(sample.hasRiverFlow());
        assertTrue(sample.hasRiverWaterSurfaceY());
    }

    @Test
    public void blankBiomeKeysNormalizeToUnavailable() {
        IrisColumnSample sample = sample(
                64,
                65,
                IrisSurfaceKind.LAND,
                "  ",
                IrisRiverState.NONE,
                IrisColumnSample.UNAVAILABLE_RIVER_DISTANCE,
                IrisColumnSample.UNAVAILABLE_RIVER_FLOW,
                IrisColumnSample.UNAVAILABLE_HEIGHT
        );

        assertNull(sample.biomeKey());
        assertFalse(sample.hasBiomeKey());
    }

    @Test
    public void theSinkReceivesTheTypedSample() {
        IrisColumnSample sample = sample(
                64,
                65,
                IrisSurfaceKind.RIVER,
                "test:river",
                IrisRiverState.WET,
                0.5D,
                3,
                67
        );
        AtomicReference<IrisColumnSample> received = new AtomicReference<>();
        IrisColumnSink sink = received::set;

        sink.accept(sample);

        assertSame(sample, received.get());
    }

    @Test
    public void invalidHydrologyValuesAreRejected() {
        assertInvalid(Double.POSITIVE_INFINITY, 1);
        assertInvalid(-0.1D, 1);
        assertInvalid(0D, -2);
    }

    private static void assertInvalid(double distance, int flow) {
        try {
            sample(
                    64,
                    65,
                    IrisSurfaceKind.RIVER,
                    "test:river",
                    IrisRiverState.WET,
                    distance,
                    flow,
                    67
            );
            fail("Expected invalid hydrology values to be rejected");
        } catch (IllegalArgumentException expected) {
            assertTrue(expected.getMessage().startsWith("river"));
        }
    }

    private static IrisColumnSample sample(
            int surfaceHeight,
            int naturalHeight,
            IrisSurfaceKind surfaceKind,
            String biomeKey,
            IrisRiverState riverState,
            double riverDistance,
            int riverFlow,
            int riverWaterSurfaceY
    ) {
        return new IrisColumnSample(
                12,
                -7,
                surfaceHeight,
                naturalHeight,
                surfaceKind,
                biomeKey,
                riverState,
                riverDistance,
                riverFlow,
                riverWaterSurfaceY,
                false
        );
    }
}
