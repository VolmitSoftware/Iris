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

package art.arcane.iris.generation.locator;

import java.util.Objects;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.atomic.AtomicBoolean;

public final class LocatorCanceller {
    protected static Runnable cancel = null;

    private LocatorCanceller() {
    }

    static <T> CompletableFuture<T> requestScoped(CompletableFuture<T> future, AtomicBoolean stop) {
        return new RequestFuture<>(Objects.requireNonNull(future), Objects.requireNonNull(stop));
    }

    private static final class RequestFuture<T> extends CompletableFuture<T> {
        private final CompletableFuture<T> delegate;
        private final AtomicBoolean stop;

        private RequestFuture(CompletableFuture<T> delegate, AtomicBoolean stop) {
            this.delegate = delegate;
            this.stop = stop;
            delegate.whenComplete((value, exception) -> {
                if (isDone()) {
                    return;
                }
                if (exception == null) {
                    complete(value);
                } else {
                    completeExceptionally(exception);
                }
            });
        }

        @Override
        public boolean cancel(boolean mayInterruptIfRunning) {
            if (isDone()) {
                return false;
            }

            stop.set(true);
            boolean cancelled = super.cancel(mayInterruptIfRunning);
            delegate.cancel(mayInterruptIfRunning);
            return cancelled;
        }
    }
}
