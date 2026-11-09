/*
 * Iris is a World Generator for Minecraft Servers
 * Copyright (c) 2026 Arcane Arts (Volmit Software)
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

package art.arcane.iris.core.structure.tooling;

import art.arcane.iris.core.service.JigsawStudioService;
import art.arcane.iris.core.structure.export.VanillaJigsawDatapackExporter;
import art.arcane.iris.core.structure.export.VanillaJigsawExportDiagnostic;
import art.arcane.iris.core.structure.export.VanillaJigsawExportFormat;
import art.arcane.iris.core.structure.export.VanillaJigsawExportRequest;
import art.arcane.iris.core.structure.export.VanillaJigsawExportResult;
import art.arcane.iris.core.structure.export.VanillaJigsawExportSource;
import art.arcane.iris.core.structure.tooling.StudioSessions.ActiveContext;
import com.volmit.tooling.Argument;
import com.volmit.tooling.Command;
import com.volmit.tooling.CommandContext;
import com.volmit.tooling.Sender;
import com.volmit.tooling.world.TextColor;
import com.volmit.tooling.world.ToolPlayer;

import java.nio.file.Path;
import java.util.Locale;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.function.Supplier;
import java.util.regex.Pattern;

/** {@code export}: writes the clean graph of the open studio as a vanilla datapack below the pack's output folder. */
final class JigsawExportCommand {
    private static final Pattern EXPORT_OUTPUT_NAME = Pattern.compile("[a-zA-Z0-9_-][a-zA-Z0-9._-]{0,127}");
    private static final Set<UUID> EXPORTING_PLAYERS = ConcurrentHashMap.newKeySet();
    private static final Set<Path> EXPORT_DESTINATIONS = ConcurrentHashMap.newKeySet();

    private static final Argument<String> NAMESPACE = Argument.word("namespace").orElse("iris")
            .describe("Datapack namespace");
    private static final Argument<String> OUTPUT = Argument.word("output").orElse("jigsaw-export")
            .describe("Output name under the Studio exports folder");
    private static final Argument<String> FORMAT = Argument.word("format").orElse("zip")
            .describe("directory or zip").suggesting(context -> java.util.List.of("directory", "zip"));
    private static final Argument<String> REPLACE = Argument.word("replace").orElse("false")
            .describe("Replace an existing export");

    private final Supplier<Optional<JigsawStudioHost>> hosts;

    JigsawExportCommand(Supplier<Optional<JigsawStudioHost>> hosts) {
        this.hosts = hosts;
    }

    Command command() {
        return Command.named("export").describe("Export the clean active graph as a vanilla datapack")
                .argument(NAMESPACE).argument(OUTPUT).argument(FORMAT).argument(REPLACE)
                .runs(this::export);
    }

    private void export(CommandContext context) {
        Optional<StudioUse> begun = StudioUse.begin(hosts, context);
        if (begun.isEmpty()) {
            return;
        }
        StudioUse use = begun.get();
        ActiveContext active = use.requireActive();
        if (active == null) {
            return;
        }
        if (active.session().isDirty()) {
            use.error("Wait for the pending autosave or discard the edits before exporting the on-disk graph.");
            return;
        }
        VanillaJigsawExportFormat exportFormat;
        try {
            exportFormat = parseFormat(context.get(FORMAT));
        } catch (IllegalArgumentException exception) {
            use.error(exception.getMessage());
            return;
        }
        Path exportRoot = use.pack().outputFolder().toAbsolutePath().normalize();
        Path destination;
        try {
            destination = resolveExportDestination(exportRoot, context.get(OUTPUT), exportFormat);
        } catch (IllegalArgumentException exception) {
            use.error(exception.getMessage());
            return;
        }
        VanillaJigsawExportRequest request;
        try {
            request = VanillaJigsawExportRequest.builder(
                            VanillaJigsawExportSource.forData(
                                    active.request().source(), active.request().structureKey()), destination)
                    .namespace(context.get(NAMESPACE))
                    .resourcePath(active.request().structureKey())
                    .format(exportFormat)
                    .replaceExisting(StudioLifecycleCommands.flagValue(REPLACE.name(), context.get(REPLACE)))
                    .build();
        } catch (IllegalArgumentException exception) {
            use.error(exception.getMessage());
            return;
        }
        ToolPlayer targetPlayer = use.player();
        UUID playerId = targetPlayer.id();
        UUID requestId = active.request().requestId();
        JigsawStudioService studioService = use.studio();
        JigsawStudioService.ExportStart exportStart = studioService.tryBeginExport(requestId, playerId);
        if (exportStart != JigsawStudioService.ExportStart.STARTED) {
            use.error(exportStartError(exportStart));
            return;
        }
        if (!beginExport(playerId, destination)) {
            studioService.finishExport(requestId);
            use.error("A vanilla Jigsaw export for this player or output is already in progress.");
            return;
        }
        ExportLease lease = new ExportLease(() -> {
            finishExport(playerId, destination);
            studioService.finishExport(requestId);
        });
        ExportOperation operation = new ExportOperation(
                use,
                context.sender(),
                requestId,
                active.request().structureKey(),
                request,
                lease);
        use.say(TextColor.GRAY, "Exporting '" + operation.structureKey()
                + "' in the background to " + destination + "...");
        try {
            runExport(operation);
        } catch (RuntimeException exception) {
            operation.lease().release();
            use.pack().log().error("Failed to schedule vanilla Jigsaw export for '"
                    + operation.structureKey() + "'.", exception);
            use.error("Vanilla export could not be scheduled: " + exception.getMessage());
        }
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

    private static void runExport(ExportOperation operation) {
        VanillaJigsawExportResult result = null;
        Throwable failure = null;
        try {
            result = new VanillaJigsawDatapackExporter().export(operation.request());
        } catch (Throwable throwable) {
            failure = throwable;
            operation.use().pack().log().error("Vanilla Jigsaw export failed for '" + operation.structureKey() + "'.", throwable);
        } finally {
            operation.lease().release();
        }
        reportExport(operation, result, failure);
    }

    private static void reportExport(
            ExportOperation operation,
            VanillaJigsawExportResult result,
            Throwable failure
    ) {
        Sender sender = operation.sender();
        if (failure != null || result == null) {
            SenderText.send(sender, TextColor.RED, "Background vanilla export for '"
                    + operation.structureKey() + "' failed: "
                    + (failure == null ? "no result" : failure.getMessage()));
            return;
        }
        for (VanillaJigsawExportDiagnostic diagnostic : result.diagnostics()) {
            TextColor color = diagnostic.isBlocking() ? TextColor.RED : TextColor.YELLOW;
            SenderText.send(sender, color, diagnostic.code() + ": " + diagnostic.message());
        }
        ActiveContext current = StudioSessions.active(operation.use().registry(), operation.use().player().id());
        boolean sameSession = current != null
                && current.request().requestId().equals(operation.requestId());
        String contextLabel = sameSession ? "Vanilla export" : "Background vanilla export for '"
                + operation.structureKey() + "'";
        SenderText.send(sender, result.isSuccess() ? TextColor.GREEN : TextColor.RED,
                contextLabel + " " + result.status() + ": " + result.output());
    }

    private static VanillaJigsawExportFormat parseFormat(String value) {
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

    private record ExportOperation(
            StudioUse use,
            Sender sender,
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
