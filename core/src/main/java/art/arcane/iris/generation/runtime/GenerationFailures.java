package art.arcane.iris.generation.runtime;

import art.arcane.iris.world.history.SavedBiomeUnavailableException;
import art.arcane.volmlib.util.mantle.MantleClosedException;

import java.io.InterruptedIOException;
import java.nio.channels.ClosedChannelException;
import java.util.concurrent.CancellationException;
import java.util.concurrent.RejectedExecutionException;
import java.util.concurrent.TimeoutException;

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
        if (Thread.currentThread().isInterrupted()) {
            return true;
        }
        Throwable current = failure;
        for (int depth = 0; current != null && depth < MAXIMUM_CAUSE_DEPTH; depth++, current = current.getCause()) {
            if (current instanceof Error
                    || current instanceof GenerationClosedException
                    || current instanceof MantleClosedException
                    || current instanceof WrongEngineBroException
                    || current instanceof SavedBiomeUnavailableException unavailable && unavailable.isLoading()
                    || current instanceof InterruptedException
                    || current instanceof InterruptedIOException
                    || current instanceof ClosedChannelException
                    || current instanceof RejectedExecutionException
                    || current instanceof CancellationException
                    || current instanceof TimeoutException) {
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
