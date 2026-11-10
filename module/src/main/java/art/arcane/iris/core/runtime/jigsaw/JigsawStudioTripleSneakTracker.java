package art.arcane.iris.core.runtime.jigsaw;

import java.util.HashMap;
import java.util.Iterator;
import java.util.Map;
import java.util.Objects;
import java.util.UUID;

public final class JigsawStudioTripleSneakTracker {
    public static final long DEFAULT_WINDOW_NANOS = 1_500_000_000L;

    private static final int REQUIRED_SNEAKS = 3;

    private final long windowNanos;
    private final Map<UUID, GestureState> gestures = new HashMap<>();

    public JigsawStudioTripleSneakTracker() {
        this(DEFAULT_WINDOW_NANOS);
    }

    public JigsawStudioTripleSneakTracker(long windowNanos) {
        if (windowNanos <= 0L) {
            throw new IllegalArgumentException("Jigsaw Studio triple-sneak window must be positive");
        }
        this.windowNanos = windowNanos;
    }

    public synchronized Progress recordSneak(
            UUID playerId,
            UUID worldId,
            UUID requestId,
            long nowNanos
    ) {
        UUID player = Objects.requireNonNull(playerId, "Jigsaw Studio gesture player ID");
        UUID world = Objects.requireNonNull(worldId, "Jigsaw Studio gesture world ID");
        UUID request = Objects.requireNonNull(requestId, "Jigsaw Studio gesture request ID");
        GestureState previous = gestures.get(player);
        if (previous == null
                || !previous.worldId().equals(world)
                || !previous.requestId().equals(request)
                || expired(previous, nowNanos)) {
            gestures.put(player, new GestureState(world, request, nowNanos, nowNanos, 1));
            return Progress.FIRST;
        }

        int count = previous.count() + 1;
        if (count >= REQUIRED_SNEAKS) {
            gestures.remove(player);
            return Progress.TRIGGERED;
        }
        gestures.put(player, new GestureState(
                world,
                request,
                previous.startedAtNanos(),
                nowNanos,
                count));
        return Progress.SECOND;
    }

    public synchronized void clearPlayer(UUID playerId) {
        if (playerId != null) {
            gestures.remove(playerId);
        }
    }

    public synchronized int clearRequest(UUID requestId) {
        if (requestId == null) {
            return 0;
        }
        int removed = 0;
        Iterator<GestureState> states = gestures.values().iterator();
        while (states.hasNext()) {
            GestureState state = states.next();
            if (state.requestId().equals(requestId)) {
                states.remove();
                removed++;
            }
        }
        return removed;
    }

    public synchronized void clearAll() {
        gestures.clear();
    }

    public synchronized int trackedPlayers() {
        return gestures.size();
    }

    private boolean expired(GestureState state, long nowNanos) {
        long elapsedSinceStart = nowNanos - state.startedAtNanos();
        long elapsedSinceLast = nowNanos - state.lastSneakAtNanos();
        return elapsedSinceStart < 0L
                || elapsedSinceLast < 0L
                || elapsedSinceStart > windowNanos;
    }

    public enum Progress {
        FIRST,
        SECOND,
        TRIGGERED
    }

    private record GestureState(
            UUID worldId,
            UUID requestId,
            long startedAtNanos,
            long lastSneakAtNanos,
            int count
    ) {
    }
}
