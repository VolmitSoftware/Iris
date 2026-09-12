package art.arcane.iris.world.lifecycle;

import art.arcane.iris.world.ExactWorldSlotPathPolicy;
import art.arcane.iris.world.WorldSlotKey;
import art.arcane.iris.world.lifecycle.BukkitWorldConfiguration.WorldGeneratorSnapshot;
import art.arcane.iris.world.lifecycle.WorldReplacementFilesystem.ReplacementPaths;
import art.arcane.iris.world.lifecycle.WorldReplacementJournal.Phase;
import art.arcane.iris.world.lifecycle.WorldReplacementJournal.Transaction;

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.LinkOption;
import java.nio.file.Path;
import java.util.List;
import java.util.Objects;
import java.util.function.Consumer;

public final class WorldReplacementBootstrap {
    private WorldReplacementBootstrap() {
    }

    public static ReconcileResult reconcile(
            Path dataDirectory,
            Path levelRoot,
            Path bukkitConfiguration,
            Consumer<String> feedback
    ) throws IOException {
        Path requiredDataDirectory = Objects.requireNonNull(dataDirectory, "dataDirectory")
                .toAbsolutePath()
                .normalize();
        Path requiredLevelRoot = Objects.requireNonNull(levelRoot, "levelRoot")
                .toAbsolutePath()
                .normalize();
        File requiredBukkitConfiguration = Objects.requireNonNull(bukkitConfiguration, "bukkitConfiguration")
                .toAbsolutePath()
                .normalize()
                .toFile();
        Consumer<String> requiredFeedback = Objects.requireNonNull(feedback, "feedback");
        List<Transaction> transactions = WorldReplacementJournal.load(requiredDataDirectory, requiredLevelRoot);
        int published = 0;
        int rolledBack = 0;
        int retained = 0;
        int skipped = 0;
        for (Transaction transaction : transactions) {
            // A journal recorded against another level root or logical world name is not this
            // boot's transaction; skip it with a pointer instead of aborting the whole startup.
            if (!WorldReplacementJournal.appliesTo(transaction, requiredLevelRoot)) {
                skipped++;
                requiredFeedback.accept("Skipping pending world replacement " + transaction.id()
                        + " for " + transaction.worldKey() + ": it was staged against level root "
                        + transaction.levelRoot() + " but this server now uses " + requiredLevelRoot
                        + ". Remove " + requiredDataDirectory.resolve(WorldReplacementJournal.DIRECTORY_NAME)
                        .resolve(transaction.id() + ".properties")
                        + " or restore the previous level-name to resolve it.");
                continue;
            }
            ReconcileAction action = reconcileTransaction(
                    requiredDataDirectory,
                    requiredLevelRoot,
                    requiredBukkitConfiguration,
                    transaction,
                    requiredFeedback
            );
            switch (action) {
                case PUBLISHED -> published++;
                case ROLLED_BACK -> rolledBack++;
                case RETAINED -> retained++;
            }
        }
        return new ReconcileResult(transactions.size(), published, rolledBack, retained, skipped);
    }

    public static WorldGeneratorSnapshot replacementSnapshot(Transaction transaction) {
        Transaction requiredTransaction = Objects.requireNonNull(transaction, "transaction");
        return new WorldGeneratorSnapshot(
                true,
                true,
                true,
                "Iris:" + requiredTransaction.dimension(),
                true,
                requiredTransaction.seed()
        );
    }

