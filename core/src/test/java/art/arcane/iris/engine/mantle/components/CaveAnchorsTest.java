package art.arcane.iris.engine.mantle.components;

import art.arcane.iris.builtin.carve.CaveAnchorMode;
import art.arcane.iris.modules.objects.CarvedSpace;
import com.volmit.mantle.CarvedVoid;
import art.arcane.volmlib.util.math.RNG;
import com.volmit.resource.ResourceId;
import com.volmit.world.BiomeSample;
import com.volmit.world.TerrainSampler;
import org.junit.Test;

import java.util.Optional;

import static org.junit.Assert.assertTrue;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

/** Cave objects are anchored where the carved space gives them something to stand on, hang from or float in. */
public class CaveAnchorsTest {
    private static final int HEIGHT = 128;
    private static final int SURFACE = 100;
    private static final int CAVE_FLOOR = 20;
    private static final int CAVE_CEILING = 30;
    private static final ResourceId BIOME = new ResourceId("test", "plains");

    @Test
    public void floorAnchorsAreTheCarvedBlocksWithSolidGroundWithinThreeBlocksBelow() {
        CaveAnchors anchors = new CaveAnchors(cave(), terrain(), HEIGHT, 8);

        for (int seed = 0; seed < 20; seed++) {
            CaveAnchors.Anchor anchor = anchors.find(new RNG(seed), 0, 0, search(CaveAnchorMode.FLOOR), null, false);

            assertNotNull(anchor);
            assertTrue("anchor " + anchor.y(), anchor.y() >= CAVE_FLOOR && anchor.y() < CAVE_FLOOR + 3);
        }
    }

    @Test
    public void ceilingAnchorsAreTheCarvedBlocksWithSolidRockWithinThreeBlocksAbove() {
        CaveAnchors anchors = new CaveAnchors(cave(), terrain(), HEIGHT, 8);

        for (int seed = 0; seed < 20; seed++) {
            CaveAnchors.Anchor anchor = anchors.find(new RNG(seed), 0, 0, search(CaveAnchorMode.CEILING), null, false);

            assertNotNull(anchor);
            assertTrue("anchor " + anchor.y(), anchor.y() >= CAVE_CEILING - 3 && anchor.y() < CAVE_CEILING);
        }
    }

    @Test
    public void noAnchorIsFoundWhereTheCaveIsDeeperThanTheObjectMayBe() {
        CaveAnchors anchors = new CaveAnchors(cave(), terrain(), HEIGHT, 8);

        CaveAnchors.Search tooShallow = new CaveAnchors.Search(CaveAnchorMode.FLOOR, 1, SURFACE, 6);

        assertNull(anchors.find(new RNG(1), 0, 0, tooShallow, null, false));
    }

    @Test
    public void aCaveBiomeThatIsNotTheExpectedOneRejectsTheAnchor() {
        ResourceId expected = new ResourceId("test", "carving/ice");
        TerrainSampler terrain = terrain();
        when(terrain.biome(anyInt(), anyInt(), anyInt())).thenReturn(new BiomeSample(BIOME, BIOME, Optional.of(new ResourceId("test", "carving/other"))));
        CaveAnchors anchors = new CaveAnchors(cave(), terrain, HEIGHT, 8);

        assertNull(anchors.find(new RNG(1), 0, 0, search(CaveAnchorMode.FLOOR), expected, false));
    }

    private static CaveAnchors.Search search(CaveAnchorMode mode) {
        return new CaveAnchors.Search(mode, 1, 6, 6);
    }

    private static TerrainSampler terrain() {
        TerrainSampler terrain = mock(TerrainSampler.class);
        when(terrain.surfaceHeight(anyInt(), anyInt())).thenReturn(SURFACE);
        when(terrain.biome(anyInt(), anyInt(), anyInt())).thenReturn(new BiomeSample(BIOME, BIOME, Optional.of(BIOME)));
        return terrain;
    }

    /** A cave from {@code CAVE_FLOOR} up to, not including, {@code CAVE_CEILING}, in every column. */
    private static CarvedSpace cave() {
        CarvedSpace cave = mock(CarvedSpace.class);
        when(cave.isCarved(anyInt(), anyInt(), anyInt())).thenAnswer(call -> isCarved(call.getArgument(1)));
        when(cave.column(anyInt(), anyInt(), anyInt())).thenAnswer(call -> {
            int height = call.getArgument(2);
            byte[] column = new byte[height];
            for (int y = 0; y < height; y++) {
                column[y] = (byte) (isCarved(y) ? 1 : 0);
            }
            return column;
        });
        when(cave.voidAt(anyInt(), anyInt(), anyInt())).thenAnswer(call ->
                isCarved(call.getArgument(1)) ? Optional.of(CarvedVoid.OPEN) : Optional.empty());
        return cave;
    }

    private static boolean isCarved(int y) {
        return y >= CAVE_FLOOR && y < CAVE_CEILING;
    }
}
