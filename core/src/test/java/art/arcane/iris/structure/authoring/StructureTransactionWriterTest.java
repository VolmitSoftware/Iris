/*
 * Iris is a World Generator for Minecraft Bukkit Servers
 * Copyright (c) 2022 Arcane Arts (Volmit Software)
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License
 * along with this program.  If not, see <https://www.gnu.org/licenses/>.
 */

package art.arcane.iris.structure.authoring;

import art.arcane.iris.testsupport.DurabilityMode;
import org.junit.ClassRule;
import org.junit.Rule;
import org.junit.Test;
import org.junit.Assume;
import org.junit.rules.TemporaryFolder;

import java.io.File;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import java.util.stream.Stream;

import static org.junit.Assert.assertArrayEquals;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertThrows;
import static org.junit.Assert.assertTrue;

public class StructureTransactionWriterTest {
    @ClassRule
    public static final DurabilityMode DURABILITY = DurabilityMode.relaxed();

    private static final StructureKey TARGET_KEY = StructureKey.parse("iris_test:temple");
    private static final StructureKey SOURCE_KEY = StructureKey.parse("minecraft:trial_chambers");

    @Rule
    public TemporaryFolder temporaryFolder = new TemporaryFolder();

    @Test
    public void dryRunPerformsPreflightWithoutWriting() throws IOException {
        Path root = temporaryFolder.newFolder("dry-run").toPath();
        StructureTransactionWriter writer = new StructureTransactionWriter(root);

        StructureWriteResult result = writer.preview(bundle("object-v1", "structure-v1"), StructureWriteMode.ADD_ONLY);

        assertEquals(StructureWriteResult.Status.DRY_RUN, result.status());
        assertEquals(StructureWriteResult.Action.ADD, result.action());
        assertTrue(result.successful());
        assertFalse(result.committed());
        assertFalse(Files.exists(root.resolve("objects/temple.iob")));
        assertFalse(Files.exists(writer.ownershipManifestPath(TARGET_KEY)));
        assertFalse(Files.exists(root.resolve(".iris")));
    }

    @Test
    public void addOnlyReportsExistingResourcesWithoutChangingThem() throws IOException {
        Path root = temporaryFolder.newFolder("add-only").toPath();
        Path existing = root.resolve("objects/temple.iob");
        Files.createDirectories(existing.getParent());
        byte[] userContent = "user-content".getBytes(StandardCharsets.UTF_8);
        Files.write(existing, userContent);
        StructureTransactionWriter writer = new StructureTransactionWriter(root);

        StructureWriteResult result = writer.write(bundle("object-v1", "structure-v1"), StructureWriteMode.ADD_ONLY);

        assertEquals(StructureWriteResult.Status.ADD_ONLY_CONFLICT, result.status());
        assertEquals(1, result.conflicts().size());
        assertEquals(StructureWriteResult.ConflictReason.RESOURCE_EXISTS, result.conflicts().get(0).reason());
        assertArrayEquals(userContent, Files.readAllBytes(existing));
        assertFalse(Files.exists(writer.ownershipManifestPath(TARGET_KEY)));
    }

    @Test
    public void ownershipManifestProtectsHandEditedResources() throws IOException {
        Path root = temporaryFolder.newFolder("ownership").toPath();
        StructureTransactionWriter writer = new StructureTransactionWriter(root);
        StructureWriteResult initial = writer.write(bundle("object-v1", "structure-v1"), StructureWriteMode.ADD_ONLY);
        Path object = root.resolve("objects/temple.iob");
        byte[] handEdit = "hand-edit".getBytes(StandardCharsets.UTF_8);
        assertEquals(failureMessage(initial), StructureWriteResult.Status.ADDED, initial.status());
        Files.write(object, handEdit);

        StructureWriteResult result = writer.write(bundle("object-v2", "structure-v2"), StructureWriteMode.OVERWRITE);

        assertEquals(failureMessage(initial), StructureWriteResult.Status.ADDED, initial.status());
        assertEquals(StructureWriteResult.Status.OWNERSHIP_CONFLICT, result.status());
        assertTrue(result.conflicts().stream().anyMatch(conflict ->
                conflict.reason() == StructureWriteResult.ConflictReason.MODIFIED_RESOURCE
                        && conflict.relativePath().equals("objects/temple.iob")));
        assertArrayEquals(handEdit, Files.readAllBytes(object));
        StructureOwnershipManifest manifest = StructureOwnershipManifest.fromJson(
                Files.readAllBytes(writer.ownershipManifestPath(TARGET_KEY))
        );
        assertEquals(StructureHash.sha256("object-v1".getBytes(StandardCharsets.UTF_8)),
                manifest.resourceHashes().get("objects/temple.iob"));
    }

