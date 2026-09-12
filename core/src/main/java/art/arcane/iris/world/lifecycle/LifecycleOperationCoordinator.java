package art.arcane.iris.world.lifecycle;

import art.arcane.iris.spi.IrisLogging;

import java.util.ArrayDeque;
import java.util.Collections;
import java.util.EnumMap;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.concurrent.atomic.AtomicBoolean;

public final class LifecycleOperationCoordinator {
    private static final LifecycleOperationCoordinator INSTANCE = new LifecycleOperationCoordinator();

    private final Object monitor;
    private final ArrayDeque<Runnable> idleCallbacks;
    private ActiveOperation mutationOperation;
    private ActiveOperation restartOperation;
    private Runnable pendingRestartCallback;
    private long nextOperationId;

    LifecycleOperationCoordinator() {
        monitor = new Object();
        idleCallbacks = new ArrayDeque<>();
        nextOperationId = 1L;
    }

    public static LifecycleOperationCoordinator get() {
        return INSTANCE;
    }

    public Lease acquire(Domain domain, OperationKind kind, String target) {
        Objects.requireNonNull(domain, "domain");
        Objects.requireNonNull(kind, "kind");
        String normalizedTarget = Objects.requireNonNull(target, "target").trim();
        if (normalizedTarget.isEmpty()) {
            throw new IllegalArgumentException("target must not be blank");
        }

        synchronized (monitor) {
            if (domain == Domain.SERVER_LIFECYCLE) {
                throw new IllegalArgumentException("SERVER_LIFECYCLE is reserved for terminal operations");
            }

            ActiveOperation currentOperation = restartOperation == null ? mutationOperation : restartOperation;
            if (currentOperation != null) {
                throw new BusyException(currentOperation);
            }

            ActiveOperation operation = new ActiveOperation(nextOperationId++, domain, kind, normalizedTarget);
            mutationOperation = operation;
            return new LeaseImpl(this, operation);
        }
    }

    public boolean quiesceForRestart(Runnable callback) {
        Runnable restartCallback = Objects.requireNonNull(callback, "callback");
        ActiveOperation operation;
        boolean dispatch;
        synchronized (monitor) {
            if (restartOperation != null) {
                return false;
            }

            operation = new ActiveOperation(
                    nextOperationId++,
                    Domain.SERVER_LIFECYCLE,
                    OperationKind.SERVER_RESTART,
                    "server");
            restartOperation = operation;
            dispatch = mutationOperation == null;
            if (!dispatch) {
                pendingRestartCallback = restartCallback;
                return true;
            }
        }

        runRestartCallback(restartCallback, operation);
        return true;
    }

    public Optional<ActiveOperation> active(Domain domain) {
        Objects.requireNonNull(domain, "domain");
        synchronized (monitor) {
            if (domain == Domain.SERVER_LIFECYCLE) {
                return Optional.ofNullable(restartOperation);
            }
            if (mutationOperation != null && mutationOperation.domain() == domain) {
                return Optional.of(mutationOperation);
            }
            return Optional.empty();
        }
    }

    public Map<Domain, ActiveOperation> snapshot() {
        synchronized (monitor) {
            EnumMap<Domain, ActiveOperation> operations = new EnumMap<>(Domain.class);
            if (mutationOperation != null) {
                operations.put(mutationOperation.domain(), mutationOperation);
            }
            if (restartOperation != null) {
                operations.put(Domain.SERVER_LIFECYCLE, restartOperation);
            }
            return Collections.unmodifiableMap(operations);
        }
    }

    public boolean isIdle() {
        synchronized (monitor) {
            return mutationOperation == null && restartOperation == null;
        }
    }

    public void whenIdle(Runnable callback) {
        Runnable idleCallback = Objects.requireNonNull(callback, "callback");
        synchronized (monitor) {
            if (mutationOperation != null || restartOperation != null) {
                idleCallbacks.addLast(idleCallback);
                return;
            }
            runIdleCallback(idleCallback);
        }
    }

