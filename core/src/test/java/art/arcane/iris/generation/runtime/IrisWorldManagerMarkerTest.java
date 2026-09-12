package art.arcane.iris.generation.runtime;

import org.junit.Test;
import org.bukkit.World;
import org.bukkit.entity.LivingEntity;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.ScheduledFuture;
import java.util.concurrent.ScheduledThreadPoolExecutor;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

public class IrisWorldManagerMarkerTest {
    @Test
    public void cancelledMantleCleanupLeavesNoDelayedExecutorEntry() {
        ScheduledThreadPoolExecutor executor = IrisWorldManager.createCleanupExecutor("test");
        try {
            ScheduledFuture<?> future = executor.schedule(() -> {
            }, 1, TimeUnit.DAYS);

            assertEquals(1, executor.getQueue().size());
            assertTrue(future.cancel(false));
            assertEquals(0, executor.getQueue().size());
        } finally {
            executor.shutdownNow();
        }
    }

    @Test
    public void mantleCleanupDoesNotExecuteDelayedTasksAfterShutdown() throws InterruptedException {
        ScheduledThreadPoolExecutor executor = IrisWorldManager.createCleanupExecutor("test");
        AtomicBoolean executed = new AtomicBoolean();
        executor.schedule(() -> executed.set(true), 1, TimeUnit.DAYS);

        executor.shutdown();

        assertTrue(executor.awaitTermination(1, TimeUnit.SECONDS));
        assertFalse(executed.get());
        assertEquals(0, executor.getQueue().size());
    }

    @Test
    public void worldBlockHeightIsTranslatedToMantleHeight() {
        assertEquals(64, WorldBlockDropRouter.toMantleY(0, -64));
        assertEquals(319, WorldBlockDropRouter.toMantleY(255, -64));
        assertEquals(42, WorldBlockDropRouter.toMantleY(42, 0));
    }

    @Test
    public void mantleHeightIsTranslatedToWorldBlockHeight() {
        assertEquals(0, WorldBlockDropRouter.toWorldY(64, -64));
        assertEquals(255, WorldBlockDropRouter.toWorldY(319, -64));
        assertEquals(42, WorldBlockDropRouter.toWorldY(42, 0));
    }

    @Test
    public void entitySaturationUsesTheCompleteLivingEntitySnapshot() {
        World world = mock(World.class);
        LivingEntity first = mock(LivingEntity.class);
        LivingEntity second = mock(LivingEntity.class);
        when(world.getLivingEntities()).thenReturn(List.of(first, second));

        assertEquals(2, WorldEntitySpawner.livingEntityCount(world));
    }

    @Test
    public void deferredDropsUseTheRouteAndFallbackOnlyWhenDeclined() {
        List<String> routed = new ArrayList<>();
        List<String> fallback = new ArrayList<>();

        WorldBlockDropRouter.routeDrops(
                List.of("routed", "fallback"),
                drop -> {
                    if (drop.equals("routed")) {
                        routed.add((String) drop);
                        return true;
                    }
                    return false;
                },
                fallback::add
        );

        assertEquals(List.of("routed"), routed);
        assertEquals(List.of("fallback"), fallback);
    }
}
