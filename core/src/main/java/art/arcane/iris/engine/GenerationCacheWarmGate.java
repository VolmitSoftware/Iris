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

package art.arcane.iris.engine;

import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionException;
import java.util.concurrent.atomic.AtomicReference;

/**
 * Whether the engine's worlds have warmed their generation caches, which generation waits for. Every world is warmed by
 * the modules' warm-up ({@link com.volmit.ModuleLifecycle#warmUp}): the first by engine initialisation, the ones started
 * after that, by a hotload or an in-place restart, each adding a warm-up that generation also waits for.
 */
final class GenerationCacheWarmGate {
    private final CompletableFuture<Void> firstWorld = new CompletableFuture<>();
    private final AtomicReference<CompletableFuture<Void>> restartedWorlds = new AtomicReference<>(CompletableFuture.completedFuture(null));

    /** Runs the warm-up on this thread; the first world is warm once it returned. A failure leaves it failed. */
    void warmFirstWorld(Runnable warmUp) {
        try {
            warmUp.run();
            firstWorld.complete(null);
        } catch (RuntimeException | Error failure) {
            firstWorld.completeExceptionally(failure);
            throw failure;
        }
    }

    /** The first world needs no warm-up. */
    void skipFirstWorld() {
        firstWorld.complete(null);
    }

    /** Makes generation wait for a world started after the first, until the returned future completes. */
    CompletableFuture<Void> beginRestartedWorld() {
        CompletableFuture<Void> warm = new CompletableFuture<>();
        restartedWorlds.updateAndGet(previous -> CompletableFuture.allOf(previous, warm));
        return warm;
    }

    /** Blocks until every world started so far is warm. */
    void await() {
        await(firstWorld);
        await(restartedWorlds.get());
    }

    boolean isPending() {
        return !firstWorld.isDone() || !restartedWorlds.get().isDone();
    }

    private static void await(CompletableFuture<Void> warm) {
        if (warm.isDone() && !warm.isCompletedExceptionally()) {
            return;
        }
        try {
            warm.join();
        } catch (CompletionException failure) {
            throw new IllegalStateException("Iris generation caches did not become ready.", failure.getCause());
        }
    }
}
