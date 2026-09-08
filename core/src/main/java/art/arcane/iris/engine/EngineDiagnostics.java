package art.arcane.iris.engine;

import art.arcane.iris.core.compat.PackCompatReport;
import art.arcane.iris.core.pack.PackValidationResult;
import art.arcane.iris.core.pack.PackValidationRegistry;
import art.arcane.iris.spi.IrisLogging;
import art.arcane.iris.spi.IrisPlatforms;

import java.io.File;
import java.util.Locale;
import java.util.concurrent.TimeUnit;

final class EngineDiagnostics {
    private final IrisEngine engine;

    EngineDiagnostics(IrisEngine engine) {
        this.engine = engine;
    }

    /**
     * One line per engine naming what this pack cannot generate on the running Minecraft version. The published
     * validation result is complete (validation force-loads the whole pack), so it is preferred; an unvalidated pack
     * falls back to what this engine has gated while building its runtime (dimension, regions, biomes). Never throws:
     * a report failure must not take an otherwise working world down with it.
     */
    void logPackCompatSummary() {
        try {
            File folder = engine.getData().getDataFolder();
            // Runtime data comes from an immutable snapshot; the dimension key identifies the pack.
            String pack = engine.getDimension().getLoadKey();
            String world = engine.getTarget().getWorld().name();
            String version = IrisPlatforms.isBound() ? IrisPlatforms.get().minecraftVersion() : null;
            PackValidationResult published = PackValidationRegistry.get(folder.toPath());
            if (published == null) {
                published = PackValidationRegistry.get(folder.getName());
            }
            PackCompatReport report = published != null && !published.getCompatFindings().isEmpty()
                    ? PackCompatReport.of(published.getCompatFindings())
                    : engine.getData().getCompatReport();
            if (!report.isEmpty()) {
                IrisLogging.info("World '" + world + "' pack '" + pack + "' " + report.summaryLine(version));
            }
            if (engine.getDimension().isCompatExcluded()) {
                IrisLogging.error("World '" + world + "' pack '" + pack + "' cannot generate on Minecraft "
                        + (version == null || version.isBlank() ? "unknown" : version));
            }
        } catch (Throwable e) {
            IrisLogging.reportError("Failed to report pack compatibility for an Iris world;"
                    + " the world keeps generating and the summary is missing.", e);
        }
    }

    void logStudioInitializationPhase(String phase, long startedAtNanos, boolean skipped) {
        if (!engine.isStudio()) {
            return;
        }
        IrisLogging.info("[Studio engine timing] world=%s kind=%s phase=%s duration=%dms skipped=%s",
                engine.getTarget().getWorld().name(),
                engine.getInitializationMode().name().toLowerCase(Locale.ROOT),
                phase,
                TimeUnit.NANOSECONDS.toMillis(System.nanoTime() - startedAtNanos),
                Boolean.toString(skipped));
    }
}