    @Test
    public void overwriteReplacesOnlyOwnedUnmodifiedResources() throws IOException {
        Path root = temporaryFolder.newFolder("overwrite").toPath();
        StructureTransactionWriter writer = new StructureTransactionWriter(root);
        StructureWriteResult initial = writer.write(bundle("object-v1", "structure-v1"), StructureWriteMode.ADD_ONLY);

        StructureWriteResult result = writer.write(bundle("object-v2", "structure-v2"), StructureWriteMode.OVERWRITE);

        assertEquals(failureMessage(initial), StructureWriteResult.Status.ADDED, initial.status());
        assertEquals(StructureWriteResult.Status.OVERWRITTEN, result.status());
        assertEquals(StructureWriteResult.Action.OVERWRITE, result.action());
        assertArrayEquals("object-v2".getBytes(StandardCharsets.UTF_8),
                Files.readAllBytes(root.resolve("objects/temple.iob")));
        assertArrayEquals("structure-v2".getBytes(StandardCharsets.UTF_8),
                Files.readAllBytes(root.resolve("structures/temple.json")));
    }

    @Test
    public void expectedManifestHashRejectsAStaleWholeGraphEdit() throws IOException {
        Path root = temporaryFolder.newFolder("manifest-cas").toPath();
        StructureTransactionWriter writer = new StructureTransactionWriter(root);
        StructureWriteResult initial = writer.write(
                bundle("object-v1", "structure-v1"),
                StructureWriteMode.ADD_ONLY);
        assertEquals(failureMessage(initial), StructureWriteResult.Status.ADDED, initial.status());
        Path manifestPath = writer.ownershipManifestPath(TARGET_KEY);
        String observedHash = StructureHash.sha256(Files.readAllBytes(manifestPath));

        StructureWriteResult competing = writer.write(
                bundle("object-v2", "structure-v2"),
                StructureWriteMode.OVERWRITE);
        assertEquals(failureMessage(competing), StructureWriteResult.Status.OVERWRITTEN, competing.status());

        StructureWriteResult stale = writer.write(
                bundle("object-stale", "structure-stale"),
                StructureWriteOptions.overwriteExpected(observedHash));

        assertEquals(StructureWriteResult.Status.OWNERSHIP_CONFLICT, stale.status());
        assertEquals(StructureWriteResult.ConflictReason.STALE_MANIFEST,
                stale.conflicts().getFirst().reason());
        assertArrayEquals("object-v2".getBytes(StandardCharsets.UTF_8),
                Files.readAllBytes(root.resolve("objects/temple.iob")));
        assertArrayEquals("structure-v2".getBytes(StandardCharsets.UTF_8),
                Files.readAllBytes(root.resolve("structures/temple.json")));
    }

    @Test
    public void failedMultiFileInstallRollsBackEveryResourceAndManifest() throws IOException {
        Path root = temporaryFolder.newFolder("rollback").toPath();
        StructureTransactionWriter initialWriter = new StructureTransactionWriter(root);
        StructureWriteResult initial = initialWriter.write(
                bundle("object-v1", "structure-v1"),
                StructureWriteMode.ADD_ONLY
        );
        Path object = root.resolve("objects/temple.iob");
        Path structure = root.resolve("structures/temple.json");
        Path manifest = initialWriter.ownershipManifestPath(TARGET_KEY);
        assertEquals(failureMessage(initial), StructureWriteResult.Status.ADDED, initial.status());
        byte[] originalObject = Files.readAllBytes(object);
        byte[] originalStructure = Files.readAllBytes(structure);
        byte[] originalManifest = Files.readAllBytes(manifest);
        FailOnceMoveOperations operations = new FailOnceMoveOperations("staged/structures/temple.json");
        StructureTransactionWriter failingWriter = new StructureTransactionWriter(root, operations);

        StructureWriteResult result = failingWriter.write(
                bundle("object-v2", "structure-v2"),
                StructureWriteMode.OVERWRITE
        );

        assertEquals(StructureWriteResult.Status.ADDED, initial.status());
        assertEquals(StructureWriteResult.Status.ROLLED_BACK, result.status());
        assertTrue(result.failure().isPresent());
        assertArrayEquals(originalObject, Files.readAllBytes(object));
        assertArrayEquals(originalStructure, Files.readAllBytes(structure));
        assertArrayEquals(originalManifest, Files.readAllBytes(manifest));
        Path staging = root.resolve(".iris/structure-staging");
        if (Files.exists(staging)) {
            try (Stream<Path> paths = Files.list(staging)) {
                assertEquals(0, paths.count());
            }
        }
    }

