package art.arcane.iris.generation.decoration.tree;

import org.junit.Test;
import art.arcane.volmlib.nativelib.terrain.NativeBlockState;

import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertSame;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.junit.Assert.assertTrue;

public class TreeBlockMaterialTest {
    @Test
    public void nativeMaterialUsesTheMemoizedStringWithoutReadingTheStateKey() {
        NativeBlockState state = mock(NativeBlockState.class);
        String material = new String("minecraft:oak_log");
        when(state.materialKey()).thenReturn(material);

        TreeBlockMaterial first = TreeBlockMaterial.of(state);
        TreeBlockMaterial second = TreeBlockMaterial.of(state);

        assertSame(material, first.materialKey());
        assertSame(first.materialKey(), second.materialKey());
        verify(state, never()).key();
        assertEquals(TreeBlockMaterial.of("minecraft:oak_log[axis=x]"), first);
    }

    @Test
    public void nativeMaterialFallsBackToTheStateKeyWhenNotMemoized() {
        NativeBlockState state = mock(NativeBlockState.class);
        when(state.key()).thenReturn("minecraft:oak_log[axis=z]");

        assertEquals(TreeBlockMaterial.of("minecraft:oak_log"), TreeBlockMaterial.of(state));
        verify(state).key();
    }

    @Test
    public void materialComparisonIgnoresBlockStateProperties() {
        TreeBlockMaterial expected = TreeBlockMaterial.of("minecraft:oak_log[axis=y]");

        assertTrue(expected.matches("minecraft:oak_log[axis=x]"));
        assertFalse(expected.matches("minecraft:spruce_log[axis=y]"));
    }
}
