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

package art.arcane.iris.generation.cache;

import art.arcane.iris.spi.IrisLogging;
import art.arcane.volmlib.util.function.NastySupplier;

import java.util.function.Supplier;

public class AtomicCache<T> {
    private static final Object NULL_VALUE = new Object();
    private transient final Object initLock = new Object();
    private transient final boolean nullSupport;
    private transient volatile Object value;
    private transient volatile Throwable failure;

    public AtomicCache() {
        this(false);
    }

    public AtomicCache(boolean nullSupport) {
        this.nullSupport = nullSupport;
    }

    public void reset() {
        synchronized (initLock) {
            value = null;
            failure = null;
        }
    }

    public T getIfPresent() {
        return unwrap(value);
    }

    public T aquireNasty(NastySupplier<T> t) {
        return aquire(() -> {
            try {
                return t.get();
            } catch (Throwable e) {
                return null;
            }
        });
    }

    public T aquireNastyPrint(NastySupplier<T> t) {
        return aquire(() -> {
            try {
                return t.get();
            } catch (Throwable e) {
                IrisLogging.reportError("Atomic cache supplier failed.", e);
                return null;
            }
        });
    }

    /**
     * Like {@link #aquire(Supplier)} but propagates a supplier failure to the caller instead
     * of swallowing it into a null return. For values that are mandatory: a caller of a
     * "@NotNull" accessor should see the supplier's real exception, not a downstream NPE.
     * <p>
     * The supplier is retried on every call until it produces a value, which is what a caller that can
     * repair the cause between attempts needs. Use {@link #aquireOnceOrThrow(Supplier)} when re-running a
     * failed supplier is itself the problem.
     */
    public T aquireOrThrow(Supplier<T> t) {
        Object v = value;

        if (v != null) {
            return unwrap(v);
        }

        synchronized (initLock) {
            v = value;

            if (v != null) {
                return unwrap(v);
            }

            T computed = t.get();
            if (computed == null) {
                throw new IllegalStateException("Atomic cache supplier produced null");
            }
            value = computed;
            return computed;
        }
    }

    /**
     * Like {@link #aquireOrThrow(Supplier)} but memoizes the failure as well as the value, so a supplier
     * that cannot produce one is run exactly once and every later caller is told the same reason. For
     * values whose supplier rebuilds shared state, where re-running it per call repeats that work and
     * hides the original cause behind whatever the retry happens to fail on. {@link #reset()} clears it.
     */
    public T aquireOnceOrThrow(Supplier<T> t) {
        Object v = value;

        if (v != null) {
            return unwrap(v);
        }
        rethrowFailure(failure);

        synchronized (initLock) {
            v = value;

            if (v != null) {
                return unwrap(v);
            }
            rethrowFailure(failure);

            T computed;
            try {
                computed = t.get();
                if (computed == null) {
                    throw new IllegalStateException("Atomic cache supplier produced null");
                }
            } catch (Throwable e) {
                failure = e;
                throw e;
            }
            value = computed;
            return computed;
        }
    }

    private static void rethrowFailure(Throwable memoized) {
        if (memoized == null) {
            return;
        }
        if (memoized instanceof RuntimeException runtimeFailure) {
            throw runtimeFailure;
        }
        if (memoized instanceof Error error) {
            throw error;
        }
        throw new IllegalStateException("Atomic cache supplier failed", memoized);
    }

    public T aquire(Supplier<T> t) {
        Object v = value;

        if (v != null) {
            return unwrap(v);
        }

        synchronized (initLock) {
            v = value;

            if (v != null) {
                return unwrap(v);
            }

            try {
                T computed = t.get();

                if (computed != null) {
                    value = computed;
                    return computed;
                }

                if (nullSupport) {
                    value = NULL_VALUE;
                }
            } catch (Throwable e) {
                // aquire retries on every call, so a supplier that stays broken is reached per sample.
                // The first statement of it carries the stack; the rest are traces of the same cause.
                if (IrisLogging.warnOnce("atomic-cache:" + e.getClass().getName() + ":" + e.getMessage(),
                        "Atomic cache supplier failed: %s: %s", e.getClass().getSimpleName(), e.getMessage())) {
                    IrisLogging.reportError(e);
                }
            }

            return null;
        }
    }

    @SuppressWarnings("unchecked")
    private T unwrap(Object v) {
        return v == null || v == NULL_VALUE ? null : (T) v;
    }
}
