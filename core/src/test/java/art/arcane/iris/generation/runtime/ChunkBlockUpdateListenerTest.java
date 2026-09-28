package art.arcane.iris.generation.runtime;

import art.arcane.volmlib.nativelib.terrain.NativeBlockState;
import org.junit.Test;

import java.util.ArrayList;
import java.util.List;
import java.util.Random;

import static org.junit.Assert.assertEquals;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

public class ChunkBlockUpdateListenerTest {
    @Test
    public void forwardsExactlyTheWritesWithBlockUpdateEffects() {
        NativeBlockState stone = state(false, false);
        NativeBlockState deepslate = state(false, false);
        NativeBlockState water = state(true, false);
        NativeBlockState custom = state(false, true);
        NativeBlockState[] palette = {stone, deepslate, water, custom, null};
        Random random = new Random(42L);
        List<String> expected = new ArrayList<>();
        List<String> actual = new ArrayList<>();
        BlockUpdater reference = effects(expected);
        ChunkBlockUpdateListener listener = new ChunkBlockUpdateListener(effects(actual), 160, -48);

        for (int write = 0; write < 20_000; write++) {
            NativeBlockState state = palette[random.nextInt(8) < 5 ? random.nextInt(2) : random.nextInt(palette.length)];
            int x = random.nextInt(16);
            int y = random.nextInt(384);
            int z = random.nextInt(16);
            reference.catchBlockUpdates(160 + x, y, -48 + z, state);
            listener.onWrite(x, y, z, state);
        }

        assertEquals(expected, actual);
    }

    private static BlockUpdater effects(List<String> log) {
        return (x, y, z, data) -> {
            if (data == null) {
                return;
            }
            if (data.isUpdatable()) {
                log.add("update " + x + "," + y + "," + z);
            }
            if (data.isCustom()) {
                log.add("custom " + (x >> 4) + "," + (z >> 4));
            }
        };
    }

    private static NativeBlockState state(boolean updatable, boolean custom) {
        NativeBlockState state = mock(NativeBlockState.class);
        when(state.isUpdatable()).thenReturn(updatable);
        when(state.isCustom()).thenReturn(custom);
        return state;
    }
}
