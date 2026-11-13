package art.arcane.iris.core.studio.map;

import art.arcane.iris.spi.PlatformBlockState;
import art.arcane.iris.util.project.hunk.Hunk;
import com.volmit.geometry.BlockPosition;
import com.volmit.object.GeneratedObject;
import com.volmit.object.ObjectBlock;
import org.junit.Test;

import java.util.HashSet;
import java.util.Set;
import java.util.function.IntBinaryOperator;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

public class CaveSpaceTest {
    private static final int HEIGHT = 32;
    private static final int MIN_WORLD_Y = -16;
    private static final int TERRAIN_TOP = 20;
    private static final IntBinaryOperator FLAT_TERRAIN = (x, z) -> TERRAIN_TOP;
    private static final PlatformBlockState STONE = block("minecraft:stone", false);
    private static final PlatformBlockState AIR = block("minecraft:air", true);

    @Test
    public void aHollowUnderTheTerrainShowsItsOutsideOnly() {
        CaveSpace space = new CaveSpace(0, 0, 1, HEIGHT, MIN_WORLD_Y);
        Hunk<PlatformBlockState> chunk = stoneUpToTerrain();
        hollow(chunk, 5, 5, 5, 3);

        space.add(0, 0, chunk, FLAT_TERRAIN);
        Set<BlockPosition> kept = positionsOf(space.scene());

        assertEquals("a 3 block cube without its middle", 26, kept.size());
        assertFalse(kept.contains(new BlockPosition(6, 6, 6)));
    }

    @Test
    public void theAirAboveTheTerrainIsNoCave() {
        CaveSpace space = new CaveSpace(0, 0, 1, HEIGHT, MIN_WORLD_Y);

        space.add(0, 0, stoneUpToTerrain(), FLAT_TERRAIN);

        assertTrue(space.scene().blocks().isEmpty());
    }

    @Test
    public void aCaveAcrossAChunkEdgeHasNoWallThere() {
        CaveSpace space = new CaveSpace(0, 0, 2, HEIGHT, MIN_WORLD_Y);
        Hunk<PlatformBlockState> west = stoneUpToTerrain();
        Hunk<PlatformBlockState> east = stoneUpToTerrain();
        hollow(west, 13, 5, 5, 3);
        hollow(east, 0, 5, 5, 3);

        space.add(0, 0, west, FLAT_TERRAIN);
        space.add(1, 0, east, FLAT_TERRAIN);
        Set<BlockPosition> kept = positionsOf(space.scene());

        assertFalse("the middle of the hollow on the west side of the edge", kept.contains(new BlockPosition(15, 6, 6)));
        assertFalse("the middle of the hollow on the east side of the edge", kept.contains(new BlockPosition(16, 6, 6)));
    }

    @Test
    public void eachBandOfHeightsHasItsColour() {
        assertEquals("minecraft:yellow_concrete", CaveSpace.colourAt(80).key());
        assertEquals("minecraft:red_concrete", CaveSpace.colourAt(0).key());
        assertEquals("minecraft:magenta_concrete", CaveSpace.colourAt(-1).key());
        assertEquals("minecraft:blue_concrete", CaveSpace.colourAt(-200).key());
    }

    @Test
    public void theSceneStandsOnItsLowestCave() {
        CaveSpace space = new CaveSpace(0, 0, 1, HEIGHT, MIN_WORLD_Y);
        Hunk<PlatformBlockState> chunk = stoneUpToTerrain();
        hollow(chunk, 5, 4, 5, 2);

        space.add(0, 0, chunk, FLAT_TERRAIN);
        GeneratedObject scene = space.scene();

        assertEquals(4, scene.bounds().minY());
        assertEquals(6, scene.bounds().maxY());
        for (ObjectBlock block : scene.blocks()) {
            assertEquals(CaveSpace.colourAt(block.position().y() + MIN_WORLD_Y), block.state());
        }
    }

    private static Hunk<PlatformBlockState> stoneUpToTerrain() {
        Hunk<PlatformBlockState> chunk = Hunk.newArrayHunk(16, HEIGHT, 16);
        for (int y = 0; y < HEIGHT; y++) {
            for (int z = 0; z < 16; z++) {
                for (int x = 0; x < 16; x++) {
                    chunk.set(x, y, z, y <= TERRAIN_TOP ? STONE : AIR);
                }
            }
        }
        return chunk;
    }

    private static void hollow(Hunk<PlatformBlockState> chunk, int minX, int minY, int minZ, int across) {
        for (int y = minY; y < minY + across; y++) {
            for (int z = minZ; z < minZ + across; z++) {
                for (int x = minX; x < Math.min(16, minX + across); x++) {
                    chunk.set(x, y, z, AIR);
                }
            }
        }
    }

    private static Set<BlockPosition> positionsOf(GeneratedObject scene) {
        Set<BlockPosition> positions = new HashSet<>();
        scene.blocks().forEach(block -> positions.add(block.position()));
        return positions;
    }

    private static PlatformBlockState block(String key, boolean air) {
        PlatformBlockState block = mock(PlatformBlockState.class);
        when(block.key()).thenReturn(key);
        when(block.isAir()).thenReturn(air);
        return block;
    }
}
