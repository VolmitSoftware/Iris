package art.arcane.iris.generation.runtime;

import java.util.IdentityHashMap;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.CancellationException;
import java.util.concurrent.TimeUnit;

public final class GenerationTransitionGate {
    private final boolean enabled;
    private final Participation noop = new Participation(null);
    private final Map<Thread, Integer> participants = new IdentityHashMap<>();
    private int active;
    private int waitingTransitions;
    private Thread transitionOwner;

    public GenerationTransitionGate(boolean enabled) {
        this.enabled = enabled;
    }

    public Participation enter() {
        if (!enabled) {
            return noop;
        }
        synchronized (this) {
            return enter(false);
        }
    }

    public Optional<Participation> tryEnter() {
        if (!enabled) {
            return Optional.of(noop);
        }
        synchronized (this) {
            Thread owner = Thread.currentThread();
            return blocked(owner, false) ? Optional.empty() : Optional.of(admit(owner));
        }
    }

    Participation continueAdmittedWork() {
        if (!enabled) {
            return noop;
        }
        synchronized (this) {
            return enter(true);
        }
    }

    private Participation enter(boolean continuation) {
        Thread owner = Thread.currentThread();
        while (blocked(owner, continuation)) {
            awaitChange();
        }
        return admit(owner);
    }

    private boolean blocked(Thread owner, boolean continuation) {
        return transitionOwner != null && transitionOwner != owner
                || waitingTransitions > 0 && transitionOwner != owner
                && !participants.containsKey(owner) && !(continuation && active > 0);
    }

    private Participation admit(Thread owner) {
        active++;
        participants.merge(owner, 1, Integer::sum);
        return new Participation(owner);
    }

    public synchronized Transition beginTransition(long timeoutMillis) {
        if (!enabled) {
            throw new IllegalStateException("Live generation transitions are disabled for this engine.");
        }
        if (timeoutMillis < 1L) {
            throw new IllegalArgumentException("Generation transition timeout must be positive.");
        }
        Thread owner = Thread.currentThread();
        if (participants.containsKey(owner) || transitionOwner == owner) {
            throw new IllegalStateException("Cannot reload generation from inside an active generation operation.");
        }
        waitingTransitions++;
        long deadline = System.nanoTime() + TimeUnit.MILLISECONDS.toNanos(timeoutMillis);
        try {
            while (transitionOwner != null || active > 0) {
                long remaining = deadline - System.nanoTime();
                if (remaining <= 0L) {
                    throw new IllegalStateException("Timed out draining generation for a Studio update after "
                            + timeoutMillis + "ms; active operations=" + active + ".");
                }
                try {
                    TimeUnit.NANOSECONDS.timedWait(this, remaining);
                } catch (InterruptedException failure) {
                    Thread.currentThread().interrupt();
                    throw new IllegalStateException("Interrupted while draining generation for a Studio update.", failure);
                }
            }
            transitionOwner = owner;
            return new Transition(owner);
        } finally {
            waitingTransitions--;
            notifyAll();
        }
    }

    private void awaitChange() {
        try {
            wait();
        } catch (InterruptedException failure) {
            Thread.currentThread().interrupt();
            CancellationException cancellation = new CancellationException(
                    "Interrupted while waiting for generation admission.");
            cancellation.initCause(failure);
            throw cancellation;
        }
    }

    public final class Participation implements AutoCloseable {
        private final Thread owner;
        private boolean closed;
        private boolean attached = true;

        private Participation(Thread owner) {
            this.owner = owner;
        }

        public Participation attach() {
            if (owner == null) {
                return this;
            }
            synchronized (GenerationTransitionGate.this) {
                if (closed) {
                    throw new IllegalStateException("Generation participation has already ended.");
                }
                return enter(true);
            }
        }

        public void detachThread() {
            if (owner == null) {
                return;
            }
            synchronized (GenerationTransitionGate.this) {
                if (!closed && attached) {
                    detach();
                }
            }
        }

        private void detach() {
            int depth = participants.get(owner);
            if (depth == 1) {
                participants.remove(owner);
            } else {
                participants.put(owner, depth - 1);
            }
            attached = false;
        }

        @Override
        public void close() {
            if (owner == null) {
                return;
            }
            synchronized (GenerationTransitionGate.this) {
                if (closed) {
                    return;
                }
                closed = true;
                if (attached) {
                    detach();
                }
                active--;
                GenerationTransitionGate.this.notifyAll();
            }
        }
    }

    public final class Transition implements AutoCloseable {
        private final Thread owner;
        private boolean closed;

        private Transition(Thread owner) {
            this.owner = owner;
        }

        @Override
        public void close() {
            synchronized (GenerationTransitionGate.this) {
                if (closed) {
                    return;
                }
                if (Thread.currentThread() != owner || transitionOwner != owner) {
                    throw new IllegalStateException("Generation transition belongs to another thread.");
                }
                closed = true;
                transitionOwner = null;
                GenerationTransitionGate.this.notifyAll();
            }
        }
    }
}
