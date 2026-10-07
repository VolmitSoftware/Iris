package art.arcane.iris.modded;

import art.arcane.volmlib.nativelib.minecraft26_2.modded.NativeBlockFaces;
import art.arcane.volmlib.nativelib.terrain.NativeBlockState;
import art.arcane.volmlib.util.hunk.Hunk;
import org.junit.Test;
import org.mockito.MockedStatic;

import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertSame;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.mockStatic;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

public class ModdedDecoratorHooksTest {
    @Test
    public void faceFixingExcludesTheUpperBoundaryWhileKeepingTheLastRowAccessible() {
        NativeBlockState state = mock(NativeBlockState.class);
        NativeBlockState lastRow = mock(NativeBlockState.class);
        Hunk<NativeBlockState> hunk = mock(Hunk.class);
        when(hunk.getHeight()).thenReturn(4);
        when(hunk.get(1, 3, 2)).thenReturn(lastRow);
        when(hunk.get(1, 4, 2)).thenThrow(new IndexOutOfBoundsException("upper boundary"));
        try (MockedStatic<NativeBlockFaces> faces = mockStatic(NativeBlockFaces.class)) {
            faces.when(() -> NativeBlockFaces.fixFaces(eq(state), any(NativeBlockFaces.Neighbors.class)))
                    .thenAnswer(invocation -> {
                        NativeBlockFaces.Neighbors neighbors = invocation.getArgument(1);
                        assertNull(neighbors.secondary(0, 1, 0));
                        assertSame(lastRow, neighbors.secondary(0, 0, 0));
                        return state;
                    });

            assertSame(state, new ModdedDecoratorHooks().fixFaces(state, hunk, 1, 2, 99, 3, 108, null));

            faces.verify(() -> NativeBlockFaces.fixFaces(eq(state), any(NativeBlockFaces.Neighbors.class)));
        }
        verify(hunk, never()).get(1, 4, 2);
        verify(hunk).get(1, 3, 2);
    }
}
