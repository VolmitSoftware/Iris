package art.arcane.iris.core.structure.conversion;

import java.time.Duration;
import java.util.Objects;

public record IrisStructureAdoptionLimits(
        int maxResources,
        int maxJsonBytes,
        int maxBinaryBytes,
        long maxTotalBytes,
        int maxStructuresScanned,
        int maxDiagnostics,
        int maxActivePlans,
        Duration planTtl
) {
    public IrisStructureAdoptionLimits {
        if (maxResources < 1 || maxResources > 100_000) {
            throw new IllegalArgumentException("Adoption resource limit must be between 1 and 100000");
        }
        if (maxJsonBytes < 1 || maxJsonBytes > 64 * 1024 * 1024) {
            throw new IllegalArgumentException("Adoption JSON limit must be between 1 and 67108864 bytes");
        }
        if (maxBinaryBytes < 1 || maxBinaryBytes > 256 * 1024 * 1024) {
            throw new IllegalArgumentException("Adoption binary limit must be between 1 and 268435456 bytes");
        }
        if (maxTotalBytes < 1L || maxTotalBytes > 1024L * 1024L * 1024L) {
            throw new IllegalArgumentException("Adoption aggregate limit must be between 1 and 1073741824 bytes");
        }
        if (maxStructuresScanned < 1 || maxStructuresScanned > 100_000) {
            throw new IllegalArgumentException("Adoption structure scan limit must be between 1 and 100000");
        }
        if (maxDiagnostics < 1 || maxDiagnostics > 10_000) {
            throw new IllegalArgumentException("Adoption diagnostic limit must be between 1 and 10000");
        }
        if (maxActivePlans < 1 || maxActivePlans > 10_000) {
            throw new IllegalArgumentException("Adoption active-plan limit must be between 1 and 10000");
        }
        planTtl = Objects.requireNonNull(planTtl, "planTtl");
        if (planTtl.isNegative() || planTtl.isZero() || planTtl.compareTo(Duration.ofHours(24L)) > 0) {
            throw new IllegalArgumentException("Adoption plan TTL must be between one nanosecond and 24 hours");
        }
    }

    public static IrisStructureAdoptionLimits defaults() {
        return new IrisStructureAdoptionLimits(
                10_000,
                8 * 1024 * 1024,
                64 * 1024 * 1024,
                1024L * 1024L * 1024L,
                10_000,
                1_000,
                1_024,
                Duration.ofMinutes(15L)
        );
    }
}
