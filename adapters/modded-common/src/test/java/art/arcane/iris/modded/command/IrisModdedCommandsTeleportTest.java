package art.arcane.iris.modded.command;

import art.arcane.iris.localization.IrisLanguage;
import art.arcane.iris.modded.IrisModdedChunkGenerator;
import art.arcane.iris.modded.ModdedDimensionManager;
import art.arcane.iris.modded.localization.ModdedCommandMessages;
import art.arcane.volmlib.nativelib.minecraft26_2.modded.NativeCommandSource;
import art.arcane.volmlib.nativelib.minecraft26_2.modded.NativeModdedServer;
import art.arcane.volmlib.nativelib.minecraft26_2.modded.NativeProtocolPlayer;
import art.arcane.volmlib.nativelib.minecraft26_2.modded.NativeWorldGenerators;
import art.arcane.volmlib.nativelib.minecraft26_2.modded.NativeWorldTeleport;
import art.arcane.volmlib.nativelib.terrain.NativeWorld;
import art.arcane.volmlib.util.localization.MessageArgument;
import net.minecraft.SharedConstants;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.Identifier;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.Bootstrap;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerChunkCache;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.level.Level;
import net.minecraft.world.entity.EntityDimensions;
import net.minecraft.world.entity.Pose;
import org.junit.BeforeClass;
import org.junit.Test;
import org.mockito.MockedStatic;
import org.mockito.invocation.InvocationOnMock;

import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionException;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;
import java.util.concurrent.atomic.AtomicReference;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertThrows;
import static org.junit.Assert.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.mockStatic;
import static org.mockito.Mockito.when;

public class IrisModdedCommandsTeleportTest {
    private static final String DIMENSION = "irisworldgen:1";

    @BeforeClass
    public static void bootstrap() {
        SharedConstants.tryDetectVersion();
        Bootstrap.bootStrap();
    }

    @Test
    public void teleportWaitsForAColdDestinationWithinABoundedDeadline() {
        NativeModdedServer server = mock(NativeModdedServer.class);
        NativeCommandSource source = mock(NativeCommandSource.class);
        when(source.server()).thenReturn(server);
        NativeWorld level = mock(NativeWorld.class);
        when(level.name()).thenReturn(DIMENSION);
        NativeProtocolPlayer player = mock(NativeProtocolPlayer.class);
        when(player.name()).thenReturn("Tester");
        AtomicReference<NativeWorldTeleport.Destination> requested = new AtomicReference<>();

        long before = System.nanoTime();
        try (MockedStatic<NativeWorldGenerators> generators = mockStatic(NativeWorldGenerators.class);
             MockedStatic<ModdedDimensionManager> dimensions = mockStatic(ModdedDimensionManager.class);
             MockedStatic<NativeWorldTeleport> teleport = mockStatic(NativeWorldTeleport.class)) {
            generators.when(() -> NativeWorldGenerators.find(level, IrisModdedChunkGenerator.class))
                    .thenReturn(mock(IrisModdedChunkGenerator.class));
            dimensions.when(() -> ModdedDimensionManager.level(server, DIMENSION)).thenReturn(level);
            teleport.when(() -> NativeWorldTeleport.teleport(any(), any())).thenAnswer((InvocationOnMock invocation) -> {
                requested.set(invocation.getArgument(1));
                return new CompletableFuture<Boolean>();
            });

            assertEquals(1, IrisModdedCommands.tp(source, level, player));
        }
        long after = System.nanoTime();

        long deadline = requested.get().deadlineNanos();
        assertTrue("deadline must outlast a cold first chunk", deadline - before >= TimeUnit.SECONDS.toNanos(60L));
        assertTrue("deadline must be bounded", deadline - after <= TimeUnit.SECONDS.toNanos(120L));
    }

