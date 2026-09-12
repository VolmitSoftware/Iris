package art.arcane.iris.world;

import org.bukkit.NamespacedKey;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Collection;
import java.util.List;
import java.util.Locale;
import java.util.Objects;

public final class WorldRemovalPathPolicy {
    private WorldRemovalPathPolicy() {
    }

    public static Target resolve(String identifier, String configuredMainWorld, Path levelRoot) {
        return resolve(identifier, configuredMainWorld, List.of(configuredMainWorld), levelRoot);
    }

    public static Target resolve(
            String identifier,
            String currentMainWorld,
            Collection<String> protectedWorldNames,
            Path levelRoot
    ) {
        String requestedIdentifier = requireIdentifier(identifier);
        String mainWorld = requireIdentifier(currentMainWorld);
        for (String protectedWorldName : Objects.requireNonNull(protectedWorldNames, "protectedWorldNames")) {
            if (protectedWorldName != null
                    && !protectedWorldName.isBlank()
                    && requestedIdentifier.equalsIgnoreCase(protectedWorldName.trim())) {
                throw new Rejection(RejectionReason.CONFIGURED_MAIN_WORLD,
                        "A current or configured main world cannot be removed.");
            }
        }
        if (requestedIdentifier.toLowerCase(Locale.ENGLISH).startsWith(NamespacedKey.MINECRAFT + ":")) {
            throw new Rejection(RejectionReason.MINECRAFT_NAMESPACE,
                    "Minecraft namespace worlds cannot be removed.");
        }

        NamespacedKey worldKey;
        try {
            worldKey = IrisWorldStorage.managedKeyFromName(requestedIdentifier, mainWorld);
        } catch (IllegalArgumentException failure) {
            throw classifyIdentifierFailure(requestedIdentifier, failure);
        }

        Path normalizedLevelRoot = Objects.requireNonNull(levelRoot, "levelRoot").toAbsolutePath().normalize();
        if (Files.isSymbolicLink(normalizedLevelRoot)) {
            throw new Rejection(RejectionReason.SYMBOLIC_LINK,
                    "World storage path contains a symbolic link: " + normalizedLevelRoot);
        }
        StorageLayout storageLayout;
        try {
            storageLayout = resolveStorageLayout(normalizedLevelRoot, worldKey);
        } catch (RuntimeException failure) {
            throw classifyStorageFailure(normalizedLevelRoot, worldKey, failure);
        }
        validateStoragePath(normalizedLevelRoot, worldKey, storageLayout.dimensionDirectory());
        validateStorageRoot(normalizedLevelRoot, worldKey, storageLayout.storageDirectory());
        return new Target(
                requestedIdentifier,
                worldKey,
                IrisWorldStorage.logicalName(worldKey, mainWorld),
                normalizedLevelRoot,
                storageLayout.dimensionDirectory(),
                storageLayout.storageDirectory()
        );
    }

    public static void validateStoragePath(Path levelRoot, NamespacedKey worldKey, Path candidate) {
        Path normalizedLevelRoot = Objects.requireNonNull(levelRoot, "levelRoot").toAbsolutePath().normalize();
        if (Files.isSymbolicLink(normalizedLevelRoot)) {
            throw new Rejection(RejectionReason.SYMBOLIC_LINK,
                    "World storage path contains a symbolic link: " + normalizedLevelRoot);
        }
        Path expected;
        try {
            expected = resolveStorageLayout(
                    normalizedLevelRoot,
                    Objects.requireNonNull(worldKey, "worldKey")
            ).dimensionDirectory();
        } catch (RuntimeException failure) {
            throw classifyStorageFailure(normalizedLevelRoot, worldKey, failure);
        }
        Path normalizedCandidate = Objects.requireNonNull(candidate, "candidate").toAbsolutePath().normalize();
        if (!normalizedCandidate.equals(expected)) {
            throw new Rejection(RejectionReason.OUTSIDE_STORAGE_ROOT,
                    "The world directory is outside its exact Iris dimension storage root.");
        }
    }

    public static void validateStorageRoot(Path levelRoot, NamespacedKey worldKey, Path candidate) {
        Path normalizedLevelRoot = Objects.requireNonNull(levelRoot, "levelRoot").toAbsolutePath().normalize();
        if (Files.isSymbolicLink(normalizedLevelRoot)) {
            throw new Rejection(RejectionReason.SYMBOLIC_LINK,
                    "World storage path contains a symbolic link: " + normalizedLevelRoot);
        }
        Path expected;
        try {
            expected = resolveStorageLayout(
                    normalizedLevelRoot,
                    Objects.requireNonNull(worldKey, "worldKey")
            ).storageDirectory();
        } catch (RuntimeException failure) {
            throw classifyStorageFailure(normalizedLevelRoot, worldKey, failure);
        }
        Path normalizedCandidate = Objects.requireNonNull(candidate, "candidate").toAbsolutePath().normalize();
        if (!normalizedCandidate.equals(expected)) {
            throw new Rejection(RejectionReason.OUTSIDE_STORAGE_ROOT,
                    "The world storage directory is outside its exact current platform root.");
        }
    }

