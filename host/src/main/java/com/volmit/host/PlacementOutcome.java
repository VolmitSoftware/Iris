package com.volmit.host;

import java.util.Objects;
import java.util.Optional;

/** Result of one placement attempt. A rejected attempt committed nothing. */
public record PlacementOutcome(Status status, Optional<String> rejection) {
    public enum Status { PLACED, REJECTED }

    public PlacementOutcome {
        Objects.requireNonNull(status, "status");
        Objects.requireNonNull(rejection, "rejection");
        if ((status == Status.REJECTED) != rejection.isPresent()) {
            throw new IllegalArgumentException("Only rejected placements carry a rejection reason");
        }
    }

    static PlacementOutcome placed() {
        return new PlacementOutcome(Status.PLACED, Optional.empty());
    }

    static PlacementOutcome rejected(String reason) {
        return new PlacementOutcome(Status.REJECTED, Optional.of(reason));
    }

    public boolean isPlaced() {
        return status == Status.PLACED;
    }
}
