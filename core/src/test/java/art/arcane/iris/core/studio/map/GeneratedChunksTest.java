package art.arcane.iris.core.studio.map;

import art.arcane.iris.spi.PlatformBlockState;
import art.arcane.iris.util.project.hunk.Hunk;
import org.junit.Test;

import java.util.function.IntBinaryOperator;
import java.util.function.UnaryOperator;

import static org.junit.Assert.assertEquals;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

public class GeneratedChunksTest {
    private static final int HEIGHT = 12;
    private static final PlatformBlockState STONE = block("minecraft:stone", true);
    private static final PlatformBlockState POPPY = block("minecraft:poppy", false);
    private static final PlatformBlockState LOG = block("minecraft:oak_log", true);
    private static final PlatformBlockState LEAVES = block("minecraft:oak_leaves", true);
    private static final PlatformBlockState TRIPWIRE = block("minecraft:tripwire", false);
    // A plugin's grass: solid on the server, a tripwire to players.
    private static final PlatformBlockState PLUGIN_GRASS = block("craftengine:custom_7", true);

    @Test
    public void theGroundIsTheTerrainsTopAndWhatStandsOnItItsCover() {
        Hunk<PlatformBlockState> blocks = Hunk.newArrayHunk(16, HEIGHT, 16);
        blocks.set(0, 3, 0, STONE);
        blocks.set(0, 4, 0, POPPY);
        blocks.set(1, 2, 0, STONE);

        ColumnTops.ColumnTop[] tops = GeneratedChunks.topsOf(blocks, terrainAt(3), UnaryOperator.identity());

        assertEquals(new ColumnTops.ColumnTop(3, "minecraft:stone", "minecraft:poppy"), tops[0]);
        assertEquals(new ColumnTops.ColumnTop(2, "minecraft:stone", null), tops[1]);
        assertEquals(ColumnTops.ColumnTop.NOTHING, tops[2]);
    }

    @Test
    public void aTreeIsRaisedOverTheGroundItStandsOnInsteadOfBeingTheGround() {
        Hunk<PlatformBlockState> blocks = Hunk.newArrayHunk(16, HEIGHT, 16);
        blocks.set(0, 3, 0, STONE);
        blocks.set(0, 4, 0, LOG);
        blocks.set(0, 5, 0, LOG);
        blocks.set(0, 6, 0, LEAVES);
        blocks.set(1, 3, 0, STONE);
        blocks.set(1, 4, 0, POPPY);
        blocks.set(1, 6, 0, LEAVES);

        ColumnTops.ColumnTop[] tops = GeneratedChunks.topsOf(blocks, terrainAt(3), UnaryOperator.identity());

        ColumnTops.Raised trunk = new ColumnTops.Raised(new short[]{6, 5, 4}, new String[]{"minecraft:oak_leaves", "minecraft:oak_log", "minecraft:oak_log"});
        assertEquals(new ColumnTops.ColumnTop(3, "minecraft:stone", null, trunk), tops[0]);
        ColumnTops.Raised canopy = new ColumnTops.Raised(new short[]{6}, new String[]{"minecraft:oak_leaves"});
        assertEquals(new ColumnTops.ColumnTop(3, "minecraft:stone", "minecraft:poppy", canopy), tops[1]);
    }

    @Test
    public void aCaveOpenToTheSkyLowersTheGround() {
        Hunk<PlatformBlockState> blocks = Hunk.newArrayHunk(16, HEIGHT, 16);
        blocks.set(0, 1, 0, STONE);

        ColumnTops.ColumnTop[] tops = GeneratedChunks.topsOf(blocks, terrainAt(3), UnaryOperator.identity());

        assertEquals(new ColumnTops.ColumnTop(1, "minecraft:stone", null), tops[0]);
    }

    @Test
    public void blocksWithNothingSolidUnderThemAreTheGround() {
        Hunk<PlatformBlockState> blocks = Hunk.newArrayHunk(16, HEIGHT, 16);
        blocks.set(0, 8, 0, STONE);
        blocks.set(0, 9, 0, STONE);

        ColumnTops.ColumnTop[] tops = GeneratedChunks.topsOf(blocks, terrainAt(3), UnaryOperator.identity());

        assertEquals(new ColumnTops.ColumnTop(9, "minecraft:stone", null), tops[0]);
    }

    @Test
    public void aPluginBlockPlayersWalkThroughIsCoverThoughTheServerHoldsItSolid() {
        Hunk<PlatformBlockState> blocks = Hunk.newArrayHunk(16, HEIGHT, 16);
        blocks.set(0, 3, 0, STONE);
        blocks.set(0, 4, 0, PLUGIN_GRASS);

        ColumnTops.ColumnTop[] tops = GeneratedChunks.topsOf(blocks, terrainAt(3), block -> block == PLUGIN_GRASS ? TRIPWIRE : block);

        assertEquals(new ColumnTops.ColumnTop(3, "minecraft:stone", "craftengine:custom_7"), tops[0]);
    }

    private static IntBinaryOperator terrainAt(int y) {
        return (x, z) -> y;
    }

    private static PlatformBlockState block(String key, boolean solid) {
        PlatformBlockState block = mock(PlatformBlockState.class);
        when(block.key()).thenReturn(key);
        when(block.isSolid()).thenReturn(solid);
        return block;
    }
}