    private void release(ActiveOperation operation) {
        Runnable restartCallback = null;
        ActiveOperation terminalOperation = null;
        synchronized (monitor) {
            if (!operation.equals(mutationOperation)) {
                throw new IllegalStateException("Lifecycle operation lease is not active: " + operation.id());
            }

            mutationOperation = null;
            if (restartOperation != null && pendingRestartCallback != null) {
                terminalOperation = restartOperation;
                restartCallback = pendingRestartCallback;
                pendingRestartCallback = null;
            } else {
                runIdleCallbacks();
            }
        }

        if (restartCallback != null) {
            runRestartCallback(restartCallback, terminalOperation);
        }
    }

    private void runIdleCallbacks() {
        while (mutationOperation == null && restartOperation == null && !idleCallbacks.isEmpty()) {
            runIdleCallback(idleCallbacks.removeFirst());
        }
    }

    private void runIdleCallback(Runnable callback) {
        try {
            callback.run();
        } catch (Throwable failure) {
            IrisLogging.reportError("Lifecycle idle callback failed.", failure);
        }
    }

    private void runRestartCallback(Runnable callback, ActiveOperation operation) {
        try {
            callback.run();
        } catch (Throwable failure) {
            IrisLogging.reportError("Lifecycle restart dispatch failed for operation " + operation.id() + ".", failure);
        }
    }

    public enum Domain {
        WORLD_MUTATION,
        PACK_MUTATION,
        SERVER_LIFECYCLE
    }

    public enum OperationKind {
        WORLD_CREATE,
        WORLD_LOAD,
        WORLD_UNLOAD,
        WORLD_REMOVE,
        WORLD_REPLACE,
        STUDIO_OPEN,
        STUDIO_CLOSE,
        PACK_CREATE,
        PACK_DOWNLOAD,
        PACK_PUBLISH,
        DATAPACK_COMPILE,
        SERVER_RESTART
    }

    public record ActiveOperation(long id, Domain domain, OperationKind kind, String target) {
        public ActiveOperation {
            if (id < 1L) {
                throw new IllegalArgumentException("id must be positive");
            }
            Objects.requireNonNull(domain, "domain");
            Objects.requireNonNull(kind, "kind");
            target = Objects.requireNonNull(target, "target").trim();
            if (target.isEmpty()) {
                throw new IllegalArgumentException("target must not be blank");
            }
        }
    }

    public static final class BusyException extends IllegalStateException {
        private final ActiveOperation currentOperation;

        private BusyException(ActiveOperation currentOperation) {
            super("Lifecycle domain " + currentOperation.domain()
                    + " is busy with " + currentOperation.kind()
                    + " target=" + currentOperation.target()
                    + " id=" + currentOperation.id());
            this.currentOperation = currentOperation;
        }

        public ActiveOperation currentOperation() {
            return currentOperation;
        }

        public long operationId() {
            return currentOperation.id();
        }

        public Domain domain() {
            return currentOperation.domain();
        }

        public OperationKind operationKind() {
            return currentOperation.kind();
        }

        public String target() {
            return currentOperation.target();
        }
    }

    public interface Lease extends AutoCloseable {
        ActiveOperation operation();

        boolean isClosed();

        @Override
        void close();
    }

    private static final class LeaseImpl implements Lease {
        private final LifecycleOperationCoordinator coordinator;
        private final ActiveOperation operation;
        private final AtomicBoolean closed;

        private LeaseImpl(LifecycleOperationCoordinator coordinator, ActiveOperation operation) {
            this.coordinator = coordinator;
            this.operation = operation;
            closed = new AtomicBoolean();
        }

        @Override
        public ActiveOperation operation() {
            return operation;
        }

        @Override
        public boolean isClosed() {
            return closed.get();
        }

        @Override
        public void close() {
            if (closed.compareAndSet(false, true)) {
                coordinator.release(operation);
            }
        }
    }
}
