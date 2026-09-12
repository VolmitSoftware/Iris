package art.arcane.iris.generation.runtime;

import art.arcane.iris.pack.loading.ResourceLoader;
import art.arcane.iris.spi.IrisServices;
import art.arcane.iris.platform.bukkit.plugin.VolmitSender;
import art.arcane.iris.generation.stream.CachedDoubleStream2D;
import art.arcane.iris.generation.stream.CachedStream2D;
import art.arcane.iris.generation.stream.CachedStream3D;
import art.arcane.volmlib.util.format.Form;

import java.util.List;

import art.arcane.iris.localization.IrisLanguage;
import art.arcane.iris.localization.BukkitCommandMessages;
import art.arcane.iris.localization.RuntimeUiMessages;
import art.arcane.volmlib.util.localization.MessageArgument;
final class IrisEngineStatus {
    private IrisEngineStatus() {
    }

    static void send(VolmitSender sender, Snapshot snapshot) {
        CacheSummary caches = summarizeCaches();
        MaintenanceMetrics metrics = snapshot.metrics();

        sender.sendMessage(IrisLanguage.text(BukkitCommandMessages.IRIS_ENGINE_STATUS_MESSAGE));
        sender.sendMessage(IrisLanguage.text(BukkitCommandMessages.IRIS_ENGINE_STATUS_STATUS));
        sender.sendMessage(IrisLanguage.text(
                BukkitCommandMessages.IRIS_ENGINE_STATUS_SERVICE,
                MessageArgument.trusted("value", status(snapshot.serviceRunning()))
        ));
        sender.sendMessage(IrisLanguage.text(
                BukkitCommandMessages.IRIS_ENGINE_STATUS_METRICS,
                MessageArgument.trusted("value", status(snapshot.metricsRunning()))
        ));
        sender.sendMessage(IrisLanguage.text(BukkitCommandMessages.IRIS_ENGINE_STATUS_MAINTENANCE_PERIOD, MessageArgument.untrusted("value", Form.duration(snapshot.maintenancePeriodMillis()))));
        sender.sendMessage(IrisLanguage.text(BukkitCommandMessages.IRIS_ENGINE_STATUS_WORKER_PARALLELISM, MessageArgument.untrusted("value", snapshot.workerParallelism())));
        sender.sendMessage(IrisLanguage.text(BukkitCommandMessages.IRIS_ENGINE_STATUS_ACTIVE_WORLD_TASKS, MessageArgument.untrusted("value", metrics.activeTasks())));
        sender.sendMessage(IrisLanguage.text(BukkitCommandMessages.IRIS_ENGINE_STATUS_TECTONIC_PLATES));
        sender.sendMessage(IrisLanguage.text(BukkitCommandMessages.IRIS_ENGINE_STATUS_CONFIGURED_RETENTION, MessageArgument.untrusted("value", Form.duration(snapshot.retentionMillis()))));
        sender.sendMessage(IrisLanguage.text(BukkitCommandMessages.IRIS_ENGINE_STATUS_HEAP_USAGE, MessageArgument.untrusted("value", Form.pc(snapshot.heapUsage()))));
        sender.sendMessage(IrisLanguage.text(BukkitCommandMessages.IRIS_ENGINE_STATUS_RESIDENT, MessageArgument.untrusted("value", metrics.residentTectonicPlates())));
        sender.sendMessage(IrisLanguage.text(BukkitCommandMessages.IRIS_ENGINE_STATUS_QUEUED, MessageArgument.untrusted("value", metrics.queuedTectonicPlates())));
        sender.sendMessage(IrisLanguage.text(BukkitCommandMessages.IRIS_ENGINE_STATUS_AVERAGE_IDLE_DURATION, MessageArgument.untrusted("value", Form.duration(metrics.averageIdleDuration(), 2))));
        sender.sendMessage(IrisLanguage.text(BukkitCommandMessages.IRIS_ENGINE_STATUS_MAX_IDLE_DURATION, MessageArgument.untrusted("value", Form.duration(metrics.maxIdleDuration(), 2))));
        sender.sendMessage(IrisLanguage.text(BukkitCommandMessages.IRIS_ENGINE_STATUS_MIN_IDLE_DURATION, MessageArgument.untrusted("value", Form.duration(metrics.minIdleDuration(), 2))));
        sender.sendMessage(IrisLanguage.text(BukkitCommandMessages.IRIS_ENGINE_STATUS_CACHES));
        sender.sendMessage(IrisLanguage.text(BukkitCommandMessages.IRIS_ENGINE_STATUS_RESOURCE, MessageArgument.untrusted("value", caches.sizes()[0]), MessageArgument.untrusted("value2", caches.counts()[0])));
        sender.sendMessage(IrisLanguage.text(BukkitCommandMessages.IRIS_ENGINE_STATUS_2D_STREAM, MessageArgument.untrusted("value", caches.sizes()[1]), MessageArgument.untrusted("value2", caches.counts()[1])));
        sender.sendMessage(IrisLanguage.text(BukkitCommandMessages.IRIS_ENGINE_STATUS_3D_STREAM, MessageArgument.untrusted("value", caches.sizes()[2]), MessageArgument.untrusted("value2", caches.counts()[2])));
        sender.sendMessage(IrisLanguage.text(BukkitCommandMessages.IRIS_ENGINE_STATUS_OTHER, MessageArgument.untrusted("value", caches.sizes()[3]), MessageArgument.untrusted("value2", caches.counts()[3])));
        sender.sendMessage(IrisLanguage.text(BukkitCommandMessages.IRIS_ENGINE_STATUS_OTHER_2));
        sender.sendMessage(IrisLanguage.text(BukkitCommandMessages.IRIS_ENGINE_STATUS_IRIS_WORLDS, MessageArgument.untrusted("value", metrics.worlds())));
        sender.sendMessage(IrisLanguage.text(BukkitCommandMessages.IRIS_ENGINE_STATUS_LOADED_CHUNKS, MessageArgument.untrusted("value", metrics.loadedChunks())));
        sender.sendMessage(IrisLanguage.text(BukkitCommandMessages.IRIS_ENGINE_STATUS_MESSAGE_2));
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
