package com.volmit.placement;

import java.util.Objects;
import java.util.Optional;

/** Result of one placement attempt ({@link Placements#place}). A rejected attempt committed nothing. */
public record PlacementOutcome(Status status, Optional<String> rejection) {
    public enum Status { PLACED, REJECTED }

    public PlacementOutcome {
        Objects.requireNonNull(status, "status");
        Objects.requireNonNull(rejection, "rejection");
        if ((status == Status.REJECTED) != rejection.isPresent()) {
            throw new IllegalArgumentException("Only rejected placements carry a rejection reason");
        }
    }

    public static PlacementOutcome placed() {
        return new PlacementOutcome(Status.PLACED, Optional.empty());
    }

    /** Rejected with the reason the stage gave, such as {@code slope}. */
    public static PlacementOutcome rejected(String reason) {
        return new PlacementOutcome(Status.REJECTED, Optional.of(reason));
    }

    public boolean isPlaced() {
        return status == Status.PLACED;
    }
}
