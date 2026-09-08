package art.arcane.iris.engine.hydrology;

import org.junit.Test;

import java.util.ArrayList;
import java.util.List;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertSame;
import static org.junit.Assert.assertTrue;

public class HydrologyColumnSampleAccessorTest {

    @Test
    public void theNullableSurfaceAccessorsAgreeWithTheOptionalOnes() {
        HydrologyColumnSample sample = sample();

        assertSame(sample.primarySurfaceLayer().orElse(null), sample.primarySurfaceLayerOrNull());
        assertSame(sample.primarySurfaceFluidLayer().orElse(null), sample.primarySurfaceFluidLayerOrNull());
        assertNotNull(sample.primarySurfaceLayerOrNull());
        assertNotNull(sample.primarySurfaceFluidLayerOrNull());
    }

    @Test
    public void aColumnWithoutSurfaceLayersReportsNothingOnBothAccessors() {
        HydrologyColumnSample empty = new HydrologyColumnSample(0, 0, 70, 62, false, "overworld/plains", List.of());

        assertNull(empty.primarySurfaceLayerOrNull());
        assertNull(empty.primarySurfaceFluidLayerOrNull());
        assertTrue(empty.primarySurfaceLayer().isEmpty());
        assertTrue(empty.primarySurfaceFluidLayer().isEmpty());
        assertEquals(70, empty.terrainHeight());
    }

    @Test
    public void terrainHeightStillFollowsThePrimarySurfaceLayer() {
        HydrologyColumnSample sample = sample();

        assertEquals(sample.primarySurfaceLayerOrNull().bedY(), sample.terrainHeight());
    }

    private static HydrologyColumnSample sample() {
        List<HydrologyColumnLayer> layers = new ArrayList<>();
        layers.add(layer(1L, HydrologyFeatureType.RIFFLE, 66, 68, true, false, true, true));
        layers.add(layer(2L, HydrologyFeatureType.SURFACE_POOL, 67, 68, false, true, false, false));
        layers.add(layer(3L, HydrologyFeatureType.MOUTH, 65, 67, true, false, true, true));
        return new HydrologyColumnSample(4, 9, 74, 62, false, "overworld/river", layers);
    }

    private static HydrologyColumnLayer layer(
            long id,
            HydrologyFeatureType type,
            int bedY,
            int fluidHeadY,
            boolean channel,
            boolean shore,
            boolean connectedFluid,
            boolean fluidOwned
    ) {
        return new HydrologyColumnLayer(
                new HydrologyFeatureRef(id, type, id, id, 4, fluidHeadY, 9, 1, 0, false),
                bedY,
                fluidHeadY,
                fluidHeadY,
                channel,
                shore,
                false,
                connectedFluid,
                false,
                false,
                true,
                fluidOwned,
                false,
                "overworld/river",
                "overworld/river-surface",
                "overworld/river-mouth",
                "overworld/river-shore",
                "overworld/river-bank",
                "overworld/river-cave");
    }
}
