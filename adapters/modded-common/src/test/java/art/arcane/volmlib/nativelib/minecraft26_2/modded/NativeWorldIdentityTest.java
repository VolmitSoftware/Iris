package art.arcane.volmlib.nativelib.minecraft26_2.modded;

import art.arcane.volmlib.nativelib.terrain.NativeWorld;
import net.minecraft.SharedConstants;
import net.minecraft.server.Bootstrap;
import net.minecraft.server.level.ServerLevel;
import org.junit.BeforeClass;
import org.junit.Test;

import java.util.HashMap;
import java.util.Map;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotEquals;
import static org.junit.Assert.assertNull;
import static org.mockito.Mockito.mock;

public class NativeWorldIdentityTest {
    @BeforeClass
    public static void bootstrapRegistries() {
        SharedConstants.tryDetectVersion();
        Bootstrap.bootStrap();
    }

    @Test
    public void repeatedViewsShareTheSameEngineKey() {
        ServerLevel level = mock(ServerLevel.class);
        NativeWorld first = new ModdedPlatformWorld(level);
        NativeWorld second = new ModdedPlatformWorld(level);
        Map<NativeWorld, String> engines = new HashMap<>();
        engines.put(first, "bound");
        assertEquals(first, second);
        assertEquals(first.hashCode(), second.hashCode());
        assertEquals("bound", engines.get(second));
    }

    @Test
    public void reopeningADimensionDoesNotReuseTheRetiredEngineKey() {
        NativeWorld retired = new ModdedPlatformWorld(mock(ServerLevel.class));
        NativeWorld reopened = new ModdedPlatformWorld(mock(ServerLevel.class));
        Map<NativeWorld, String> engines = new HashMap<>();
        engines.put(retired, "retired");
        assertNotEquals(retired, reopened);
        assertNull(engines.get(reopened));
    }
}
