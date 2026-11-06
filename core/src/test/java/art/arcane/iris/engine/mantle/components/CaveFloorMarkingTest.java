package art.arcane.iris.engine.mantle.components;

import art.arcane.iris.core.modules.CarveTestTerrain;
import art.arcane.iris.core.modules.CarveTestWorld;
import art.arcane.iris.core.modules.InMemoryMantle;
import com.volmit.mantle.CarvedVoid;
import com.volmit.mantle.MantleLayer;
import com.volmit.placement.PlacedMarker;
import com.volmit.placement.PlacementTerrain;
import org.junit.Before;
import org.junit.Test;

import java.util.ArrayList;
import java.util.List;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.mock;

/** The floors of the caves a chunk was planned with are marked for the spawners: one in sixteen of the runs of three or more voids, by seed and position alone. */
public class CaveFloorMarkingTest {
    private static final int HEIGHT = 128;
    private static final int FLUID_HEIGHT = 40;
    private static final int SURFACE = 100;
    private static final int FLOOR_Y = 10;
    private static final int CEILING_Y = 13;

    private final InMemoryMantle mantle = new InMemoryMantle(HEIGHT / 16);
    private final List<PlacedMarker> marks = new ArrayList<>();
    private final PlacementTerrain placement = mock(PlacementTerrain.class);
    private CarveTestTerrain terrain;
    private MantleLayer<CarvedVoid> voids;

    @Before
    public void setUp() {
        voids = mantle.carved();
        terrain = new CarveTestTerrain(0, 0, HEIGHT, FLUID_HEIGHT, (x, z) -> SURFACE);
        doAnswer(call -> marks.add(call.getArgument(0))).when(placement).mark(any());
    }

    @Test
    public void everyMarkIsTheLowestBlockOfARunAndTheSameSeedMarksTheSameFloors() {
        carveEveryColumn(FLOOR_Y, CEILING_Y, CarvedVoid.OPEN);

        mark(1L);
        List<PlacedMarker> first = List.copyOf(marks);
        marks.clear();
        mark(1L);

        assertTrue("about one in sixteen of the 256 floors", !first.isEmpty() && first.size() < 64);
        assertEquals(first, marks);
        for (PlacedMarker marker : first) {
            PlacedMarker.PackMarker floor = (PlacedMarker.PackMarker) marker;
            assertEquals("cave_floor", floor.key());
            assertEquals(FLOOR_Y, floor.position().y());
        }
        marks.clear();
        mark(2L);
        assertTrue("another seed rolls other floors", !marks.equals(first));
    }

    @Test
    public void runsShorterThanThreeBlocksHaveNoFloor() {
        carveEveryColumn(FLOOR_Y, FLOOR_Y + 1, CarvedVoid.OPEN);

        mark(1L);

        assertTrue(marks.isEmpty());
    }

    @Test
    public void openVoidsHeldByTheTerrainsFluidAreNotCaveFloorsButExplicitOnesAre() {
        terrain = new CarveTestTerrain(0, 0, HEIGHT, 60, (x, z) -> 20);
        carveEveryColumn(FLUID_HEIGHT + 5, FLUID_HEIGHT + 8, CarvedVoid.OPEN);
        mark(1L);
        assertTrue("water above the sea floor", marks.isEmpty());

        carveEveryColumn(FLUID_HEIGHT + 5, FLUID_HEIGHT + 8, CarvedVoid.AIR);
        mark(1L);
        assertTrue(!marks.isEmpty());
    }

    @Test
    public void nothingBelowADimensionStackedAboveItsFloorIsMarked() {
        terrain = new CarveTestTerrain(0, 0, HEIGHT, FLUID_HEIGHT, (x, z) -> SURFACE).upperDimension((x, z) -> FLOOR_Y + 2);
        carveEveryColumn(FLOOR_Y, CEILING_Y, CarvedVoid.OPEN);

        mark(1L);

        assertTrue("only two blocks of the run lie below the dimension above", marks.isEmpty());
    }

    private void mark(long seed) {
        CarveWorld world = new CarveWorld(new CarveTestWorld().resources().noise(), seed, HEIGHT, FLUID_HEIGHT, 8, CarveTestWorld.MIN_HEIGHT);
        new CaveFloorMarking(world, false).mark(voids, terrain, placement, 0, 0);
    }

    private void carveEveryColumn(int floorY, int ceilingY, CarvedVoid carvedVoid) {
        for (int x = 0; x < 16; x++) {
            for (int z = 0; z < 16; z++) {
                for (int y = floorY; y <= ceilingY; y++) {
                    voids.set(x, y, z, carvedVoid);
                }
            }
        }
    }
}
