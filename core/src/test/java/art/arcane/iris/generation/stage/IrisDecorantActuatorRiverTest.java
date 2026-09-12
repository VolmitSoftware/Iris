package art.arcane.iris.generation.stage;

import art.arcane.iris.generation.hydrology.HydrologyColumnLayer;
import art.arcane.iris.generation.hydrology.HydrologyColumnSample;
import art.arcane.iris.generation.hydrology.HydrologyFeatureRef;
import art.arcane.iris.generation.hydrology.HydrologyFeatureType;
import art.arcane.iris.spi.PlatformBlockState;
import art.arcane.volmlib.util.hunk.Hunk;
import org.junit.Test;

import java.util.List;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertSame;
import static org.junit.Assert.assertTrue;
import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.mock;

public class IrisDecorantActuatorRiverTest {
    @Test
    public void acceptedSurfaceRoleControlsShorelineDecoration() {
        HydrologyColumnSample shore = surfaceRole(false, true, true);
        HydrologyColumnSample channel = surfaceRole(true, false, false);
        HydrologyColumnSample grading = surfaceRole(false, false, true);
        HydrologyColumnSample underground = sample(HydrologyFeatureType.UNDERGROUND_POOL, true, true);
        HydrologyColumnSample overlap = new HydrologyColumnSample(
                0,
                0,
                70,
                63,
                false,
                "parent",
                List.of(
                        layer(1L, true, false, false, HydrologyFeatureType.SURFACE_POOL, true, true),
                        layer(2L, false, true, true, HydrologyFeatureType.SURFACE_POOL, false, false)
                )
        );

        assertEquals(
                IrisDecorantActuator.ShorelineDecorationMode.ACCEPTED,
                IrisDecorantActuator.shorelineDecorationMode(shore, 62, 65)
        );
        assertEquals(
                IrisDecorantActuator.ShorelineDecorationMode.NONE,
                IrisDecorantActuator.shorelineDecorationMode(channel, 65, 65)
        );
        assertEquals(
                IrisDecorantActuator.ShorelineDecorationMode.NONE,
                IrisDecorantActuator.shorelineDecorationMode(grading, 65, 65)
        );
        assertEquals(
                IrisDecorantActuator.ShorelineDecorationMode.NONE,
                IrisDecorantActuator.shorelineDecorationMode(overlap, 65, 65)
        );
        assertEquals(
                IrisDecorantActuator.ShorelineDecorationMode.LEGACY,
                IrisDecorantActuator.shorelineDecorationMode(underground, 65, 65)
        );
        assertEquals(
                IrisDecorantActuator.ShorelineDecorationMode.LEGACY,
                IrisDecorantActuator.shorelineDecorationMode(null, 65, 65)
        );
        assertEquals(
                IrisDecorantActuator.ShorelineDecorationMode.NONE,
                IrisDecorantActuator.shorelineDecorationMode(null, 64, 65)
        );
    }

    @Test
    public void onlyAcceptedFallingWaterfallThroatsRejectSurfaceDecoration() {
        assertTrue(IrisDecorantActuator.isFallingWaterfallThroat(
                sample(HydrologyFeatureType.WATERFALL, true, true, true, true)
        ));
        assertTrue(IrisDecorantActuator.isFallingWaterfallThroat(
                sample(HydrologyFeatureType.SINKHOLE, true, true, true, true)
        ));
        assertFalse(IrisDecorantActuator.isFallingWaterfallThroat(
                sample(HydrologyFeatureType.WATERFALL, false, false, false, false)
        ));
        assertFalse(IrisDecorantActuator.isFallingWaterfallThroat(
                sample(HydrologyFeatureType.UNDERGROUND_DROP, true, true)
        ));
        assertFalse(IrisDecorantActuator.isFallingWaterfallThroat(null));
    }

    @Test
    public void seaDecoratorsRequireConnectedWaterBelowTheConfiguredSurface() {
        Hunk<PlatformBlockState> output = Hunk.newArrayHunk(3, 4, 1);
        PlatformBlockState water = mock(PlatformBlockState.class);
        PlatformBlockState adjacentWater = mock(PlatformBlockState.class);
        doReturn(true).when(water).isWater();
        doReturn(true).when(adjacentWater).isWater();
        output.set(1, 1, 0, water);

        assertFalse(IrisDecorantActuator.hasConnectedSurfaceWater(output, 1, 0, 0, 1));
        assertFalse(IrisDecorantActuator.hasConnectedSurfaceWater(output, 1, 0, 0, 2));

        output.set(0, 1, 0, adjacentWater);
        assertTrue(IrisDecorantActuator.hasConnectedSurfaceWater(output, 1, 0, 0, 2));
    }

