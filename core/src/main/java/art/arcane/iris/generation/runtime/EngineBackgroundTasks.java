/*
 * Iris is a World Generator for Minecraft Bukkit Servers
 * Copyright (c) 2022 Arcane Arts (Volmit Software)
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

package art.arcane.iris.generation.runtime;

import art.arcane.iris.spi.IrisLogging;
import art.arcane.iris.world.task.J;
import art.arcane.iris.generation.context.IrisContext;

import java.util.ArrayList;
import java.util.Collections;
import java.util.IdentityHashMap;
import java.util.List;
import java.util.Set;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;
import java.util.concurrent.atomic.AtomicInteger;

import static art.arcane.iris.generation.runtime.EngineShutdownSequence.appendFailure;
import static art.arcane.iris.generation.runtime.EngineShutdownSequence.propagate;

/**
 * Admission gate and drain tracker for the asynchronous work an {@link IrisEngine} schedules.
 * Admission is closed before any lifecycle transition so a draining engine can never accrue new
 * background work, and every tracked task is awaited (or cancelled) before resources are released.
 */
final class EngineBackgroundTasks {
    private static final long BACKGROUND_TASK_TIMEOUT_MILLIS = 15000L;

    private final Object backgroundTaskLock = new Object();
    private final List<TrackedBackgroundTask> backgroundTasks = new ArrayList<>();
    private boolean backgroundTaskAdmission;
    private int activeDrains;

    boolean scheduleTrackedTask(Runnable task) {
        return scheduleTrackedTask(task, false);
    }

    void scheduleAdmittedTask(Engine engine, Runnable task) {
        IrisContext context = IrisContext.get();
        GenerationSessionManager sessions = engine.getGenerationSessions();
        if (context == null || context.getEngine() != engine || context.getGenerationSessionId() == 0L
                || context.getGenerationSessionId() != engine.getGenerationSessionId()
                || sessions == null || sessions.activeLeases() == 0) {
            throw new IllegalStateException("Iris background continuation requires an active generation lease.");
        }
        scheduleTrackedTask(task, true);
    }

    private boolean scheduleTrackedTask(Runnable task, boolean admitted) {
        synchronized (backgroundTaskLock) {
            // A finished task is not outstanding work, however it finished. Retaining failed
            // entries made the NEXT transition's drain re-report a long-settled failure and
            // blocked close() from ever marking the engine closed.
            if (activeDrains == 0) {
                backgroundTasks.removeIf(tracked -> tracked.completion.isDone());
            }
            if (!backgroundTaskAdmission && !admitted) {
                return false;
            }
            TrackedBackgroundTask tracked = new TrackedBackgroundTask();
            Future<Void> future = J.a(() -> {
                // The pool behind J.a cannot stop a callable it already dequeued, so the claim is
                // decided here: a cancellation that won the claim first means this body never runs.
                if (!tracked.claim.compareAndSet(TrackedBackgroundTask.QUEUED, TrackedBackgroundTask.RUNNING)) {
                    return null;
                }
                try {
                    task.run();
                    tracked.completion.complete(null);
                    return null;
                } catch (Throwable exception) {
                    tracked.completion.completeExceptionally(exception);
                    // J.a(Callable) routes through a future nobody reads; report here or the
                    // failure is invisible.
                    IrisLogging.reportError(exception);
                    IrisLogging.error("Iris background task failed.");
                    throw propagate(exception);
                }
            });
            if (future == null) {
                throw new IllegalStateException("Iris background task scheduler returned no task handle.");
            }
            tracked.future = future;
            backgroundTasks.add(tracked);
            return true;
        }
    }

