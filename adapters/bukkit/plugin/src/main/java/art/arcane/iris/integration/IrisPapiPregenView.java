package art.arcane.iris.integration;

import art.arcane.iris.api.pregen.IrisPregenProgress;
import art.arcane.volmlib.util.bukkit.papi.PlaceholderValues;

public record IrisPapiPregenView(
        String world,
        String percent,
        String eta,
        String etaText,
        String chunks,
        String total,
        String chunksPerSecond,
        String paused) {
    private static final long MILLIS_PER_SECOND = 1_000L;
    private static final long SECONDS_PER_MINUTE = 60L;
    private static final long SECONDS_PER_HOUR = 3_600L;

    public static IrisPapiPregenView of(IrisPregenProgress progress) {
        if (progress == null) {
            return null;
        }

        return new IrisPapiPregenView(
                PlaceholderValues.text(progress.worldName()),
                PlaceholderValues.num(progress.percent()),
                PlaceholderValues.count(progress.etaMillis() / MILLIS_PER_SECOND),
                duration(progress.etaMillis()),
                PlaceholderValues.count(progress.generatedChunks()),
                PlaceholderValues.count(progress.totalChunks()),
                PlaceholderValues.num(progress.chunksPerSecond()),
                PlaceholderValues.bool(progress.paused()));
    }

    static String duration(long millis) {
        long totalSeconds = millis / MILLIS_PER_SECOND;
        long hours = totalSeconds / SECONDS_PER_HOUR;
        long minutes = (totalSeconds % SECONDS_PER_HOUR) / SECONDS_PER_MINUTE;
        long seconds = totalSeconds % SECONDS_PER_MINUTE;

        if (hours > 0L) {
            return hours + "h " + minutes + "m";
        }

        if (minutes > 0L) {
            return minutes + "m " + seconds + "s";
        }

        return seconds + "s";
    }
}
