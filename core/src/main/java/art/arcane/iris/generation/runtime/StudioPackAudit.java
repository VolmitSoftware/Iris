package art.arcane.iris.generation.runtime;

import art.arcane.iris.generation.concurrent.MultiBurst;
import art.arcane.iris.pack.PackValidationResult;
import art.arcane.iris.pack.PackValidator;
import art.arcane.iris.spi.IrisLogging;
import art.arcane.iris.world.history.GenerationHistory;

import java.nio.file.Path;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.function.Consumer;

/**
 * Runs the full pack validator against an activated Studio epoch after the hotload, so authoring mistakes the
 * activation gate does not cover are still reported without holding up the cutover.
 */
final class StudioPackAudit {
    private static final int LOGGED_WARNING_LIMIT = 3;

    private final IrisEngine engine;
    private final GenerationHistory history;
    private final String epochId;
    private final Consumer<String> blockingErrorReport;

    StudioPackAudit(IrisEngine engine, GenerationHistory history, String epochId, Consumer<String> blockingErrorReport) {
        this.engine = engine;
        this.history = history;
        this.epochId = epochId;
        this.blockingErrorReport = blockingErrorReport;
    }

    CompletableFuture<Void> runInBackground() {
        return MultiBurst.burst.completeValueAsync(this::validate)
                .thenAccept(this::report)
                .exceptionally(failure -> {
                    if (!engine.isClosed()) {
                        IrisLogging.reportError("Studio pack validation failed for '" + packKey() + "'.", failure);
                    }
                    return null;
                });
    }

    private PackValidationResult validate() {
        Path pack = history.paths().packRoot(epochId);
        return PackValidator.validate(pack.toFile());
    }

    void report(PackValidationResult result) {
        if (engine.isClosed() || !history.activeEpoch().epochId().equals(epochId)) {
            return;
        }
        reportWarnings(result.getWarnings());
        if (result.isLoadable()) {
            return;
        }
        String errors = String.join("; ", result.getBlockingErrors());
        IrisLogging.warn("Studio pack '%s' is active but has blocking validation errors: %s", packKey(), errors);
        blockingErrorReport.accept("Pack validation: " + errors);
    }

    private void reportWarnings(List<String> warnings) {
        if (warnings.isEmpty()) {
            return;
        }
        List<String> shown = warnings.subList(0, Math.min(LOGGED_WARNING_LIMIT, warnings.size()));
        IrisLogging.warn("Studio pack '%s' has %d validation warnings: %s", packKey(), warnings.size(), String.join("; ", shown));
    }

    private String packKey() {
        return engine.getPackSource().getFileName().toString();
    }
}
