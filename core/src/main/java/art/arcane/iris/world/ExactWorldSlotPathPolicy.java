package art.arcane.iris.world;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.LinkOption;
import java.nio.file.NoSuchFileException;
import java.nio.file.Path;
import java.nio.file.attribute.BasicFileAttributes;
import java.util.Objects;
import java.util.regex.Pattern;

public final class ExactWorldSlotPathPolicy {
    private static final Pattern SAFE_IRIS_KEY = Pattern.compile("^[a-z0-9_-]+$");

    private ExactWorldSlotPathPolicy() {
    }

    public static Target resolve(Path levelRoot, WorldSlotKey worldKey) {
        WorldSlotKey requiredWorldKey = Objects.requireNonNull(worldKey, "worldKey");
        Path canonicalLevelRoot = canonicalLevelRoot(levelRoot);
        SlotKind slotKind = classify(requiredWorldKey);
        Path dimensionsRoot = canonicalLevelRoot.resolve("dimensions");
        Path namespaceRoot = dimensionsRoot.resolve(requiredWorldKey.namespace());
        Path worldDirectory = namespaceRoot.resolve(requiredWorldKey.key()).normalize();
        if (!Objects.equals(worldDirectory.getParent(), namespaceRoot)) {
            throw new Rejection(
                    RejectionReason.PATH_TRAVERSAL,
                    "World key escapes its exact dimension namespace: " + requiredWorldKey
            );
        }

        requireDirectoryOrAbsent(dimensionsRoot, "dimension storage");
        requireDirectoryOrAbsent(namespaceRoot, "dimension namespace");
        requireDirectoryOrAbsent(worldDirectory, "world slot");
        return new Target(requiredWorldKey, slotKind, canonicalLevelRoot, namespaceRoot, worldDirectory);
    }

    public static Target validate(Path levelRoot, WorldSlotKey worldKey, Path candidate) {
        Path requiredCandidate = Objects.requireNonNull(candidate, "candidate");
        rejectTraversal(requiredCandidate, "World candidate");
        Target target = resolve(levelRoot, worldKey);
        Path normalizedCandidate = requiredCandidate.toAbsolutePath().normalize();
        if (!normalizedCandidate.equals(target.worldDirectory())) {
            throw new Rejection(
                    RejectionReason.PATH_MISMATCH,
                    "World candidate is not the exact expected dimension slot."
            );
        }
        requireDirectoryOrAbsent(normalizedCandidate, "world slot");
        return target;
    }

    private static Path canonicalLevelRoot(Path levelRoot) {
        Path requiredLevelRoot = Objects.requireNonNull(levelRoot, "levelRoot");
        rejectTraversal(requiredLevelRoot, "Level root");
        Path normalizedLevelRoot = requiredLevelRoot.toAbsolutePath().normalize();
        if (normalizedLevelRoot.getParent() == null) {
            throw new Rejection(RejectionReason.UNSAFE_ENTRY, "A filesystem root cannot be a level root.");
        }
        requireDirectory(normalizedLevelRoot, "level root", true);
        try {
            return normalizedLevelRoot.toRealPath();
        } catch (IOException exception) {
            throw new Rejection(
                    RejectionReason.UNSAFE_ENTRY,
                    "Could not canonicalize the level root: " + normalizedLevelRoot,
                    exception
            );
        }
    }

    private static SlotKind classify(WorldSlotKey worldKey) {
        if ("iris".equals(worldKey.namespace())) {
            if (!SAFE_IRIS_KEY.matcher(worldKey.key()).matches()) {
                throw new Rejection(
                        RejectionReason.INVALID_IRIS_KEY,
                        "Iris world keys must be safe single path segments."
                );
            }
            return SlotKind.IRIS_MANAGED;
        }
        if (!"minecraft".equals(worldKey.namespace())) {
            throw new Rejection(
                    RejectionReason.FOREIGN_NAMESPACE,
                    "Only Iris-managed and exact vanilla dimension slots can be replaced."
            );
        }
        return switch (worldKey.key()) {
            case "overworld" -> SlotKind.VANILLA_OVERWORLD;
            case "the_nether" -> SlotKind.VANILLA_NETHER;
            case "the_end" -> SlotKind.VANILLA_END;
            default -> throw new Rejection(
                    RejectionReason.UNSUPPORTED_MINECRAFT_SLOT,
                    "Only minecraft:overworld, minecraft:the_nether, and minecraft:the_end can be replaced."
            );
        };
    }