    @Test
    public void preparedTransactionRecoveryRestoresBackupsAndRemovesNewTargets() throws IOException {
        Path root = temporaryFolder.newFolder("prepared-recovery").toPath();
        Path originalTarget = root.resolve("objects/temple.iob");
        Path newTarget = root.resolve("structures/temple.json");
        Files.createDirectories(originalTarget.getParent());
        Files.createDirectories(newTarget.getParent());
        Files.write(originalTarget, "partially-installed".getBytes(StandardCharsets.UTF_8));
        Files.write(newTarget, "new-target".getBytes(StandardCharsets.UTF_8));
        byte[] partialReplacement = "partially-installed".getBytes(StandardCharsets.UTF_8);
        byte[] newReplacement = "new-target".getBytes(StandardCharsets.UTF_8);
        UUID transactionId = UUID.randomUUID();
        Path transactionRoot = transactionRoot(root, transactionId);
        Path backup = transactionRoot.resolve("backup/objects/temple.iob");
        Files.createDirectories(backup.getParent());
        byte[] original = "original".getBytes(StandardCharsets.UTF_8);
        Files.write(backup, original);
        writeJournal(transactionRoot, StructureTransactionJournal.prepared(transactionId, List.of(
                new StructureTransactionJournal.Target(
                        "objects/temple.iob",
                        true,
                        StructureHash.sha256(original),
                        StructureHash.sha256(partialReplacement)
                ),
                new StructureTransactionJournal.Target(
                        "structures/temple.json",
                        false,
                        "",
                        StructureHash.sha256(newReplacement)
                )
        )));
        StructureTransactionWriter writer = new StructureTransactionWriter(root);

        StructureRecoveryResult result = writer.recoverIncompleteTransactions();

        assertTrue(result.successful());
        assertEquals(1, result.restoredPreparedTransactions());
        assertEquals(1, result.recoveredTransactions());
        assertArrayEquals("original".getBytes(StandardCharsets.UTF_8), Files.readAllBytes(originalTarget));
        assertFalse(Files.exists(newTarget));
        assertFalse(Files.exists(transactionRoot));
    }

    @Test
    public void committedTransactionRecoveryKeepsInstalledTargetsAndCleansRecoveryData() throws IOException {
        Path root = temporaryFolder.newFolder("committed-recovery").toPath();
        Path target = root.resolve("objects/temple.iob");
        Files.createDirectories(target.getParent());
        Files.write(target, "committed".getBytes(StandardCharsets.UTF_8));
        UUID transactionId = UUID.randomUUID();
        Path transactionRoot = transactionRoot(root, transactionId);
        Path backup = transactionRoot.resolve("backup/objects/temple.iob");
        Files.createDirectories(backup.getParent());
        byte[] original = "original".getBytes(StandardCharsets.UTF_8);
        Files.write(backup, original);
        writeJournal(transactionRoot, StructureTransactionJournal.prepared(transactionId, List.of(
                new StructureTransactionJournal.Target(
                        "objects/temple.iob",
                        true,
                        StructureHash.sha256(original),
                        StructureHash.sha256("committed".getBytes(StandardCharsets.UTF_8))
                )
        )).committed());
        StructureTransactionWriter writer = new StructureTransactionWriter(root);

        StructureRecoveryResult result = writer.recoverIncompleteTransactions();

        assertTrue(result.successful());
        assertEquals(1, result.cleanedCommittedTransactions());
        assertEquals(1, result.recoveredTransactions());
        assertArrayEquals("committed".getBytes(StandardCharsets.UTF_8), Files.readAllBytes(target));
        assertFalse(Files.exists(transactionRoot));
    }

    @Test
    public void nextOnlyPreparedJournalIsRecoveredAfterANonAtomicMoveInterruption() throws IOException {
        Path root = temporaryFolder.newFolder("next-journal-recovery").toPath();
        Path target = root.resolve("objects/temple.iob");
        byte[] original = "original".getBytes(StandardCharsets.UTF_8);
        byte[] replacement = "replacement".getBytes(StandardCharsets.UTF_8);
        Files.createDirectories(target.getParent());
        Files.write(target, replacement);
        UUID transactionId = UUID.randomUUID();
        Path transactionRoot = transactionRoot(root, transactionId);
        Path backup = transactionRoot.resolve("backup/objects/temple.iob");
        Files.createDirectories(backup.getParent());
        Files.write(backup, original);
        StructureTransactionJournal journal = StructureTransactionJournal.prepared(transactionId, List.of(
                new StructureTransactionJournal.Target(
                        "objects/temple.iob",
                        true,
                        StructureHash.sha256(original),
                        StructureHash.sha256(replacement)
                )
        ));
        Files.write(transactionRoot.resolve(StructureTransactionJournal.NEXT_FILE_NAME), journal.toJson());
        StructureTransactionWriter writer = new StructureTransactionWriter(root);

        StructureRecoveryResult result = writer.recoverIncompleteTransactions();

        assertTrue(result.successful());
        assertArrayEquals(original, Files.readAllBytes(target));
        assertFalse(Files.exists(transactionRoot));
    }

    @Test
    public void committedRecoveryRetainsBackupsWhenInstalledTargetsDoNotMatch() throws IOException {
        Path root = temporaryFolder.newFolder("committed-mismatch").toPath();
        Path target = root.resolve("objects/temple.iob");
        byte[] original = "original".getBytes(StandardCharsets.UTF_8);
        byte[] unexpected = "unexpected".getBytes(StandardCharsets.UTF_8);
        Files.createDirectories(target.getParent());
        Files.write(target, unexpected);
        UUID transactionId = UUID.randomUUID();
        Path transactionRoot = transactionRoot(root, transactionId);
        Path backup = transactionRoot.resolve("backup/objects/temple.iob");
        Files.createDirectories(backup.getParent());
        Files.write(backup, original);
        StructureTransactionJournal journal = StructureTransactionJournal.prepared(transactionId, List.of(
                new StructureTransactionJournal.Target(
                        "objects/temple.iob",
                        true,
                        StructureHash.sha256(original),
                        StructureHash.sha256("committed".getBytes(StandardCharsets.UTF_8))
                )
        )).committed();
        Files.write(transactionRoot.resolve(StructureTransactionJournal.FILE_NAME), journal.toJson());
        StructureTransactionWriter writer = new StructureTransactionWriter(root);

        StructureRecoveryResult result = writer.recoverIncompleteTransactions();

        assertFalse(result.successful());
        assertArrayEquals(unexpected, Files.readAllBytes(target));
        assertTrue(Files.exists(backup));
        assertTrue(Files.exists(transactionRoot));
    }