    @Test(timeout = 15_000L)
    public void expiredTeleportReportsTheCallersDeadline() throws Exception {
        MinecraftServer server = mock(MinecraftServer.class);
        when(server.isSameThread()).thenReturn(true);
        ResourceKey<Level> dimension = ResourceKey.create(Registries.DIMENSION, Identifier.parse(DIMENSION));
        ServerChunkCache chunks = mock(ServerChunkCache.class);
        when(chunks.addTicketAndLoadWithRadius(any(), any(), anyInt())).thenReturn(new CompletableFuture<>());
        ServerLevel serverLevel = mock(ServerLevel.class);
        when(serverLevel.getServer()).thenReturn(server);
        when(serverLevel.dimension()).thenReturn(dimension);
        when(serverLevel.getChunkSource()).thenReturn(chunks);
        when(server.getLevel(dimension)).thenReturn(serverLevel);
        ServerPlayer serverPlayer = mock(ServerPlayer.class);
        when(serverPlayer.getUUID()).thenReturn(UUID.randomUUID());
        when(serverPlayer.getDimensions(Pose.STANDING)).thenReturn(EntityDimensions.scalable(0.6F, 1.8F));
        NativeWorld level = mock(NativeWorld.class);
        when(level.nativeHandle()).thenReturn(serverLevel);

        CompletableFuture<Boolean> teleport = NativeWorldTeleport.teleport(NativeProtocolPlayer.fromHandle(serverPlayer),
                new NativeWorldTeleport.Destination(NativeModdedServer.fromHandle(server), level, 8.5D,
                        Double.MIN_VALUE, 8.5D, System.nanoTime() + TimeUnit.SECONDS.toNanos(2L)));

        ExecutionException failure = assertThrows(ExecutionException.class, () -> teleport.get(10L, TimeUnit.SECONDS));
        assertTrue(failure.getCause() instanceof TimeoutException);
        assertTrue(failure.getCause().getMessage(), failure.getCause().getMessage().contains("within 2 seconds"));
    }

    @Test
    public void failureReportsItsRootCauseInsteadOfAnUnloadedDimension() {
        String message = IrisModdedCommands.teleportFailure(DIMENSION, "Tester",
                new CompletionException(new IllegalStateException("Exception generating new chunk")), true, true);

        assertEquals(IrisLanguage.plain(ModdedCommandMessages.IRIS_MODDED_COMMANDS_TELEPORT_FAILED_REASON,
                MessageArgument.untrusted("dimensionId", DIMENSION),
                MessageArgument.untrusted("reason", "Exception generating new chunk")), message);
    }

    @Test
    public void failureWithoutMessageNamesTheFailureType() {
        String message = IrisModdedCommands.teleportFailure(DIMENSION, "Tester", new TimeoutException(), true, true);

        assertEquals(IrisLanguage.plain(ModdedCommandMessages.IRIS_MODDED_COMMANDS_TELEPORT_FAILED_REASON,
                MessageArgument.untrusted("dimensionId", DIMENSION),
                MessageArgument.untrusted("reason", "TimeoutException")), message);
    }

    @Test
    public void unloadedDimensionIsReportedOnlyWhenItIsGone() {
        assertEquals(IrisLanguage.plain(ModdedCommandMessages.IRIS_MODDED_COMMANDS_TELEPORT_FAILED_DIMENSION_IS_NOT_LOADED,
                        MessageArgument.untrusted("dimensionId", DIMENSION)),
                IrisModdedCommands.teleportFailure(DIMENSION, "Tester", null, false, true));
        assertEquals(IrisLanguage.plain(ModdedCommandMessages.IRIS_MODDED_COMMANDS_TELEPORT_CANCELLED_PLAYER_OFFLINE,
                        MessageArgument.untrusted("dimensionId", DIMENSION),
                        MessageArgument.untrusted("value", "Tester")),
                IrisModdedCommands.teleportFailure(DIMENSION, "Tester", null, true, false));
        assertEquals(IrisLanguage.plain(ModdedCommandMessages.IRIS_MODDED_COMMANDS_TELEPORT_REFUSED,
                        MessageArgument.untrusted("dimensionId", DIMENSION)),
                IrisModdedCommands.teleportFailure(DIMENSION, "Tester", null, true, true));
    }
}