    private static void rejectTraversal(Path path, String label) {
        for (Path component : path) {
            if ("..".equals(component.toString())) {
                throw new Rejection(
                        RejectionReason.PATH_TRAVERSAL,
                        label + " contains path traversal."
                );
            }
        }
    }

    private static void requireDirectoryOrAbsent(Path path, String label) {
        if (Files.isSymbolicLink(path)) {
            throw new Rejection(
                    RejectionReason.SYMBOLIC_LINK,
                    "The " + label + " is a symbolic link: " + path
            );
        }
        requireDirectory(path, label, false);
    }

    private static void requireDirectory(Path path, String label, boolean required) {
        if (Files.isSymbolicLink(path)) {
            throw new Rejection(
                    RejectionReason.SYMBOLIC_LINK,
                    "The " + label + " is a symbolic link: " + path
            );
        }
        BasicFileAttributes attributes;
        try {
            attributes = Files.readAttributes(path, BasicFileAttributes.class, LinkOption.NOFOLLOW_LINKS);
        } catch (NoSuchFileException exception) {
            if (!required) {
                return;
            }
            throw new Rejection(
                    RejectionReason.MISSING_LEVEL_ROOT,
                    "The level root does not exist: " + path,
                    exception
            );
        } catch (IOException exception) {
            throw new Rejection(
                    RejectionReason.UNSAFE_ENTRY,
                    "Could not inspect the " + label + ": " + path,
                    exception
            );
        }
        if (!attributes.isDirectory()) {
            throw new Rejection(
                    RejectionReason.UNSAFE_ENTRY,
                    "The " + label + " is not a directory: " + path
            );
        }
    }

    public record Target(
            WorldSlotKey worldKey,
            SlotKind slotKind,
            Path levelRoot,
            Path namespaceRoot,
            Path worldDirectory
    ) {
        public Target {
            Objects.requireNonNull(worldKey, "worldKey");
            Objects.requireNonNull(slotKind, "slotKind");
            levelRoot = Objects.requireNonNull(levelRoot, "levelRoot").toAbsolutePath().normalize();
            namespaceRoot = Objects.requireNonNull(namespaceRoot, "namespaceRoot").toAbsolutePath().normalize();
            worldDirectory = Objects.requireNonNull(worldDirectory, "worldDirectory").toAbsolutePath().normalize();
            SlotKind expectedSlotKind = classify(worldKey);
            Path expectedNamespaceRoot = levelRoot.resolve("dimensions").resolve(worldKey.namespace());
            Path expectedWorldDirectory = expectedNamespaceRoot.resolve(worldKey.key());
            if (slotKind != expectedSlotKind
                    || !namespaceRoot.equals(expectedNamespaceRoot)
                    || !worldDirectory.equals(expectedWorldDirectory)) {
                throw new IllegalArgumentException("World slot paths do not form an exact dimension hierarchy.");
            }
        }
    }

    public enum SlotKind {
        IRIS_MANAGED,
        VANILLA_OVERWORLD,
        VANILLA_NETHER,
        VANILLA_END
    }

    public enum RejectionReason {
        INVALID_IRIS_KEY,
        FOREIGN_NAMESPACE,
        UNSUPPORTED_MINECRAFT_SLOT,
        MISSING_LEVEL_ROOT,
        SYMBOLIC_LINK,
        UNSAFE_ENTRY,
        PATH_TRAVERSAL,
        PATH_MISMATCH
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