    @Test
    public void invalidRecoveryJournalBlocksWritesAndRetainsRecoveryData() throws IOException {
        Path root = temporaryFolder.newFolder("invalid-recovery").toPath();
        UUID transactionId = UUID.randomUUID();
        Path transactionRoot = transactionRoot(root, transactionId);
        Path journalPath = transactionRoot.resolve(StructureTransactionJournal.FILE_NAME);
        Files.createDirectories(transactionRoot);
        Files.write(journalPath, "not-json".getBytes(StandardCharsets.UTF_8));
        StructureTransactionWriter writer = new StructureTransactionWriter(root);

        StructureWriteResult result = writer.write(bundle("object-v1", "structure-v1"),
                StructureWriteMode.ADD_ONLY);

        assertEquals(StructureWriteResult.Status.FAILED, result.status());
        assertTrue(result.failure().isPresent());
        assertTrue(Files.exists(journalPath));
        assertFalse(Files.exists(root.resolve("objects/temple.iob")));
    }

    @Test
    public void recoveryBoundsTransactionDirectoryCount() throws IOException {
        Path root = temporaryFolder.newFolder("bounded-transaction-count").toPath();
        Path staging = root.resolve(".iris/structure-staging");
        Files.createDirectories(staging);
        for (int i = 0; i < 1_025; i++) {
            Files.createDirectory(staging.resolve(UUID.randomUUID().toString()));
        }

        StructureRecoveryResult recovery = new StructureTransactionWriter(root)
                .recoverIncompleteTransactions();

        assertFalse(recovery.successful());
        assertTrue(recovery.failures().getFirst().cause().getMessage().contains("transaction count"));
        try (Stream<Path> transactions = Files.list(staging)) {
            assertEquals(1_025L, transactions.count());
        }
    }

    @Test
    public void writeRecoversPreparedTransactionBeforePreflight() throws IOException {
        Path root = temporaryFolder.newFolder("write-recovery").toPath();
        UUID transactionId = UUID.randomUUID();
        Path transactionRoot = transactionRoot(root, transactionId);
        byte[] interruptedReplacement = "interrupted".getBytes(StandardCharsets.UTF_8);
        writeJournal(transactionRoot, StructureTransactionJournal.prepared(transactionId, List.of(
                new StructureTransactionJournal.Target(
                        "objects/temple.iob",
                        false,
                        "",
                        StructureHash.sha256(interruptedReplacement)
                )
        )));
        StructureTransactionWriter writer = new StructureTransactionWriter(root);

        StructureWriteResult result = writer.write(bundle("object-v1", "structure-v1"),
                StructureWriteMode.ADD_ONLY);

        assertEquals(failureMessage(result), StructureWriteResult.Status.ADDED, result.status());
        assertArrayEquals("object-v1".getBytes(StandardCharsets.UTF_8),
                Files.readAllBytes(root.resolve("objects/temple.iob")));
        assertFalse(Files.exists(transactionRoot));
    }

    @Test
    public void preparedRecoveryRetainsUnexpectedReplacement() throws IOException {
        Path root = temporaryFolder.newFolder("replacement-conflict").toPath();
        Path target = root.resolve("objects/temple.iob");
        Files.createDirectories(target.getParent());
        byte[] userEdit = "user-edit".getBytes(StandardCharsets.UTF_8);
        Files.write(target, userEdit);
        UUID transactionId = UUID.randomUUID();
        Path transactionRoot = transactionRoot(root, transactionId);
        writeJournal(transactionRoot, StructureTransactionJournal.prepared(transactionId, List.of(
                new StructureTransactionJournal.Target(
                        "objects/temple.iob",
                        false,
                        "",
                        StructureHash.sha256("interrupted".getBytes(StandardCharsets.UTF_8))
                )
        )));
        StructureTransactionWriter writer = new StructureTransactionWriter(root);

        StructureRecoveryResult result = writer.recoverIncompleteTransactions();

        assertFalse(result.successful());
        assertEquals(1, result.failures().size());
        assertArrayEquals(userEdit, Files.readAllBytes(target));
        assertTrue(Files.exists(transactionRoot));
    }

    @Test
    public void concurrentTargetCreationIsNotDeletedByRollback() throws IOException {
        Path root = temporaryFolder.newFolder("concurrent-target").toPath();
        byte[] userContent = "concurrent-user-file".getBytes(StandardCharsets.UTF_8);
        FailOnceMoveOperations operations = new FailOnceMoveOperations(
                "staged/objects/temple.iob",
                userContent
        );
        StructureTransactionWriter writer = new StructureTransactionWriter(root, operations);

        StructureWriteResult result = writer.write(bundle("object-v1", "structure-v1"),
                StructureWriteMode.ADD_ONLY);

        assertEquals(StructureWriteResult.Status.ROLLED_BACK, result.status());
        assertArrayEquals(userContent, Files.readAllBytes(root.resolve("objects/temple.iob")));
    }

