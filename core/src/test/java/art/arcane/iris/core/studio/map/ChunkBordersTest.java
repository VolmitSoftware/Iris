package art.arcane.iris.core.studio.map;

import art.arcane.iris.spi.PlatformBlockState;
import art.arcane.iris.util.project.hunk.Hunk;
import org.junit.Test;

import java.util.ArrayList;
import java.util.List;
import java.util.function.UnaryOperator;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

public class ChunkBordersTest {
    private static final int HEIGHT = 12;
    private static final int GROUND_Y = 5;
    private static final int NO_FLUID = -1;
    private static final PlatformBlockState STONE = block("minecraft:stone", false);
    private static final PlatformBlockState WATER = block("minecraft:water", true);

    @Test
    public void groundBesideAHoleDugAcrossTheChunksEdgeShowsItsSideIntoTheHole() {
        Hunk<PlatformBlockState> dug = groundUpTo(GROUND_Y);
        dug.set(0, GROUND_Y, 7, null);
        dug.set(0, GROUND_Y - 1, 7, null);

        List<String> faces = facesOf(groundUpTo(GROUND_Y), dug, ChunkBorders.EAST);

        assertEquals(List.of("15,4,7 minecraft:stone", "15,5,7 minecraft:stone"), faces);
    }

    @Test
    public void groundBesideGroundOfTheSameHeightShowsNoSide() {
        assertTrue(facesOf(groundUpTo(GROUND_Y), groundUpTo(GROUND_Y), ChunkBorders.EAST).isEmpty());
    }

    @Test
    public void waterShowsItsSideAgainstAHoleAcrossTheEdgeButNotAgainstWater() {
        Hunk<PlatformBlockState> pond = groundUpTo(GROUND_Y);
        pond.set(15, GROUND_Y, 7, WATER);
        Hunk<PlatformBlockState> pondBeside = groundUpTo(GROUND_Y);
        pondBeside.set(0, GROUND_Y, 7, WATER);
        Hunk<PlatformBlockState> holeBeside = groundUpTo(GROUND_Y);
        holeBeside.set(0, GROUND_Y, 7, null);

        assertTrue(facesOf(pond, pondBeside, ChunkBorders.EAST).isEmpty());
        assertEquals(List.of("15,5,7 minecraft:water"), facesOf(pond, holeBeside, ChunkBorders.EAST));
    }

    @Test
    public void eachSideMeetsTheOppositeSideOfTheChunkBesideIt() {
        Hunk<PlatformBlockState> dug = groundUpTo(GROUND_Y);
        dug.set(3, GROUND_Y, 15, null);

        List<String> faces = facesOf(groundUpTo(GROUND_Y), dug, ChunkBorders.NORTH);

        assertEquals(List.of("3,5,0 minecraft:stone"), faces);
    }

    // The faces the first chunk shows on its side against the second, which lies on that side; the terrain's height
    // beside each is the ground's, which knows nothing dug.
    private static List<String> facesOf(Hunk<PlatformBlockState> chunk, Hunk<PlatformBlockState> beside, int side) {
        ChunkBorders borders = VisibleBlocks.of(chunk, (x, z) -> GROUND_Y, NO_FLUID, UnaryOperator.identity()).borders();
        ChunkBorders besideBorders = VisibleBlocks.of(beside, (x, z) -> GROUND_Y, NO_FLUID, UnaryOperator.identity()).borders();
        List<String> faces = new ArrayList<>();
        borders.facesAgainst(besideBorders, side, (x, y, z, block, faceSide) -> {
            assertEquals(side, faceSide);
            faces.add(x + "," + y + "," + z + " " + block);
        });
        return faces;
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

    private static PlatformBlockState block(String key, boolean fluid) {
        PlatformBlockState block = mock(PlatformBlockState.class);
        when(block.key()).thenReturn(key);
        when(block.isOccluding()).thenReturn(!fluid);
        when(block.isSolid()).thenReturn(!fluid);
        when(block.isFluid()).thenReturn(fluid);
        return block;
    }
}
