package art.arcane.iris.generation.runtime;

import art.arcane.iris.pack.loading.ResourceLoader;
import art.arcane.iris.spi.IrisServices;
import art.arcane.iris.platform.bukkit.plugin.VolmitSender;
import art.arcane.iris.generation.stream.CachedDoubleStream2D;
import art.arcane.iris.generation.stream.CachedStream2D;
import art.arcane.iris.generation.stream.CachedStream3D;
import art.arcane.volmlib.util.format.Form;

import java.util.List;

import art.arcane.iris.localization.ChatReport;
import art.arcane.iris.localization.IrisLanguage;
import art.arcane.iris.localization.BukkitCommandMessages;
import art.arcane.volmlib.util.localization.TextKey;
import art.arcane.iris.localization.RuntimeUiMessages;
import art.arcane.volmlib.util.localization.MessageArgument;
final class IrisEngineStatus {
    private IrisEngineStatus() {
    }

    static void send(VolmitSender sender, Snapshot snapshot) {
        CacheSummary caches = summarizeCaches();
        MaintenanceMetrics metrics = snapshot.metrics();

        sender.sendReport(section(BukkitCommandMessages.IRIS_ENGINE_STATUS_STATUS,
                IrisLanguage.text(BukkitCommandMessages.IRIS_ENGINE_STATUS_SERVICE, MessageArgument.trusted("value", status(snapshot.serviceRunning()))),
                IrisLanguage.text(BukkitCommandMessages.IRIS_ENGINE_STATUS_METRICS, MessageArgument.trusted("value", status(snapshot.metricsRunning()))),
                IrisLanguage.text(BukkitCommandMessages.IRIS_ENGINE_STATUS_MAINTENANCE_PERIOD, MessageArgument.untrusted("value", Form.duration(snapshot.maintenancePeriodMillis()))),
                IrisLanguage.text(BukkitCommandMessages.IRIS_ENGINE_STATUS_WORKER_PARALLELISM, MessageArgument.untrusted("value", snapshot.workerParallelism())),
                IrisLanguage.text(BukkitCommandMessages.IRIS_ENGINE_STATUS_ACTIVE_WORLD_TASKS, MessageArgument.untrusted("value", metrics.activeTasks()))));
        sender.sendReport(section(BukkitCommandMessages.IRIS_ENGINE_STATUS_TECTONIC_PLATES,
                IrisLanguage.text(BukkitCommandMessages.IRIS_ENGINE_STATUS_CONFIGURED_RETENTION, MessageArgument.untrusted("value", Form.duration(snapshot.retentionMillis()))),
                IrisLanguage.text(BukkitCommandMessages.IRIS_ENGINE_STATUS_HEAP_USAGE, MessageArgument.untrusted("value", Form.pc(snapshot.heapUsage()))),
                IrisLanguage.text(BukkitCommandMessages.IRIS_ENGINE_STATUS_RESIDENT, MessageArgument.untrusted("value", metrics.residentTectonicPlates())),
                IrisLanguage.text(BukkitCommandMessages.IRIS_ENGINE_STATUS_QUEUED, MessageArgument.untrusted("value", metrics.queuedTectonicPlates())),
                IrisLanguage.text(BukkitCommandMessages.IRIS_ENGINE_STATUS_AVERAGE_IDLE_DURATION, MessageArgument.untrusted("value", Form.duration(metrics.averageIdleDuration(), 2))),
                IrisLanguage.text(BukkitCommandMessages.IRIS_ENGINE_STATUS_MAX_IDLE_DURATION, MessageArgument.untrusted("value", Form.duration(metrics.maxIdleDuration(), 2))),
                IrisLanguage.text(BukkitCommandMessages.IRIS_ENGINE_STATUS_MIN_IDLE_DURATION, MessageArgument.untrusted("value", Form.duration(metrics.minIdleDuration(), 2)))));
        sender.sendReport(section(BukkitCommandMessages.IRIS_ENGINE_STATUS_CACHES,
                IrisLanguage.text(BukkitCommandMessages.IRIS_ENGINE_STATUS_RESOURCE, MessageArgument.untrusted("value", caches.sizes()[0]), MessageArgument.untrusted("value2", caches.counts()[0])),
                IrisLanguage.text(BukkitCommandMessages.IRIS_ENGINE_STATUS_2D_STREAM, MessageArgument.untrusted("value", caches.sizes()[1]), MessageArgument.untrusted("value2", caches.counts()[1])),
                IrisLanguage.text(BukkitCommandMessages.IRIS_ENGINE_STATUS_3D_STREAM, MessageArgument.untrusted("value", caches.sizes()[2]), MessageArgument.untrusted("value2", caches.counts()[2])),
                IrisLanguage.text(BukkitCommandMessages.IRIS_ENGINE_STATUS_OTHER, MessageArgument.untrusted("value", caches.sizes()[3]), MessageArgument.untrusted("value2", caches.counts()[3]))));
        sender.sendReport(section(BukkitCommandMessages.IRIS_ENGINE_STATUS_OTHER_2,
                IrisLanguage.text(BukkitCommandMessages.IRIS_ENGINE_STATUS_IRIS_WORLDS, MessageArgument.untrusted("value", metrics.worlds())),
                IrisLanguage.text(BukkitCommandMessages.IRIS_ENGINE_STATUS_LOADED_CHUNKS, MessageArgument.untrusted("value", metrics.loadedChunks()))));
    }

