package art.arcane.iris.core.service;

import com.volmit.ModuleLog;
import com.volmit.geometry.BlockPosition;
import com.volmit.tooling.world.ToolPlayer;
import com.volmit.tooling.world.ToolWorld;

import java.util.concurrent.atomic.AtomicBoolean;
import java.util.function.BooleanSupplier;

/**
 * Moves work onto the thread that owns a player or a chunk, the way the studio needs it: a method that must run on a
 * player's thread asks {@link #owns} and, when it does not, hands itself to {@link #call}, which runs it at once when the
 * caller already is that thread.
 */
final class JigsawStudioThreads {
    private static final int CHUNK_SHIFT = 4;

    private JigsawStudioThreads() {
    }

    /**
     * Whether the calling thread is the one that owns the player. A player runs a task at once on their own thread and
     * later on any other, so a probe task tells which this is.
     */
    static boolean owns(ToolPlayer player) {
        boolean[] ranDuringCall = {false};
        boolean[] stillInCall = {true};
        player.run(() -> ranDuringCall[0] = stillInCall[0]);
        stillInCall[0] = false;
        return ranDuringCall[0];
    }

    /**
     * Runs {@code task} on the player's thread and returns what it returned. When the task has to wait for the thread it
     * returns {@code true}, standing for "scheduled".
     */
    static boolean call(ToolPlayer player, BooleanSupplier task) {
        boolean[] result = {true};
        player.run(() -> result[0] = task.getAsBoolean());
        return result[0];
    }

    /** Runs {@code task} on the player's thread, now when the caller is that thread. */
    static void run(ToolPlayer player, Runnable task) {
        player.run(task);
    }

    /**
     * Runs {@code task} on the thread that owns the chunk. {@code notScheduled} runs instead when the host cannot
     * schedule it; a task that throws is reported to {@code log}.
     */
    static void onRegion(
            ModuleLog log,
            ToolWorld world,
            int chunkX,
            int chunkZ,
            Runnable task,
            Runnable notScheduled
    ) {
        AtomicBoolean started = new AtomicBoolean();
        world.call(chunkOrigin(chunkX, chunkZ), () -> {
            started.set(true);
            runReporting(log, task);
            return null;
        }).whenComplete((ignored, failure) -> {
            if (failure != null && !started.get()) {
                notScheduled.run();
            }
        });
    }

    /** Whether the calling thread owns the chunk. */
    static boolean ownsRegion(ToolWorld world, int chunkX, int chunkZ) {
        return world.owns(chunkOrigin(chunkX, chunkZ));
    }

    static BlockPosition chunkOrigin(int chunkX, int chunkZ) {
        return new BlockPosition(chunkX << CHUNK_SHIFT, 0, chunkZ << CHUNK_SHIFT);
    }

    private static void runReporting(ModuleLog log, Runnable task) {
        try {
            task.run();
        } catch (Throwable failure) {
            log.error("A Jigsaw Studio region task failed.", failure);
        }
    }
}