    private static Rejection classifyIdentifierFailure(String identifier, IllegalArgumentException failure) {
        NamespacedKey parsed = identifier.contains(":")
                ? NamespacedKey.fromString(identifier.toLowerCase(Locale.ENGLISH))
                : null;
        RejectionReason reason = parsed != null && !"iris".equals(parsed.getNamespace())
                ? RejectionReason.NOT_IRIS_NAMESPACE
                : RejectionReason.INVALID_IDENTIFIER;
        return new Rejection(reason, failure.getMessage(), failure);
    }

    private static Rejection classifyStorageFailure(
            Path levelRoot,
            NamespacedKey worldKey,
            RuntimeException failure
    ) {
        Path dimensions = levelRoot.resolve("dimensions");
        Path namespace = dimensions.resolve(worldKey.getNamespace());
        Path target = namespace.resolve(worldKey.getKey());
        String failureMessage = String.valueOf(failure.getMessage()).toLowerCase(Locale.ENGLISH);
        RejectionReason reason = failureMessage.contains("symbolic link")
                || Files.isSymbolicLink(dimensions)
                || Files.isSymbolicLink(namespace)
                || Files.isSymbolicLink(target)
                ? RejectionReason.SYMBOLIC_LINK
                : RejectionReason.OUTSIDE_STORAGE_ROOT;
        return new Rejection(reason, failure.getMessage(), failure);
    }

    private static StorageLayout resolveStorageLayout(Path levelRoot, NamespacedKey worldKey) {
        Path worldContainer = levelRoot.getParent();
        if (worldContainer == null) {
            throw new IllegalArgumentException("Selected level root has no world container: " + levelRoot);
        }
        String configuredWorldName = IrisWorldStorage.configuredWorldName(
                worldKey,
                levelRoot.getFileName().toString()
        );
        Path directDimension = IrisWorldStorage.requireSafeManagedDimensionRoot(
                levelRoot.toFile(),
                worldKey
        ).toPath().toAbsolutePath().normalize();
        Path dimensionDirectory = IrisWorldStorage.frozenDimensionRoot(
                worldContainer.toFile(),
                levelRoot.toFile(),
                configuredWorldName,
                worldKey
        ).map(file -> file.toPath().toAbsolutePath().normalize()).orElse(directDimension);
        if (dimensionDirectory.equals(directDimension)) {
            return new StorageLayout(directDimension, directDimension);
        }

        Path configuredDimension = IrisWorldStorage.configuredDimensionRoot(
                worldContainer.toFile(),
                levelRoot.toFile(),
                worldKey
        ).toPath().toAbsolutePath().normalize();
        if (!dimensionDirectory.equals(configuredDimension)) {
            throw new IllegalStateException("Iris world storage does not match the current platform layout.");
        }
        Path configuredLevel = IrisWorldStorage.configuredLevelRoot(
                worldContainer.toFile(),
                levelRoot.toFile(),
                worldKey
        ).toPath().toAbsolutePath().normalize();
        return new StorageLayout(configuredDimension, configuredLevel);
    }

    private static String requireIdentifier(String identifier) {
        if (identifier == null || identifier.isBlank()) {
            throw new Rejection(RejectionReason.INVALID_IDENTIFIER, "The world identifier cannot be empty.");
        }
        return identifier.trim();
    }

    public record Target(
            String requestedIdentifier,
            NamespacedKey worldKey,
            String logicalName,
            Path levelRoot,
            Path worldDirectory,
            Path storageDirectory
    ) {
        public Target {
            Objects.requireNonNull(requestedIdentifier, "requestedIdentifier");
            Objects.requireNonNull(worldKey, "worldKey");
            Objects.requireNonNull(logicalName, "logicalName");
            Objects.requireNonNull(levelRoot, "levelRoot");
            Objects.requireNonNull(worldDirectory, "worldDirectory");
            Objects.requireNonNull(storageDirectory, "storageDirectory");
        }
    }

    private record StorageLayout(Path dimensionDirectory, Path storageDirectory) {
    }

    public enum RejectionReason {
        INVALID_IDENTIFIER,
        CONFIGURED_MAIN_WORLD,
        MINECRAFT_NAMESPACE,
        NOT_IRIS_NAMESPACE,
        OUTSIDE_STORAGE_ROOT,
        SYMBOLIC_LINK
    }

    public static final class Rejection extends IllegalArgumentException {
        private final RejectionReason reason;

        private Rejection(RejectionReason reason, String message) {
            super(message);
            this.reason = Objects.requireNonNull(reason, "reason");
        }

        private Rejection(RejectionReason reason, String message, Throwable cause) {
            super(message, cause);
            this.reason = Objects.requireNonNull(reason, "reason");
        }

        public RejectionReason reason() {
            return reason;
        }
    }
}
