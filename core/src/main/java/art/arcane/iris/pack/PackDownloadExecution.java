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

package art.arcane.iris.pack;

import art.arcane.iris.world.lifecycle.LifecycleOperationCoordinator;
import art.arcane.iris.spi.IrisLogging;

import java.util.Objects;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;
import java.util.concurrent.atomic.AtomicBoolean;

public final class PackDownloadExecution implements Runnable {
    private final Object monitor = new Object();
    private final LifecycleOperationCoordinator.Lease lease;
    private final Work work;
    private final PackDownloader.DownloadCancellation cancellation;
    private final CompletableFuture<Void> completion;
    private final AtomicBoolean finished;
    private Future<?> future;
    private boolean cancellationRequested;
    private boolean started;

    public PackDownloadExecution(LifecycleOperationCoordinator.Lease lease, Work work) {
        this.lease = Objects.requireNonNull(lease, "lease");
        this.work = Objects.requireNonNull(work, "work");
        cancellation = new PackDownloader.DownloadCancellation();
        completion = new CompletableFuture<>();
        finished = new AtomicBoolean();
    }

    public void bind(Future<?> submittedFuture) {
        Future<?> acceptedFuture = Objects.requireNonNull(submittedFuture, "submittedFuture");
        boolean cancelBeforeStart;
        synchronized (monitor) {
            future = acceptedFuture;
            cancelBeforeStart = cancellationRequested && !started;
        }
        if (cancelBeforeStart) {
            acceptedFuture.cancel(false);
            finish();
        }
    }

    public void onCompletion(Runnable callback) {
        Runnable completionCallback = Objects.requireNonNull(callback, "callback");
        completion.whenComplete((ignored, failure) -> completionCallback.run());
    }

    public void cancel() {
        Future<?> submittedFuture;
        boolean cancelBeforeStart;
        synchronized (monitor) {
            cancellationRequested = true;
            submittedFuture = future;
            cancelBeforeStart = !started;
        }
        cancellation.cancel();
        if (cancelBeforeStart) {
            if (submittedFuture != null) {
                submittedFuture.cancel(false);
            }
            finish();
        }
    }

    public boolean await(long timeout, TimeUnit unit) throws InterruptedException {
        try {
            completion.get(timeout, unit);
            return true;
        } catch (TimeoutException exception) {
            return false;
        } catch (ExecutionException exception) {
            return true;
        }
    }

    public boolean isPublishing() {
        return cancellation.isPublishing();
    }

    public boolean isComplete() {
        return completion.isDone();
    }

    @Override
    public void run() {
        synchronized (monitor) {
            if (cancellationRequested) {
                finish();
                return;
            }
            started = true;
        }

        try {
            cancellation.attachCurrentThread();
            work.run(cancellation);
        } catch (PackDownloader.PackDownloadCancelledException ignored) {
        } catch (Throwable failure) {
            IrisLogging.reportError("Pack download worker failed.", failure);
        } finally {
            cancellation.complete();
            finish();
        }
    }

    private void finish() {
        if (!finished.compareAndSet(false, true)) {
            return;
        }
        try {
            lease.close();
        } catch (Throwable failure) {
            IrisLogging.reportError("Failed to release the pack download lifecycle lease.", failure);
        } finally {
            completion.complete(null);
        }
    }

    @FunctionalInterface
    public interface Work {
        void run(PackDownloader.DownloadCancellation cancellation) throws Exception;
    }
}
