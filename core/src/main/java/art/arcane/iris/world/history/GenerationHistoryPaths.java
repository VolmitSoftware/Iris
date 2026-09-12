package art.arcane.iris.world.history;

import java.nio.file.Path;
import java.util.Objects;
import java.util.regex.Pattern;

public final class GenerationHistoryPaths {
    public static final String IRIS_DIRECTORY_NAME = "iris";
    public static final String GENERATION_DIRECTORY_NAME = "generation";
    public static final String EPOCHS_DIRECTORY_NAME = "epochs";
    public static final String ACTIVATIONS_DIRECTORY_NAME = "activations";
    public static final String OWNERSHIP_DIRECTORY_NAME = "ownership";
    public static final String LEGACY_PACK_DIRECTORY_NAME = "pack";
    public static final String LEGACY_MANTLE_DIRECTORY_NAME = "mantle-hydrology";
    public static final String REGION_DIRECTORY_NAME = "region";

    private static final Pattern SHA_256 = Pattern.compile("[0-9a-f]{64}");

    private final Path dimensionRoot;
    private final Path irisRoot;
    private final Path generationRoot;
    private final Path epochsRoot;
    private final Path activationsRoot;
    private final Path ownershipRoot;
    private final Path manifest;
    private final Path legacyPackRoot;
    private final Path legacyMantleRoot;
    private final Path regionRoot;

    private GenerationHistoryPaths(Path dimensionRoot) {
        this.dimensionRoot = Objects.requireNonNull(dimensionRoot, "dimensionRoot")
                .toAbsolutePath()
                .normalize();
        irisRoot = this.dimensionRoot.resolve(IRIS_DIRECTORY_NAME);
        generationRoot = irisRoot.resolve(GENERATION_DIRECTORY_NAME);
        epochsRoot = generationRoot.resolve(EPOCHS_DIRECTORY_NAME);
        activationsRoot = generationRoot.resolve(ACTIVATIONS_DIRECTORY_NAME);
        ownershipRoot = generationRoot.resolve(OWNERSHIP_DIRECTORY_NAME);
        manifest = generationRoot.resolve(GenerationHistoryStore.MANIFEST_FILE_NAME);
        legacyPackRoot = irisRoot.resolve(LEGACY_PACK_DIRECTORY_NAME);
        legacyMantleRoot = this.dimensionRoot.resolve(LEGACY_MANTLE_DIRECTORY_NAME);
        regionRoot = this.dimensionRoot.resolve(REGION_DIRECTORY_NAME);
    }

    public static GenerationHistoryPaths forDimension(Path dimensionRoot) {
        return new GenerationHistoryPaths(dimensionRoot);
    }

    public Path dimensionRoot() {
        return dimensionRoot;
    }

    public Path irisRoot() {
        return irisRoot;
    }

    public Path generationRoot() {
        return generationRoot;
    }

    public Path epochsRoot() {
        return epochsRoot;
    }

    public Path epochRoot(String epochId) {
        String requiredEpochId = Objects.requireNonNull(epochId, "epochId");
        if (!SHA_256.matcher(requiredEpochId).matches()) {
            throw new IllegalArgumentException("epochId must be a lowercase SHA-256 digest");
        }
        return epochsRoot.resolve(requiredEpochId);
    }

    public Path packRoot(String epochId) {
        return epochRoot(epochId).resolve(LEGACY_PACK_DIRECTORY_NAME);
    }

    public Path activationsRoot() {
        return activationsRoot;
    }

    public Path activationRoot(long activationId) {
        if (activationId <= 0L) {
            throw new IllegalArgumentException("activationId must be positive");
        }
        return activationsRoot.resolve(Long.toString(activationId));
    }

    public Path activationMantleRoot(long activationId) {
        return activationRoot(activationId).resolve(LEGACY_MANTLE_DIRECTORY_NAME);
    }

    public Path ownershipRoot() {
        return ownershipRoot;
    }

    public Path manifest() {
        return manifest;
    }

    public Path legacyPackRoot() {
        return legacyPackRoot;
    }

    public Path legacyMantleRoot() {
        return legacyMantleRoot;
    }

    public Path regionRoot() {
        return regionRoot;
    }
}