    BackgroundTaskDrain drainBackgroundTasks(String reason) {
        long deadline = System.nanoTime() + TimeUnit.MILLISECONDS.toNanos(BACKGROUND_TASK_TIMEOUT_MILLIS);
        Set<TrackedBackgroundTask> observed = Collections.newSetFromMap(new IdentityHashMap<>());
        Set<TrackedBackgroundTask> settledAtEntry = Collections.newSetFromMap(new IdentityHashMap<>());
        List<TrackedBackgroundTask> tasks;
        synchronized (backgroundTaskLock) {
            activeDrains++;
            tasks = List.copyOf(backgroundTasks);
            for (TrackedBackgroundTask task : tasks) {
                if (task.completion.isDone()) {
                    settledAtEntry.add(task);
                }
            }
        }
        Throwable failure = null;
        try {
            while (true) {
                failure = appendFailure(failure, drainTaskBatch(tasks, settledAtEntry, deadline, reason));
                observed.addAll(tasks);
                synchronized (backgroundTaskLock) {
                    tasks = new ArrayList<>();
                    for (TrackedBackgroundTask task : backgroundTasks) {
                        if (!observed.contains(task)) {
                            tasks.add(task);
                        }
                    }
                    if (tasks.isEmpty()) {
                        return new BackgroundTaskDrain(failure, allTasksComplete());
                    }
                }
                if (System.nanoTime() >= deadline) {
                    for (TrackedBackgroundTask task : tasks) {
                        cancelBackgroundTask(task, reason);
                    }
                    failure = appendFailure(failure, new TimeoutException(
                            "Timed out waiting for Iris background tasks during " + reason + "."));
                    synchronized (backgroundTaskLock) {
                        return new BackgroundTaskDrain(failure, allTasksComplete());
                    }
                }
            }
        } finally {
            synchronized (backgroundTaskLock) {
                activeDrains--;
                if (activeDrains == 0) {
                    backgroundTasks.removeIf(tracked -> tracked.completion.isDone());
                }
            }
        }
    }

    private boolean allTasksComplete() {
        for (TrackedBackgroundTask task : backgroundTasks) {
            if (!task.completion.isDone()) {
                return false;
            }
        }
        return true;
    }

    private Throwable drainTaskBatch(List<TrackedBackgroundTask> tasks,
                                     Set<TrackedBackgroundTask> settledAtEntry,
                                     long deadline, String reason) {
        Throwable failure = null;
        for (TrackedBackgroundTask task : tasks) {
            long remaining = deadline - System.nanoTime();
            if (remaining <= 0L) {
                cancelBackgroundTask(task, reason);
                failure = appendFailure(failure, new TimeoutException("Timed out waiting for Iris background tasks during " + reason + "."));
                continue;
            }
            boolean alreadyDone = settledAtEntry.contains(task);
            try {
                task.completion.get(remaining, TimeUnit.NANOSECONDS);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                cancelBackgroundTask(task, reason);
                failure = appendFailure(failure, e);
            } catch (ExecutionException | TimeoutException e) {
                cancelBackgroundTask(task, reason);
                // A task that had settled before this drain began was already reported when it
                // failed; only genuinely in-flight failures may poison this transition.
                if (!alreadyDone) {
                    failure = appendFailure(failure, e);
                } else {
                    IrisLogging.debug("Ignoring pre-settled background task failure during " + reason + ".");
                }
            }
        }
        return failure;
    }

    private void cancelBackgroundTask(TrackedBackgroundTask task, String reason) {
        boolean claimed = task.claim.compareAndSet(TrackedBackgroundTask.QUEUED, TrackedBackgroundTask.CANCELLED);
        Future<?> future = task.future;
        if (future != null) {
            future.cancel(true);
        }
        if (claimed) {
            task.completion.completeExceptionally(
                    new IllegalStateException("Iris background task was cancelled before starting during " + reason + "."));
        }
    }

    void openBackgroundTaskAdmission() {
        synchronized (backgroundTaskLock) {
            backgroundTaskAdmission = true;
        }
    }

    void closeBackgroundTaskAdmission() {
        synchronized (backgroundTaskLock) {
            backgroundTaskAdmission = false;
        }
    }

    void cancelBackgroundTasks(String reason) {
        List<TrackedBackgroundTask> tasks;
        synchronized (backgroundTaskLock) {
            tasks = List.copyOf(backgroundTasks);
        }
        for (TrackedBackgroundTask task : tasks) {
            cancelBackgroundTask(task, reason);
        }
    }

    private static final class TrackedBackgroundTask {
        private static final int QUEUED = 0;
        private static final int RUNNING = 1;
        private static final int CANCELLED = 2;

        private final AtomicInteger claim = new AtomicInteger(QUEUED);
        private final CompletableFuture<Void> completion = new CompletableFuture<>();
        private volatile Future<?> future;
    }

    record BackgroundTaskDrain(Throwable failure, boolean complete) {
        boolean allowsResourceRelease() {
            return complete;
        }

        void requireComplete(String reason) {
            if (failure != null) {
                throw new IllegalStateException("Iris background tasks failed to drain during " + reason + ".", failure);
            }
            if (!complete) {
                throw new IllegalStateException("Iris background tasks remain active during " + reason + ".");
            }
        }
    }
}
