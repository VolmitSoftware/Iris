package art.arcane.iris.core.safeguard;

import java.util.List;

/**
 * The operator lines a boot prints when Iris enabled but its runtime is locked.
 * <p>
 * The Danger banner states the mode; this states the consequence. A locked runtime that still enabled is not
 * covered by the vanilla-fallback refusal - that only runs when enable itself fails - so without this the
 * only evidence is the first chunk request throwing hours later.
 */
public final class RuntimeLockNotice {
    private RuntimeLockNotice() {
    }

    public static List<String> compose(String denialReason, boolean hasManagedWorldStorage) {
        if (denialReason == null || denialReason.isBlank()) {
            return List.of();
        }

        return List.of(
                "Iris enabled with a locked runtime: " + denialReason.trim(),
                hasManagedWorldStorage
                        ? "Every configured Iris world is generation-locked and refuses to generate terrain"
                        + " until this is resolved and the server restarts."
                        : "This server has no Iris world storage, so nothing is generation-locked;"
                        + " world creation and player login stay refused until this is resolved.");
    }
}