    private static ChatReport section(TextKey header, String... statLines) {
        ChatReport.Builder report = ChatReport.of(ChatReport.Tone.INFO, stripTrailingColon(IrisLanguage.text(header))).unlimited();
        for (String statLine : statLines) {
            report.styledItem(themedStat(statLine));
        }
        return report.build();
    }

    /** Status keys render as {@code - Label: value}; the label and value are recoloured to the Iris theme. */
    private static String themedStat(String statLine) {
        String cleaned = ChatReport.clean(statLine);
        String withoutDash = cleaned.startsWith("-") ? cleaned.substring(1).strip() : cleaned;
        int separator = withoutDash.indexOf(": ");
        if (separator < 0) {
            return withoutDash;
        }
        return ChatReport.label(withoutDash.substring(0, separator), withoutDash.substring(separator + 2));
    }

    private static String stripTrailingColon(String header) {
        String cleaned = ChatReport.clean(header);
        return cleaned.endsWith(":") ? cleaned.substring(0, cleaned.length() - 1) : cleaned;
    }

    private static String status(boolean running) {
        return IrisLanguage.text(running ? RuntimeUiMessages.STATUS_RUNNING : RuntimeUiMessages.STATUS_STOPPED);
    }

    private static CacheSummary summarizeCaches() {
        long[] sizes = new long[4];
        long[] counts = new long[4];
        PreservationSVC preservation = IrisServices.get(PreservationSVC.class);
        List<MeteredCache> caches = preservation == null ? List.of() : preservation.getCaches();

        for (MeteredCache cache : caches) {
            int type = switch (cache) {
                case ResourceLoader<?> ignored -> 0;
                case CachedStream2D<?> ignored -> 1;
                case CachedDoubleStream2D ignored -> 1;
                case CachedStream3D<?> ignored -> 2;
                default -> 3;
            };
            sizes[type] += cache.getSize();
            counts[type]++;
        }
        return new CacheSummary(sizes, counts);
    }

    record Snapshot(boolean serviceRunning,
                    boolean metricsRunning,
                    long maintenancePeriodMillis,
                    int workerParallelism,
                    long retentionMillis,
                    double heapUsage,
                    MaintenanceMetrics metrics) {
    }

    record MaintenanceMetrics(long residentTectonicPlates,
                              long queuedTectonicPlates,
                              long loadedChunks,
                              int worlds,
                              int activeTasks,
                              double averageIdleDuration,
                              double maxIdleDuration,
                              double minIdleDuration) {
        static final MaintenanceMetrics EMPTY = new MaintenanceMetrics(0L, 0L, 0L, 0, 0, 0D, 0D, 0D);
    }

    private record CacheSummary(long[] sizes, long[] counts) {
    }
}
