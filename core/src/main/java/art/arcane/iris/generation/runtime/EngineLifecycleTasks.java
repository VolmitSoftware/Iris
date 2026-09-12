package art.arcane.iris.generation.runtime;

import art.arcane.iris.generation.context.IrisContext;

import java.util.Objects;
import java.util.Optional;
import java.util.function.Supplier;

public final class EngineLifecycleTasks {
    private EngineLifecycleTasks() {
    }

    public static boolean run(Engine engine, String operation, Runnable task) {
        Objects.requireNonNull(engine);
        Objects.requireNonNull(operation);
        Objects.requireNonNull(task);
        try {
            Optional<GenerationSessionLease> admitted = tryAcquire(engine, operation);
            if (admitted.isEmpty()) {
                return false;
            }
            try (GenerationSessionLease lease = admitted.get();
                 IrisContext.Scope ignored = IrisContext.open(engine, lease.sessionId(), null)) {
                task.run();
                return true;
            }
        } catch (GenerationSessionException exception) {
            if (engine.isClosing() || engine.isClosed() || exception.isExpectedTeardown()) {
                return false;
            }
            throw new IllegalStateException("Iris lifecycle rejected " + operation + ".", exception);
        }
    }

    public static <T> T call(Engine engine, String operation, Supplier<T> task, T unavailable) {
        Objects.requireNonNull(task);
        Objects.requireNonNull(engine);
        Objects.requireNonNull(operation);
        try {
            Optional<GenerationSessionLease> admitted = tryAcquire(engine, operation);
            if (admitted.isEmpty()) {
                return unavailable;
            }
            try (GenerationSessionLease lease = admitted.get();
                 IrisContext.Scope ignored = IrisContext.open(engine, lease.sessionId(), null)) {
                return task.get();
            }
        } catch (GenerationSessionException exception) {
            if (engine.isClosing() || engine.isClosed() || exception.isExpectedTeardown()) {
                return unavailable;
            }
            throw new IllegalStateException("Iris lifecycle rejected " + operation + ".", exception);
        }
    }

    private static Optional<GenerationSessionLease> tryAcquire(Engine engine, String operation)
            throws GenerationSessionException {
        GenerationSessionManager sessions = engine.getGenerationSessions();
        return sessions == null ? Optional.of(GenerationSessionLease.noop())
                : sessions.tryAcquireForEngine(engine, operation);
    }
}
