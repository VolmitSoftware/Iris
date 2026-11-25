package art.arcane.iris.structure.object;

import art.arcane.volmlib.nativelib.terrain.NativeBlockState;
import org.junit.Test;

import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertSame;
import static org.junit.Assert.assertTrue;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

public class ObjectLeafAndLogRulesTest {
    @Test
    public void leavesNeverCutALog() {
        assertTrue(ObjectBlockPass.keepsExisting(leaves(), log()));
    }

    @Test
    public void leavesReplaceOtherLeavesAndAir() {
        assertFalse(ObjectBlockPass.keepsExisting(leaves(), leaves()));
        assertFalse(ObjectBlockPass.keepsExisting(leaves(), null));
    }

    @Test
    public void logsStillReplaceLeaves() {
        assertFalse(ObjectBlockPass.keepsExisting(log(), leaves()));
    }

    @Test
    public void vinesStillKeepSolidBlocks() {
        NativeBlockState vine = mock(NativeBlockState.class);
        when(vine.isVineBlock()).thenReturn(true);
        NativeBlockState solid = mock(NativeBlockState.class);
        when(solid.isSolid()).thenReturn(true);

        assertTrue(ObjectBlockPass.keepsExisting(vine, solid));
        assertFalse(ObjectBlockPass.keepsExisting(vine, leaves()));
    }

    @Test
    public void customLeavesLosingTheirDecayAreMadePersistent() {
        NativeBlockState persistent = mock(NativeBlockState.class);
        NativeBlockState decaying = mock(NativeBlockState.class);
        when(decaying.key()).thenReturn("craftengine:test/leaves[persistent=false,distance=7]");
        when(decaying.withProperty("persistent", "true")).thenReturn(persistent);

        assertSame(persistent, paletteOf(true, decaying).decayed(0));
    }

    @Test
    public void customLeavesWithoutADistanceAreMadePersistentToo() {
        NativeBlockState persistent = mock(NativeBlockState.class);
        NativeBlockState decaying = mock(NativeBlockState.class);
        when(decaying.key()).thenReturn("craftengine:test/leaves[persistent=false]");
        when(decaying.withProperty("persistent", "true")).thenReturn(persistent);

        assertSame(persistent, paletteOf(true, decaying).decayed(0));
    }

    @Test
    public void decayIsLeftAloneWhenItIsNotPrevented() {
        NativeBlockState decaying = mock(NativeBlockState.class);
        when(decaying.key()).thenReturn("minecraft:oak_leaves[distance=3,persistent=false]");

        assertSame(decaying, paletteOf(false, decaying).decayed(0));
    }

    @Test
    public void blocksWithoutAPersistentPropertyAreLeftAlone() {
        NativeBlockState stone = mock(NativeBlockState.class);
        when(stone.key()).thenReturn("minecraft:stone");

        assertSame(stone, paletteOf(true, stone).decayed(0));
    }

    private static PlacementPalette paletteOf(boolean preventingDecay, NativeBlockState state) {
        return new PlacementPalette(new NativeBlockState[]{state}, new IrisObjectRotation(), 0, 0, 0,
                preventingDecay, false, java.util.Set.of());
    }

    private static NativeBlockState leaves() {
        NativeBlockState state = mock(NativeBlockState.class);
        when(state.isLeaves()).thenReturn(true);
        return state;
    }

    private static NativeBlockState log() {
        NativeBlockState state = mock(NativeBlockState.class);
        when(state.isLog()).thenReturn(true);
        return state;
    }
}
