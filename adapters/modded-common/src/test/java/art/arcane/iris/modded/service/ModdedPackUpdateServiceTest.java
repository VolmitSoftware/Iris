package art.arcane.iris.modded.service;

import art.arcane.iris.diagnostics.splash.IrisSplashPackScanner.SplashPackMetadata;
import art.arcane.iris.pack.BuiltInPackUpdates;
import art.arcane.iris.spi.IrisPlatform;
import art.arcane.iris.spi.IrisPlatforms;
import art.arcane.volmlib.nativelib.minecraft26_2.modded.NativeModdedServer;
import art.arcane.volmlib.nativelib.minecraft26_2.modded.NativeProtocolPlayer;
import net.minecraft.SharedConstants;
import net.minecraft.server.Bootstrap;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.server.permissions.LevelBasedPermissionSet;
import org.junit.BeforeClass;
import org.junit.Test;
import org.mockito.MockedStatic;

import java.io.File;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.Consumer;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.contains;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.mockStatic;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

public class ModdedPackUpdateServiceTest {
    @BeforeClass
    public static void bootstrap() {
        SharedConstants.tryDetectVersion();
        Bootstrap.bootStrap();
    }

    @Test
    public void pendingCheckNotifiesOnlineAndLaterOperatorsOnceOnServerTick() {
        CompletableFuture<ModdedPackUpdateService.PackCheck> pending = new CompletableFuture<>();
        ModdedPackUpdateService service = new ModdedPackUpdateService(folder -> pending);
        NativeProtocolPlayer online = player(true, false);
        NativeModdedServer server = server(online);
        File root = new File("config/irisworldgen/packs").getAbsoluteFile();
        try (MockedStatic<IrisPlatforms> platforms = platform(root)) {
            service.onEnable();
            service.onServerTick(server);
            service.notifyPlayer(online);
            verify(online, never()).sendMessage(anyString());
            pending.complete(outdated());
            verify(online, never()).sendMessage(anyString());
            service.onServerTick(server);
            service.onServerTick(server);
            service.notifyPlayer(online);
            verify(online, times(1)).sendMessage(contains("installed v1, latest built-in release v2"));
            verify(online, times(1)).sendMessage(contains(new File(root, "overworld").toString()));
            verify(online, times(1)).sendMessage(contains("/iris download pack=overworld overwrite=true"));
            verify(online, never()).sendMessage(contains("plugins/"));
            NativeProtocolPlayer later = player(true, false);
            service.notifyPlayer(later);
            verify(later).sendMessage(contains("release metadata is outdated"));
            service.onDisable();
        }
    }

    @Test
    public void integratedOwnerWithoutCheatsReceivesNoticeAndRemoteOrdinaryPlayersDoNot() {
        NativeProtocolPlayer owner = player(false, true);
        NativeProtocolPlayer remote = player(false, false);
        ModdedPackUpdateService service = new ModdedPackUpdateService(folder -> CompletableFuture.completedFuture(outdated()));
        try (MockedStatic<IrisPlatforms> platforms = platform(new File("config/irisworldgen/packs"))) {
            service.onEnable();
            service.onServerTick(server(owner));
            verify(owner).sendMessage(contains("release metadata is outdated"));
            service.notifyPlayer(remote);
            verify(remote, never()).sendMessage(anyString());
            service.onDisable();
        }
    }

    @Test
    public void disableCancelsCheckAndRestartDoesNotPublishOldNotices() {
        CompletableFuture<ModdedPackUpdateService.PackCheck> first = new CompletableFuture<>();
        CompletableFuture<ModdedPackUpdateService.PackCheck> second = new CompletableFuture<>();
        AtomicInteger checks = new AtomicInteger();
        ModdedPackUpdateService service = new ModdedPackUpdateService(folder -> checks.getAndIncrement() == 0 ? first : second);
        NativeProtocolPlayer operator = player(true, false);
        NativeModdedServer server = server(operator);
        try (MockedStatic<IrisPlatforms> platforms = platform(new File("config/irisworldgen/packs"))) {
            service.onEnable();
            service.onServerTick(server);
            service.onDisable();
            assertTrue(first.isCancelled());
            assertFalse(first.complete(outdated()));
            service.onEnable();
            service.onServerTick(server);
            second.complete(new ModdedPackUpdateService.PackCheck(List.of(), Map.of()));
            service.onServerTick(server);
            verify(operator, never()).sendMessage(anyString());
            assertEquals(2, checks.get());
            service.onDisable();
        }
    }

