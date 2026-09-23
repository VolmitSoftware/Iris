package art.arcane.iris.probe;

import java.time.Duration;
import java.util.HashSet;
import java.util.Objects;
import java.util.Set;
import java.util.concurrent.ExecutorCompletionService;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;

final class RegionGenerationWindow {
    private static final int UNRESPONSIVE_WORKER_EXIT = 70;

    private RegionGenerationWindow() {
    }

    static <T> void process(Request<T> request) throws Exception {
        process(request, Policy.EMBEDDED);
    }

    static <T> void process(Request<T> request, Policy policy) throws Exception {
        ExecutorService workers = Executors.newFixedThreadPool(request.parallelism());
        ExecutorCompletionService<Completed<T>> completions = new ExecutorCompletionService<>(workers);
        Set<Future<Completed<T>>> active = new HashSet<>(request.parallelism());
        try {
            int submitted = 0;
            for (; submitted < Math.min(request.count(), request.parallelism()); submitted++) {
                active.add(submit(completions, request.generator(), submitted));
            }
            for (int completed = 0; completed < request.count(); completed++) {
                Future<Completed<T>> pending = completions.poll(policy.completionTimeout().toNanos(), TimeUnit.NANOSECONDS);
                if (pending == null) {
                    throw new TimeoutException("No generation worker completed within " + policy.completionTimeout());
                }
                active.remove(pending);
                Completed<T> result = pending.get();
                if (submitted < request.count()) {
                    active.add(submit(completions, request.generator(), submitted++));
                }
                request.sink().accept(result.index(), result.value());
            }
        } catch (Exception | Error failure) {
            for (Future<Completed<T>> future : active) {
                future.cancel(true);
            }
            workers.shutdownNow();
            drain(workers, policy, failure);
            throw failure;
        }
        workers.shutdown();
        drain(workers, policy, null);
    }

    private static void drain(ExecutorService workers, Policy policy, Throwable failure) {
        boolean interrupted = Thread.interrupted() || failure instanceof InterruptedException;
        long deadline = System.nanoTime() + policy.drainTimeout().toNanos();
        try {
            while (!workers.isTerminated()) {
                long remaining = deadline - System.nanoTime();
                if (remaining <= 0L) {
                    break;
                }
                try {
                    workers.awaitTermination(remaining, TimeUnit.NANOSECONDS);
                } catch (InterruptedException interruption) {
                    interrupted = true;
                }
            }
            if (workers.isTerminated()) {
                return;
            }
            IllegalStateException stalled = new IllegalStateException(
                    "Generation workers did not stop within " + policy.drainTimeout()
                            + "; generation resources remain open and no checkpoint will be published", failure);
            stalled.printStackTrace(System.err);
            if (policy.haltOnUnresponsiveWorkers()) {
                System.err.println("Terminating offline generation; incomplete output is retained for inspection");
                System.err.flush();
                Runtime.getRuntime().halt(UNRESPONSIVE_WORKER_EXIT);
            }
            System.err.println("Waiting for embedded generation workers to stop before releasing their resources");
            while (!workers.isTerminated()) {
                try {
                    workers.awaitTermination(1L, TimeUnit.DAYS);
                } catch (InterruptedException interruption) {
                    interrupted = true;
                }
            }
        } finally {
            if (interrupted) {
                Thread.currentThread().interrupt();
            }
        }
    }

    private static <T> Future<Completed<T>> submit(ExecutorCompletionService<Completed<T>> completions,
                                                  Generator<T> generator, int index) {
        return completions.submit(() -> new Completed<>(index, generator.generate(index)));
    }

    record Policy(Duration completionTimeout, Duration drainTimeout, boolean haltOnUnresponsiveWorkers) {
        static final Policy EMBEDDED = new Policy(Duration.ofMinutes(5), Duration.ofSeconds(30), false);
        static final Policy STANDALONE = new Policy(Duration.ofMinutes(5), Duration.ofSeconds(30), true);

        Policy {
            Objects.requireNonNull(completionTimeout, "completionTimeout");
            Objects.requireNonNull(drainTimeout, "drainTimeout");
            if (completionTimeout.isNegative() || completionTimeout.isZero()
                    || drainTimeout.isNegative() || drainTimeout.isZero()) {
                throw new IllegalArgumentException("Worker completion and drain timeouts must be positive");
            }
            completionTimeout.toNanos();
            drainTimeout.toNanos();
        }
    }

    record Request<T>(int count, int parallelism, Generator<T> generator, Sink<T> sink) {
        Request {
            if (count < 1 || count > 4096 || parallelism < 1 || parallelism > 32) {
                throw new IllegalArgumentException("Generation window requires 1..4096 tasks and 1..32 workers");
            }
        }
    }

    private record Completed<T>(int index, T value) {
    }

    @FunctionalInterface
    interface Generator<T> {
        T generate(int index) throws Exception;
    }

    @FunctionalInterface
    interface Sink<T> {
        void accept(int index, T value) throws Exception;
    }
}
