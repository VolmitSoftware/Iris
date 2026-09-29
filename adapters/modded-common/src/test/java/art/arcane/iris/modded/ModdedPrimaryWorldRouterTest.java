package art.arcane.iris.modded;

import art.arcane.volmlib.nativelib.minecraft26_2.modded.NativeModdedServer;
import art.arcane.volmlib.nativelib.minecraft26_2.modded.NativeProtocolPlayer;
import art.arcane.volmlib.nativelib.minecraft26_2.modded.NativeWorldTeleport;
import art.arcane.volmlib.nativelib.terrain.NativeWorld;
import org.junit.After;
import org.junit.Test;
import org.mockito.MockedStatic;
import org.mockito.invocation.InvocationOnMock;

import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.Consumer;

import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertSame;
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
    public void aPrimaryWorldThisSaveListsButDidNotRestoreRefusesPlayers() {
        NativeModdedServer server = mock(NativeModdedServer.class);
        NativeProtocolPlayer online = mock(NativeProtocolPlayer.class);
        onlinePlayers(server, online);
        ModdedModConfig routed = config(true, PRIMARY);

        try (MockedStatic<ModdedModConfig> configs = mockStatic(ModdedModConfig.class);
             MockedStatic<ModdedDimensionManager> manager = mockStatic(ModdedDimensionManager.class);
             MockedStatic<ModdedDimensionRegistryStore> registry = mockStatic(ModdedDimensionRegistryStore.class);
             MockedStatic<ModdedStartup> startup = mockStatic(ModdedStartup.class);
             MockedStatic<ModdedIrisLog> log = mockStatic(ModdedIrisLog.class)) {
            configs.when(ModdedModConfig::get).thenReturn(routed);
            manager.when(() -> ModdedDimensionManager.level(server, PRIMARY)).thenReturn(null);
            registry.when(() -> ModdedDimensionRegistryStore.get(server, PRIMARY)).thenReturn(persisted());
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
     * primaryWorld lives in the instance-wide modded.json, but the dimension it names lives in one save. Another
     * save, a new world or a reset world folder never had it, so nothing failed there and nothing is refused.
     */
    @Test
    public void aPrimaryWorldThisSaveNeverHadLeavesRoutingIdleWithOneWarning() {
        NativeModdedServer server = mock(NativeModdedServer.class);
        NativeProtocolPlayer online = mock(NativeProtocolPlayer.class);
        onlinePlayers(server, online);
        ModdedModConfig routed = config(true, PRIMARY);

        try (MockedStatic<ModdedModConfig> configs = mockStatic(ModdedModConfig.class);
             MockedStatic<ModdedDimensionManager> manager = mockStatic(ModdedDimensionManager.class);
             MockedStatic<ModdedDimensionRegistryStore> registry = mockStatic(ModdedDimensionRegistryStore.class);
             MockedStatic<ModdedStartup> startup = mockStatic(ModdedStartup.class);
             MockedStatic<ModdedIrisLog> log = mockStatic(ModdedIrisLog.class)) {
            configs.when(ModdedModConfig::get).thenReturn(routed);
            manager.when(() -> ModdedDimensionManager.level(server, PRIMARY)).thenReturn(null);
            registry.when(() -> ModdedDimensionRegistryStore.get(server, PRIMARY)).thenReturn(null);
            startup.when(ModdedStartup::dimensionsRestored).thenReturn(true);

            evaluate(server);
            evaluate(server);

            verify(online, never()).disconnect(anyString());
            assertFalse(ModdedPrimaryWorldRouter.refuseIfUnavailable(mock(NativeProtocolPlayer.class)));
            log.verify(() -> ModdedIrisLog.warn(argThat((String line) -> line != null && line.contains(PRIMARY))), times(1));
            log.verify(() -> ModdedIrisLog.error(anyString()), never());
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
             MockedStatic<ModdedDimensionRegistryStore> registry = mockStatic(ModdedDimensionRegistryStore.class);
             MockedStatic<ModdedStartup> startup = mockStatic(ModdedStartup.class);
             MockedStatic<ModdedIrisLog> log = mockStatic(ModdedIrisLog.class)) {
            registry.when(() -> ModdedDimensionRegistryStore.get(server, PRIMARY)).thenReturn(persisted());
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

    /**
     * A registry that cannot be read cannot prove the primary world was never here, so it counts as expected.
     */
    @Test
    public void anUnreadableRegistryRefusesPlayersRatherThanGuessing() {
        NativeModdedServer server = mock(NativeModdedServer.class);
        NativeProtocolPlayer online = mock(NativeProtocolPlayer.class);
        onlinePlayers(server, online);
        ModdedModConfig routed = config(true, PRIMARY);

        try (MockedStatic<ModdedModConfig> configs = mockStatic(ModdedModConfig.class);
             MockedStatic<ModdedDimensionManager> manager = mockStatic(ModdedDimensionManager.class);
             MockedStatic<ModdedDimensionRegistryStore> registry = mockStatic(ModdedDimensionRegistryStore.class);
             MockedStatic<ModdedStartup> startup = mockStatic(ModdedStartup.class);
             MockedStatic<ModdedIrisLog> log = mockStatic(ModdedIrisLog.class)) {
            configs.when(ModdedModConfig::get).thenReturn(routed);
            manager.when(() -> ModdedDimensionManager.level(server, PRIMARY)).thenReturn(null);
            registry.when(() -> ModdedDimensionRegistryStore.get(server, PRIMARY))
                    .thenThrow(new IllegalStateException("registry could not be read"));
            startup.when(ModdedStartup::dimensionsRestored).thenReturn(true);

            evaluate(server);

            verify(online).disconnect(contains(PRIMARY));
            assertTrue(ModdedPrimaryWorldRouter.refuseIfUnavailable(mock(NativeProtocolPlayer.class)));
        }
    }

    /**
     * Routing waits on the primary world's first chunk, which on a cold world also loads the pack and binds the
     * engine, so it gets the same bounded deadline as /iris tp instead of timing out and retrying every second.
     */
    @Test
    public void routingWaitsForAColdPrimaryWorldWithinTheTeleportDeadline() {
        NativeModdedServer server = mock(NativeModdedServer.class);
        NativeWorld overworld = mock(NativeWorld.class);
        when(overworld.nativeHandle()).thenReturn(new Object());
        NativeWorld primary = mock(NativeWorld.class);
        when(primary.nativeHandle()).thenReturn(new Object());
        when(server.overworld()).thenReturn(overworld);
        NativeProtocolPlayer player = mock(NativeProtocolPlayer.class);
        when(player.id()).thenReturn(UUID.randomUUID());
        when(player.isInWorld(overworld)).thenReturn(true);
        onlinePlayers(server, player);
        ModdedModConfig routed = config(true, PRIMARY);
        AtomicReference<NativeWorldTeleport.Destination> requested = new AtomicReference<>();

        long before = System.nanoTime();
        try (MockedStatic<ModdedModConfig> configs = mockStatic(ModdedModConfig.class);
             MockedStatic<ModdedDimensionManager> manager = mockStatic(ModdedDimensionManager.class);
             MockedStatic<NativeWorldTeleport> teleport = mockStatic(NativeWorldTeleport.class)) {
            configs.when(ModdedModConfig::get).thenReturn(routed);
            manager.when(() -> ModdedDimensionManager.level(server, PRIMARY)).thenReturn(primary);
            teleport.when(() -> NativeWorldTeleport.teleport(any(), any())).thenAnswer((InvocationOnMock invocation) -> {
                requested.set(invocation.getArgument(1));
                return new CompletableFuture<Boolean>();
            });

            evaluate(server);
        }
        long after = System.nanoTime();

        assertSame(primary, requested.get().world());
        long deadline = requested.get().deadlineNanos();
        assertTrue("deadline must outlast a cold first chunk", deadline - before >= TimeUnit.SECONDS.toNanos(60L));
        assertTrue("deadline must be bounded", deadline - after <= TimeUnit.SECONDS.toNanos(120L));
    }

    private static ModdedDimensionRegistryStore.PersistentDimension persisted() {
        return new ModdedDimensionRegistryStore.PersistentDimension(PRIMARY, "overworld", "overworld", 1337L);
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