    private static ReconcileAction reconcileTransaction(
            Path dataDirectory,
            Path levelRoot,
            File bukkitConfiguration,
            Transaction transaction,
            Consumer<String> feedback
    ) throws IOException {
        ExactWorldSlotPathPolicy.Target target = WorldReplacementJournal.resolveTarget(transaction, levelRoot);
        ReplacementPaths paths = WorldReplacementFilesystem.paths(target, transaction.id());
        WorldGeneratorSnapshot current = BukkitWorldConfiguration.snapshot(
                bukkitConfiguration,
                transaction.worldName()
        );
        WorldGeneratorSnapshot replacement = replacementSnapshot(transaction);
        if (transaction.phase() == Phase.CLEANUP_PENDING) {
            if (!current.matchesGeneratorAndSeed(replacement)) {
                throw conflict(transaction, "A verified replacement no longer matches bukkit.yml.");
            }
            WorldReplacementFilesystem.validateCommittedTarget(paths, transaction.packFingerprint());
            return ReconcileAction.RETAINED;
        }
        if (transaction.phase() == Phase.ROLLBACK_CLEANUP) {
            if (!current.matchesGeneratorAndSeed(transaction.originalConfiguration())) {
                throw conflict(transaction, "Rollback cleanup no longer matches the retained bukkit.yml state.");
            }
            WorldReplacementFilesystem.finishPreparedRollback(paths, transaction.originalTargetPresent());
            WorldReplacementJournal.delete(dataDirectory, transaction.id());
            feedback.accept("Restored the retained world for " + transaction.worldKey() + ".");
            return ReconcileAction.ROLLED_BACK;
        }
        if (transaction.phase() == Phase.ROLLBACK_PENDING) {
            rollback(dataDirectory, bukkitConfiguration, transaction, paths, current, replacement);
            feedback.accept("Restored the retained world for " + transaction.worldKey() + ".");
            return ReconcileAction.ROLLED_BACK;
        }
        Transaction active = transaction;
        if (active.phase() == Phase.PREPARED) {
            if (current.matchesGeneratorAndSeed(active.originalConfiguration())) {
                rollback(dataDirectory, bukkitConfiguration, active, paths, current, replacement);
                feedback.accept("Cancelled incomplete Iris world replacement for " + active.worldKey() + ".");
                return ReconcileAction.ROLLED_BACK;
            }
            if (!current.matchesGeneratorAndSeed(replacement)) {
                throw conflict(active, "bukkit.yml matches neither the replacement nor its retained original state.");
            }
            active = active.withPhase(Phase.ARMED);
            WorldReplacementJournal.write(dataDirectory, active);
        }
        if (active.phase() == Phase.ARMED) {
            if (!current.matchesGeneratorAndSeed(replacement)) {
                if (current.matchesGeneratorAndSeed(active.originalConfiguration())) {
                    rollback(dataDirectory, bukkitConfiguration, active, paths, current, replacement);
                    feedback.accept("Cancelled Iris world replacement for " + active.worldKey() + ".");
                    return ReconcileAction.ROLLED_BACK;
                }
                throw conflict(active, "bukkit.yml matches neither the replacement nor its retained original state.");
            }
            try {
                refreshOverworldEntryGuard(levelRoot, active, paths);
                WorldReplacementFilesystem.publish(
                        paths,
                        active.originalTargetPresent(),
                        active.packFingerprint()
                );
            } catch (IOException publishFailure) {
                throw new IOException("Pending replacement for " + active.worldKey()
                        + " cannot be published: " + publishFailure.getMessage()
                        + " To recover, restore the original generator entry for \"" + active.worldName()
                        + "\" in bukkit.yml (the next boot rolls the replacement back), or delete the journal at "
                        + dataDirectory.resolve(WorldReplacementJournal.DIRECTORY_NAME).resolve(active.id() + ".properties")
                        + ".", publishFailure);
            }
            active = active.withPhase(Phase.PUBLISHED);
            WorldReplacementJournal.write(dataDirectory, active);
            feedback.accept("Published Iris world replacement for " + active.worldKey()
                    + "; waiting for runtime verification.");
            return ReconcileAction.PUBLISHED;
        }
        if (active.phase() == Phase.PUBLISHED) {
            if (!current.matchesGeneratorAndSeed(replacement)) {
                if (current.matchesGeneratorAndSeed(active.originalConfiguration())) {
                    rollback(dataDirectory, bukkitConfiguration, active, paths, current, replacement);
                    feedback.accept("Rolled back Iris world replacement for " + active.worldKey() + ".");
                    return ReconcileAction.ROLLED_BACK;
                }
                throw conflict(active, "bukkit.yml matches neither the replacement nor its retained original state.");
            }
            refreshOverworldEntryGuard(levelRoot, active, paths);
            WorldReplacementFilesystem.publish(
                    paths,
                    active.originalTargetPresent(),
                    active.packFingerprint()
            );
            return ReconcileAction.RETAINED;
        }
        throw conflict(active, "Replacement journal reached an unsupported bootstrap phase.");
    }