    @Test
    public void preparedRecoveryAcceptsAlreadyRestoredOriginalWithRemainingBackup() throws IOException {
        Path root = temporaryFolder.newFolder("repeated-recovery").toPath();
        byte[] original = "original".getBytes(StandardCharsets.UTF_8);
        Path target = root.resolve("objects/temple.iob");
        Files.createDirectories(target.getParent());
        Files.write(target, original);
        UUID transactionId = UUID.randomUUID();
        Path transactionRoot = transactionRoot(root, transactionId);
        Path backup = transactionRoot.resolve("backup/objects/temple.iob");
        Files.createDirectories(backup.getParent());
        Files.write(backup, original);
        writeJournal(transactionRoot, StructureTransactionJournal.prepared(transactionId, List.of(
                new StructureTransactionJournal.Target(
                        "objects/temple.iob",
                        true,
                        StructureHash.sha256(original),
                        StructureHash.sha256("replacement".getBytes(StandardCharsets.UTF_8))
                )
        )));
        StructureTransactionWriter writer = new StructureTransactionWriter(root);

        StructureRecoveryResult result = writer.recoverIncompleteTransactions();

        assertTrue(result.successful());
        assertEquals(1, result.restoredPreparedTransactions());
        assertArrayEquals(original, Files.readAllBytes(target));
        assertFalse(Files.exists(transactionRoot));
    }

    @Test
    public void ownedRemovalDeletesOnlyVerifiedBundleResources() throws IOException {
        Path root = temporaryFolder.newFolder("owned-removal").toPath();
        StructureTransactionWriter writer = new StructureTransactionWriter(root);
        StructureWriteResult initial = writer.write(bundle("object-v1", "structure-v1"), StructureWriteMode.ADD_ONLY);
        assertEquals(failureMessage(initial), StructureWriteResult.Status.ADDED, initial.status());

        boolean removed = writer.removeOwned(TARGET_KEY, StructureSource.Kind.VANILLA, SOURCE_KEY);

        assertTrue(removed);
        assertFalse(Files.exists(root.resolve("objects/temple.iob")));
        assertFalse(Files.exists(root.resolve("structures/temple.json")));
        assertFalse(Files.exists(writer.ownershipManifestPath(TARGET_KEY)));
        assertFalse(writer.removeOwned(TARGET_KEY, StructureSource.Kind.VANILLA, SOURCE_KEY));
    }

    @Test
    public void ownedRemovalPreservesModifiedResourcesAndManifest() throws IOException {
        Path root = temporaryFolder.newFolder("owned-removal-modified").toPath();
        StructureTransactionWriter writer = new StructureTransactionWriter(root);
        StructureWriteResult initial = writer.write(bundle("object-v1", "structure-v1"), StructureWriteMode.ADD_ONLY);
        assertEquals(failureMessage(initial), StructureWriteResult.Status.ADDED, initial.status());
        Path object = root.resolve("objects/temple.iob");
        Files.writeString(object, "user-edit", StandardCharsets.UTF_8);

        try {
            writer.removeOwned(TARGET_KEY, StructureSource.Kind.VANILLA, SOURCE_KEY);
            throw new AssertionError("Expected modified owned resource to block removal");
        } catch (IOException expected) {
            assertTrue(expected.getMessage().contains("modified"));
        }

        assertEquals("user-edit", Files.readString(object, StandardCharsets.UTF_8));
        assertTrue(Files.exists(root.resolve("structures/temple.json")));
        assertTrue(Files.exists(writer.ownershipManifestPath(TARGET_KEY)));
    }

    @Test
    public void preparedMultiBundleRemovalRestoresEarlierMovesWhenALaterMoveFails() throws IOException {
        Path root = temporaryFolder.newFolder("owned-removal-batch-rollback").toPath();
        StructureKey alphaKey = StructureKey.parse("iris_test:alpha");
        StructureKey zetaKey = StructureKey.parse("iris_test:zeta");
        StructureKey alphaSource = StructureKey.parse("example:alpha");
        StructureKey zetaSource = StructureKey.parse("example:zeta");
        StructureTransactionWriter writer = new StructureTransactionWriter(root);
        StructureWriteResult alphaWrite = writer.write(
                bundle(alphaKey, alphaSource, "alpha-object", "alpha-structure"),
                StructureWriteMode.ADD_ONLY
        );
        StructureWriteResult zetaWrite = writer.write(
                bundle(zetaKey, zetaSource, "zeta-object", "zeta-structure"),
                StructureWriteMode.ADD_ONLY
        );
        assertEquals(failureMessage(alphaWrite), StructureWriteResult.Status.ADDED, alphaWrite.status());
        assertEquals(failureMessage(zetaWrite), StructureWriteResult.Status.ADDED, zetaWrite.status());
        StructureTransactionWriter failingWriter = new StructureTransactionWriter(
                root,
                new FailOnceMoveOperations("objects/zeta.iob")
        );

        try {
            failingWriter.prepareOwnedRemovals(List.of(
                    new StructureTransactionWriter.OwnedRemoval(
                            alphaKey,
                            StructureSource.Kind.DATAPACK,
                            alphaSource,
                            Optional.empty(),
                            Optional.empty()
                    ),
                    new StructureTransactionWriter.OwnedRemoval(
                            zetaKey,
                            StructureSource.Kind.DATAPACK,
                            zetaSource,
                            Optional.empty(),
                            Optional.empty()
                    )
            ));
            throw new AssertionError("Expected the later removal move to fail");
        } catch (IOException expected) {
            assertTrue(expected.getMessage().contains("Injected install failure"));
        }

        assertEquals("alpha-object", Files.readString(root.resolve("objects/alpha.iob"), StandardCharsets.UTF_8));
        assertEquals("zeta-object", Files.readString(root.resolve("objects/zeta.iob"), StandardCharsets.UTF_8));
        assertTrue(Files.exists(writer.ownershipManifestPath(alphaKey)));
        assertTrue(Files.exists(writer.ownershipManifestPath(zetaKey)));
    }

