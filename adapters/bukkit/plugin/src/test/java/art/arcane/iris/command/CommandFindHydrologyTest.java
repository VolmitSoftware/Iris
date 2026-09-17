package art.arcane.iris.command;

import art.arcane.iris.Iris;
import art.arcane.iris.generation.hydrology.HydrologyFeatureType;
import art.arcane.iris.generation.runtime.Engine;
import art.arcane.iris.generation.runtime.IrisComplex;
import art.arcane.iris.localization.IrisLanguage;
import art.arcane.iris.platform.bukkit.BukkitPlatform;
import art.arcane.iris.platform.bukkit.plugin.VolmitSender;
import art.arcane.iris.world.history.GenerationSemanticQueries;
import art.arcane.iris.world.task.J;
import art.arcane.volmlib.util.director.annotations.Director;
import org.bukkit.Chunk;
import org.bukkit.Location;
import org.bukkit.World;
import org.bukkit.entity.Player;
import org.junit.Test;
import org.mockito.MockedStatic;

import java.util.Optional;
import java.util.OptionalLong;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.atomic.AtomicReference;

import static org.junit.Assert.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.contains;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.mockStatic;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.mockito.Mockito.CALLS_REAL_METHODS;

public class CommandFindHydrologyTest {
    @Test
    public void riverCapturesPlayerStateOnTheOwningThread() throws Exception {
        assertTrue(CommandFind.class.getMethod("river", String.class, boolean.class)
                .getAnnotation(Director.class).sync());
    }

    @Test
    public void worldChangeDuringSearchDoesNotLoadOrTeleportIntoTheNewWorld() {
        try (Fixture fixture = new Fixture()) {
            fixture.command.river("surface", true);
            when(fixture.player.getWorld()).thenReturn(mock(World.class));
            fixture.search.get().run();

            fixture.platform.verify(() -> BukkitPlatform.chunkAtAsync(any(World.class), anyInt(), anyInt(), eq(true)), never());
            fixture.platform.verify(() -> BukkitPlatform.teleportAsync(any(Player.class), any(Location.class)), never());
            verify(fixture.sender).sendMessage(contains("world changed"));
        }
    }

    @Test
    public void disconnectDuringSearchDoesNotLoadTheDestination() {
        try (Fixture fixture = new Fixture()) {
            fixture.command.river("surface", true);
            when(fixture.player.isOnline()).thenReturn(false);
            fixture.search.get().run();

            fixture.platform.verify(() -> BukkitPlatform.chunkAtAsync(any(World.class), anyInt(), anyInt(), eq(true)), never());
        }
    }

    @Test
    public void worldChangeWhileLoadingTheDestinationCancelsTeleport() {
        try (Fixture fixture = new Fixture()) {
            fixture.command.river("surface", true);
            fixture.search.get().run();
            when(fixture.player.getWorld()).thenReturn(mock(World.class));
            fixture.chunk.complete(mock(Chunk.class));

            fixture.platform.verify(() -> BukkitPlatform.teleportAsync(any(Player.class), any(Location.class)), never());
            verify(fixture.sender).sendMessage(contains("world changed"));
        }
    }

    @Test
    public void successIsReportedOnlyAfterTeleportCompletes() {
        try (Fixture fixture = new Fixture()) {
            fixture.command.river("surface", true);
            fixture.search.get().run();
            fixture.chunk.complete(mock(Chunk.class));

            verify(fixture.sender, never()).sendMessage("teleported");
            fixture.teleport.complete(true);
            verify(fixture.sender).sendMessage("teleported");
        }
    }

    @Test
    public void rejectedTeleportReportsFailureWithoutSuccess() {
        try (Fixture fixture = new Fixture()) {
            fixture.command.river("surface", true);
            fixture.search.get().run();
            fixture.chunk.complete(mock(Chunk.class));
            fixture.teleport.complete(false);

            verify(fixture.sender, never()).sendMessage("teleported");
            verify(fixture.sender).sendMessage(contains("Could not teleport"));
        }
    }

    @Test
    public void failedTeleportReportsTheFullCauseWithoutSuccess() {
        try (Fixture fixture = new Fixture()) {
            IllegalStateException failure = new IllegalStateException("destination unloaded");
            fixture.command.river("surface", true);
            fixture.search.get().run();
            fixture.chunk.complete(mock(Chunk.class));
            fixture.teleport.completeExceptionally(failure);

            verify(fixture.sender, never()).sendMessage("teleported");
            verify(fixture.sender).sendMessage(contains("Could not teleport"));
            fixture.logging.verify(() -> Iris.reportError(any(String.class), eq(failure)));
        }
    }

    private static final class Fixture implements AutoCloseable {
        private final CommandFind command = mock(CommandFind.class, CALLS_REAL_METHODS);
        private final Engine engine = mock(Engine.class);
        private final IrisComplex complex = mock(IrisComplex.class);
        private final Player player = mock(Player.class);
        private final World world = mock(World.class);
        private final VolmitSender sender = mock(VolmitSender.class);
        private final AtomicReference<Runnable> search = new AtomicReference<>();
        private final CompletableFuture<Chunk> chunk = new CompletableFuture<>();
        private final CompletableFuture<Boolean> teleport = new CompletableFuture<>();
        private final MockedStatic<J> scheduler = mockStatic(J.class);
        private final MockedStatic<BukkitPlatform> platform = mockStatic(BukkitPlatform.class);
        private final MockedStatic<GenerationSemanticQueries> queries = mockStatic(GenerationSemanticQueries.class);
        private final MockedStatic<IrisLanguage> language = mockStatic(IrisLanguage.class, invocation -> "teleported");
        private final MockedStatic<Iris> logging = mockStatic(Iris.class);

        private Fixture() {
            doReturn(engine).when(command).engine();
            doReturn(player).when(command).player();
            doReturn(sender).when(command).sender();
            when(engine.getComplex()).thenReturn(complex);
            when(player.getWorld()).thenReturn(world);
            when(player.getLocation()).thenReturn(new Location(world, 0, 70, 0));
            when(player.isOnline()).thenReturn(true);
            when(world.getMinHeight()).thenReturn(-64);
            when(world.getMaxHeight()).thenReturn(320);
            scheduler.when(() -> J.a(any(Runnable.class))).thenAnswer(invocation -> {
                search.set(invocation.getArgument(0));
                return null;
            });
            scheduler.when(() -> J.runEntity(eq(player), any(Runnable.class))).thenAnswer(invocation -> {
                invocation.<Runnable>getArgument(1).run();
                return true;
            });
            scheduler.when(() -> J.runRegion(any(World.class), anyInt(), anyInt(), any(Runnable.class))).thenAnswer(invocation -> {
                invocation.<Runnable>getArgument(3).run();
                return true;
            });
            platform.when(() -> BukkitPlatform.chunkAtAsync(any(World.class), anyInt(), anyInt(), eq(true))).thenReturn(chunk);
            platform.when(() -> BukkitPlatform.teleportAsync(eq(player), any(Location.class))).thenReturn(teleport);
            queries.when(() -> GenerationSemanticQueries.nearestRiver(eq(engine), any(), eq(0), eq(0), anyInt(), any()))
                    .thenReturn(Optional.of(new GenerationSemanticQueries.RiverResult(
                            GenerationSemanticQueries.RiverSource.ACTIVE_PREDICTION,
                            HydrologyFeatureType.RIFFLE, Optional.empty(), 1L, 32, 63, 32, OptionalLong.empty())));
        }

        @Override
        public void close() {
            logging.close();
            language.close();
            queries.close();
            platform.close();
            scheduler.close();
        }
    }
}
