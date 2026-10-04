package art.arcane.iris.generation.decoration;

import art.arcane.volmlib.nativelib.terrain.NativeBlockState;
import art.arcane.volmlib.util.hunk.Hunk;
import org.junit.Test;

import static org.junit.Assert.assertSame;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

public class IrisPlacementWaterloggingTest {
    @Test
    public void authoredWetStateIsClearedForDryAndLavaTargets() {
        NativeBlockState wet = state("minecraft:oak_slab[type=bottom,waterlogged=true]");
        NativeBlockState dry = state("minecraft:oak_slab[type=bottom,waterlogged=false]");
        when(wet.withProperty("waterlogged", "false")).thenReturn(dry);
        assertSame(dry, IrisProceduralBlocks.normalizeWaterlogging(wet, state("minecraft:cave_air"), false));
        assertSame(dry, IrisProceduralBlocks.normalizeWaterlogging(wet, state("minecraft:lava"), true));
    }

    @Test
    public void waterloggedDecorationRequiresActualWaterAtItsOwnVoxel() {
        NativeBlockState dry = state("minecraft:oak_slab[type=bottom,waterlogged=false]");
        NativeBlockState wet = state("minecraft:oak_slab[type=bottom,waterlogged=true]");
        NativeBlockState water = state("minecraft:water");
        when(water.isWater()).thenReturn(true);
        when(dry.withProperty("waterlogged", "true")).thenReturn(wet);
        assertSame(wet, IrisProceduralBlocks.normalizeWaterlogging(dry, water, true));
        assertSame(dry, IrisProceduralBlocks.normalizeWaterlogging(dry, water, false));
        assertSame(wet, IrisProceduralBlocks.normalizeWaterlogging(wet, water, false));
        Hunk<NativeBlockState> hunk = Hunk.newArrayHunk(16, 64, 16);
        hunk.set(3, 28, 5, water);
        hunk.set(3, 29, 5, state("minecraft:cave_air"));
        when(wet.withProperty("waterlogged", "false")).thenReturn(dry);
        assertSame(wet, DecoratorCore.fixFacesForHunk(dry, hunk, 3, 5, 3, 28, 5, null));
        assertSame(dry, DecoratorCore.fixFacesForHunk(wet, hunk, 3, 5, 3, 29, 5, null));
    }

    private NativeBlockState state(String key) {
        NativeBlockState state = mock(NativeBlockState.class);
        when(state.key()).thenReturn(key);
        return state;
    }
}
