package art.arcane.iris.world;

import java.util.List;
import java.util.Optional;

public final class IrisStartupValidation {
    private static volatile Snapshot snapshot = Snapshot.disabled();

    private IrisStartupValidation() {
    }

    public static synchronized void begin() {
        snapshot = new Snapshot(true, ValidationState.PENDING, ValidationState.PENDING, List.of(), List.of(), ValidationState.DISABLED, "");
    }

    public static synchronized void disable() {
        snapshot = Snapshot.disabled();
    }

    public static synchronized void beginRuntimeValidation() {
        if (!snapshot.enforced()) {
            return;
        }
        snapshot = new Snapshot(true, snapshot.datapacks(), snapshot.packs(),
                snapshot.datapackFailures(), snapshot.packFailures(), ValidationState.PENDING, "");
    }

    public static synchronized void markRuntimeReady() {
        if (!snapshot.enforced()) {
            return;
        }
        snapshot = new Snapshot(true, snapshot.datapacks(), snapshot.packs(),
                snapshot.datapackFailures(), snapshot.packFailures(), ValidationState.READY, "");
    }

    public static synchronized void markRuntimeInvalid(String failure) {
        if (!snapshot.enforced()) {
            return;
        }
        snapshot = new Snapshot(true, snapshot.datapacks(), snapshot.packs(),
                snapshot.datapackFailures(), snapshot.packFailures(), ValidationState.INVALID,
                normalizeFailure(failure, "Iris runtime injection failed. Resolve the startup errors and restart the server."));
    }

    public static synchronized void beginDatapackValidation() {
        if (!snapshot.enforced() || snapshot.datapacks() == ValidationState.RESTART_REQUIRED) {
            return;
        }
        snapshot = new Snapshot(true, ValidationState.PENDING, snapshot.packs(), List.of(), snapshot.packFailures(), snapshot.runtime(), snapshot.runtimeFailure());
    }

    public static synchronized void markDatapacksReady() {
        if (!snapshot.enforced() || snapshot.datapacks() == ValidationState.RESTART_REQUIRED) {
            return;
        }
        snapshot = new Snapshot(true, ValidationState.READY, snapshot.packs(), List.of(), snapshot.packFailures(), snapshot.runtime(), snapshot.runtimeFailure());
    }

    public static synchronized void markDatapacksInvalid(String failure) {
        if (!snapshot.enforced()) {
            return;
        }
        snapshot = new Snapshot(
                true,
                ValidationState.INVALID,
                snapshot.packs(),
                List.of(normalizeFailure(failure, "External datapack validation failed.")),
                snapshot.packFailures(), snapshot.runtime(), snapshot.runtimeFailure());
    }

    public static synchronized void requireRestart(String reason) {
        if (!snapshot.enforced()) {
            return;
        }
        snapshot = new Snapshot(
                true,
                ValidationState.RESTART_REQUIRED,
                snapshot.packs(),
                List.of(normalizeFailure(reason, "A restart is required to load validated datapacks.")),
                snapshot.packFailures(), snapshot.runtime(), snapshot.runtimeFailure());
    }

    public static synchronized void markPacksReady() {
        if (!snapshot.enforced()) {
            return;
        }
        snapshot = new Snapshot(true, snapshot.datapacks(), ValidationState.READY, snapshot.datapackFailures(), List.of(), snapshot.runtime(), snapshot.runtimeFailure());
    }

    public static synchronized void markPacksInvalid(List<String> failures) {
        if (!snapshot.enforced()) {
            return;
        }
        List<String> normalized = failures == null || failures.isEmpty()
                ? List.of("Iris dimension-pack validation failed.")
                : failures.stream()
                .map(failure -> normalizeFailure(failure, "Iris dimension-pack validation failed."))
                .toList();
        snapshot = new Snapshot(true, snapshot.datapacks(), ValidationState.INVALID,
                snapshot.datapackFailures(), normalized, snapshot.runtime(), snapshot.runtimeFailure());
    }

    public static boolean isReady() {
        return isReady(snapshot);
    }

    public static boolean isRestartRequired() {
        Snapshot current = snapshot;
        return current.enforced() && current.datapacks() == ValidationState.RESTART_REQUIRED;
    }

    public static Optional<String> denialReason() {
        return denialReason(snapshot);
    }

    public static Optional<String> studioDenialReason(boolean force) {
        Snapshot current = snapshot;
        if (force
                && current.enforced()
                && isRuntimeReady(current)
                && current.datapacks() == ValidationState.RESTART_REQUIRED
                && current.packs() == ValidationState.READY) {
            return Optional.empty();
        }
        return denialReason(current);
    }

    private static Optional<String> denialReason(Snapshot current) {
        if (!current.enforced() || isReady(current)) {
            return Optional.empty();
        }
        if (!isRuntimeReady(current)) {
            return Optional.of(current.runtime() == ValidationState.INVALID
                    ? current.runtimeFailure()
                    : "Iris is still validating runtime injection.");
        }
        if (current.datapacks() == ValidationState.RESTART_REQUIRED) {
            return Optional.of(firstFailure(current.datapackFailures(),
                    "Iris installed validated datapacks and requires a restart before the server is safe."));
        }
        if (current.datapacks() == ValidationState.INVALID) {
            return Optional.of(firstFailure(current.datapackFailures(),
                    "Iris external datapack validation failed."));
        }
        if (current.datapacks() == ValidationState.PENDING) {
            return Optional.of("Iris is still validating external datapacks.");
        }
        if (current.packs() == ValidationState.INVALID) {
            return Optional.of(firstFailure(current.packFailures(),
                    "Iris dimension-pack validation failed."));
        }
        return Optional.of("Iris is still validating dimension packs.");
    }

    public static void requireWorldCreationReady() {
        Optional<String> denial = denialReason();
        if (denial.isPresent()) {
            throw new IllegalStateException("Iris world creation is locked: " + denial.get());
        }
    }

    public static void requireWorldReplacementStagingReady() {
        Snapshot current = snapshot;
        if (current.enforced()
                && isRuntimeReady(current)
                && current.datapacks() == ValidationState.RESTART_REQUIRED
                && current.packs() == ValidationState.READY) {
            return;
        }
        requireWorldCreationReady();
    }

    static Snapshot snapshot() {
        return snapshot;
    }

    private static String normalizeFailure(String failure, String fallback) {
        return failure == null || failure.isBlank() ? fallback : failure.trim();
    }

    private static String firstFailure(List<String> failures, String fallback) {
        return failures == null || failures.isEmpty() ? fallback : failures.getFirst();
    }

    private static boolean isRuntimeReady(Snapshot current) {
        return current.runtime() == ValidationState.DISABLED || current.runtime() == ValidationState.READY;
    }

    private static boolean isReady(Snapshot current) {
        return !current.enforced()
                || isRuntimeReady(current)
                && current.datapacks() == ValidationState.READY
                && current.packs() == ValidationState.READY;
    }

    enum ValidationState {
        PENDING,
        READY,
        INVALID,
        RESTART_REQUIRED,
        DISABLED
    }

    record Snapshot(
            boolean enforced,
            ValidationState datapacks,
            ValidationState packs,
            List<String> datapackFailures,
            List<String> packFailures,
            ValidationState runtime,
            String runtimeFailure
    ) {
        Snapshot {
            datapackFailures = List.copyOf(datapackFailures);
            packFailures = List.copyOf(packFailures);
        }

        private static Snapshot disabled() {
            return new Snapshot(false, ValidationState.DISABLED, ValidationState.DISABLED, List.of(), List.of(), ValidationState.DISABLED, "");
        }
    }
}
