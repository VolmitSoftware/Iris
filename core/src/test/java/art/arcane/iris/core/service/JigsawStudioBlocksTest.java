package art.arcane.iris.core.service;

import art.arcane.iris.engine.object.prefab.ObjectTurn;
import com.volmit.block.BlockRegistry;
import com.volmit.block.BlockState;
import org.junit.Test;

import java.util.Optional;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertSame;
import static org.junit.Assert.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

public class JigsawStudioBlocksTest {
    private static final ObjectTurn QUARTER_TURN = ObjectTurn.of(0, 90.0D, 0);

    @Test
    public void aBlockTheRegistryCannotTurnComesBackAsNothingNotAsAir() {
        BlockRegistry registry = mock(BlockRegistry.class);
        BlockState sign = BlockState.parse("minecraft:oak_sign");
        when(registry.tryRotate(any(), any())).thenReturn(Optional.empty());

        assertNull(JigsawStudioBlocks.rotated(registry, sign, QUARTER_TURN));
    }

    @Test
    public void airThatStaysAirIsNotABlockThatWasDropped() {
        BlockRegistry registry = mock(BlockRegistry.class);
        BlockState caveAir = BlockState.parse("minecraft:cave_air");
        when(registry.tryRotate(any(), any())).thenReturn(Optional.of(caveAir));

        assertSame(caveAir, JigsawStudioBlocks.rotated(registry, caveAir, QUARTER_TURN));
    }

    @Test
    public void aTurnThatTurnsNothingNeverAsksTheRegistry() {
        BlockRegistry registry = mock(BlockRegistry.class);
        BlockState stairs = BlockState.parse("minecraft:oak_stairs[facing=east]");

        assertSame(stairs, JigsawStudioBlocks.rotated(registry, stairs, ObjectTurn.of(0, 0, 0)));
        verifyNoInteractions(registry);
    }

    @Test
    public void aWrittenBlockMatchesOnlyTheSameBlockWithEveryPropertyEqual() {
        BlockState written = BlockState.parse("minecraft:chest[facing=east,type=single,waterlogged=false]");

        assertTrue(JigsawStudioService.sameBlock(
                BlockState.parse("minecraft:chest[facing=east,type=single,waterlogged=false]"), written));
        assertFalse(JigsawStudioService.sameBlock(
                BlockState.parse("minecraft:chest[facing=east,type=single,waterlogged=true]"), written));
        assertFalse(JigsawStudioService.sameBlock(BlockState.parse("minecraft:chest"), written));
        assertFalse(JigsawStudioService.sameBlock(BlockState.parse("minecraft:trapped_chest"), written));
    }

    @Test
    public void everyKindOfAirIsItsOwnBlockWhenMaterializationIsChecked() {
        assertFalse(JigsawStudioService.sameBlock(
                BlockState.parse("minecraft:cave_air"), BlockState.parse("minecraft:air")));
        assertEquals(BlockState.parse("minecraft:air"), JigsawStudioBlocks.AIR);
    }
}
