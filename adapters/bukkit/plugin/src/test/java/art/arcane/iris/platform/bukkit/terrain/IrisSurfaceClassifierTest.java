package art.arcane.iris.platform.bukkit.terrain;

import art.arcane.iris.api.terrain.IrisSurfaceKind;
import art.arcane.iris.generation.hydrology.HydrologyColumnLayer;
import art.arcane.iris.generation.hydrology.HydrologyColumnSample;
import art.arcane.iris.generation.hydrology.HydrologyFeatureRef;
import art.arcane.iris.generation.hydrology.HydrologyFeatureType;
import art.arcane.iris.generation.terrain.InferredType;
import org.junit.Test;

import java.util.List;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

public class IrisSurfaceClassifierTest {
    private static final int FLUID = 127;

    @Test
    public void columnAtOrBelowWorldMinimumIsVoid() {
        assertEquals(IrisSurfaceKind.VOID, IrisSurfaceClassifier.classify(0, FLUID, InferredType.LAND));
        assertEquals(IrisSurfaceKind.VOID, IrisSurfaceClassifier.classify(-8, FLUID, InferredType.SEA));
    }

    @Test
    public void oceanIsExactlyTheEngineUnderwaterPredicate() {
        assertEquals(IrisSurfaceKind.OCEAN, IrisSurfaceClassifier.classify(FLUID, FLUID, InferredType.LAND));
        assertEquals(IrisSurfaceKind.OCEAN, IrisSurfaceClassifier.classify(FLUID - 1, FLUID, InferredType.LAND));
        assertEquals(IrisSurfaceKind.LAND, IrisSurfaceClassifier.classify(FLUID + 1, FLUID, InferredType.LAND));
    }

    @Test
    public void shoreOnlyAppliesAboveTheFluidLine() {
        assertEquals(IrisSurfaceKind.SHORE, IrisSurfaceClassifier.classify(FLUID + 1, FLUID, InferredType.SHORE));
        assertEquals(IrisSurfaceKind.OCEAN, IrisSurfaceClassifier.classify(FLUID, FLUID, InferredType.SHORE));
    }

    @Test
    public void caveAndAbsentTypesFallBackToLandAboveWater() {
        assertEquals(IrisSurfaceKind.LAND, IrisSurfaceClassifier.classify(FLUID + 10, FLUID, InferredType.CAVE));
        assertEquals(IrisSurfaceKind.LAND, IrisSurfaceClassifier.classify(FLUID + 10, FLUID, InferredType.SEA));
        assertEquals(IrisSurfaceKind.LAND, IrisSurfaceClassifier.classify(FLUID + 10, FLUID, null));
    }

    @Test
    public void biomeIsOnlyRequiredWhenTheAnswerCanDependOnIt() {
        assertFalse(IrisSurfaceClassifier.requiresSurfaceBiome(0, FLUID));
        assertFalse(IrisSurfaceClassifier.requiresSurfaceBiome(FLUID, FLUID));
        assertTrue(IrisSurfaceClassifier.requiresSurfaceBiome(FLUID + 1, FLUID));
    }

    @Test
    public void whenBiomeIsNotRequiredEveryInferredTypeYieldsTheSameKind() {
        for (int surface = -4; surface <= FLUID; surface++) {
            if (IrisSurfaceClassifier.requiresSurfaceBiome(surface, FLUID)) {
                continue;
            }

            IrisSurfaceKind expected = IrisSurfaceClassifier.classify(surface, FLUID, null);
            for (InferredType inferredType : InferredType.values()) {
                assertEquals(
                        "surface=" + surface + " type=" + inferredType,
                        expected,
                        IrisSurfaceClassifier.classify(surface, FLUID, inferredType)
                );
            }
        }
    }

    @Test
    public void acceptedSurfaceRolesOverrideGenericOceanAndLandKinds() {
        assertEquals(IrisSurfaceKind.RIVER, IrisSurfaceClassifier.classify(
                60,
                63,
                InferredType.SEA,
                hydrology(HydrologyFeatureType.SURFACE_POOL, true, false, true)
        ));
        assertEquals(IrisSurfaceKind.RIVER, IrisSurfaceClassifier.classify(
                60,
                63,
                InferredType.SEA,
                hydrology(HydrologyFeatureType.MOUTH, true, false, true)
        ));
        assertEquals(IrisSurfaceKind.RIVER_SHORE, IrisSurfaceClassifier.classify(
                64,
                63,
                InferredType.SHORE,
                hydrology(HydrologyFeatureType.SURFACE_POOL, false, true, false)
        ));
        assertEquals(IrisSurfaceKind.DRY_CHANNEL, IrisSurfaceClassifier.classify(
                60,
                60,
                InferredType.LAND,
                hydrology(HydrologyFeatureType.SURFACE_POOL, true, false, false)
        ));
        assertEquals(IrisSurfaceKind.LAND, IrisSurfaceClassifier.classify(
                60,
                60,
                InferredType.LAND,
                hydrology(HydrologyFeatureType.SURFACE_POOL, false, false, false)
        ));
    }

    @Test
    public void voidClassificationWinsOverAcceptedRiverGeometry() {
        assertEquals(IrisSurfaceKind.VOID, IrisSurfaceClassifier.classify(
                0,
                63,
                InferredType.LAND,
                hydrology(HydrologyFeatureType.SURFACE_POOL, true, false, true)
        ));
    }

    @Test
    public void absentRejectedCandidatesDoNotCreatePublicRiverKinds() {
        assertEquals(IrisSurfaceKind.LAND, IrisSurfaceClassifier.classify(
                64,
                63,
                InferredType.LAND,
                null
        ));
    }

    private static HydrologyColumnSample hydrology(
            HydrologyFeatureType type,
            boolean channel,
            boolean shore,
            boolean connectedFluid
    ) {
        HydrologyFeatureRef feature = new HydrologyFeatureRef(
                1L,
                type,
                2L,
                3L,
                0,
                63,
                0,
                1,
                0,
                false
        );
        HydrologyColumnLayer layer = new HydrologyColumnLayer(
                feature,
                60,
                63,
                63,
                channel,
                shore,
                !channel && !shore,
                connectedFluid,
                false,
                false,
                true,
                connectedFluid && channel,
                false,
                "water",
                "river",
                "mouth",
                "shore",
                "dry",
                "cave"
        );
        return new HydrologyColumnSample(0, 0, 70, 63, false, "parent", List.of(layer));
    }
}
