package art.arcane.iris.core.commands;

import art.arcane.iris.Iris;
import art.arcane.iris.core.service.JigsawStudioService;
import art.arcane.iris.core.structure.export.VanillaJigsawDatapackExporter;
import art.arcane.iris.core.structure.export.VanillaJigsawExportDiagnostic;
import art.arcane.iris.core.structure.export.VanillaJigsawExportFormat;
import art.arcane.iris.core.structure.export.VanillaJigsawExportRequest;
import art.arcane.iris.core.structure.export.VanillaJigsawExportResult;
import art.arcane.iris.util.common.format.C;
import art.arcane.iris.util.common.plugin.VolmitSender;
import art.arcane.iris.util.common.scheduling.J;
import org.bukkit.entity.Player;

import java.nio.file.Path;
import java.util.Locale;
import java.util.Objects;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.regex.Pattern;

final class CommandJigsawExports {
    private static final Pattern EXPORT_OUTPUT_NAME = Pattern.compile("[a-zA-Z0-9_-][a-zA-Z0-9._-]{0,127}");
    private static final Set<UUID> EXPORTING_PLAYERS = ConcurrentHashMap.newKeySet();
    private static final Set<Path> EXPORT_DESTINATIONS = ConcurrentHashMap.newKeySet();

    private CommandJigsawExports() {
    }

    static String exportStartError(JigsawStudioService.ExportStart exportStart) {
        Objects.requireNonNull(exportStart, "Jigsaw Studio export start status");
        return switch (exportStart) {
            case NOT_ACTIVE -> "The active Jigsaw Studio is no longer available.";
            case NOT_OWNER -> "Only the Jigsaw Studio owner can export this project.";
            case DIRTY -> "Wait for the pending autosave or discard the edits before exporting the on-disk graph.";
            case CLOSING -> "The active Jigsaw Studio is closing and cannot be exported.";
            case SAVE_IN_PROGRESS -> "Wait for the current Jigsaw Studio save to finish before exporting.";
            case OPERATION_IN_PROGRESS -> "Wait for the current Jigsaw Studio operation to finish before exporting.";
            case IN_PROGRESS -> "A Jigsaw Studio export is already in progress.";
            case STARTED -> throw new IllegalArgumentException("STARTED is not an export failure");
        };
    }

    static boolean beginExport(UUID playerId, Path destination) {
        Objects.requireNonNull(playerId);
        Path normalizedDestination = Objects.requireNonNull(destination).toAbsolutePath().normalize();
        if (!EXPORTING_PLAYERS.add(playerId)) {
            return false;
        }
        if (EXPORT_DESTINATIONS.add(normalizedDestination)) {
            return true;
        }
        EXPORTING_PLAYERS.remove(playerId);
        return false;
    }

    static void finishExport(UUID playerId, Path destination) {
        EXPORTING_PLAYERS.remove(Objects.requireNonNull(playerId));
        EXPORT_DESTINATIONS.remove(Objects.requireNonNull(destination).toAbsolutePath().normalize());
    }

    static void runExport(ExportOperation operation) {
        VanillaJigsawExportResult result = null;
        Throwable failure = null;
        try {
            result = new VanillaJigsawDatapackExporter().export(operation.request());
        } catch (Throwable throwable) {
            failure = throwable;
            Iris.reportError("Vanilla Jigsaw export failed for '" + operation.structureKey() + "'.", throwable);
        } finally {
            operation.lease().release();
        }
        VanillaJigsawExportResult completedResult = result;
        Throwable completedFailure = failure;
        Runnable response = () -> reportExport(operation, completedResult, completedFailure);
        J.runEntity(operation.player(), response);
    }

    private static void reportExport(
            ExportOperation operation,
            VanillaJigsawExportResult result,
            Throwable failure
    ) {
        if (failure != null || result == null) {
            operation.sender().sendMessage(C.RED + "Background vanilla export for '"
                    + operation.structureKey() + "' failed: "
                    + (failure == null ? "no result" : failure.getMessage()));
            return;
        }
        for (VanillaJigsawExportDiagnostic diagnostic : result.diagnostics()) {
            C color = diagnostic.isBlocking() ? C.RED : C.YELLOW;
            operation.sender().sendMessage(color.toString() + diagnostic.code() + ": " + diagnostic.message());
        }
        CommandJigsaw.ActiveContext current = CommandJigsaw.active(operation.player());
        boolean sameSession = current != null
                && current.request().requestId().equals(operation.requestId());
        String contextLabel = sameSession ? "Vanilla export" : "Background vanilla export for '"
                + operation.structureKey() + "'";
        operation.sender().sendMessage((result.isSuccess() ? C.GREEN : C.RED) + contextLabel + " "
                + result.status() + ": " + result.output());
    }

    static VanillaJigsawExportFormat parseFormat(String value) {
        return switch (value.trim().toLowerCase(Locale.ROOT)) {
            case "zip" -> VanillaJigsawExportFormat.ZIP;
            case "directory", "dir", "folder" -> VanillaJigsawExportFormat.DIRECTORY;
            default -> throw new IllegalArgumentException("Export format must be directory or zip.");
        };
    }

    static Path resolveExportDestination(
            Path exportRoot,
            String output,
            VanillaJigsawExportFormat format
    ) {
        Path root = Objects.requireNonNull(exportRoot, "Jigsaw export root").toAbsolutePath().normalize();
        String requested = Objects.requireNonNull(output, "Jigsaw export output name");
        String name = requested.trim();
        if (!name.equals(requested) || !EXPORT_OUTPUT_NAME.matcher(name).matches()) {
            throw new IllegalArgumentException(
                    "Export output must be one file name using letters, numbers, '.', '_', or '-'.");
        }
        String fileName = format == VanillaJigsawExportFormat.ZIP && !name.endsWith(".zip")
                ? name + ".zip" : name;
        Path destination = root.resolve(fileName).normalize();
        if (destination.equals(root) || !root.equals(destination.getParent())) {
            throw new IllegalArgumentException("Export output must be one artifact inside the Studio exports folder.");
        }
        return destination;
    }

    record ExportOperation(
            Player player,
            VolmitSender sender,
            UUID requestId,
            String structureKey,
            VanillaJigsawExportRequest request,
            ExportLease lease
    ) {
    }

    static final class ExportLease {
        private final Runnable release;
        private final AtomicBoolean released;

        ExportLease(Runnable release) {
            this.release = Objects.requireNonNull(release, "Jigsaw export lease release action");
            this.released = new AtomicBoolean();
        }

        void release() {
            if (released.compareAndSet(false, true)) {
                release.run();
            }
        }
    }
}