    @Test
    public void preparedRemovalRejectsAStaleExpectedManifestHashBeforeMovingResources() throws IOException {
        Path root = temporaryFolder.newFolder("owned-removal-stale-manifest").toPath();
        StructureTransactionWriter writer = new StructureTransactionWriter(root);
        StructureWriteResult initial = writer.write(
                bundle("object-v1", "structure-v1"),
                StructureWriteMode.ADD_ONLY);
        assertEquals(failureMessage(initial), StructureWriteResult.Status.ADDED, initial.status());
        Path manifestPath = writer.ownershipManifestPath(TARGET_KEY);
        String staleManifestHash = StructureHash.sha256(Files.readAllBytes(manifestPath));
        StructureWriteResult updated = writer.write(
                bundle("object-v2", "structure-v2"),
                StructureWriteOptions.overwriteExpected(staleManifestHash));
        assertEquals(failureMessage(updated), StructureWriteResult.Status.OVERWRITTEN, updated.status());

        try {
            writer.prepareOwnedRemovals(List.of(new StructureTransactionWriter.OwnedRemoval(
                    TARGET_KEY,
                    StructureSource.Kind.VANILLA,
                    SOURCE_KEY,
                    Optional.empty(),
                    Optional.of(staleManifestHash))));
            throw new AssertionError("Expected stale removal manifest hash to be rejected");
        } catch (IOException expected) {
            assertTrue(expected.getMessage().contains("changed after removal was planned"));
        }

        assertEquals("object-v2", Files.readString(root.resolve("objects/temple.iob"), StandardCharsets.UTF_8));
        assertEquals("structure-v2", Files.readString(
                root.resolve("structures/temple.json"),
                StandardCharsets.UTF_8));
        assertTrue(Files.exists(manifestPath));
    }

    @Test
    public void lockedRemovalValidatorRejectsBeforeMovingOwnedResources() throws IOException {
        Path root = temporaryFolder.newFolder("owned-removal-locked-validator").toPath();
        StructureTransactionWriter writer = new StructureTransactionWriter(root);
        StructureWriteResult initial = writer.write(
                bundle("object-v1", "structure-v1"),
                StructureWriteMode.ADD_ONLY);
        assertEquals(failureMessage(initial), StructureWriteResult.Status.ADDED, initial.status());
        Path objectPath = root.resolve("objects/temple.iob");
        Path structurePath = root.resolve("structures/temple.json");
        Path manifestPath = writer.ownershipManifestPath(TARGET_KEY);
        byte[] expectedObject = Files.readAllBytes(objectPath);
        byte[] expectedStructure = Files.readAllBytes(structurePath);
        byte[] expectedManifest = Files.readAllBytes(manifestPath);

        IOException failure = assertThrows(
                IOException.class,
                () -> writer.prepareOwnedRemovals(
                        List.of(new StructureTransactionWriter.OwnedRemoval(
                                TARGET_KEY,
                                StructureSource.Kind.VANILLA,
                                SOURCE_KEY,
                                Optional.empty(),
                                Optional.of(StructureHash.sha256(expectedManifest)))),
                        () -> {
                            assertArrayEquals(expectedObject, Files.readAllBytes(objectPath));
                            assertArrayEquals(expectedStructure, Files.readAllBytes(structurePath));
                            assertArrayEquals(expectedManifest, Files.readAllBytes(manifestPath));
                            throw new IOException("Injected locked removal validation failure");
                        }));

        assertTrue(failure.getMessage().contains("Injected locked removal validation failure"));
        assertArrayEquals(expectedObject, Files.readAllBytes(objectPath));
        assertArrayEquals(expectedStructure, Files.readAllBytes(structurePath));
        assertArrayEquals(expectedManifest, Files.readAllBytes(manifestPath));
    }

