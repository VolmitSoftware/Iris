package art.arcane.iris.core.studio.map;

import art.arcane.iris.spi.PlatformBlockState;
import art.arcane.iris.util.project.hunk.Hunk;
import org.junit.Test;

import java.util.HashMap;
import java.util.Map;
import java.util.function.UnaryOperator;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

public class VisibleBlocksTest {
    private static final int HEIGHT = 12;
    private static final int GROUND_Y = 3;
    private static final int NO_FLUID = -1;
    private static final int DOWN = 1;
    private static final int UP = 1 << 1;
    private static final int NORTH = 1 << 2;
    private static final int SOUTH = 1 << 3;
    private static final int WEST = 1 << 4;
    private static final int EAST = 1 << 5;
    private static final PlatformBlockState STONE = block("minecraft:stone", true, false);
    private static final PlatformBlockState LOG = block("minecraft:oak_log", true, false);
    private static final PlatformBlockState LEAVES = block("minecraft:oak_leaves", false, false);
    private static final PlatformBlockState WATER = block("minecraft:water", false, true);
    private static final PlatformBlockState KELP = block("minecraft:kelp_plant", false, false);

    @Test
    public void flatGroundShowsOnlyTheTopOfItsHighestLayer() {
        Map<Long, Integer> seen = facesByPlace(VisibleBlocks.of(groundUpTo(GROUND_Y), (x, z) -> GROUND_Y, NO_FLUID, UnaryOperator.identity()));

        assertEquals(16 * 16, seen.size());
        assertEquals(UP, (int) seen.get(place(7, GROUND_Y, 7)));
    }

    @Test
    public void aTreeShowsTheSidesOfItsWoodAndEveryLeafFaceThatTouchesTheOpenAir() {
        Hunk<PlatformBlockState> chunk = groundUpTo(GROUND_Y);
        chunk.set(5, 4, 5, LOG);
        chunk.set(5, 5, 5, LOG);
        chunk.set(5, 6, 5, LEAVES);
        chunk.set(6, 6, 5, LEAVES);

        Map<Long, Integer> seen = facesByPlace(VisibleBlocks.of(chunk, (x, z) -> GROUND_Y, NO_FLUID, UnaryOperator.identity()));

        assertEquals(NORTH | SOUTH | WEST | EAST, (int) seen.get(place(5, 4, 5)));
        // The leaf on the trunk shows its side on the other leaf, as leaves are seen through.
        assertEquals(UP | NORTH | SOUTH | WEST | EAST, (int) seen.get(place(5, 6, 5)));
        assertEquals(DOWN | UP | NORTH | SOUTH | WEST | EAST, (int) seen.get(place(6, 6, 5)));
        assertFalse(seen.containsKey(place(5, GROUND_Y, 5)));
    }

    @Test
    public void aCaveClosedToTheSkyIsNotSeen() {
        Hunk<PlatformBlockState> chunk = groundUpTo(GROUND_Y);
        chunk.set(8, 1, 8, null);

        Map<Long, Integer> seen = facesByPlace(VisibleBlocks.of(chunk, (x, z) -> GROUND_Y, NO_FLUID, UnaryOperator.identity()));

        assertFalse(seen.containsKey(place(8, 0, 8)));
        assertFalse(seen.containsKey(place(8, 2, 8)));
    }

    @Test
    public void groundStandingAboveTheTerrainBesideTheChunkShowsItsSideThere() {
        Hunk<PlatformBlockState> chunk = groundUpTo(GROUND_Y);

        Map<Long, Integer> seen = facesByPlace(VisibleBlocks.of(chunk, (x, z) -> x < 0 ? GROUND_Y - 2 : GROUND_Y, NO_FLUID, UnaryOperator.identity()));

        assertEquals(UP | WEST, (int) seen.get(place(0, GROUND_Y, 7)));
        assertEquals(WEST, (int) seen.get(place(0, GROUND_Y - 1, 7)));
        assertFalse(seen.containsKey(place(0, GROUND_Y - 2, 7)));
    }

    @Test
    public void theFloorOfALakeShowsThroughItsWaterWhichShowsOnlyItsSurface() {
        Hunk<PlatformBlockState> chunk = groundUpTo(GROUND_Y);
        for (int z = 0; z < 16; z++) {
            for (int x = 0; x < 16; x++) {
                chunk.set(x, GROUND_Y + 1, z, WATER);
            }
        }

        Map<Long, Integer> seen = facesByPlace(VisibleBlocks.of(chunk, (x, z) -> GROUND_Y, GROUND_Y + 1, UnaryOperator.identity()));

        assertEquals(UP, (int) seen.get(place(7, GROUND_Y, 7)));
        assertEquals(UP, (int) seen.get(place(7, GROUND_Y + 1, 7)));
        assertEquals(UP, (int) seen.get(place(0, GROUND_Y + 1, 7)));
    }

    @Test
    public void waterShowsNoSideAgainstAPlantThatGrowsInIt() {
        Hunk<PlatformBlockState> chunk = groundUpTo(GROUND_Y);
        for (int z = 0; z < 16; z++) {
            for (int x = 0; x < 16; x++) {
                chunk.set(x, GROUND_Y + 1, z, WATER);
                chunk.set(x, GROUND_Y + 2, z, WATER);
            }
        }
        chunk.set(7, GROUND_Y + 1, 7, KELP);

        Map<Long, Integer> seen = facesByPlace(VisibleBlocks.of(chunk, (x, z) -> GROUND_Y, GROUND_Y + 2, UnaryOperator.identity()));

        assertEquals(0, (int) seen.get(place(6, GROUND_Y + 1, 7)));
    }

    private static Hunk<PlatformBlockState> groundUpTo(int topY) {
        Hunk<PlatformBlockState> chunk = Hunk.newArrayHunk(16, HEIGHT, 16);
        for (int y = 0; y <= topY; y++) {
            for (int z = 0; z < 16; z++) {
                for (int x = 0; x < 16; x++) {
                    chunk.set(x, y, z, STONE);
                }
            }
        }
        return chunk;
    }

    private static Map<Long, Integer> facesByPlace(VisibleBlocks visible) {
        Map<Long, Integer> faces = new HashMap<>();
        for (int at = 0; at < visible.count(); at++) {
            int position = visible.positions()[at];
            faces.put(place(position & 15, position >> VisibleBlocks.POSITION_Y_SHIFT, (position >> 4) & 15), visible.faces()[at] & 0xFF);
        }
        return faces;
    }

    private static long place(int x, int y, int z) {
        return ((long) y << 16) | (z << 8) | x;
    }

    private static PlatformBlockState block(String key, boolean occluding, boolean fluid) {
        PlatformBlockState block = mock(PlatformBlockState.class);
        when(block.key()).thenReturn(key);
        when(block.isOccluding()).thenReturn(occluding);
        when(block.isSolid()).thenReturn(!fluid);
        when(block.isFluid()).thenReturn(fluid);
        return block;
    }
}