    @Test
    public void currentCustomUnknownAndUnavailableVersionsNeverBecomeOutdated() {
        ModdedPackUpdateService.PackCheck result = new ModdedPackUpdateService.PackCheck(List.of(
                new SplashPackMetadata("overworld", "unknown"),
                new SplashPackMetadata("underworld", "3"),
                new SplashPackMetadata("custom", "1")), Map.of(
                "overworld", new BuiltInPackUpdates.Update("2"),
                "underworld", new BuiltInPackUpdates.Update("2")));
        ModdedPackUpdateService service = new ModdedPackUpdateService(folder -> CompletableFuture.completedFuture(result));
        NativeProtocolPlayer operator = player(true, false);
        try (MockedStatic<IrisPlatforms> platforms = platform(new File("config/irisworldgen/packs"))) {
            service.onEnable();
            service.onServerTick(server(operator));
            verify(operator, never()).sendMessage(anyString());
            service.onDisable();
        }
    }

    @Test
    public void successfulAuthoringUpdateClearsOldNoticesAndRescansInstalledVersion() {
        CompletableFuture<ModdedPackUpdateService.PackCheck> current = new CompletableFuture<>();
        AtomicInteger checks = new AtomicInteger();
        ModdedPackUpdateService service = new ModdedPackUpdateService(folder -> checks.getAndIncrement() == 0
                ? CompletableFuture.completedFuture(outdated()) : current);
        NativeProtocolPlayer operator = player(true, false);
        NativeModdedServer server = server(operator);
        try (MockedStatic<IrisPlatforms> platforms = platform(new File("config/irisworldgen/packs"))) {
            service.onEnable();
            service.onServerTick(server);
            verify(operator).sendMessage(contains("release metadata is outdated"));
            service.refresh();
            NativeProtocolPlayer later = player(true, false);
            service.notifyPlayer(later);
            verify(later, never()).sendMessage(anyString());
            service.onServerTick(server);
            current.complete(new ModdedPackUpdateService.PackCheck(List.of(new SplashPackMetadata("overworld", "2")),
                    Map.of("overworld", new BuiltInPackUpdates.Update("2"))));
            service.onServerTick(server);
            service.notifyPlayer(later);
            verify(later, never()).sendMessage(anyString());
            assertEquals(2, checks.get());
            service.onDisable();
        }
    }

    @Test
    public void nativeOwnerCheckDoesNotGrantGameMasterPermissions() {
        ServerPlayer handle = mock(ServerPlayer.class);
        ServerLevel level = mock(ServerLevel.class);
        MinecraftServer server = mock(MinecraftServer.class);
        when(handle.level()).thenReturn(level);
        when(level.getServer()).thenReturn(server);
        when(handle.permissions()).thenReturn(LevelBasedPermissionSet.ALL);
        NativeProtocolPlayer player = NativeProtocolPlayer.fromHandle(handle);
        when(server.isSingleplayerOwner(handle.nameAndId())).thenReturn(true);
        assertTrue(player.isServerOwner());
        assertFalse(player.isGameMaster());
        when(server.isSingleplayerOwner(handle.nameAndId())).thenReturn(false);
        assertFalse(player.isServerOwner());
        assertFalse(player.isGameMaster());
    }

    private static ModdedPackUpdateService.PackCheck outdated() {
        return new ModdedPackUpdateService.PackCheck(List.of(new SplashPackMetadata("overworld", "1")),
                Map.of("overworld", new BuiltInPackUpdates.Update("2")));
    }

    private static MockedStatic<IrisPlatforms> platform(File root) {
        IrisPlatform platform = mock(IrisPlatform.class);
        when(platform.packsFolderNoCreate()).thenReturn(root);
        MockedStatic<IrisPlatforms> platforms = mockStatic(IrisPlatforms.class);
        platforms.when(IrisPlatforms::get).thenReturn(platform);
        return platforms;
    }

    private static NativeProtocolPlayer player(boolean operator, boolean owner) {
        NativeProtocolPlayer player = mock(NativeProtocolPlayer.class);
        when(player.id()).thenReturn(UUID.randomUUID());
        when(player.connected()).thenReturn(true);
        when(player.isGameMaster()).thenReturn(operator);
        when(player.isServerOwner()).thenReturn(owner);
        return player;
    }

    @SuppressWarnings("unchecked")
    private static NativeModdedServer server(NativeProtocolPlayer player) {
        NativeModdedServer server = mock(NativeModdedServer.class);
        when(server.hasPlayerList()).thenReturn(true);
        doAnswer(invocation -> {
            invocation.getArgument(0, Consumer.class).accept(player);
            return null;
        }).when(server).forEachPlayer(any());
        return server;
    }
}
