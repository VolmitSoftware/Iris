package art.arcane.iris.world;

import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.TemporaryFolder;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertThrows;

public class ExactWorldSlotPathPolicyTest {
    @Rule
    public TemporaryFolder temporaryFolder = new TemporaryFolder();

    @Test
    public void resolvesIrisAndExactVanillaSlots() throws Exception {
        Path levelRoot = temporaryFolder.newFolder("world").toPath();
        Path canonicalRoot = levelRoot.toRealPath();
        List<SlotExpectation> expectations = List.of(
                new SlotExpectation(
                        new WorldSlotKey("iris", "underworld"),
                        ExactWorldSlotPathPolicy.SlotKind.IRIS_MANAGED,
                        "dimensions/iris/underworld"
                ),
                new SlotExpectation(
                        WorldSlotKey.minecraft("overworld"),
                        ExactWorldSlotPathPolicy.SlotKind.VANILLA_OVERWORLD,
                        "dimensions/minecraft/overworld"
                ),
                new SlotExpectation(
                        WorldSlotKey.minecraft("the_nether"),
                        ExactWorldSlotPathPolicy.SlotKind.VANILLA_NETHER,
                        "dimensions/minecraft/the_nether"
                ),
                new SlotExpectation(
                        WorldSlotKey.minecraft("the_end"),
                        ExactWorldSlotPathPolicy.SlotKind.VANILLA_END,
                        "dimensions/minecraft/the_end"
                )
        );

        for (SlotExpectation expectation : expectations) {
            ExactWorldSlotPathPolicy.Target target = ExactWorldSlotPathPolicy.resolve(
                    levelRoot,
                    expectation.worldKey()
            );

            assertEquals(expectation.worldKey(), target.worldKey());
            assertEquals(expectation.slotKind(), target.slotKind());
            assertEquals(canonicalRoot, target.levelRoot());
            assertEquals(canonicalRoot.resolve(expectation.relativePath()), target.worldDirectory());
        }
        assertFalse(Files.exists(canonicalRoot.resolve("dimensions")));
    }

    @Test
    public void acceptsAnExistingExactDirectorySlot() throws Exception {
        Path levelRoot = temporaryFolder.newFolder("existing-world").toPath();
        Path worldDirectory = Files.createDirectories(levelRoot.resolve("dimensions/minecraft/the_nether"));

        ExactWorldSlotPathPolicy.Target target = ExactWorldSlotPathPolicy.resolve(
                levelRoot,
                WorldSlotKey.minecraft("the_nether")
        );

        assertEquals(worldDirectory.toRealPath(), target.worldDirectory());
    }

    @Test
    public void rejectsForeignNestedAndUnsupportedKeys() throws Exception {
        Path levelRoot = temporaryFolder.newFolder("key-policy").toPath();

        ExactWorldSlotPathPolicy.Rejection foreign = assertThrows(
                ExactWorldSlotPathPolicy.Rejection.class,
                () -> ExactWorldSlotPathPolicy.resolve(levelRoot, new WorldSlotKey("foreign", "world"))
        );
        ExactWorldSlotPathPolicy.Rejection nestedIris = assertThrows(
                ExactWorldSlotPathPolicy.Rejection.class,
                () -> ExactWorldSlotPathPolicy.resolve(levelRoot, new WorldSlotKey("iris", "nested/world"))
        );
        ExactWorldSlotPathPolicy.Rejection unsupportedMinecraft = assertThrows(
                ExactWorldSlotPathPolicy.Rejection.class,
                () -> ExactWorldSlotPathPolicy.resolve(levelRoot, WorldSlotKey.minecraft("custom"))
        );

        assertEquals(ExactWorldSlotPathPolicy.RejectionReason.FOREIGN_NAMESPACE, foreign.reason());
        assertEquals(ExactWorldSlotPathPolicy.RejectionReason.INVALID_IRIS_KEY, nestedIris.reason());
        assertEquals(
                ExactWorldSlotPathPolicy.RejectionReason.UNSUPPORTED_MINECRAFT_SLOT,
                unsupportedMinecraft.reason()
        );
    }

    @Test
    public void validatesOnlyTheExactExpectedCandidate() throws Exception {
        Path levelRoot = temporaryFolder.newFolder("candidate-policy").toPath();
        WorldSlotKey worldKey = WorldSlotKey.minecraft("the_nether");
        Path expected = levelRoot.toRealPath().resolve("dimensions/minecraft/the_nether");

        ExactWorldSlotPathPolicy.Target target = ExactWorldSlotPathPolicy.validate(
                levelRoot,
                worldKey,
                expected
        );
        ExactWorldSlotPathPolicy.Rejection mismatch = assertThrows(
                ExactWorldSlotPathPolicy.Rejection.class,
                () -> ExactWorldSlotPathPolicy.validate(
                        levelRoot,
                        worldKey,
                        levelRoot.resolve("dimensions/iris/the_nether")
                )
        );
        ExactWorldSlotPathPolicy.Rejection traversal = assertThrows(
                ExactWorldSlotPathPolicy.Rejection.class,
                () -> ExactWorldSlotPathPolicy.validate(
                        levelRoot,
                        worldKey,
                        levelRoot.resolve("dimensions/minecraft/unused/../the_nether")
                )
        );

        assertEquals(expected, target.worldDirectory());
        assertEquals(ExactWorldSlotPathPolicy.RejectionReason.PATH_MISMATCH, mismatch.reason());
        assertEquals(ExactWorldSlotPathPolicy.RejectionReason.PATH_TRAVERSAL, traversal.reason());
    }