    @Test
    public void seaDecoratorStacksRequireConnectedWaterAtEveryPlacedLevel() {
        Hunk<PlatformBlockState> output = Hunk.newArrayHunk(3, 4, 1);
        PlatformBlockState lowerWater = waterState();
        PlatformBlockState upperWater = waterState();
        PlatformBlockState adjacentWater = waterState();
        output.set(1, 1, 0, lowerWater);
        output.set(1, 2, 0, upperWater);
        output.set(0, 1, 0, adjacentWater);

        assertFalse(IrisDecorantActuator.hasConnectedWaterColumn(output, 1, 0, 1, 2));

        output.set(0, 2, 0, waterState());
        assertTrue(IrisDecorantActuator.hasConnectedWaterColumn(output, 1, 0, 1, 2));
    }

    @Test
    public void dryAquaticSeaSurfacePlacementRestoresTheOriginalBlock() {
        Hunk<PlatformBlockState> output = Hunk.newArrayHunk(3, 3, 1);
        PlatformBlockState air = state("minecraft:air");
        PlatformBlockState seagrass = state("minecraft:seagrass");
        output.set(1, 1, 0, air);

        output.set(1, 1, 0, seagrass);
        IrisDecorantActuator.restoreUnsupportedAquaticPlacement(output, 1, 1, 0, air);

        assertSame(air, output.get(1, 1, 0));
    }

    private static HydrologyColumnSample sample(
            HydrologyFeatureType type,
            boolean connectedFluid,
            boolean fluidOwned
    ) {
        return sample(
                type,
                connectedFluid,
                fluidOwned,
                true,
                type == HydrologyFeatureType.WATERFALL || type == HydrologyFeatureType.SINKHOLE
        );
    }

    private static HydrologyColumnSample sample(
            HydrologyFeatureType type,
            boolean connectedFluid,
            boolean fluidOwned,
            boolean channel,
            boolean fallingFluid
    ) {
        HydrologyColumnLayer layer = layer(
                1L,
                channel,
                false,
                !channel,
                type,
                connectedFluid,
                fluidOwned,
                fallingFluid
        );
        return new HydrologyColumnSample(0, 0, 70, 63, false, "parent", List.of(layer));
    }

    private static HydrologyColumnSample surfaceRole(
            boolean channel,
            boolean shore,
            boolean grading
    ) {
        HydrologyColumnLayer layer = layer(
                1L,
                channel,
                shore,
                grading,
                HydrologyFeatureType.SURFACE_POOL,
                channel,
                channel
        );
        return new HydrologyColumnSample(0, 0, 70, 63, false, "parent", List.of(layer));
    }

    private static HydrologyColumnLayer layer(
            long id,
            boolean channel,
            boolean shore,
            boolean grading,
            HydrologyFeatureType type,
            boolean connectedFluid,
            boolean fluidOwned
    ) {
        return layer(id, channel, shore, grading, type, connectedFluid, fluidOwned, false);
    }

    private static HydrologyColumnLayer layer(
            long id,
            boolean channel,
            boolean shore,
            boolean grading,
            HydrologyFeatureType type,
            boolean connectedFluid,
            boolean fluidOwned,
            boolean fallingFluid
    ) {
        HydrologyFeatureRef feature = new HydrologyFeatureRef(
                id,
                type,
                2L,
                3L,
                0,
                65,
                0,
                1,
                0,
                false
        );
        return new HydrologyColumnLayer(
                feature,
                63,
                65,
                type.isUnderground() ? 72 : 65,
                channel,
                shore,
                grading,
                connectedFluid,
                fallingFluid,
                fallingFluid,
                true,
                fluidOwned,
                false,
                "water",
                "river",
                "mouth",
                "shore",
                "dry",
                "cave"
        );
    }

    private static PlatformBlockState waterState() {
        PlatformBlockState water = state("minecraft:water");
        doReturn(true).when(water).isWater();
        return water;
    }

    private static PlatformBlockState state(String key) {
        PlatformBlockState state = mock(PlatformBlockState.class);
        doReturn(key).when(state).key();
        return state;
    }
}
