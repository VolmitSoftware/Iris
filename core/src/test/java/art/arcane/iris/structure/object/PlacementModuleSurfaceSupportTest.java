package art.arcane.iris.structure.object;

import art.arcane.iris.generation.hydrology.raster.WaterRole;
import art.arcane.iris.generation.stream.ProvisionalAnswerException;
import art.arcane.iris.testsupport.PlatformBinding;
import art.arcane.volmlib.nativelib.terrain.NativeBlockState;
import art.arcane.volmlib.util.math.RNG;
import org.junit.Rule;
import org.junit.Test;

import java.util.List;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertThrows;
import static org.junit.Assert.assertTrue;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

public class PlacementModuleSurfaceSupportTest {
    @Rule
    public final PlatformBinding platform = PlatformBinding.mockPlatform();

    private final HydrologyWaterFixture world = new HydrologyWaterFixture();

    @Test
    public void underwaterObjectsPlaceInPonds() {
        world.water(WaterRole.POND, 7, 13);
        HydrologyWaterFixture.Placer placer = world.placer();

        assertTrue(place(HydrologyWaterFixture.pillar(), placer, underwater()) >= 0);
        assertFalse(placer.writes().isEmpty());
        assertEquals(world.floorAt(0, 0), placer.lowestWrite());
        assertTrue(placer.highestWrite() < world.headAt(0, 0));
    }

    @Test
    public void underwaterObjectsSkipWaterTooShallowForTheirResolvedY() {
        world.water(WaterRole.POND, 9, 13);
        HydrologyWaterFixture.Placer deep = world.placer();
        assertTrue(place(HydrologyWaterFixture.pillar(), deep, underwater()) >= 0);

        world.water(WaterRole.POND, 10, 13);
        HydrologyWaterFixture.Placer shallow = world.placer();
        assertEquals(-1, place(HydrologyWaterFixture.pillar(), shallow, underwater()));
        assertTrue(shallow.writes().isEmpty());
    }

    @Test
    public void underwaterObjectsTreatLavaPondsLikeLavaSeas() {
        NativeBlockState lava = mock(NativeBlockState.class);
        when(lava.key()).thenReturn("minecraft:lava");
        when(world.complex().resolveWaterFluid(anyInt(), anyInt())).thenReturn(lava);
        world.water(WaterRole.POND, 7, 13);
        HydrologyWaterFixture.Placer placer = world.placer();

        assertTrue(place(HydrologyWaterFixture.pillar(), placer, underwater()) >= 0);
        assertEquals(world.floorAt(0, 0), placer.lowestWrite());
    }

    @Test
    public void onwaterObjectsFloatOnRivers() {
        world.water(WaterRole.CHANNEL, 9, 13);
        HydrologyWaterFixture.Placer placer = world.placer();

        assertTrue(place(HydrologyWaterFixture.pillar(), placer, onwater()) >= 0);
        assertEquals(world.headAt(0, 0), placer.lowestWrite());
    }

    @Test
    public void onwaterObjectsSkipDryColumns() {
        HydrologyWaterFixture.Placer placer = world.placer();

        assertEquals(-1, place(HydrologyWaterFixture.pillar(), placer, onwater()));
        assertTrue(placer.writes().isEmpty());
    }

    @Test
    public void forcePlaceObjectsPlaceInRiverWater() {
        world.water(WaterRole.CHANNEL, 9, 13);
        HydrologyWaterFixture.Placer placer = world.placer();

        assertTrue(place(HydrologyWaterFixture.pillar(), placer, forced()) >= 0);
        assertEquals(world.headAt(0, 0), placer.lowestWrite());
    }

    @Test
    public void landObjectsStayOutOfRiverWater() {
        world.water(WaterRole.CHANNEL, 9, 13);
        HydrologyWaterFixture.Placer placer = world.placer();

        assertEquals(-1, place(HydrologyWaterFixture.pillar(), placer, placement()));
        assertTrue(placer.writes().isEmpty());
    }

    @Test
    public void flatPondsTakeWideWaterObjects() {
        world.water(WaterRole.POND, 5, 13);

        for (IrisObjectPlacement placement : List.of(underwater(), onwater(), forced())) {
            HydrologyWaterFixture.Placer placer = world.placer();

            assertTrue(place(HydrologyWaterFixture.raft(), placer, placement) >= 0);
            assertFalse(placer.writes().isEmpty());
        }
    }

