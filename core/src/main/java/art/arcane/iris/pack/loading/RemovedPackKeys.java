package art.arcane.iris.pack.loading;

import java.util.Set;

/**
 * Keys that packs may still carry although no field reads them any more, and that pack validation already reports
 * once per file with advice on what to use instead.
 */
public final class RemovedPackKeys {
    public static final String CAVE_PROFILE = "caveProfile";
    private static final Set<String> REPORTED_BY_PACK_VALIDATION = Set.of(CAVE_PROFILE);

    private RemovedPackKeys() {
    }

    public static boolean isReportedByPackValidation(String key) {
        return REPORTED_BY_PACK_VALIDATION.contains(key);
    }
}