    @Test
    public void preparedRemovalCanRollbackAfterCommitMarkerBeforeCoordinatorPublish() throws IOException {
        Path root = temporaryFolder.newFolder("owned-removal-coordinator-rollback").toPath();
        StructureTransactionWriter writer = new StructureTransactionWriter(root);
        StructureWriteResult initial = writer.write(bundle("object-v1", "structure-v1"), StructureWriteMode.ADD_ONLY);
        assertEquals(failureMessage(initial), StructureWriteResult.Status.ADDED, initial.status());
        StructureTransactionWriter.PreparedRemoval removal = writer.prepareOwnedRemovals(List.of(
                new StructureTransactionWriter.OwnedRemoval(
                        TARGET_KEY,
                        StructureSource.Kind.VANILLA,
                        SOURCE_KEY,
                        Optional.empty(),
                        Optional.empty()
                )
        ));
        assertFalse(Files.exists(root.resolve("objects/temple.iob")));

        removal.markCommitted();
        removal.rollback();

        assertEquals("object-v1", Files.readString(root.resolve("objects/temple.iob"), StandardCharsets.UTF_8));
        assertEquals("structure-v1", Files.readString(root.resolve("structures/temple.json"), StandardCharsets.UTF_8));
        assertTrue(Files.exists(writer.ownershipManifestPath(TARGET_KEY)));
    }

    @Test
    public void preparedRemovalTokenCanRestoreAfterTheCoordinatorProcessDies() throws IOException {
        Path root = temporaryFolder.newFolder("owned-removal-token-rollback").toPath();
        StructureTransactionWriter writer = new StructureTransactionWriter(root);
        StructureWriteResult initial = writer.write(bundle("object-v1", "structure-v1"), StructureWriteMode.ADD_ONLY);
        assertEquals(failureMessage(initial), StructureWriteResult.Status.ADDED, initial.status());
        StructureTransactionWriter.PreparedRemoval removal = writer.prepareOwnedRemovals(List.of(
                new StructureTransactionWriter.OwnedRemoval(
                        TARGET_KEY,
                        StructureSource.Kind.VANILLA,
                        SOURCE_KEY,
                        Optional.empty(),
                        Optional.empty()
                )
        ));
        StructureTransactionWriter.PreparedRemovalToken token = removal.recoveryToken().orElseThrow();
        removal.leaveForRecovery();

        new StructureTransactionWriter(root).resolvePreparedRemoval(token, false);

        assertEquals("object-v1", Files.readString(root.resolve("objects/temple.iob"), StandardCharsets.UTF_8));
        assertEquals("structure-v1", Files.readString(root.resolve("structures/temple.json"), StandardCharsets.UTF_8));
        assertTrue(Files.exists(writer.ownershipManifestPath(TARGET_KEY)));
        assertFalse(Files.exists(transactionRoot(root, token.transactionId())));
    }

    @Test
    public void preparedRemovalTokenCanCommitAfterTheCoordinatorProcessDies() throws IOException {
        Path root = temporaryFolder.newFolder("owned-removal-token-commit").toPath();
        StructureTransactionWriter writer = new StructureTransactionWriter(root);
        StructureWriteResult initial = writer.write(bundle("object-v1", "structure-v1"), StructureWriteMode.ADD_ONLY);
        assertEquals(failureMessage(initial), StructureWriteResult.Status.ADDED, initial.status());
        StructureTransactionWriter.PreparedRemoval removal = writer.prepareOwnedRemovals(List.of(
                new StructureTransactionWriter.OwnedRemoval(
                        TARGET_KEY,
                        StructureSource.Kind.VANILLA,
                        SOURCE_KEY,
                        Optional.empty(),
                        Optional.empty()
                )
        ));
        StructureTransactionWriter.PreparedRemovalToken token = removal.recoveryToken().orElseThrow();
        removal.leaveForRecovery();

        new StructureTransactionWriter(root).resolvePreparedRemoval(token, true);

        assertFalse(Files.exists(root.resolve("objects/temple.iob")));
        assertFalse(Files.exists(root.resolve("structures/temple.json")));
        assertFalse(Files.exists(writer.ownershipManifestPath(TARGET_KEY)));
        assertFalse(Files.exists(transactionRoot(root, token.transactionId())));
    }

    @Test
    public void preparedRemovalRejectsMissingAndInvalidJournalsWithoutDeletingRecoveryData() throws IOException {
        Path root = temporaryFolder.newFolder("owned-removal-invalid-journal").toPath();
        StructureTransactionWriter writer = new StructureTransactionWriter(root);
        StructureTransactionWriter.PreparedRemovalToken token =
                new StructureTransactionWriter.PreparedRemovalToken(root, UUID.randomUUID());
        Path transactionRoot = transactionRoot(token.packRoot(), token.transactionId());
        Path journalPath = transactionRoot.resolve(StructureTransactionJournal.FILE_NAME);
        Files.createDirectories(transactionRoot);

        for (boolean commit : new boolean[]{false, true}) {
            IOException failure = assertThrows(
                    IOException.class, () -> writer.resolvePreparedRemoval(token, commit));
            assertEquals("Missing prepared removal journal at " + transactionRoot, failure.getMessage());
            assertTrue(Files.isDirectory(transactionRoot));
        }

        Files.writeString(journalPath, "not-json", StandardCharsets.UTF_8);
        for (boolean commit : new boolean[]{false, true}) {
            IOException failure = assertThrows(
                    IOException.class, () -> writer.resolvePreparedRemoval(token, commit));
            assertEquals("Invalid prepared removal journal at " + journalPath, failure.getMessage());
            assertTrue(failure.getCause() instanceof RuntimeException);
            assertEquals("not-json", Files.readString(journalPath, StandardCharsets.UTF_8));
        }
    }

