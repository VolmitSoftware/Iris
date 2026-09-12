package art.arcane.iris.generation.hydrology.cave;

import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.Map;
import java.util.Objects;
import java.util.Set;

public record HydrologyCaveCandidate(
        HydrologyCaveSource source,
        String profileKey,
        HydrologyCavePlannerSettings settings,
        boolean allowDryCaveConnections,
        Map<CavePosition, HydrologyCaveAction> actions,
        Set<CavePosition> intentionalOpenings
) {
    public HydrologyCaveCandidate {
        Objects.requireNonNull(source);
        if (profileKey == null || profileKey.isBlank()) {
            throw new IllegalArgumentException("profileKey must not be blank");
        }
        profileKey = profileKey.trim();
        Objects.requireNonNull(settings);
        actions = Collections.unmodifiableMap(new LinkedHashMap<>(Objects.requireNonNull(actions)));
        intentionalOpenings = Collections.unmodifiableSet(
                new LinkedHashSet<>(Objects.requireNonNull(intentionalOpenings))
        );
        if (actions.isEmpty()) {
            throw new IllegalArgumentException("A cave candidate requires at least one planned action");
        }
    }
}
