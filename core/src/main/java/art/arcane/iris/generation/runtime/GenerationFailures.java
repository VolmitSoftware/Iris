package art.arcane.iris.generation.runtime;

import art.arcane.iris.world.history.SavedBiomeUnavailableException;
import art.arcane.volmlib.util.mantle.MantleClosedException;

import java.io.InterruptedIOException;
import java.nio.channels.ClosedChannelException;
import java.util.concurrent.CancellationException;
import java.util.concurrent.RejectedExecutionException;
import java.util.concurrent.TimeoutException;
import java.util.function.Predicate;

/**
 * Separates failures of the engine, its mantle, generation history, executors or the JVM from failures of one
 * piece of content. Content loops (objects, decorators, layers) may skip a failed item because every rebuild
 * skips it the same way. An engine failure is transient, so skipping would cache and persist terrain that a
 * healthy engine never produces: it must fail the whole chunk instead.
 */
public final class GenerationFailures {
    private static final int MAXIMUM_CAUSE_DEPTH = 32;

    private GenerationFailures() {
    }

    public static boolean isEngineFailure(Throwable failure) {
        return isShutdownFailure(failure) || hasCause(failure, (Throwable cause) -> cause instanceof Error
                || cause instanceof WrongEngineBroException
                || cause instanceof SavedBiomeUnavailableException unavailable && unavailable.isLoading()
                || cause instanceof TimeoutException);
    }

    /**
     * The engine failures that only mean a runtime, pool or thread is stopping. They say nothing about the world, so
     * nothing may remember them against it once the next runtime is up.
     */
    public static boolean isShutdownFailure(Throwable failure) {
        return Thread.currentThread().isInterrupted() || hasCause(failure, (Throwable cause) ->
                cause instanceof GenerationClosedException
                        || cause instanceof MantleClosedException
                        || cause instanceof GenerationSessionException session && session.isExpectedTeardown()
                        || cause instanceof InterruptedException
                        || cause instanceof InterruptedIOException
                        || cause instanceof ClosedChannelException
                        || cause instanceof RejectedExecutionException
                        || cause instanceof CancellationException);
    }

    private static boolean hasCause(Throwable failure, Predicate<Throwable> match) {
        Throwable current = failure;
        for (int depth = 0; current != null && depth < MAXIMUM_CAUSE_DEPTH; depth++, current = current.getCause()) {
            if (match.test(current)) {
                return true;
            }
        }
        return false;
    }

    public static void rethrowEngineFailure(Throwable failure) {
        if (!isEngineFailure(failure)) {
            return;
        }
        if (failure instanceof RuntimeException runtimeFailure) {
            throw runtimeFailure;
        }
        if (failure instanceof Error error) {
            throw error;
        }
        if (failure instanceof InterruptedException) {
            Thread.currentThread().interrupt();
        }
        throw new IllegalStateException("Iris generation stopped: " + failure, failure);
    }
}
