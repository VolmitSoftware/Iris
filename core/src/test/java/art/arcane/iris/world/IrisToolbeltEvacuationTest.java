package art.arcane.iris.world;

import art.arcane.iris.platform.bukkit.nms.INMS;
import art.arcane.iris.world.task.J;
import art.arcane.volmlib.util.bukkit.WorldIdentity;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.NamespacedKey;
import org.bukkit.World;
import org.bukkit.entity.Player;
import org.junit.Test;
import org.mockito.MockedStatic;

import java.util.List;
import java.util.concurrent.CompletableFuture;

import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.mockStatic;
import static org.mockito.Mockito.verifyNoInteractions;

public class IrisToolbeltEvacuationTest {
    @Test
    public void evacuationCompletionWaitsForEveryPlayerTeleport() {
        CompletableFuture<Boolean> first = new CompletableFuture<>();
        CompletableFuture<Boolean> second = new CompletableFuture<>();

        CompletableFuture<Boolean> evacuation = IrisToolbelt.settleEvacuations(List.of(first, second));

        first.complete(true);
        assertFalse(evacuation.isDone());
        second.complete(true);
        assertTrue(evacuation.join());
    }

    @Test
    public void failedPlayerTeleportFailsEvacuation() {
        CompletableFuture<Boolean> evacuation = IrisToolbelt.settleEvacuations(List.of(
                CompletableFuture.completedFuture(true),
                CompletableFuture.completedFuture(false)));

        assertFalse(evacuation.join());
    }

    @Test
    public void exceptionalPlayerTeleportFailsEvacuation() {
        CompletableFuture<Boolean> evacuation = IrisToolbelt.settleEvacuations(List.of(
                CompletableFuture.completedFuture(true),
                CompletableFuture.failedFuture(new IllegalStateException("teleport failed"))));

        assertFalse(evacuation.join());
    }
    @Test
    public void rejectedEntitySchedulingDoesNotRunPlayerWorkOnCallerThread() {
        assertSchedulerFailureSettles(false);
    }

    @Test
    public void retiredEntitySchedulingSettlesWithoutWaitingForTeleport() {
        assertSchedulerFailureSettles(true);
    }

    private static void assertSchedulerFailureSettles(boolean retired) {
        World source = mock(World.class);
        World target = mock(World.class);
        Player player = mock(Player.class);
        doReturn(List.of(player)).when(source).getPlayers();
        doReturn(new Location(target, 0.5D, 80D, 0.5D)).when(target).getSpawnLocation();
        try (MockedStatic<Bukkit> bukkit = mockStatic(Bukkit.class);
             MockedStatic<WorldIdentity> identity = mockStatic(WorldIdentity.class);
             MockedStatic<INMS> nms = mockStatic(INMS.class);
             MockedStatic<J> scheduler = mockStatic(J.class)) {
            bukkit.when(Bukkit::getWorlds).thenReturn(List.of(source, target));
            identity.when(() -> WorldIdentity.key(source)).thenReturn(new NamespacedKey("iris", "evacuate_source"));
            identity.when(() -> WorldIdentity.key(target)).thenReturn(NamespacedKey.minecraft("overworld"));
            scheduler.when(() -> J.runEntity(eq(player), any(Runnable.class), eq(0), any(Runnable.class)))
                    .thenAnswer(invocation -> {
                        if (retired) {
                            Runnable retirement = invocation.getArgument(3);
                            retirement.run();
                        }
                        return retired;
                    });

            CompletableFuture<Boolean> result = IrisToolbelt.evacuateAsync(source);

            assertTrue(result.isDone());
            assertFalse(result.join());
            scheduler.verify(() -> J.runEntity(eq(player), any(Runnable.class), eq(0), any(Runnable.class)));
            verifyNoInteractions(player);
        }
    }

}
