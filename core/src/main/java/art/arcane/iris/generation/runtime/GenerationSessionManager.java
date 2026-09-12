package art.arcane.iris.generation.runtime;

import art.arcane.iris.generation.context.IrisContext;

import java.util.Optional;

import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicLong;
import java.util.concurrent.atomic.AtomicReference;
import java.util.concurrent.CancellationException;

public final class GenerationSessionManager {
    private final AtomicLong sessionSequence;
    private final AtomicReference<GenerationSessionState> current;
    private final Object drainMonitor;
    private final GenerationTransitionGate transitionGate;

    public GenerationSessionManager() {
        this(false);
    }

    public GenerationSessionManager(boolean studioTransitions) {
        this.transitionGate = new GenerationTransitionGate(studioTransitions);
        this.sessionSequence = new AtomicLong(0L);
        this.current = new AtomicReference<>(new GenerationSessionState(nextSessionId(), new AtomicBoolean(true), new AtomicInteger(0), new AtomicBoolean(false), new AtomicReference<>(null)));
        this.drainMonitor = new Object();
    }

    public GenerationSessionLease acquire(String operation) throws GenerationSessionException {
        GenerationTransitionGate.Participation participation = transitionGate.enter();
        try {
            return acquireAdmitted(operation, participation);
        } catch (Throwable failure) {
            participation.close();
            throw failure;
        }
    }

    public GenerationTransitionGate transitionGate() {
        return transitionGate;
    }

    public GenerationSessionLease acquireForEngine(Engine engine, String operation) throws GenerationSessionException {
        GenerationTransitionGate.Participation participation;
        try {
            participation = transitionGate.enter();
        } catch (CancellationException cancellation) {
            GenerationSessionException failure = new GenerationSessionException(
                    "Generation session cancelled while waiting to run " + operation + ".",
                    engine.isClosing() || engine.isClosed());
            failure.initCause(cancellation);
            throw failure;
        }
        try {
            if (engine.isClosing() || engine.isClosed()) {
                throw new GenerationSessionException("Generation session rejected new work for " + operation
                        + " while the Iris engine is closing.", engine.isClosed());
            }
            return acquireAdmitted(operation, participation);
        } catch (Throwable failure) {
            participation.close();
            throw failure;
        }
    }

    public Optional<GenerationSessionLease> tryAcquireForEngine(Engine engine, String operation)
            throws GenerationSessionException {
        Optional<GenerationTransitionGate.Participation> entered = transitionGate.tryEnter();
        if (entered.isEmpty()) {
            return Optional.empty();
        }
        GenerationTransitionGate.Participation participation = entered.get();
        try {
            if (engine.isClosing() || engine.isClosed()) {
                throw new GenerationSessionException("Generation session rejected new work for " + operation
                        + " while the Iris engine is closing.", engine.isClosed());
            }
            IrisContext context = IrisContext.get();
            return Optional.of(context != null && context.getEngine() == engine && context.getGenerationSessionId() != 0L
                    ? continueAdmittedSession(operation, context.getGenerationSessionId(), participation)
                    : acquireAdmitted(operation, participation));
        } catch (Throwable failure) {
            participation.close();
            throw failure;
        }
    }

    private GenerationSessionLease acquireAdmitted(String operation,
                                                   GenerationTransitionGate.Participation participation)
            throws GenerationSessionException {
        while (true) {
            GenerationSessionState state = current.get();
            if (state == null || !state.accepting().get()) {
                throw rejected(operation, state == null ? null : state);
            }

            state.activeLeases().incrementAndGet();
            if (state != current.get()) {
                state.activeLeases().decrementAndGet();
                continue;
            }

            if (!state.accepting().get()) {
                releaseLease(state);
                throw rejected(operation, state);
            }

            return new GenerationSessionLease(this, state, state.sessionId(), participation);
        }
    }

    public GenerationSessionLease continueSession(String operation, long sessionId) throws GenerationSessionException {
        GenerationTransitionGate.Participation participation = transitionGate.continueAdmittedWork();
        try {
            return continueAdmittedSession(operation, sessionId, participation);
        } catch (Throwable failure) {
            participation.close();
            throw failure;
        }
    }

    private GenerationSessionLease continueAdmittedSession(String operation, long sessionId,
                                                           GenerationTransitionGate.Participation participation)
            throws GenerationSessionException {
        while (true) {
            GenerationSessionState state = current.get();
            if (state == null || state.sessionId() != sessionId) {
                throw rejected(operation, state);
            }

            state.activeLeases().incrementAndGet();
            if (state != current.get()) {
                releaseLease(state);
                continue;
            }

            return new GenerationSessionLease(this, state, state.sessionId(), participation);
        }
    }

    public long currentSessionId() {
        GenerationSessionState state = current.get();
        return state == null ? 0L : state.sessionId();
    }

    public int activeLeases() {
        GenerationSessionState state = current.get();
        return state == null ? 0 : state.activeLeases().get();
    }

    public void sealAndAwait(String reason, long timeoutMs) throws GenerationSessionException {
        sealAndAwait(reason, timeoutMs, false);
    }

    public void sealAndAwait(String reason, long timeoutMs, boolean teardown) throws GenerationSessionException {
        GenerationSessionState state = current.get();
        if (state == null) {
            return;
        }

        state.accepting().set(false);
        state.teardown().set(teardown);
        state.sealReason().set(reason);
        long deadline = System.currentTimeMillis() + Math.max(0L, timeoutMs);
        synchronized (drainMonitor) {
            while (state.activeLeases().get() > 0) {
                long remaining = deadline - System.currentTimeMillis();
                if (remaining <= 0L) {
                    break;
                }

                try {
                    drainMonitor.wait(remaining);
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                    throw new GenerationSessionException("Generation session " + state.sessionId() + " was interrupted while draining for " + reason + ".", teardown);
                }
            }
        }

        if (state.activeLeases().get() > 0) {
            throw new GenerationSessionException("Generation session " + state.sessionId() + " failed to drain for " + reason + " after " + timeoutMs + "ms. Active leases=" + state.activeLeases().get() + ".", teardown);
        }
    }

    public void activateNextSession() {
        current.set(new GenerationSessionState(nextSessionId(), new AtomicBoolean(true), new AtomicInteger(0), new AtomicBoolean(false), new AtomicReference<>(null)));
    }

    private long nextSessionId() {
        return sessionSequence.incrementAndGet();
    }

    void releaseLease(GenerationSessionState state) {
        int remaining = state.activeLeases().decrementAndGet();
        if (remaining <= 0) {
            synchronized (drainMonitor) {
                drainMonitor.notifyAll();
            }
        }
    }

    private GenerationSessionException rejected(String operation, GenerationSessionState state) {
        long sessionId = state == null ? currentSessionId() : state.sessionId();
        boolean teardown = state != null && state.teardown().get();
        String reason = state == null ? null : state.sealReason().get();
        if (teardown && reason != null && !reason.isBlank()) {
            return new GenerationSessionException("Generation session " + sessionId + " rejected new work for " + operation + " during " + reason + ".", true);
        }

        return new GenerationSessionException("Generation session " + sessionId + " rejected new work for " + operation + ".", teardown);
    }

    record GenerationSessionState(long sessionId, AtomicBoolean accepting, AtomicInteger activeLeases, AtomicBoolean teardown, AtomicReference<String> sealReason) {
    }
}
