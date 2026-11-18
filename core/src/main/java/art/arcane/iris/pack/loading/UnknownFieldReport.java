package art.arcane.iris.pack.loading;

import art.arcane.iris.localization.C;
import art.arcane.iris.spi.IrisLogging;
import art.arcane.iris.world.history.GenerationHistoryPaths;

import java.io.File;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeMap;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.ScheduledFuture;
import java.util.concurrent.TimeUnit;

final class UnknownFieldReport {
    private static final long QUIET_PERIOD_BEFORE_FLUSH_MS = 1_500L;
    private static final String FLUSH_THREAD_NAME = "Iris Unknown Field Report";
    private static final Set<String> REPORTED = ConcurrentHashMap.newKeySet();
    private static final List<UnknownField> PENDING = new ArrayList<>();
    private static final ScheduledExecutorService FLUSHER = Executors.newSingleThreadScheduledExecutor(runnable -> {
        Thread thread = new Thread(runnable, FLUSH_THREAD_NAME);
        thread.setDaemon(true);
        return thread;
    });
    private static ScheduledFuture<?> scheduledFlush;

    private UnknownFieldReport() {
    }

    static void record(File dataFolder, File file, String resourceTypeName, String field, int line, String suggestion) {
        String pack = packLabel(dataFolder);
        String location = relativeLocation(dataFolder, file, line);
        UnknownField unknown = new UnknownField(pack, resourceTypeName, field, location, suggestion);
        if (!REPORTED.add(unknown.identity())) {
            return;
        }
        synchronized (PENDING) {
            PENDING.add(unknown);
            rescheduleFlush();
        }
    }

    static void flush() {
        drainReportLines().forEach(line -> IrisLogging.warn("%s", line));
    }

    static List<String> drainReportLines() {
        List<UnknownField> batch;
        synchronized (PENDING) {
            batch = new ArrayList<>(PENDING);
            PENDING.clear();
        }
        List<String> lines = new ArrayList<>();
        groupByPack(batch).forEach((pack, fields) -> appendPackLines(lines, pack, fields));
        return lines;
    }

    static void resetForTests() {
        synchronized (PENDING) {
            PENDING.clear();
        }
        REPORTED.clear();
    }

    private static void rescheduleFlush() {
        if (scheduledFlush != null) {
            scheduledFlush.cancel(false);
        }
        scheduledFlush = FLUSHER.schedule(UnknownFieldReport::flush, QUIET_PERIOD_BEFORE_FLUSH_MS, TimeUnit.MILLISECONDS);
    }

    private static Map<String, Map<String, List<UnknownField>>> groupByPack(List<UnknownField> batch) {
        Map<String, Map<String, List<UnknownField>>> packs = new TreeMap<>();
        for (UnknownField unknown : batch) {
            packs.computeIfAbsent(unknown.pack(), key -> new TreeMap<>())
                    .computeIfAbsent(unknown.typeAndField(), key -> new ArrayList<>())
                    .add(unknown);
        }
        return packs;
    }

    private static void appendPackLines(List<String> lines, String pack, Map<String, List<UnknownField>> fields) {
        lines.add(pack + ": unknown fields, ignored");
        int labelWidth = fields.keySet().stream().mapToInt(String::length).max().orElse(0);
        fields.forEach((typeAndField, occurrences) -> lines.add(describeField(typeAndField, occurrences, labelWidth)));
    }

    private static String describeField(String typeAndField, List<UnknownField> occurrences, int labelWidth) {
        UnknownField first = occurrences.getFirst();
        int count = occurrences.size();
        StringBuilder out = new StringBuilder("  ")
                .append(C.WHITE).append(padRight(typeAndField, labelWidth)).append(C.YELLOW)
                .append("  ").append(count).append(count == 1 ? " file " : " files")
                .append("  ").append(count == 1 ? "" : "e.g. ").append(first.location());
        if (first.suggestion() != null) {
            out.append(" (did you mean ").append(C.WHITE).append('"').append(first.suggestion()).append('"').append(C.YELLOW).append("?)");
        }
        return out.toString();
    }

    private static String padRight(String text, int width) {
        return text + " ".repeat(Math.max(0, width - text.length()));
    }

    private static String packLabel(File dataFolder) {
        if (dataFolder == null) {
            return "pack";
        }
        Path epochDimension = epochDimensionRoot(dataFolder.toPath().toAbsolutePath().normalize());
        return epochDimension != null ? epochDimension.getFileName().toString() : dataFolder.getName();
    }

    private static Path epochDimensionRoot(Path dataFolder) {
        Path epochs = ancestor(dataFolder, 2);
        if (!GenerationHistoryPaths.LEGACY_PACK_DIRECTORY_NAME.equals(name(dataFolder))
                || !GenerationHistoryPaths.EPOCHS_DIRECTORY_NAME.equals(name(epochs))) {
            return null;
        }
        return ancestor(epochs, 3);
    }

    private static Path ancestor(Path path, int levels) {
        Path current = path;
        for (int i = 0; i < levels && current != null; i++) {
            current = current.getParent();
        }
        return current;
    }

    private static String name(Path path) {
        return path == null || path.getFileName() == null ? null : path.getFileName().toString();
    }

    private static String relativeLocation(File dataFolder, File file, int line) {
        String path = file.getPath();
        if (dataFolder != null) {
            Path root = dataFolder.toPath().toAbsolutePath().normalize();
            Path target = file.toPath().toAbsolutePath().normalize();
            if (target.startsWith(root)) {
                path = root.relativize(target).toString().replace(File.separatorChar, '/');
            }
        }
        return line > 0 ? path + ":" + line : path;
    }

    private record UnknownField(String pack, String resourceTypeName, String field, String location, String suggestion) {
        String typeAndField() {
            return resourceTypeName + " \"" + field + "\"";
        }

        String identity() {
            return pack + '\u0000' + typeAndField() + '\u0000' + location;
        }
    }
}
