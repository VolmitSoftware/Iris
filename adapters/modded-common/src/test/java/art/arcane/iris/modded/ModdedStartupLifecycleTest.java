package art.arcane.iris.modded;

import art.arcane.volmlib.nativelib.minecraft26_2.modded.NativeModdedServer;
import org.junit.After;
import org.junit.Test;
import org.mockito.MockedStatic;

import java.util.List;
import java.util.concurrent.atomic.AtomicBoolean;

import static org.junit.Assert.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.CALLS_REAL_METHODS;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.mockStatic;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

public class ModdedStartupLifecycleTest {
    @After
    public void resetStartup() {
        ModdedStartup.reset();
    }

    @Test
    @SuppressWarnings("unchecked")
    public void restoresAfterWorldsLoadWithoutRepeatingDuringServerTicks() {
        NativeModdedServer server = mock(NativeModdedServer.class);
        AtomicBoolean worldsRefreshed = new AtomicBoolean();
        when(server.refreshWorlds()).thenAnswer(invocation -> {
            worldsRefreshed.set(true);
            return Thread.currentThread();
        });
        when(server.hasPlayerList()).thenReturn(true);
        ModdedDimensionRegistryStore.PersistentDimension dimension =
                new ModdedDimensionRegistryStore.PersistentDimension("iris:moon", "overworld", "overworld", 1337L);
        ModdedDimensionInjection<Object, Object, Object> restore = mock(ModdedDimensionInjection.class);
        when(restore.finish()).thenAnswer(invocation -> {
            assertTrue(worldsRefreshed.get());
            return List.of();
        });

        try (MockedStatic<ModdedStartup> startup = mockStatic(ModdedStartup.class, CALLS_REAL_METHODS);
             MockedStatic<ModdedDimensionRegistryStore> store = mockStatic(ModdedDimensionRegistryStore.class);
             MockedStatic<ModdedDimensionInjection> injection = mockStatic(ModdedDimensionInjection.class);
             MockedStatic<ModdedForcedDatapack> datapack = mockStatic(ModdedForcedDatapack.class);
             MockedStatic<ModdedMixinAudit> audit = mockStatic(ModdedMixinAudit.class);
             MockedStatic<ModdedIrisLog> log = mockStatic(ModdedIrisLog.class)) {
            startup.when(ModdedStartup::prepareForStartup).thenAnswer(invocation -> null);
            store.when(() -> ModdedDimensionRegistryStore.loadForStartup(server)).thenReturn(List.of(dimension));
            injection.when(() -> ModdedDimensionInjection.start(eq(List.of(dimension)), any())).thenReturn(restore);

            ModdedLifecycleCallbacks.create().levelsReady().accept(server);

            verify(restore).finish();
            ModdedStartup.runOnce(server);
            ModdedStartup.runOnce(server);
            store.verify(() -> ModdedDimensionRegistryStore.loadForStartup(server), times(1));
            injection.verify(() -> ModdedDimensionInjection.start(any(), any()), times(1));
            verify(restore, times(1)).finish();
            datapack.verify(ModdedForcedDatapack::verifyInjected, times(1));
            audit.verify(ModdedMixinAudit::runOnce, times(1));
        }
    }
}
