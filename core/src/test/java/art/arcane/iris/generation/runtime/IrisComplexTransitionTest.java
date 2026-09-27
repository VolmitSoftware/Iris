package art.arcane.iris.generation.runtime;

import art.arcane.iris.generation.hydrology.HydrologyColumnLayer;
import art.arcane.iris.generation.hydrology.HydrologyColumnSample;
import art.arcane.iris.generation.hydrology.HydrologyFeatureRef;
import art.arcane.iris.generation.hydrology.HydrologyFeatureType;
import art.arcane.iris.world.history.TransitionDisplacementField;
import art.arcane.iris.generation.hydrology.runtime.IrisHydrologyRuntime;
import art.arcane.iris.world.history.TransitionGenerationPlan;
import org.junit.Test;

import java.util.List;
import java.util.Optional;
import java.lang.reflect.Field;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;
import static org.mockito.Answers.CALLS_REAL_METHODS;
import static org.junit.Assert.assertSame;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

public class IrisComplexTransitionTest {
    @Test
    public void preservesHydrologyObjectsAtFullWeight() {
        HydrologyColumnSample hydrology = hydrologySample();

        assertSame(hydrology, IrisComplex.taperHydrologySample(hydrology, 1D));
    }

    @Test
    public void tapersRiverDepthWithoutRaisingItsFluidHead() {
        HydrologyColumnSample tapered = IrisComplex.taperHydrologySample(hydrologySample(), 0.5D);
        HydrologyColumnLayer layer = tapered.layers().getFirst();

        assertEquals(73, layer.bedY());
        assertEquals(70, layer.fluidHeadY());
        assertEquals(70, layer.ceilingY());
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
        assertEquals(70, tapered.layers().getLast().fluidHeadY());
        assertEquals(70, tapered.layers().getLast().ceilingY());
    }

    @Test
    public void loweredSurfaceWaterPreservesContainedOceanCaveAndPlannerDatum() {
        assertOceanWaterTransition(40, 63);
    }

    @Test
    public void raisedSurfaceWaterKeepsContainedOceanCaveAndRaisesPlannerDatum() {
        assertOceanWaterTransition(70, 70);
    }

    @Test
    public void missingDisplacementPreservesHydrologyAtFullWeight() {
        HydrologyColumnSample sample = hydrologySample();
        assertSame(sample, IrisComplex.transitionHydrologySample(sample, 1D, null));
    }

    @Test
    public void tinyRiverWeightsExcavateGraduallyUntilReachingUnraisedWater() {
        HydrologyColumnSample sample = hydrologySample();
        int previousBed = 80;
        for (int step = 1; step <= 100; step++) {
            double weight = step / 100D;
            HydrologyColumnSample tapered = IrisComplex.taperHydrologySample(sample, weight);
            HydrologyColumnLayer layer = tapered.layers().getFirst();
            assertEquals(70, layer.fluidHeadY());
            assertTrue(layer.bedY() <= previousBed);
            assertTrue(previousBed - layer.bedY() <= 1);
            assertEquals(layer.bedY(), tapered.terrainHeight());
            assertEquals(layer.bedY() <= layer.fluidHeadY(), layer.channel());
            assertEquals(layer.channel(), layer.fluidOwned());
            if (layer.bedY() > layer.fluidHeadY()) {
                assertFalse(layer.connectedFluid());
                assertFalse(layer.fallingFluid());
                assertFalse(layer.receivingPool());
            }
            previousBed = layer.bedY();
        }
        assertEquals(66, previousBed);
    }

    @Test
    public void exactSeamSuppressesSurfaceCutsAndPreservesCaves() {
        HydrologyColumnLayer cave = hydrologySample(HydrologyFeatureType.UNDERGROUND_POOL).layers().getFirst();
        HydrologyColumnLayer surface = hydrologySample().layers().getFirst();
        HydrologyColumnSample sample = new HydrologyColumnSample(4, 6, 80, 63, false, "parent", List.of(surface, cave));

        HydrologyColumnSample transitioned = IrisComplex.taperHydrologySample(sample, 0D);

        assertEquals(List.of(cave), transitioned.layers());
        assertEquals(80, transitioned.terrainHeight());
    }

    @Test
    public void historicalRiverHeadRestoresFluidAboveTaperedBed() {
        HydrologyColumnSample sample = hydrologySample();
        TransitionDisplacementField displacement = mock(TransitionDisplacementField.class);
        when(displacement.sample(4, 6)).thenReturn(
                new TransitionDisplacementField.Sample(0D, 8D, 0.25D, 1D, 80D, 80D, "minecraft:water"));
        when(displacement.fluidHeight(4, 6, 63D)).thenReturn(68D);
        when(displacement.fluidHeight(4, 6, 70D)).thenReturn(77D);

        HydrologyColumnSample transitioned = IrisComplex.transitionHydrologySample(sample, 0.35D, displacement);
        HydrologyColumnLayer river = transitioned.primarySurfaceFluidLayerOrNull();

        assertEquals(75, transitioned.terrainHeight());
        assertEquals(75, river.bedY());
        assertEquals(77, river.fluidHeadY());
        assertTrue(river.channel());
        assertTrue(river.fluidOwned());
        assertTrue(river.connectedFluid());
        assertTrue(transitioned.surfacePublicationCellAt(76).isPresent());
    }

