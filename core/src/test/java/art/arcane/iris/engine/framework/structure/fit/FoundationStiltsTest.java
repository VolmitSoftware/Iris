package art.arcane.iris.engine.framework.structure.fit;

import org.junit.Test;

import static art.arcane.iris.engine.framework.structure.fit.FitFixture.BOTTOM_Y;
import static art.arcane.iris.engine.framework.structure.fit.FitFixture.state;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

public class FoundationStiltsTest {
    private static final Box COLUMN = new Box(0, 0, 0, 0, 0, 0);

    @Test
    public void sparseStiltGridIsDeterministic() {
        assertTrue(FoundationStilts.isStiltColumn(0, 0, 4));
        assertTrue(FoundationStilts.isStiltColumn(-4, 8, 4));
        assertFalse(FoundationStilts.isStiltColumn(1, 0, 4));
        assertTrue(FoundationStilts.isStiltColumn(1, 1, 1));
    }

    @Test
    public void stiltsOnlyAnchorOnSturdyGroundWithinTheirDepth() {
        FitFixture.World world = new FitFixture.World(COLUMN);
        world.put(0, 7, 0, state("deepslate"));
        world.put(0, 8, 0, state("sculk_vein"));

        assertEquals(7, FoundationStilts.findStiltAnchorY(world.fitBlocks(), 0, 0, 10, 2, BOTTOM_Y, BOTTOM_Y));
        assertEquals(Integer.MIN_VALUE, FoundationStilts.findStiltAnchorY(world.fitBlocks(), 0, 0, 10, 1, BOTTOM_Y, BOTTOM_Y));
        assertEquals(Integer.MIN_VALUE, FoundationStilts.findStiltAnchorY(
                new FitFixture.World(COLUMN).fitBlocks(), 0, 0, 10, 64, BOTTOM_Y, BOTTOM_Y));
    }
}
