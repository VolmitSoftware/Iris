package art.arcane.iris.studio.view;

import art.arcane.iris.generation.runtime.Engine;
import art.arcane.iris.platform.bukkit.BukkitWorldBinding;
import art.arcane.iris.world.IrisWorld;
import art.arcane.iris.world.task.J;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.World;
import org.bukkit.entity.Player;
import org.junit.Test;
import org.mockito.MockedStatic;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.same;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.mockStatic;
import static org.mockito.Mockito.when;

public class BukkitOpenerPositionTest {
    @Test
    public void playerMarkersAreCapturedOnTheirEntitySchedulers() {
        try (Harness harness = new Harness()) {
            harness.binding.when(() -> BukkitWorldBinding.players(harness.target)).thenReturn(List.of(harness.player));
            when(harness.player.getName()).thenReturn("Explorer");
            assertTrue(harness.overlay.players().isEmpty());

            harness.runEntity();

            List<GuiMarker> markers = harness.overlay.players();
            assertEquals(1, markers.size());
            assertEquals("Explorer", markers.getFirst().label());
            assertEquals(18.5D, markers.getFirst().worldX(), 0D);
            assertEquals(-4.25D, markers.getFirst().worldZ(), 0D);
        }
    }

    @Test
    public void positionIsCapturedOnlyOnTheOwningEntityTask() {
        try (Harness harness = new Harness()) {
            harness.overlay.requestOpenerPosition(harness.results::add);
            assertTrue(harness.results.isEmpty());

            harness.runEntity();

            assertEquals(List.of(OpenerPosition.inWorld(18.5D, 72D, -4.25D)), harness.results);
        }
    }

    @Test
    public void retiredEntityCompletesTheRequestOnce() {
        try (Harness harness = new Harness()) {
            harness.overlay.requestOpenerPosition(harness.results::add);
            assertTrue("retirement callback must be registered", harness.retired != null);

            harness.retired.run();
            harness.retired.run();

            assertEquals(List.of(OpenerPosition.OFFLINE), harness.results);
        }
    }

    @Test
    public void disconnectBeforeCaptureCompletesAsOfflineAndReconnectCanRefresh() {
        try (Harness harness = new Harness()) {
            harness.overlay.requestOpenerPosition(harness.results::add);
            when(harness.player.isOnline()).thenReturn(false);
            harness.runEntity();
            assertEquals(List.of(OpenerPosition.OFFLINE), harness.results);

            when(harness.player.isOnline()).thenReturn(true);
            harness.overlay.requestOpenerPosition(harness.results::add);
            harness.runEntity();
            assertEquals(List.of(OpenerPosition.OFFLINE, OpenerPosition.inWorld(18.5D, 72D, -4.25D)), harness.results);
        }
    }

    @Test
    public void rejectedEntityTaskCompletesOnceEvenIfRetirementAlreadyRan() {
        try (Harness harness = new Harness()) {
            harness.reject = true;
            harness.overlay.requestOpenerPosition(harness.results::add);
            assertEquals(List.of(OpenerPosition.OFFLINE), harness.results);
        }
    }

    @Test
    public void changingWorldBeforeCaptureReportsOtherWorld() {
        try (Harness harness = new Harness()) {
            harness.overlay.requestOpenerPosition(harness.results::add);
            when(harness.player.getWorld()).thenReturn(mock(World.class));
            harness.runEntity();
            assertEquals(List.of(OpenerPosition.IN_OTHER_WORLD), harness.results);
        }
    }

    private static final class Harness implements AutoCloseable {
        private final Player player = mock(Player.class);
        private final World world = mock(World.class);
        private final Engine engine = mock(Engine.class);
        private final IrisWorld target = mock(IrisWorld.class);
        private final UUID opener = UUID.randomUUID();
        private final List<OpenerPosition> results = new ArrayList<>();
        private final MockedStatic<J> scheduling = mockStatic(J.class);
        private final MockedStatic<Bukkit> bukkit = mockStatic(Bukkit.class);
        private final MockedStatic<BukkitWorldBinding> binding = mockStatic(BukkitWorldBinding.class);
        private final BukkitVisionOverlay overlay = new BukkitVisionOverlay(engine, opener);
        private Runnable task;
        private Runnable retired;
        private boolean entityOwned;
        private boolean reject;

        private Harness() {
            when(engine.getWorld()).thenReturn(target);
            when(player.isOnline()).thenReturn(true);
            when(player.getWorld()).thenReturn(world);
            when(player.getLocation()).thenAnswer(invocation -> {
                assertTrue("location read outside owning entity task", entityOwned);
                return new Location(world, 18.5D, 72D, -4.25D);
            });
            bukkit.when(() -> Bukkit.getPlayer(opener)).thenReturn(player);
            binding.when(() -> BukkitWorldBinding.world(target)).thenReturn(world);
            scheduling.when(() -> J.runGlobal(any(Runnable.class))).thenAnswer(invocation -> {
                invocation.getArgument(0, Runnable.class).run();
                return true;
            });
            scheduling.when(() -> J.runEntity(same(player), any(Runnable.class))).thenAnswer(invocation -> {
                task = invocation.getArgument(1);
                return !reject;
            });
            scheduling.when(() -> J.runEntity(same(player), any(Runnable.class), eq(0), any(Runnable.class)))
                    .thenAnswer(invocation -> {
                        task = invocation.getArgument(1);
                        retired = invocation.getArgument(3);
                        if (reject) {
                            retired.run();
                        }
                        return !reject;
                    });
        }

        private void runEntity() {
            entityOwned = true;
            try {
                task.run();
            } finally {
                entityOwned = false;
            }
        }

        @Override
        public void close() {
            binding.close();
            bukkit.close();
            scheduling.close();
        }
    }
}
