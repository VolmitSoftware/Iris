package art.arcane.iris.core.loader;

import art.arcane.iris.spi.IrisLogging;
import art.arcane.iris.spi.IrisPlatforms;
import art.arcane.iris.util.common.format.C;

import java.io.File;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicLong;
import java.util.stream.Collectors;

/**
 * Gathers the fields pack files hold that nothing reads and states them as one list per pack, shortly after the
 * first one turns up, so a pack loading hundreds of files gives one warning instead of one per field. A field of a
 * file is stated once per server start: the several loads of a pack while the server starts repeat nothing.
 */
public final class UnknownPackFields {
    private static final long GATHER_PERIOD_MS = 3000;
    private static final long LONGEST_PLATFORM_WAIT_MS = 120_000;
    private static final int MAX_STATED_FIELDS = 65536;
    private static final AtomicLong waitedForPlatformMs = new AtomicLong();
    private static final Set<String> STATED = ConcurrentHashMap.newKeySet();
    private static final Map<File, PendingPack> PENDING = new LinkedHashMap<>();
    private static final ScheduledExecutorService REPORTER = Executors.newSingleThreadScheduledExecutor(task -> {
        Thread thread = new Thread(task, "Iris unknown pack fields");
        thread.setDaemon(true);
        return thread;
    });

    private UnknownPackFields() {
    }

    /** The pack a field was found in: its folder, how logs name it, and whether it loads any module. */
    public record Pack(File folder, String label, boolean loadsModules) {
    }

    /**
     * A field of {@code resourceType} that nothing reads, in {@code file} (pack-relative) at {@code line}, or at no
     * line when the key was not found in the text; {@code suggestion} is the known field it most likely misspells.
     */
    public record Field(String resourceType, String key, String file, int line, String suggestion) {
        String location() {
            return line > 0 ? file + ":" + line : file;
        }
    }

    private record PendingPack(Pack pack, List<Field> fields) {
    }

    /** Queues the fields not stated yet; the first queued field of a quiet moment schedules the report. */
    public static void add(Pack pack, List<Field> fields) {
        List<Field> unstated = fields.stream().filter(field -> claim(pack, field)).toList();
        if (unstated.isEmpty()) {
            return;
        }
        synchronized (PENDING) {
            if (PENDING.isEmpty()) {
                REPORTER.schedule(UnknownPackFields::reportPending, GATHER_PERIOD_MS, TimeUnit.MILLISECONDS);
            }
            PENDING.computeIfAbsent(pack.folder(), folder -> new PendingPack(pack, new ArrayList<>())).fields().addAll(unstated);
        }
    }

    private static boolean claim(Pack pack, Field field) {
        if (STATED.size() >= MAX_STATED_FIELDS) {
            return false;
        }
        return STATED.add(pack.folder().getAbsolutePath() + "|" + field.file() + "|" + field.key());
    }

    /** The datapack compiler loads packs before Iris is enabled; the report waits for the platform's coloured log. */
    private static void reportPending() {
        if (!IrisPlatforms.isBound() && waitedForPlatformMs.addAndGet(GATHER_PERIOD_MS) < LONGEST_PLATFORM_WAIT_MS) {
            REPORTER.schedule(UnknownPackFields::reportPending, GATHER_PERIOD_MS, TimeUnit.MILLISECONDS);
            return;
        }
        List<PendingPack> due;
        synchronized (PENDING) {
            due = List.copyOf(PENDING.values());
            PENDING.clear();
        }
        due.forEach(pending -> IrisLogging.warn(describe(pending.pack(), pending.fields())));
    }

    /** One header naming the pack, then a line per resource type and field with how many files hold it and where. */
    static String describe(Pack pack, List<Field> fields) {
        Map<String, List<Field>> byTypeAndKey = fields.stream()
                .sorted(Comparator.comparing(Field::resourceType).thenComparing(Field::key).thenComparing(Field::file))
                .collect(Collectors.groupingBy(field -> field.resourceType() + " " + field.key(), LinkedHashMap::new, Collectors.toList()));
        StringBuilder out = new StringBuilder()
                .append(C.YELLOW).append(pack.label()).append(" has ").append(C.WHITE).append(byTypeAndKey.size())
                .append(C.YELLOW).append(byTypeAndKey.size() == 1 ? " field" : " fields").append(" nothing reads");
        if (!pack.loadsModules()) {
            out.append(", since it loads no modules");
        }
        out.append(':');
        byTypeAndKey.values().forEach(sameField -> out.append('\n').append(describeField(sameField)));
        return out.toString();
    }

    private static String describeField(List<Field> sameField) {
        Field first = sameField.getFirst();
        StringBuilder line = new StringBuilder()
                .append(C.GRAY).append("  ").append(first.resourceType()).append(' ')
                .append(C.WHITE).append(first.key())
                .append(C.GRAY).append(" in ").append(first.location());
        if (sameField.size() > 1) {
            line.append(" and ").append(sameField.size() - 1).append(sameField.size() == 2 ? " other file" : " other files");
        }
        if (first.suggestion() != null) {
            line.append(C.YELLOW).append(", did you mean ").append(C.WHITE).append(first.suggestion()).append(C.YELLOW).append('?');
        }
        return line.toString();
    }
}