    @Test
    public void risingSeaDoesNotKeepElevatedRiverWaterOnSeaLevelGround() {
        HydrologyColumnSample sample = hydrologySample();
        TransitionDisplacementField displacement = mock(TransitionDisplacementField.class);
        when(displacement.sample(4, 6)).thenReturn(
                new TransitionDisplacementField.Sample(0D, 8D, 0.25D, 1D, 90D, 80D, "minecraft:water"));
        when(displacement.fluidHeight(4, 6, 63D)).thenReturn(80D);
        when(displacement.fluidHeight(4, 6, 70D)).thenReturn(85D);

        HydrologyColumnSample transitioned = IrisComplex.transitionHydrologySample(sample, 1D, displacement);

        assertEquals(80, transitioned.seaLevel());
        assertTrue(transitioned.layers().isEmpty());
    }

    @Test
    public void zeroWeightSeamKeepsCavesAndDoesNotClaimColdHydrologyIsPlanned() throws Exception {
        IrisComplex complex = mock(IrisComplex.class, CALLS_REAL_METHODS);
        IrisHydrologyRuntime runtime = mock(IrisHydrologyRuntime.class);
        complex.setHydrologyRuntime(runtime);
        Field plan = IrisComplex.class.getDeclaredField("transitionGenerationPlan");
        plan.setAccessible(true);
        plan.set(complex, mock(TransitionGenerationPlan.class));
        TransitionDisplacementField displacement = mock(TransitionDisplacementField.class);
        Field field = IrisComplex.class.getDeclaredField("transitionDisplacement");
        field.setAccessible(true);
        field.set(complex, displacement);
        when(displacement.sample(4, 6)).thenReturn(
                new TransitionDisplacementField.Sample(0D, 1D, 0D, 0D, 0D, 80D, null));
        when(displacement.fluidHeight(4, 6, 63D)).thenReturn(63D);
        HydrologyColumnSample sample = hydrologySample(HydrologyFeatureType.UNDERGROUND_POOL);
        when(runtime.sample(4D, 6D)).thenReturn(Optional.of(sample));

        assertFalse(complex.isHydrologyPlanned(4, 6));
        assertSame(sample.layers().getFirst(), complex.sampleHydrologyColumn(4, 6).layers().getFirst());
    }

    private static void assertOceanWaterTransition(int surfaceHead, int plannerSeaLevel) {
        HydrologyColumnLayer cave = oceanLayer(HydrologyFeatureType.UNDERGROUND_POOL, 55, false);
        HydrologyColumnLayer apron = oceanLayer(HydrologyFeatureType.MOUTH, 63, true);
        HydrologyColumnSample sample = new HydrologyColumnSample(4, 6, 30, 63, true, "parent", List.of(cave, apron));
        TransitionDisplacementField displacement = mock(TransitionDisplacementField.class);
        when(displacement.sample(4, 6)).thenReturn(
                new TransitionDisplacementField.Sample(0D, 8D, 0.25D, 1D, surfaceHead, 30D, "minecraft:water"));
        when(displacement.fluidHeight(4, 6, 63D)).thenReturn((double) surfaceHead);

        HydrologyColumnSample transitioned = IrisComplex.transitionHydrologySample(sample, 1D, displacement);

        assertEquals(plannerSeaLevel, transitioned.seaLevel());
        assertSame(cave, transitioned.layers().stream()
                .filter(layer -> layer.feature().type() == HydrologyFeatureType.UNDERGROUND_POOL).findFirst().orElseThrow());
        HydrologyColumnLayer surface = transitioned.layers().stream()
                .filter(HydrologyColumnLayer::oceanApron).findFirst().orElseThrow();
        assertEquals(surfaceHead, surface.fluidHeadY());
        assertEquals(surfaceHead, surface.ceilingY());
    }

    private static HydrologyColumnLayer oceanLayer(HydrologyFeatureType type, int head, boolean apron) {
        HydrologyFeatureRef feature = new HydrologyFeatureRef(1L, type, 2L, 3L, 4, head, 6, 1, 0, true);
        return new HydrologyColumnLayer(feature, 20, head, head, true, false, false, true,
                false, false, false, false, apron, "river", "surface", "mouth", "shore", "bank", "cave");
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
