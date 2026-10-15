/*
 * Iris is a World Generator for Minecraft Servers
 * Copyright (c) 2026 Arcane Arts (Volmit Software)
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License
 * along with this program.  If not, see <https://www.gnu.org/licenses/>.
 */

package art.arcane.iris.modded;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.concurrent.CancellationException;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionException;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.Executor;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;
import java.util.concurrent.atomic.AtomicBoolean;

final class ModdedDimensionInjection<P, L, E> implements AutoCloseable {
    private static final int MINIMUM_WORKERS = 2;
    private static final long CANCEL_BUILD_WAIT_SECONDS = 120L;

    private final List<Preparation<P>> preparations;
    private final Steps<P, L, E> steps;
    private final ExecutorService workers;
    private final AtomicBoolean finished = new AtomicBoolean();

    private ModdedDimensionInjection(List<Preparation<P>> preparations, Steps<P, L, E> steps, ExecutorService workers) {
        this.preparations = preparations;
        this.steps = steps;
        this.workers = workers;
    }

    static <P, L, E> ModdedDimensionInjection<P, L, E> start(
            List<ModdedDimensionRegistryStore.PersistentDimension> dimensions,
            Steps<P, L, E> steps
    ) {
        Steps<P, L, E> requiredSteps = Objects.requireNonNull(steps, "steps");
        ExecutorService workers = Executors.newFixedThreadPool(workerCount(dimensions.size()),
                Thread.ofPlatform().name("Iris Dimension Restore ", 1L).daemon(true).factory());
        List<Preparation<P>> preparations = new ArrayList<>(dimensions.size());
        for (ModdedDimensionRegistryStore.PersistentDimension dimension : dimensions) {
            preparations.add(new Preparation<>(dimension, CompletableFuture.supplyAsync(
                    () -> timed(() -> requiredSteps.prepare(dimension)), workers)));
        }
        return new ModdedDimensionInjection<>(List.copyOf(preparations), requiredSteps, workers);
    }

    static <P, L, E> Async<P, L, E> async(
            ModdedDimensionRegistryStore.PersistentDimension dimension,
            Steps<P, L, E> steps,
            Executor serverThread
    ) {
        return new Async<>(dimension, steps, serverThread);
    }

    void awaitPreparations() {
        List<CompletableFuture<Timed<P>>> futures = new ArrayList<>(preparations.size());
        for (Preparation<P> preparation : preparations) {
            futures.add(preparation.future());
        }
        CompletableFuture.allOf(futures.toArray(CompletableFuture[]::new))
                .handle((Void ignored, Throwable failure) -> null)
                .join();
    }

    List<Result> finish() {
        if (!finished.compareAndSet(false, true)) {
            throw new IllegalStateException("Iris dimension injection already finished");
        }
        try {
            List<Building<L, E>> building = new ArrayList<>(preparations.size());
            Result[] results = new Result[preparations.size()];
            for (int index = 0; index < preparations.size(); index++) {
                Preparation<P> preparation = preparations.get(index);
                Timed<P> prepared;
                try {
                    prepared = join(preparation.future());
                } catch (Throwable failure) {
                    results[index] = new Result(preparation.dimension(), failure, 0L, 0L);
                    continue;
                }
                L level;
                try {
                    level = steps.begin(preparation.dimension(), prepared.value());
                } catch (Throwable failure) {
                    discard(steps, prepared.value(), failure);
                    results[index] = new Result(preparation.dimension(), failure, prepared.millis(), 0L);
                    continue;
                }
                CompletableFuture<Timed<E>> engine = CompletableFuture.supplyAsync(
                        () -> timed(() -> steps.build(level, prepared.value())), workers);
                building.add(new Building<>(index, preparation.dimension(), prepared.millis(), level, engine));
            }
            for (Building<L, E> pending : building) {
                results[pending.index()] = complete(pending);
            }
            return List.of(results);
        } finally {
            workers.shutdown();
        }
    }

    @Override
    public void close() {
        if (finished.compareAndSet(false, true)) {
            for (Preparation<P> preparation : preparations) {
                preparation.future().whenComplete((Timed<P> prepared, Throwable failure) -> {
                    if (prepared != null) {
                        discard(steps, prepared.value(), null);
                    }
                });
            }
        }
        workers.shutdown();
    }

