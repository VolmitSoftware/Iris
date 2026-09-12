package art.arcane.iris.generation.runtime;

public final class GenerationSessionLease implements AutoCloseable {
    private static final GenerationSessionLease NOOP = new GenerationSessionLease(null, null, 0L, null);

    private final GenerationSessionManager manager;
    private final GenerationSessionManager.GenerationSessionState state;
    private final long sessionId;
    private final GenerationTransitionGate.Participation participation;
    private boolean released;

    GenerationSessionLease(GenerationSessionManager manager, GenerationSessionManager.GenerationSessionState state,
                           long sessionId, GenerationTransitionGate.Participation participation) {
        this.manager = manager;
        this.state = state;
        this.sessionId = sessionId;
        this.participation = participation;
        this.released = false;
    }

    public static GenerationSessionLease noop() {
        return NOOP;
    }

    public long sessionId() {
        return sessionId;
    }

    public void detachThread() {
        if (participation != null) {
            participation.detachThread();
        }
    }

    @Override
    public void close() {
        if (released || state == null) {
            return;
        }

        released = true;
        try {
            manager.releaseLease(state);
        } finally {
            participation.close();
        }
    }
}
