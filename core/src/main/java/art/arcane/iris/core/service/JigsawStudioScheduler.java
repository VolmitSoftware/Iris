package art.arcane.iris.core.service;

import com.volmit.ModuleLog;
import com.volmit.tooling.world.ToolPlayer;
import com.volmit.tooling.world.ToolWorld;

import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.RejectedExecutionException;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.ScheduledThreadPoolExecutor;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * The studio's own clock: background work, and work that waits some game ticks before it hops to the thread that owns a
 * player or a chunk. A tick is {@value #TICK_MILLIS} milliseconds here, as the server runs when it keeps up.
 */
final class JigsawStudioScheduler {
    static final long TICK_MILLIS = 50L;
    private static final int WORKER_THREADS = 2;
    private static final String THREAD_NAME_PREFIX = "jigsaw-studio-";

    private final ModuleLog log;
    private final ScheduledExecutorService executor;
    private final Set<UUID> departedPlayers = ConcurrentHashMap.newKeySet();

    JigsawStudioScheduler(ModuleLog log) {
        this.log = log;
        AtomicInteger threadNumber = new AtomicInteger();
        ScheduledThreadPoolExecutor pool = new ScheduledThreadPoolExecutor(WORKER_THREADS, task -> {
            Thread thread = new Thread(task, THREAD_NAME_PREFIX + threadNumber.incrementAndGet());
            thread.setDaemon(true);
            return thread;
        });
        pool.setRemoveOnCancelPolicy(true);
        this.executor = pool;
    }

    /** Runs {@code task} off the server's threads, as soon as a worker is free. */
    void async(Runnable task) {
        try {
            executor.execute(guarded(task));
        } catch (RejectedExecutionException stopped) {
            log.debug("A Jigsaw Studio background task was dropped because the studio stopped.");
        }
    }

    /** Runs {@code task} on a studio worker after {@code delayTicks}; it hops to a platform thread itself where it must. */
    void later(int delayTicks, Runnable task) {
        try {
            executor.schedule(guarded(task), delayTicks * TICK_MILLIS, TimeUnit.MILLISECONDS);
        } catch (RejectedExecutionException stopped) {
            log.debug("A Jigsaw Studio timer was dropped because the studio stopped.");
        }
    }

    /**
     * Runs {@code task} on the player's thread after {@code delayTicks}. {@code retired} runs instead when the player has
     * left by then or the scheduler is stopped.
     */
    void onPlayerLater(ToolPlayer player, Runnable task, int delayTicks, Runnable retired) {
        Runnable hop = () -> {
            if (departedPlayers.contains(player.id())) {
                retired.run();
                return;
            }
            JigsawStudioThreads.run(player, task);
        };
        try {
            executor.schedule(guarded(hop), delayTicks * TICK_MILLIS, TimeUnit.MILLISECONDS);
        } catch (RejectedExecutionException stopped) {
            retired.run();
        }
    }

    /**
     * Runs {@code task} on the thread that owns the chunk after {@code delayTicks}. {@code notScheduled} runs instead when
     * the host cannot schedule it.
     */
    void onRegionLater(ToolWorld world, int chunkX, int chunkZ, Runnable task, int delayTicks, Runnable notScheduled) {
        Runnable hop = () -> JigsawStudioThreads.onRegion(log, world, chunkX, chunkZ, task, notScheduled);
        try {
            executor.schedule(guarded(hop), delayTicks * TICK_MILLIS, TimeUnit.MILLISECONDS);
        } catch (RejectedExecutionException stopped) {
            notScheduled.run();
        }
    }

    void playerEntered(UUID playerId) {
        departedPlayers.remove(playerId);
    }

    void playerLeft(UUID playerId) {
        departedPlayers.add(playerId);
    }

    void stop() {
        executor.shutdownNow();
    }

    private Runnable guarded(Runnable task) {
        return () -> {
            try {
                task.run();
            } catch (Throwable failure) {
                log.error("A Jigsaw Studio background task failed.", failure);
            }
        };
    }
}