    private Result complete(Building<L, E> pending) {
        Timed<E> engine;
        try {
            engine = join(pending.engine());
        } catch (Throwable failure) {
            abandon(steps, pending.level(), null, failure);
            return new Result(pending.dimension(), failure, pending.preparedMillis(), 0L);
        }
        try {
            steps.complete(pending.level(), engine.value());
            return new Result(pending.dimension(), null, pending.preparedMillis(), engine.millis());
        } catch (Throwable failure) {
            return new Result(pending.dimension(), failure, pending.preparedMillis(), engine.millis());
        }
    }

    private static <P, L, E> void abandon(Steps<P, L, E> steps, L level, E engine, Throwable failure) {
        try {
            steps.abandon(level, engine, failure);
        } catch (Throwable cleanupFailure) {
            failure.addSuppressed(cleanupFailure);
        }
    }

    private static <P, L, E> void discard(Steps<P, L, E> steps, P prepared, Throwable failure) {
        try {
            steps.discard(prepared);
        } catch (Throwable cleanupFailure) {
            if (failure != null) {
                failure.addSuppressed(cleanupFailure);
                return;
            }
            ModdedIrisLog.error("Iris could not release an unused dimension preparation", cleanupFailure);
        }
    }

    private static int workerCount(int dimensions) {
        int processors = Runtime.getRuntime().availableProcessors();
        return Math.max(1, Math.min(dimensions, Math.max(MINIMUM_WORKERS, processors / 2)));
    }

    private static <T> Timed<T> timed(Step<T> step) {
        long startedAt = System.nanoTime();
        try {
            T value = step.run();
            return new Timed<>(value, (System.nanoTime() - startedAt) / 1_000_000L);
        } catch (RuntimeException | Error failure) {
            throw failure;
        } catch (Exception failure) {
            throw new CompletionException(failure);
        }
    }

    private static Throwable unwrap(Throwable failure) {
        Throwable current = failure;
        while (current instanceof CompletionException && current.getCause() != null) {
            current = current.getCause();
        }
        return current;
    }

    private static <T> T join(CompletableFuture<T> future) throws Throwable {
        try {
            return future.get();
        } catch (ExecutionException failure) {
            Throwable cause = failure.getCause();
            if (cause instanceof CompletionException wrapped && wrapped.getCause() != null) {
                throw wrapped.getCause();
            }
            throw cause == null ? failure : cause;
        } catch (InterruptedException interrupted) {
            Thread.currentThread().interrupt();
            throw new IllegalStateException("Interrupted while injecting Iris dimensions", interrupted);
        }
    }

    interface Steps<P, L, E> {
        P prepare(ModdedDimensionRegistryStore.PersistentDimension dimension) throws Exception;

        L begin(ModdedDimensionRegistryStore.PersistentDimension dimension, P prepared) throws Exception;

        E build(L level, P prepared) throws Exception;

        void complete(L level, E engine) throws Exception;

        void abandon(L level, E engine, Throwable failure);

        void discard(P prepared);
    }

    record Result(
            ModdedDimensionRegistryStore.PersistentDimension dimension,
            Throwable failure,
            long preparedMillis,
            long builtMillis
    ) {
    }

    static final class Async<P, L, E> {
        private final ModdedDimensionRegistryStore.PersistentDimension dimension;
        private final Steps<P, L, E> steps;
        private final Executor serverThread;
        private final ExecutorService worker;
        private final CompletableFuture<Result> result = new CompletableFuture<>();
        private State state = State.UNSTARTED;
        private Timed<P> prepared;
        private L level;
        private CompletableFuture<Timed<E>> engine;

        private Async(
                ModdedDimensionRegistryStore.PersistentDimension dimension,
                Steps<P, L, E> steps,
                Executor serverThread
        ) {
            this.dimension = Objects.requireNonNull(dimension, "dimension");
            this.steps = Objects.requireNonNull(steps, "steps");
            this.serverThread = Objects.requireNonNull(serverThread, "serverThread");
            this.worker = Executors.newSingleThreadExecutor(
                    Thread.ofPlatform().name("Iris Dimension Create " + dimension.id()).daemon(true).factory());
        }

        CompletableFuture<Result> result() {
            return result;
        }

        synchronized void start() {
            if (state != State.UNSTARTED) {
                throw new IllegalStateException("Iris dimension injection for '" + dimension.id() + "' already started");
            }
            state = State.PREPARING;
            CompletableFuture.supplyAsync(() -> timed(() -> steps.prepare(dimension)), worker)
                    .whenComplete(this::prepared);
        }

