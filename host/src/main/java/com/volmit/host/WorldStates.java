package com.volmit.host;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Deque;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.Supplier;
import java.util.stream.Collectors;

/** The reference store behind {@link com.volmit.world.PackWorld#state}: one instance per type, created once, closed
 * with the world. A host's world implementation keeps one per world and passes {@code state} calls on, binding the
 * world into the factory.
 *
 * <p>Reads of existing state take no lock. Creation runs under the store's lock, so each type is created once even
 * when threads race; a factory may ask for other state on its own thread (the lock is re-entrant), and a type asked
 * for again while it is being created fails instead of recursing. {@link #close()} closes every {@link AutoCloseable}
 * state, newest first, and ends the store.
 */
public final class WorldStates implements AutoCloseable {
    private final Map<Class<?>, Object> states = new ConcurrentHashMap<>();
    private final List<Object> createdInOrder = new ArrayList<>();
    private final Deque<Class<?>> creating = new ArrayDeque<>();
    private final Object lock = new Object();
    private boolean closed;

    /** The {@code type} state, created by {@code create} the first time it is asked for. See {@link com.volmit.world.PackWorld#state}. */
    public <T> T state(Class<T> type, Supplier<? extends T> create) {
        Objects.requireNonNull(type, "type");
        Objects.requireNonNull(create, "create");
        Object existing = states.get(type);
        if (existing != null) {
            return type.cast(existing);
        }
        synchronized (lock) {
            requireOpen(type);
            existing = states.get(type);
            if (existing != null) {
                return type.cast(existing);
            }
            return create(type, create);
        }
    }

    /** Closes every {@link AutoCloseable} state, the newest first, even when one fails, then throws the first failure
     * with the others suppressed. Asking for state afterwards throws. Closing twice does nothing.
     */
    @Override
    public void close() {
        List<Object> closing;
        synchronized (lock) {
            if (closed) {
                return;
            }
            closed = true;
            closing = new ArrayList<>(createdInOrder);
            createdInOrder.clear();
            states.clear();
        }
        RuntimeException failure = null;
        for (int index = closing.size() - 1; index >= 0; index--) {
            failure = closeState(closing.get(index), failure);
        }
        if (failure != null) {
            throw failure;
        }
    }

    private <T> T create(Class<T> type, Supplier<? extends T> create) {
        if (creating.contains(type)) {
            throw new IllegalStateException("World state " + type.getName() + " was asked for while it is being created: "
                    + creationChain(type));
        }
        creating.push(type);
        try {
            T created = type.cast(Objects.requireNonNull(create.get(), () -> "The factory of world state " + type.getName()
                    + " returned null"));
            states.put(type, created);
            createdInOrder.add(created);
            return created;
        } finally {
            creating.pop();
        }
    }

    private String creationChain(Class<?> repeated) {
        var chain = new ArrayList<Class<?>>(creating);
        Collections.reverse(chain);
        chain.add(repeated);
        return chain.stream().map(Class::getName).collect(Collectors.joining(" -> "));
    }

    private void requireOpen(Class<?> type) {
        if (closed) {
            throw new IllegalStateException("World state " + type.getName() + " was asked for after the world stopped");
        }
    }

    private static RuntimeException closeState(Object state, RuntimeException failure) {
        if (!(state instanceof AutoCloseable closeable)) {
            return failure;
        }
        try {
            closeable.close();
            return failure;
        } catch (Exception exception) {
            var closeFailure = new IllegalStateException("World state " + state.getClass().getName() + " failed to close: "
                    + exception, exception);
            if (failure == null) {
                return closeFailure;
            }
            failure.addSuppressed(closeFailure);
            return failure;
        }
    }
}
