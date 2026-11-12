package art.arcane.iris.core.studio.map;

import art.arcane.iris.spi.PlatformBlockState;
import art.arcane.iris.util.project.hunk.Hunk;
import org.junit.Test;

import java.util.function.UnaryOperator;

import static org.junit.Assert.assertEquals;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

public class GeneratedChunksTest {
    private static final int HEIGHT = 8;
    private static final PlatformBlockState STONE = block("minecraft:stone", true);
    private static final PlatformBlockState POPPY = block("minecraft:poppy", false);
    private static final PlatformBlockState TRIPWIRE = block("minecraft:tripwire", false);
    // A plugin's grass: solid on the server, a tripwire to players.
    private static final PlatformBlockState PLUGIN_GRASS = block("craftengine:custom_7", true);

    @Test
    public void theGroundIsTheHighestSolidBlockAndWhatStandsOnItItsCover() {
        Hunk<PlatformBlockState> blocks = Hunk.newArrayHunk(16, HEIGHT, 16);
        blocks.set(0, 3, 0, STONE);
        blocks.set(0, 4, 0, POPPY);
        blocks.set(1, 2, 0, STONE);

        ColumnTops.ColumnTop[] tops = GeneratedChunks.topsOf(blocks, UnaryOperator.identity());

        assertEquals(new ColumnTops.ColumnTop(3, "minecraft:stone", "minecraft:poppy"), tops[0]);
        assertEquals(new ColumnTops.ColumnTop(2, "minecraft:stone", null), tops[1]);
        assertEquals(ColumnTops.ColumnTop.NOTHING, tops[2]);
    }

    @Test
    public void aPluginBlockPlayersWalkThroughIsCoverThoughTheServerHoldsItSolid() {
        Hunk<PlatformBlockState> blocks = Hunk.newArrayHunk(16, HEIGHT, 16);
        blocks.set(0, 3, 0, STONE);
        blocks.set(0, 4, 0, PLUGIN_GRASS);

        ColumnTops.ColumnTop[] tops = GeneratedChunks.topsOf(blocks, block -> block == PLUGIN_GRASS ? TRIPWIRE : block);

        assertEquals(new ColumnTops.ColumnTop(3, "minecraft:stone", "craftengine:custom_7"), tops[0]);
    }

    private static PlatformBlockState block(String key, boolean solid) {
        PlatformBlockState block = mock(PlatformBlockState.class);
        when(block.key()).thenReturn(key);
        when(block.isSolid()).thenReturn(solid);
        return block;
    }
}