        void cancel() {
            Timed<P> unused = null;
            CompletableFuture<Timed<E>> building = null;
            L created = null;
            synchronized (this) {
                if (state == State.DONE || state == State.CANCELLED) {
                    return;
                }
                if (state == State.PREPARED) {
                    unused = prepared;
                } else if (state == State.BUILDING) {
                    building = engine;
                    created = level;
                }
                state = State.CANCELLED;
            }
            CancellationException cancellation = new CancellationException("Iris dimension injection for '"
                    + dimension.id() + "' was cancelled because the server is stopping");
            long preparedMillis = 0L;
            if (unused != null) {
                discard(steps, unused.value(), cancellation);
                preparedMillis = unused.millis();
            }
            if (building != null) {
                abandon(steps, created, awaitCancelledEngine(building, cancellation), cancellation);
            }
            done(new Result(dimension, cancellation, preparedMillis, 0L));
        }

        private void prepared(Timed<P> value, Throwable failure) {
            synchronized (this) {
                if (state == State.CANCELLED) {
                    if (value != null) {
                        discard(steps, value.value(), null);
                    }
                    worker.shutdown();
                    return;
                }
                if (failure != null) {
                    state = State.DONE;
                } else {
                    prepared = value;
                    state = State.PREPARED;
                }
            }
            if (failure != null) {
                done(new Result(dimension, unwrap(failure), 0L, 0L));
                return;
            }
            serverThread.execute(this::begin);
        }

        private void begin() {
            Timed<P> ready;
            synchronized (this) {
                if (state != State.PREPARED) {
                    return;
                }
                ready = prepared;
                state = State.BEGINNING;
            }
            L created;
            try {
                created = steps.begin(dimension, ready.value());
            } catch (Throwable failure) {
                discard(steps, ready.value(), failure);
                synchronized (this) {
                    state = State.DONE;
                }
                done(new Result(dimension, failure, ready.millis(), 0L));
                return;
            }
            CompletableFuture<Timed<E>> building;
            synchronized (this) {
                level = created;
                state = State.BUILDING;
                building = CompletableFuture.supplyAsync(() -> timed(() -> steps.build(created, ready.value())), worker);
                engine = building;
            }
            building.whenComplete((Timed<E> built, Throwable failure) -> serverThread.execute(this::complete));
        }

        private void complete() {
            CompletableFuture<Timed<E>> building;
            L created;
            long preparedMillis;
            synchronized (this) {
                if (state != State.BUILDING) {
                    return;
                }
                state = State.DONE;
                building = engine;
                created = level;
                preparedMillis = prepared.millis();
            }
            Timed<E> built;
            try {
                built = join(building);
            } catch (Throwable failure) {
                abandon(steps, created, null, failure);
                done(new Result(dimension, failure, preparedMillis, 0L));
                return;
            }
            try {
                steps.complete(created, built.value());
                done(new Result(dimension, null, preparedMillis, built.millis()));
            } catch (Throwable failure) {
                done(new Result(dimension, failure, preparedMillis, built.millis()));
            }
        }

        private E awaitCancelledEngine(CompletableFuture<Timed<E>> building, CancellationException cancellation) {
            try {
                return building.get(CANCEL_BUILD_WAIT_SECONDS, TimeUnit.SECONDS).value();
            } catch (ExecutionException failure) {
                return null;
            } catch (TimeoutException failure) {
                ModdedIrisLog.error("Iris dimension engine for '{}' did not finish within {}s of shutdown; abandoning its level",
                        dimension.id(), CANCEL_BUILD_WAIT_SECONDS);
                cancellation.addSuppressed(failure);
                return null;
            } catch (InterruptedException interrupted) {
                Thread.currentThread().interrupt();
                cancellation.addSuppressed(interrupted);
                return null;
            }
        }

        private void done(Result outcome) {
            worker.shutdown();
            result.complete(outcome);
        }

        private enum State {
            UNSTARTED,
            PREPARING,
            PREPARED,
            BEGINNING,
            BUILDING,
            DONE,
            CANCELLED
        }
    }

    @FunctionalInterface
    private interface Step<T> {
        T run() throws Exception;
    }

    private record Timed<T>(T value, long millis) {
    }

    private record Preparation<P>(
            ModdedDimensionRegistryStore.PersistentDimension dimension,
            CompletableFuture<Timed<P>> future
    ) {
    }

    private record Building<L, E>(
            int index,
            ModdedDimensionRegistryStore.PersistentDimension dimension,
            long preparedMillis,
            L level,
            CompletableFuture<Timed<E>> engine
    ) {
    }
}