    @Test
    public void rejectsTraversalInLevelRoot() throws Exception {
        Path parent = temporaryFolder.newFolder("level-traversal").toPath();
        Path levelRoot = Files.createDirectory(parent.resolve("world"));

        ExactWorldSlotPathPolicy.Rejection failure = assertThrows(
                ExactWorldSlotPathPolicy.Rejection.class,
                () -> ExactWorldSlotPathPolicy.resolve(
                        levelRoot.resolve("child/.."),
                        new WorldSlotKey("iris", "underworld")
                )
        );

        assertEquals(ExactWorldSlotPathPolicy.RejectionReason.PATH_TRAVERSAL, failure.reason());
    }

    @Test
    public void rejectsSymbolicLinksAtEveryManagedPathComponent() throws Exception {
        Path linkedLevelTarget = temporaryFolder.newFolder("linked-level-target").toPath();
        Path levelLink = temporaryFolder.getRoot().toPath().resolve("linked-level");
        Files.createSymbolicLink(levelLink, linkedLevelTarget);
        assertSymbolicLinkRejected(levelLink, new WorldSlotKey("iris", "underworld"));

        Path dimensionsLevel = temporaryFolder.newFolder("linked-dimensions").toPath();
        Path externalDimensions = temporaryFolder.newFolder("external-dimensions").toPath();
        Files.createSymbolicLink(dimensionsLevel.resolve("dimensions"), externalDimensions);
        assertSymbolicLinkRejected(dimensionsLevel, new WorldSlotKey("iris", "underworld"));

        Path namespaceLevel = temporaryFolder.newFolder("linked-namespace").toPath();
        Path dimensions = Files.createDirectories(namespaceLevel.resolve("dimensions"));
        Path externalNamespace = temporaryFolder.newFolder("external-namespace").toPath();
        Files.createSymbolicLink(dimensions.resolve("minecraft"), externalNamespace);
        assertSymbolicLinkRejected(namespaceLevel, WorldSlotKey.minecraft("the_nether"));

        Path targetLevel = temporaryFolder.newFolder("linked-target").toPath();
        Path namespace = Files.createDirectories(targetLevel.resolve("dimensions/minecraft"));
        Path externalTarget = temporaryFolder.newFolder("external-target").toPath();
        Files.createSymbolicLink(namespace.resolve("the_nether"), externalTarget);
        assertSymbolicLinkRejected(targetLevel, WorldSlotKey.minecraft("the_nether"));
    }

    @Test
    public void rejectsNonDirectoryStorageEntries() throws Exception {
        Path dimensionsLevel = temporaryFolder.newFolder("file-dimensions").toPath();
        Files.writeString(dimensionsLevel.resolve("dimensions"), "not a directory");
        assertUnsafeEntryRejected(dimensionsLevel, new WorldSlotKey("iris", "underworld"));

        Path namespaceLevel = temporaryFolder.newFolder("file-namespace").toPath();
        Path dimensions = Files.createDirectories(namespaceLevel.resolve("dimensions"));
        Files.writeString(dimensions.resolve("iris"), "not a directory");
        assertUnsafeEntryRejected(namespaceLevel, new WorldSlotKey("iris", "underworld"));

        Path targetLevel = temporaryFolder.newFolder("file-target").toPath();
        Path namespace = Files.createDirectories(targetLevel.resolve("dimensions/iris"));
        Files.writeString(namespace.resolve("underworld"), "not a directory");
        assertUnsafeEntryRejected(targetLevel, new WorldSlotKey("iris", "underworld"));
    }

    @Test
    public void rejectsMissingAndFilesystemLevelRoots() throws Exception {
        Path missing = temporaryFolder.getRoot().toPath().resolve("missing");

        ExactWorldSlotPathPolicy.Rejection missingFailure = assertThrows(
                ExactWorldSlotPathPolicy.Rejection.class,
                () -> ExactWorldSlotPathPolicy.resolve(missing, new WorldSlotKey("iris", "underworld"))
        );
        ExactWorldSlotPathPolicy.Rejection filesystemFailure = assertThrows(
                ExactWorldSlotPathPolicy.Rejection.class,
                () -> ExactWorldSlotPathPolicy.resolve(
                        missing.toAbsolutePath().getRoot(),
                        new WorldSlotKey("iris", "underworld")
                )
        );

        assertEquals(ExactWorldSlotPathPolicy.RejectionReason.MISSING_LEVEL_ROOT, missingFailure.reason());
        assertEquals(ExactWorldSlotPathPolicy.RejectionReason.UNSAFE_ENTRY, filesystemFailure.reason());
    }

    private void assertSymbolicLinkRejected(Path levelRoot, WorldSlotKey worldKey) {
        ExactWorldSlotPathPolicy.Rejection failure = assertThrows(
                ExactWorldSlotPathPolicy.Rejection.class,
                () -> ExactWorldSlotPathPolicy.resolve(levelRoot, worldKey)
        );

        assertEquals(ExactWorldSlotPathPolicy.RejectionReason.SYMBOLIC_LINK, failure.reason());
    }

    private void assertUnsafeEntryRejected(Path levelRoot, WorldSlotKey worldKey) {
        ExactWorldSlotPathPolicy.Rejection failure = assertThrows(
                ExactWorldSlotPathPolicy.Rejection.class,
                () -> ExactWorldSlotPathPolicy.resolve(levelRoot, worldKey)
        );

        assertEquals(ExactWorldSlotPathPolicy.RejectionReason.UNSAFE_ENTRY, failure.reason());
    }

    private record SlotExpectation(
            WorldSlotKey worldKey,
            ExactWorldSlotPathPolicy.SlotKind slotKind,
            String relativePath
    ) {
    }
}