    private static void refreshOverworldEntryGuard(
            Path levelRoot,
            Transaction transaction,
            ReplacementPaths paths
    ) throws IOException {
        if (!WorldSlotKey.minecraft("overworld").equals(transaction.worldKey())) {
            return;
        }
        Path replacementWorld = Files.isDirectory(paths.stage(), LinkOption.NOFOLLOW_LINKS)
                ? paths.stage()
                : paths.target();
        WorldReplacementEntryGuard.refreshPlayers(levelRoot, replacementWorld, transaction.id());
    }

    private static void rollback(
            Path dataDirectory,
            File bukkitConfiguration,
            Transaction transaction,
            ReplacementPaths paths,
            WorldGeneratorSnapshot current,
            WorldGeneratorSnapshot replacement
    ) throws IOException {
        if (!current.matchesGeneratorAndSeed(replacement)
                && !current.matchesGeneratorAndSeed(transaction.originalConfiguration())) {
            throw conflict(transaction, "bukkit.yml conflicts with the pending world rollback.");
        }
        Transaction rollback = transaction.phase() == Phase.ROLLBACK_PENDING
                ? transaction
                : transaction.withPhase(Phase.ROLLBACK_PENDING);
        if (transaction.phase() != Phase.ROLLBACK_PENDING) {
            WorldReplacementJournal.write(dataDirectory, rollback);
        }
        WorldReplacementFilesystem.prepareRollback(paths, rollback.originalTargetPresent());
        if (current.matchesGeneratorAndSeed(replacement)) {
            boolean restored;
            try {
                restored = BukkitWorldConfiguration.restoreIfMatching(
                        bukkitConfiguration,
                        rollback.worldName(),
                        replacement,
                        rollback.originalConfiguration()
                );
            } catch (IOException failure) {
                republishAfterConfigurationFailure(rollback, paths, failure);
                throw failure;
            }
            if (!restored) {
                WorldGeneratorSnapshot observed = BukkitWorldConfiguration.snapshot(
                        bukkitConfiguration,
                        rollback.worldName()
                );
                if (!observed.matchesGeneratorAndSeed(rollback.originalConfiguration())) {
                    IOException failure = conflict(
                            rollback,
                            "bukkit.yml changed during startup rollback."
                    );
                    republishAfterConfigurationFailure(rollback, paths, failure);
                    throw failure;
                }
            }
        }
        Transaction cleanup = rollback.withPhase(Phase.ROLLBACK_CLEANUP);
        WorldReplacementJournal.write(dataDirectory, cleanup);
        WorldReplacementFilesystem.finishPreparedRollback(paths, cleanup.originalTargetPresent());
        WorldReplacementJournal.delete(dataDirectory, cleanup.id());
    }

    private static void republishAfterConfigurationFailure(
            Transaction transaction,
            ReplacementPaths paths,
            IOException failure
    ) {
        try {
            WorldReplacementFilesystem.publish(
                    paths,
                    transaction.originalTargetPresent(),
                    transaction.packFingerprint()
            );
        } catch (Throwable republishFailure) {
            failure.addSuppressed(republishFailure);
        }
    }

    private static IOException conflict(Transaction transaction, String detail) {
        return new IOException("Pending replacement for " + transaction.worldKey() + " is blocked: " + detail);
    }

    public record ReconcileResult(int transactions, int published, int rolledBack, int retained, int skipped) {
    }

    private enum ReconcileAction {
        PUBLISHED,
        ROLLED_BACK,
        RETAINED
    }
}
