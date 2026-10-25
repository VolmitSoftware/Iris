package art.arcane.iris.platform.bukkit.terrain;

import art.arcane.iris.api.terrain.IrisSurfaceKind;
import art.arcane.iris.generation.hydrology.raster.WaterRole;
import art.arcane.iris.generation.terrain.InferredType;
import org.junit.Test;

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
    public void waterRolesOverrideGenericOceanAndLandKinds() {
        for (WaterRole role : WaterRole.values()) {
            IrisSurfaceKind expected = switch (role) {
                case NONE -> IrisSurfaceKind.OCEAN;
                case SHORE, POND_SHORE -> IrisSurfaceKind.RIVER_SHORE;
                case BANK, DAM -> IrisSurfaceKind.LAND;
                case CHANNEL, POND, MOUTH, CASCADE, WATERFALL, INLET -> IrisSurfaceKind.RIVER;
            };
            assertEquals(role.name(), expected, IrisSurfaceClassifier.classify(60, 63, InferredType.SEA, role));
        }
    }

    @Test
    public void voidClassificationWinsOverWaterRoles() {
        for (WaterRole role : WaterRole.values()) {
            assertEquals(role.name(), IrisSurfaceKind.VOID, IrisSurfaceClassifier.classify(0, 63, InferredType.LAND, role));
        }
    }

    @Test
    public void columnsWithoutAWaterRoleKeepTheGenericKinds() {
        assertEquals(IrisSurfaceKind.LAND, IrisSurfaceClassifier.classify(64, 63, InferredType.LAND, WaterRole.NONE));
        assertEquals(IrisSurfaceKind.SHORE, IrisSurfaceClassifier.classify(64, 63, InferredType.SHORE, WaterRole.NONE));
    }
}