    @Test
    public void onwaterObjectsSkipCascadeSteps() {
        world.water(WaterRole.CHANNEL, 9, 13);
        world.column(1, 0, WaterRole.CASCADE, 8, 11);
        HydrologyWaterFixture.Placer placer = world.placer();

        assertEquals(-1, place(HydrologyWaterFixture.raft(), placer, onwater()));
        assertTrue(placer.writes().isEmpty());
    }

    @Test
    public void underwaterObjectsSkipDamWalls() {
        world.water(WaterRole.POND, 5, 13);
        world.column(1, 0, WaterRole.DAM, 14, 0);
        HydrologyWaterFixture.Placer placer = world.placer();

        assertEquals(-1, place(HydrologyWaterFixture.raft(), placer, underwater()));
        assertTrue(placer.writes().isEmpty());
    }

    @Test
    public void waterObjectsSkipWaterAtAnotherHead() {
        world.water(WaterRole.CHANNEL, 9, 13);
        world.column(1, 0, WaterRole.CHANNEL, 8, 12);

        for (IrisObjectPlacement placement : List.of(underwater(), onwater(), forced())) {
            HydrologyWaterFixture.Placer placer = world.placer();

            assertEquals(-1, place(HydrologyWaterFixture.raft(), placer, placement));
            assertTrue(placer.writes().isEmpty());
        }
    }

    @Test
    public void waterObjectsNeverPlaceOnProvisionalHydrology() {
        world.water(WaterRole.POND, 7, 13);
        world.provisional();

        for (IrisObjectPlacement placement : List.of(underwater(), onwater(), forced())) {
            HydrologyWaterFixture.Placer placer = world.placer();

            ProvisionalAnswerException error = assertThrows(ProvisionalAnswerException.class,
                    () -> place(HydrologyWaterFixture.pillar(), placer, placement));

            assertTrue(error.getMessage().contains("provisional"));
            assertTrue(placer.writes().isEmpty());
        }
    }

    @Test
    public void landObjectsNeverPlaceOnProvisionalHydrology() {
        world.provisional();
        HydrologyWaterFixture.Placer placer = world.placer();

        assertThrows(ProvisionalAnswerException.class, () -> place(HydrologyWaterFixture.pillar(), placer, placement()));
        assertTrue(placer.writes().isEmpty());
    }

    @Test
    public void provisionalAnswersStopABoreBeforeItClearsAir() {
        world.provisional();
        HydrologyWaterFixture.Placer placer = world.placer();
        IrisObjectPlacement placement = placement();
        placement.setBore(true);

        assertThrows(ProvisionalAnswerException.class, () -> place(HydrologyWaterFixture.pillar(), placer, placement));
        assertTrue(placer.writes().isEmpty());
    }

    @Test
    public void explicitPlacementsIgnoreProvisionalAnswers() {
        world.provisional();
        HydrologyWaterFixture.Placer placer = world.placer();
        int surface = world.floorAt(0, 0);

        assertTrue(HydrologyWaterFixture.pillar().place(0, surface + 1, 0, placer, placement(), new RNG(1234L), world.data()) >= 0);
        assertFalse(placer.writes().isEmpty());
    }

    private int place(IrisObject object, HydrologyWaterFixture.Placer placer, IrisObjectPlacement placement) {
        return object.place(0, -1, 0, placer, placement, new RNG(1234L), world.data());
    }

    private static IrisObjectPlacement placement() {
        IrisObjectPlacement placement = new IrisObjectPlacement();
        placement.setMode(ObjectPlaceMode.CENTER_HEIGHT);
        return placement;
    }

    private static IrisObjectPlacement underwater() {
        IrisObjectPlacement placement = placement();
        placement.setUnderwater(true);
        return placement;
    }

    private static IrisObjectPlacement onwater() {
        IrisObjectPlacement placement = placement();
        placement.setOnwater(true);
        return placement;
    }

    private static IrisObjectPlacement forced() {
        IrisObjectPlacement placement = placement();
        placement.setForcePlace(true);
        return placement;
    }
}