    @Test
    public void symbolicLinkAncestorsCannotEscapeThePackRoot() throws IOException {
        Path root = temporaryFolder.newFolder("symlink-pack").toPath();
        Path outside = temporaryFolder.newFolder("symlink-outside").toPath();
        try {
            Files.createSymbolicLink(root.resolve("objects"), outside);
        } catch (IOException | UnsupportedOperationException | SecurityException e) {
            Assume.assumeNoException(e);
        }
        StructureTransactionWriter writer = new StructureTransactionWriter(root);

        StructureWriteResult result = writer.write(bundle("object-v1", "structure-v1"),
                StructureWriteMode.ADD_ONLY);

        assertEquals(StructureWriteResult.Status.FAILED, result.status());
        assertFalse(Files.exists(outside.resolve("temple.iob")));
    }

    private StructureResourceBundle bundle(String objectContent, String structureContent) {
        return bundle(TARGET_KEY, SOURCE_KEY, objectContent, structureContent);
    }

    private StructureResourceBundle bundle(
            StructureKey targetKey,
            StructureKey sourceKey,
            String objectContent,
            String structureContent
    ) {
        return StructureResourceBundle.builder(targetKey)
                .source(StructureSource.of(
                        sourceKey.namespace().equals("minecraft")
                                ? StructureSource.Kind.VANILLA : StructureSource.Kind.DATAPACK,
                        sourceKey
                ))
                .backend(StructureBackend.IRIS_ASSEMBLY)
                .capability(StructureCapability.BLOCKS)
                .capability(StructureCapability.CONNECTORS)
                .loss(StructureLoss.warning(
                        StructureCapability.PROCESSORS,
                        "processors_omitted",
                        "The source processor list was not represented"
                ))
                .resource("objects/" + targetKey.path() + ".iob", objectContent.getBytes(StandardCharsets.UTF_8))
                .textResource("structures/" + targetKey.path() + ".json", structureContent)
                .build();
    }

    private Path transactionRoot(Path root, UUID transactionId) {
        return root.resolve(".iris/structure-staging").resolve(transactionId.toString());
    }

    private String failureMessage(StructureWriteResult result) {
        return result.failure().map(Throwable::toString).orElse("");
    }

    private void writeJournal(
            Path transactionRoot,
            StructureTransactionJournal journal
    ) throws IOException {
        Files.createDirectories(transactionRoot);
        Files.write(transactionRoot.resolve(StructureTransactionJournal.FILE_NAME), journal.toJson());
    }

    private static final class FailOnceMoveOperations implements StructureFileOperations {
        private final NioStructureFileOperations delegate;
        private final String failingSuffix;
        private final byte[] competingContent;
        private boolean failed;

        private FailOnceMoveOperations(String failingSuffix) {
            this(failingSuffix, null);
        }

        private FailOnceMoveOperations(String failingSuffix, byte[] competingContent) {
            delegate = new NioStructureFileOperations();
            this.failingSuffix = failingSuffix;
            this.competingContent = competingContent == null ? null : competingContent.clone();
        }

        @Override
        public boolean exists(Path path) {
            return delegate.exists(path);
        }

        @Override
        public boolean isRegularFile(Path path) {
            return delegate.isRegularFile(path);
        }

        @Override
        public byte[] readAllBytes(Path path) throws IOException {
            return delegate.readAllBytes(path);
        }

        @Override
        public String sha256(Path path) throws IOException {
            return delegate.sha256(path);
        }

        @Override
        public void createDirectories(Path path) throws IOException {
            delegate.createDirectories(path);
        }

        @Override
        public void writeNew(Path path, byte[] content) throws IOException {
            delegate.writeNew(path, content);
        }

        @Override
        public void move(Path source, Path target) throws IOException {
            String portableSource = source.toString().replace(File.separatorChar, '/');
            if (!failed && portableSource.endsWith(failingSuffix)) {
                failed = true;
                throw new IOException("Injected install failure for " + failingSuffix);
            }
            delegate.move(source, target);
        }

        @Override
        public void moveNew(Path source, Path target) throws IOException {
            String portableSource = source.toString().replace(File.separatorChar, '/');
            if (!failed && competingContent != null && portableSource.endsWith(failingSuffix)) {
                failed = true;
                delegate.createDirectories(target.getParent());
                delegate.writeNew(target, competingContent);
                delegate.moveNew(source, target);
                return;
            }
            StructureFileOperations.super.moveNew(source, target);
        }

        @Override
        public void deleteIfExists(Path path) throws IOException {
            delegate.deleteIfExists(path);
        }

        @Override
        public void deleteTree(Path root) throws IOException {
            delegate.deleteTree(root);
        }
    }
}
