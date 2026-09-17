package art.arcane.iris.engine.object;

import java.util.Objects;

/** A named prerequisite, scoped to one placement plan. */
public record PlacementLifecycleDependency(String name) {
    public PlacementLifecycleDependency {
        Objects.requireNonNull(name, "Dependency name");
        if (name.isBlank()) {
            throw new IllegalArgumentException("Placement dependency name cannot be blank");
        }
    }
}
