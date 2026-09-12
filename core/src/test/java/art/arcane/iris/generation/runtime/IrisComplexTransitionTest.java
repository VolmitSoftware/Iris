package art.arcane.iris.generation.runtime;

import art.arcane.iris.generation.hydrology.HydrologyColumnLayer;
import art.arcane.iris.generation.hydrology.HydrologyColumnSample;
import art.arcane.iris.generation.hydrology.HydrologyFeatureRef;
import art.arcane.iris.generation.hydrology.HydrologyFeatureType;
import org.junit.Test;

import java.util.List;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertSame;

public class IrisComplexTransitionTest {
    @Test
    public void preservesHydrologyObjectsAtFullWeight() {
        HydrologyColumnSample hydrology = hydrologySample();

        assertSame(hydrology, IrisComplex.taperHydrologySample(hydrology, 1D));
    }

    @Test
    public void tapersHydrologyGeometryTowardNaturalTerrain() {
        HydrologyColumnSample tapered = IrisComplex.taperHydrologySample(hydrologySample(), 0.5D);
        HydrologyColumnLayer layer = tapered.layers().getFirst();

        assertEquals(73, layer.bedY());
        assertEquals(75, layer.fluidHeadY());
        assertEquals(75, layer.ceilingY());
        assertEquals(73, tapered.terrainHeight());
        assertEquals("river", layer.profileKey());
    }

    @Test
    public void preservesCaveContainmentCoordinatesThroughoutTransition() {
        for (HydrologyFeatureType type : HydrologyFeatureType.values()) {
            if (!type.isUnderground() && !type.isDeepFluid()) {
                continue;
            }
            HydrologyColumnSample sample = hydrologySample(type);
            for (double weight : new double[]{0D, 0.01D, 0.25D, 0.5D, 0.99D, 1D}) {
                HydrologyColumnSample tapered = IrisComplex.taperHydrologySample(sample, weight);
                assertSame(type + " at " + weight, sample.layers().getFirst(), tapered.layers().getFirst());
            }
        }
    }

    @Test
    public void tapersSurfaceLayerWithoutMovingOverlappingCave() {
        HydrologyColumnLayer cave = hydrologySample(HydrologyFeatureType.UNDERGROUND_POOL).layers().getFirst();
        HydrologyColumnLayer surface = hydrologySample().layers().getFirst();
        HydrologyColumnSample sample = new HydrologyColumnSample(4, 6, 80, 63, false, "parent", List.of(surface, cave));

        HydrologyColumnSample tapered = IrisComplex.taperHydrologySample(sample, 0.5D);

        assertSame(cave, tapered.layers().getFirst());
        assertEquals(73, tapered.layers().getLast().bedY());
        assertEquals(75, tapered.layers().getLast().fluidHeadY());
        assertEquals(75, tapered.layers().getLast().ceilingY());
    }

    private static HydrologyColumnSample hydrologySample() {
        return hydrologySample(HydrologyFeatureType.SURFACE_POOL);
    }

    private static HydrologyColumnSample hydrologySample(HydrologyFeatureType type) {
        HydrologyFeatureRef feature = new HydrologyFeatureRef(
                1L,
                type,
                2L,
                3L,
                4,
                70,
                6,
                1,
                0,
                true
        );
        HydrologyColumnLayer layer = new HydrologyColumnLayer(
                feature,
                66,
                70,
                70,
                true,
                false,
                false,
                true,
                false,
                false,
                true,
                true,
                false,
                "river",
                "surface",
                "mouth",
                "shore",
                "bank",
                "cave"
        );
        return new HydrologyColumnSample(4, 6, 80, 63, false, "parent", List.of(layer));
    }
}
