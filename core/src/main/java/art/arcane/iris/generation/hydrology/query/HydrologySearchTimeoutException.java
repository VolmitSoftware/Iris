package art.arcane.iris.generation.hydrology.query;

import java.time.Duration;
import java.util.Optional;
import java.util.concurrent.TimeoutException;

public final class HydrologySearchTimeoutException extends TimeoutException {
    private final HydrologyFeatureRef nearestSoFar;

    public HydrologySearchTimeoutException(Duration timeout, HydrologyFeatureRef nearestSoFar) {
        super("The hydrology feature search ran past its " + timeout.toMillis() + " ms limit.");
        this.nearestSoFar = nearestSoFar;
    }

    public Optional<HydrologyFeatureRef> nearestSoFar() {
        return Optional.ofNullable(nearestSoFar);
    }
}
