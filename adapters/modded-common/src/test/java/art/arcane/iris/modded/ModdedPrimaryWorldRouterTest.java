package art.arcane.iris.modded;

import art.arcane.volmlib.nativelib.minecraft26_2.modded.NativeModdedServer;
import art.arcane.volmlib.nativelib.minecraft26_2.modded.NativeProtocolPlayer;
import art.arcane.volmlib.nativelib.terrain.NativeWorld;
import org.junit.After;
import org.junit.Test;
import org.mockito.MockedStatic;

import java.util.function.Consumer;

import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.argThat;
import static org.mockito.ArgumentMatchers.contains;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.mockStatic;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * A configured primary Iris world that did not load used to be skipped silently, leaving every player in the
 * overworld generating terrain Iris was configured to replace.
 */
public class ModdedPrimaryWorldRouterTest {
    private static final int TICK_INTERVAL = 20;
    private static final String PRIMARY = "iris:primary";

    @After
    public void clearRouter() {
        ModdedPrimaryWorldRouter.clear();
    }

    @Test
    public void aMissingPrimaryWorldRefusesPlayersOnceStartupHasRestoredDimensions() {
        NativeModdedServer server = mock(NativeModdedServer.class);
        NativeProtocolPlayer online = mock(NativeProtocolPlayer.class);
        onlinePlayers(server, online);
        ModdedModConfig routed = config(true, PRIMARY);

        try (MockedStatic<ModdedModConfig> configs = mockStatic(ModdedModConfig.class);
             MockedStatic<ModdedDimensionManager> manager = mockStatic(ModdedDimensionManager.class);
             MockedStatic<ModdedStartup> startup = mockStatic(ModdedStartup.class);
             MockedStatic<ModdedIrisLog> log = mockStatic(ModdedIrisLog.class)) {
            configs.when(ModdedModConfig::get).thenReturn(routed);
            manager.when(() -> ModdedDimensionManager.level(server, PRIMARY)).thenReturn(null);
            startup.when(ModdedStartup::dimensionsRestored).thenReturn(true);

            evaluate(server);
            evaluate(server);

            verify(online, times(2)).disconnect(contains(PRIMARY));
            log.verify(() -> ModdedIrisLog.error(argThat((String line) -> line != null && line.startsWith("Cause: ")
                    && line.contains(PRIMARY))), times(1));
            NativeProtocolPlayer joining = mock(NativeProtocolPlayer.class);
            assertTrue(ModdedPrimaryWorldRouter.refuseIfUnavailable(joining));
            verify(joining).disconnect(contains(PRIMARY));
        }
    }

    /**
     * Joins are processed before the first Iris tick restores persistent dimensions, so a primary world that is
     * missing then is still loading, not broken.
     */
    @Test
    public void aPrimaryWorldIsNotJudgedBeforeStartupRestoresDimensions() {
        NativeModdedServer server = mock(NativeModdedServer.class);
        NativeProtocolPlayer online = mock(NativeProtocolPlayer.class);
        onlinePlayers(server, online);
        ModdedModConfig routed = config(true, PRIMARY);

        try (MockedStatic<ModdedModConfig> configs = mockStatic(ModdedModConfig.class);
             MockedStatic<ModdedDimensionManager> manager = mockStatic(ModdedDimensionManager.class);
             MockedStatic<ModdedStartup> startup = mockStatic(ModdedStartup.class);
             MockedStatic<ModdedIrisLog> log = mockStatic(ModdedIrisLog.class)) {
            configs.when(ModdedModConfig::get).thenReturn(routed);
            manager.when(() -> ModdedDimensionManager.level(server, PRIMARY)).thenReturn(null);
            startup.when(ModdedStartup::dimensionsRestored).thenReturn(false);

            evaluate(server);

            verify(online, never()).disconnect(anyString());
            assertFalse(ModdedPrimaryWorldRouter.refuseIfUnavailable(mock(NativeProtocolPlayer.class)));
        }
    }

    @Test
    public void refusalLiftsOnceThePrimaryWorldIsLoadedOrUnconfigured() {
        NativeModdedServer server = mock(NativeModdedServer.class);
        NativeWorld overworld = mock(NativeWorld.class);
        when(server.overworld()).thenReturn(overworld);
        onlinePlayers(server, mock(NativeProtocolPlayer.class));
        ModdedModConfig routed = config(true, PRIMARY);
        ModdedModConfig unconfigured = config(true, "");

        try (MockedStatic<ModdedModConfig> configs = mockStatic(ModdedModConfig.class);
             MockedStatic<ModdedDimensionManager> manager = mockStatic(ModdedDimensionManager.class);
             MockedStatic<ModdedStartup> startup = mockStatic(ModdedStartup.class);
             MockedStatic<ModdedIrisLog> log = mockStatic(ModdedIrisLog.class)) {
            startup.when(ModdedStartup::dimensionsRestored).thenReturn(true);
            configs.when(ModdedModConfig::get).thenReturn(routed);
            evaluate(server);
            assertTrue(ModdedPrimaryWorldRouter.refuseIfUnavailable(mock(NativeProtocolPlayer.class)));

            configs.when(ModdedModConfig::get).thenReturn(unconfigured);
            evaluate(server);
            assertFalse(ModdedPrimaryWorldRouter.refuseIfUnavailable(mock(NativeProtocolPlayer.class)));

            configs.when(ModdedModConfig::get).thenReturn(routed);
            evaluate(server);
            manager.when(() -> ModdedDimensionManager.level(server, PRIMARY)).thenReturn(overworld);
            evaluate(server);
            assertFalse(ModdedPrimaryWorldRouter.refuseIfUnavailable(mock(NativeProtocolPlayer.class)));
        }
    }

    private static void evaluate(NativeModdedServer server) {
        for (int tick = 0; tick < TICK_INTERVAL; tick++) {
            ModdedPrimaryWorldRouter.tick(server);
        }
    }

    @SuppressWarnings("unchecked")
    private static void onlinePlayers(NativeModdedServer server, NativeProtocolPlayer player) {
        doAnswer(invocation -> {
            invocation.getArgument(0, Consumer.class).accept(player);
            return null;
        }).when(server).forEachPlayer(any());
    }

    private static ModdedModConfig config(boolean route, String primary) {
        ModdedModConfig config = mock(ModdedModConfig.class);
        when(config.routePlayersToPrimaryWorld()).thenReturn(route);
        when(config.primaryWorld()).thenReturn(primary);
        return config;
    }
}
