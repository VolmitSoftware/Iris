package art.arcane.iris.world.storage.matter;

import java.util.Set;

/**
 * Slice ids that older Iris releases wrote into saved matter and that a newer jar may no longer register.
 * A reader skips these instead of failing; every other unknown id stays a hard error.
 */
public final class RetiredMatterSlices {
    public static final String TREE_CANOPY = "art.arcane.iris.generation.mantle.TreeCanopy";

    private static final Set<String> IDS = Set.of(TREE_CANOPY);

    private RetiredMatterSlices() {
    }

    public static boolean isRetired(String id) {
        return IDS.contains(id);
    }
}
