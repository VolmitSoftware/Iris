package art.arcane.iris.modded;

import art.arcane.volmlib.nativelib.minecraft26_2.modded.NativeEntityLoot;
import art.arcane.volmlib.nativelib.terrain.NativeWorld;
import org.junit.Test;

import java.util.Set;

import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

public class ModdedDeathLootTest {
    @Test
    public void unboundEntityRetainsItsNativeBaseLoot() {
        NativeEntityLoot entity = mock(NativeEntityLoot.class);
        when(entity.world()).thenReturn(mock(NativeWorld.class));
        when(entity.tags()).thenReturn(Set.of("unrelated"));
        assertFalse(ModdedDeathLoot.replaceBaseLoot(entity));
    }

    @Test
    public void malformedBindingStillSuppressesNativeBaseLoot() {
        NativeEntityLoot entity = mock(NativeEntityLoot.class);
        when(entity.world()).thenReturn(mock(NativeWorld.class));
        when(entity.tags()).thenReturn(Set.of("iris_loot|invalid"));
        assertTrue(ModdedDeathLoot.replaceBaseLoot(entity));
    }

    @Test
    public void entityOutsideServerWorldRetainsNativeBaseLoot() {
        NativeEntityLoot entity = mock(NativeEntityLoot.class);
        assertFalse(ModdedDeathLoot.replaceBaseLoot(entity));
        assertFalse(ModdedDeathLoot.replaceBaseLoot(null));
    }
}
