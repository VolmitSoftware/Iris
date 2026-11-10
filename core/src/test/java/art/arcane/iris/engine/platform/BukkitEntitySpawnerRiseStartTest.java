package art.arcane.iris.engine.platform;

import art.arcane.iris.util.common.plugin.Chunks;
import art.arcane.iris.util.common.scheduling.J;
import org.bukkit.Location;
import org.bukkit.World;
import org.junit.Test;
import org.mockito.MockedStatic;

import java.util.concurrent.CompletableFuture;

import static org.junit.Assert.assertEquals;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.mockStatic;
import static org.mockito.Mockito.never;

public class BukkitEntitySpawnerRiseStartTest {
    private static final double SPAWN_Y = 70;
    private static final double RISE_START_Y = 65;

    @Test
    public void watchedRiseStartRunsThroughTheRegionFutureAndNeverThroughTheNextTickFuture() {
        World world = mock(World.class);
        Location at = new Location(world, 33, SPAWN_Y, -17);

        try (MockedStatic<J> scheduling = mockStatic(J.class);
             MockedStatic<Chunks> chunks = mockStatic(Chunks.class)) {
            scheduling.when(() -> J.runRegionFuture(eq(world), anyInt(), anyInt(), any(Runnable.class)))
                    .thenAnswer(invocation -> {
                        invocation.<Runnable>getArgument(3).run();
                        return CompletableFuture.completedFuture(null);
                    });
            chunks.when(() -> Chunks.hasPlayersNearby(any(Location.class))).thenReturn(true);

            Location start = BukkitEntitySpawner.startBelowGroundWhenWatched(at);

            assertEquals(RISE_START_Y, start.getY(), 0.0);
            scheduling.verify(() -> J.runRegionFuture(eq(world), eq(2), eq(-2), any(Runnable.class)));
            scheduling.verify(() -> J.sfut(any(Runnable.class)), never());
        }
    }
}
