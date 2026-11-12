package art.arcane.iris.core.studio.map;

import art.arcane.iris.spi.PlatformBlockState;
import art.arcane.iris.util.project.hunk.Hunk;
import it.unimi.dsi.fastutil.ints.Int2ByteMaps;
import org.junit.Test;

import java.util.function.UnaryOperator;

import static org.junit.Assert.assertEquals;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

public class ChunkCellsTest {
    private static final int HEIGHT = 12;
    private static final int GROUND_Y = 3;
    private static final int NO_FLUID = -1;
    private static final int DOWN = 0;
    private static final int UP = 1;
    private static final PlatformBlockState STONE = block("minecraft:stone", true);
    private static final PlatformBlockState LEAVES = block("minecraft:oak_leaves", false);

    @Test
    public void openGroundIsFullyLitWithNoCornerDarkened() {
        short shade = cellsOf(groundUpTo(GROUND_Y)).topShade(7, GROUND_Y, 7);

        assertEquals(ChunkCells.FULL_LIGHT, ChunkCells.lightOf(shade));
        assertEquals(0, ChunkCells.opaqueAroundCorners(shade));
    }

    @Test
    public void eachLeafAboveDimsTheSkyByALevel() {
        Hunk<PlatformBlockState> chunk = groundUpTo(GROUND_Y);
        fillLayer(chunk, GROUND_Y + 3, LEAVES);
        fillLayer(chunk, GROUND_Y + 4, LEAVES);

        assertEquals(ChunkCells.FULL_LIGHT - 2, ChunkCells.lightOf(cellsOf(chunk).topShade(7, GROUND_Y, 7)));
    }

    @Test
    public void groundUnderARoofIsLitFromTheSideLessAStepAtATime() {
        Hunk<PlatformBlockState> chunk = groundUpTo(GROUND_Y);
        fillLayer(chunk, GROUND_Y + 3, STONE);
        for (int z = 0; z < 16; z++) {
            chunk.set(15, GROUND_Y + 3, z, null);
        }

        ChunkCells cells = cellsOf(chunk);

        assertEquals(ChunkCells.FULL_LIGHT, cells.lightAt(15, GROUND_Y + 1, 7));
        assertEquals(ChunkCells.FULL_LIGHT - 3, cells.lightAt(12, GROUND_Y + 1, 7));
    }

    @Test
    public void groundAtTheFootOfAWallDarkensTheCornersAgainstIt() {
        Hunk<PlatformBlockState> chunk = groundUpTo(GROUND_Y);
        for (int z = 0; z < 16; z++) {
            chunk.set(8, GROUND_Y + 1, z, STONE);
        }

        short shade = cellsOf(chunk).topShade(7, GROUND_Y, 7);

        // The up face's corners run from the north west: two lie against the wall to the east, each beside it and past it.
        assertEquals(4, ChunkCells.opaqueAroundCorners(shade));
        assertEquals(ChunkCells.FULL_LIGHT, ChunkCells.lightOf(shade));
    }

    @Test
    public void aFaceAgainstOpaqueSidesOnBothHandsHidesTheCornerBetweenThem() {
        Hunk<PlatformBlockState> chunk = groundUpTo(GROUND_Y);
        chunk.set(8, GROUND_Y + 1, 7, STONE);
        chunk.set(7, GROUND_Y + 1, 8, STONE);

        short shade = cellsOf(chunk).topShade(7, GROUND_Y, 7);

        // The south east corner has both sides opaque, so counts as all three; the corners on one side count one each.
        assertEquals(ChunkCells.MOST_OPAQUE_AROUND_CORNER + 2, ChunkCells.opaqueAroundCorners(shade));
    }

    @Test
    public void theUndersideOfBuriedGroundIsDark() {
        assertEquals(0, ChunkCells.lightOf(cellsOf(groundUpTo(GROUND_Y)).faceShade(7, 1, 7, DOWN)));
        assertEquals(ChunkCells.FULL_LIGHT, ChunkCells.lightOf(cellsOf(groundUpTo(GROUND_Y)).faceShade(7, GROUND_Y, 7, UP)));
    }

    private static ChunkCells cellsOf(Hunk<PlatformBlockState> chunk) {
        return ChunkCells.of(chunk, (x, z) -> GROUND_Y, NO_FLUID, UnaryOperator.identity(), Int2ByteMaps.EMPTY_MAP);
    }

    private static void fillLayer(Hunk<PlatformBlockState> chunk, int y, PlatformBlockState block) {
        for (int z = 0; z < 16; z++) {
            for (int x = 0; x < 16; x++) {
                chunk.set(x, y, z, block);
            }
        }
    }

    private static Hunk<PlatformBlockState> groundUpTo(int topY) {
        Hunk<PlatformBlockState> chunk = Hunk.newArrayHunk(16, HEIGHT, 16);
        for (int y = 0; y <= topY; y++) {
            fillLayer(chunk, y, STONE);
        }
        return chunk;
    }

    private static PlatformBlockState block(String key, boolean occluding) {
        PlatformBlockState block = mock(PlatformBlockState.class);
        when(block.key()).thenReturn(key);
        when(block.isOccluding()).thenReturn(occluding);
        when(block.isSolid()).thenReturn(true);
        return block;
    }
}
