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
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionException;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.atomic.AtomicBoolean;

final class ModdedDimensionRestore<P, L, E> implements AutoCloseable {
    private static final int MINIMUM_WORKERS = 2;

    private final List<Preparation<P>> preparations;
    private final Steps<P, L, E> steps;
    private final ExecutorService workers;
    private final AtomicBoolean finished = new AtomicBoolean();

    private ModdedDimensionRestore(List<Preparation<P>> preparations, Steps<P, L, E> steps, ExecutorService workers) {
        this.preparations = preparations;
        this.steps = steps;
        this.workers = workers;
    }

    static <P, L, E> ModdedDimensionRestore<P, L, E> start(
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
        return new ModdedDimensionRestore<>(List.copyOf(preparations), requiredSteps, workers);
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
            throw new IllegalStateException("Iris dimension restore already finished");
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
                    discard(prepared.value(), failure);
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
                        discard(prepared.value(), null);
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
            abandon(pending.level(), failure);
            return new Result(pending.dimension(), failure, pending.preparedMillis(), 0L);
        }
        try {
            steps.complete(pending.level(), engine.value());
            return new Result(pending.dimension(), null, pending.preparedMillis(), engine.millis());
        } catch (Throwable failure) {
            return new Result(pending.dimension(), failure, pending.preparedMillis(), engine.millis());
        }
    }

    private void abandon(L level, Throwable failure) {
        try {
            steps.abandon(level, failure);
        } catch (Throwable cleanupFailure) {
            failure.addSuppressed(cleanupFailure);
        }
    }

    private void discard(P prepared, Throwable failure) {
        try {
            steps.discard(prepared);
        } catch (Throwable cleanupFailure) {
            if (failure != null) {
                failure.addSuppressed(cleanupFailure);
                return;
            }
            ModdedIrisLog.error("Iris could not release an unused dimension restore preparation", cleanupFailure);
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
            throw new IllegalStateException("Interrupted while restoring Iris dimensions", interrupted);
        }
    }

    interface Steps<P, L, E> {
        P prepare(ModdedDimensionRegistryStore.PersistentDimension dimension) throws Exception;

        L begin(ModdedDimensionRegistryStore.PersistentDimension dimension, P prepared) throws Exception;

        E build(L level, P prepared) throws Exception;

        void complete(L level, E engine) throws Exception;

        void abandon(L level, Throwable failure);

        void discard(P prepared);
    }

    record Result(
            ModdedDimensionRegistryStore.PersistentDimension dimension,
            Throwable failure,
            long preparedMillis,
            long builtMillis
    ) {
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
