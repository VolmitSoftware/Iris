package art.arcane.iris.integration;

import art.arcane.iris.generation.runtime.IrisEngineSVC;
import art.arcane.iris.generation.runtime.IrisTelemetrySnapshot;

import art.arcane.iris.Iris;
import art.arcane.iris.generation.runtime.EngineTelemetrySnapshot;
import art.arcane.iris.spi.IrisServices;
import art.arcane.iris.platform.bukkit.plugin.IrisService;
import art.arcane.volmlib.integration.IntegrationHandshakeRequest;
import art.arcane.volmlib.integration.IntegrationHandshakeResponse;
import art.arcane.volmlib.integration.IntegrationHeartbeat;
import art.arcane.volmlib.integration.IntegrationMetricDescriptor;
import art.arcane.volmlib.integration.IntegrationMetricGroup;
import art.arcane.volmlib.integration.IntegrationMetricSample;
import art.arcane.volmlib.integration.IntegrationMetricSchema;
import art.arcane.volmlib.integration.IntegrationProtocolNegotiator;
import art.arcane.volmlib.integration.IntegrationProtocolVersion;
import art.arcane.volmlib.integration.IntegrationServiceContract;
import org.bukkit.Bukkit;
import org.bukkit.plugin.ServicePriority;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.function.Supplier;

public class IrisIntegrationService implements IrisService, IntegrationServiceContract {
    private static final IntegrationProtocolVersion CURRENT_PROTOCOL = new IntegrationProtocolVersion(1, 2);
    private static final Set<IntegrationProtocolVersion> SUPPORTED_PROTOCOLS = Set.of(
            new IntegrationProtocolVersion(1, 0),
            new IntegrationProtocolVersion(1, 1),
            CURRENT_PROTOCOL
    );
    private static final Set<String> CAPABILITIES = Set.of(
            "handshake",
            "heartbeat",
            "metrics",
            "metric-groups",
            "iris-engine-metrics",
            "iris-world-metrics"
    );
    private static final Map<String, String> TIMING_KEYS = Map.ofEntries(
            Map.entry("total", IntegrationMetricSchema.IRIS_GENERATION_TOTAL_MS),
            Map.entry("updates", IntegrationMetricSchema.IRIS_GENERATION_UPDATES_MS),
            Map.entry("terrain", IntegrationMetricSchema.IRIS_GENERATION_TERRAIN_MS),
            Map.entry("biome", IntegrationMetricSchema.IRIS_GENERATION_BIOME_MS),
            Map.entry("post", IntegrationMetricSchema.IRIS_GENERATION_POST_MS),
            Map.entry("perfection", IntegrationMetricSchema.IRIS_GENERATION_PERFECTION_MS),
            Map.entry("decoration", IntegrationMetricSchema.IRIS_GENERATION_DECORATION_MS),
            Map.entry("cave", IntegrationMetricSchema.IRIS_GENERATION_CAVE_MS),
            Map.entry("deposit", IntegrationMetricSchema.IRIS_GENERATION_DEPOSIT_MS),
            Map.entry("carve.resolve", IntegrationMetricSchema.IRIS_GENERATION_CARVE_RESOLVE_MS),
            Map.entry("carve.apply", IntegrationMetricSchema.IRIS_GENERATION_CARVE_APPLY_MS),
            Map.entry("context.prefill", IntegrationMetricSchema.IRIS_GENERATION_CONTEXT_PREFILL_MS),
            Map.entry("pregen.wait.permit", IntegrationMetricSchema.IRIS_PREGEN_WAIT_PERMIT_MS),
            Map.entry("pregen.wait.adaptive", IntegrationMetricSchema.IRIS_PREGEN_WAIT_ADAPTIVE_MS)
    );

    private final Supplier<IrisTelemetrySnapshot> telemetrySupplier;

    public IrisIntegrationService() {
        this(IrisIntegrationService::currentTelemetry);
    }

    IrisIntegrationService(Supplier<IrisTelemetrySnapshot> telemetrySupplier) {
        this.telemetrySupplier = telemetrySupplier == null
                ? () -> IrisTelemetrySnapshot.EMPTY
                : telemetrySupplier;
    }

    @Override
    public void onEnable() {
        Bukkit.getServicesManager().register(IntegrationServiceContract.class, this, Iris.instance, ServicePriority.Normal);
        Iris.verbose("Integration provider registered for Iris");
    }

    @Override
    public void onDisable() {
        Bukkit.getServicesManager().unregister(IntegrationServiceContract.class, this);
    }

    @Override
    public String pluginId() {
        return "iris";
    }

    @Override
    public String pluginVersion() {
        return Iris.instance.getDescription().getVersion();
    }

    @Override
    public Set<IntegrationProtocolVersion> supportedProtocols() {
        return SUPPORTED_PROTOCOLS;
    }

    @Override
    public Set<String> capabilities() {
        return CAPABILITIES;
    }

    @Override
    public Set<IntegrationMetricDescriptor> metricDescriptors() {
        Set<IntegrationMetricDescriptor> descriptors = new LinkedHashSet<>();
        for (String key : IntegrationMetricSchema.irisKeys()) {
            descriptors.add(IntegrationMetricSchema.descriptor(key));
        }
        return Set.copyOf(descriptors);
    }

    @Override
    public IntegrationHandshakeResponse handshake(IntegrationHandshakeRequest request) {
        long now = System.currentTimeMillis();
        if (request == null) {
            return new IntegrationHandshakeResponse(
                    pluginId(),
                    pluginVersion(),
                    false,
                    null,
                    SUPPORTED_PROTOCOLS,
                    CAPABILITIES,
                    "missing request",
                    now
            );
        }

        Optional<IntegrationProtocolVersion> negotiated = IntegrationProtocolNegotiator.negotiate(
                SUPPORTED_PROTOCOLS,
                request.supportedProtocols()
        );
        if (negotiated.isEmpty()) {
            return new IntegrationHandshakeResponse(
                    pluginId(),
                    pluginVersion(),
                    false,
                    null,
                    SUPPORTED_PROTOCOLS,
                    CAPABILITIES,
                    "no-common-protocol",
                    now
            );
        }

        return new IntegrationHandshakeResponse(
                pluginId(),
                pluginVersion(),
                true,
                negotiated.get(),
                SUPPORTED_PROTOCOLS,
                CAPABILITIES,
                "ok",
                now
        );
    }

    @Override
    public IntegrationHeartbeat heartbeat() {
        return new IntegrationHeartbeat(CURRENT_PROTOCOL, true, System.currentTimeMillis(), "ok");
    }

    @Override
    public Map<String, IntegrationMetricSample> sampleMetrics(Set<String> metricKeys) {
        Set<String> requested = metricKeys == null || metricKeys.isEmpty()
                ? IntegrationMetricSchema.irisKeys()
                : Set.copyOf(metricKeys);
        IrisTelemetrySnapshot snapshot = safeSnapshot();
        long sampledAtMs = snapshot.sampledAtMs() > 0L
                ? snapshot.sampledAtMs()
                : System.currentTimeMillis();
        if (snapshot.sampledAtMs() <= 0L) {
            return unavailableMetrics(requested, "telemetry-not-ready", sampledAtMs);
        }

        Map<String, IntegrationMetricSample> available = globalSamples(snapshot);
        Map<String, IntegrationMetricSample> selected = new LinkedHashMap<>(requested.size());
        for (String key : requested) {
            IntegrationMetricSample sample = available.get(key);
            selected.put(key, sample == null
                    ? unavailable(key, "unsupported-key", sampledAtMs)
                    : sample);
        }
        return Map.copyOf(selected);
    }

    @Override
    public List<IntegrationMetricGroup> metricGroups() {
        IrisTelemetrySnapshot snapshot = safeSnapshot();
        if (snapshot.sampledAtMs() <= 0L || snapshot.worlds().isEmpty()) {
            return List.of();
        }

        List<EngineTelemetrySnapshot> worlds = new ArrayList<>(snapshot.worlds());
        worlds.sort(Comparator.comparing(EngineTelemetrySnapshot::worldIdentity));
        List<IntegrationMetricGroup> groups = new ArrayList<>(worlds.size());
        for (EngineTelemetrySnapshot world : worlds) {
            groups.add(new IntegrationMetricGroup(
                    "world",
                    world.worldIdentity(),
                    world.worldName(),
                    Map.of(
                            "plugin", "iris",
                            "dimension", world.dimensionKey()
                    ),
                    worldSamples(world, snapshot.pregenerator())
            ));
        }
        return List.copyOf(groups);
    }

    private Map<String, IntegrationMetricSample> globalSamples(IrisTelemetrySnapshot snapshot) {
        long sampledAtMs = snapshot.sampledAtMs();
        EngineTelemetrySnapshot.Aggregate aggregate = snapshot.aggregate();
        Map<String, IntegrationMetricSample> samples = new LinkedHashMap<>();
        put(samples, IntegrationMetricSchema.IRIS_WORLD_COUNT, aggregate.worldCount(), sampledAtMs);
        put(samples, IntegrationMetricSchema.IRIS_ENGINE_ACTIVE, aggregate.active(), sampledAtMs);
        put(samples, IntegrationMetricSchema.IRIS_ENGINE_CLOSING, aggregate.closing() + snapshot.closingEngines(), sampledAtMs);
        put(samples, IntegrationMetricSchema.IRIS_ENGINE_FAILED, aggregate.failed(), sampledAtMs);
        put(samples, IntegrationMetricSchema.IRIS_ENGINE_STUDIO, aggregate.studio(), sampledAtMs);
        put(samples, IntegrationMetricSchema.IRIS_ENGINE_PENDING_REGISTRATIONS, snapshot.pendingRegistrations(), sampledAtMs);
        put(samples, IntegrationMetricSchema.IRIS_LOADED_CHUNKS, aggregate.loadedChunks(), sampledAtMs);
        put(samples, IntegrationMetricSchema.IRIS_LOADED_ENTITIES, aggregate.loadedEntities(), sampledAtMs);
        put(samples, IntegrationMetricSchema.IRIS_ENTITY_SATURATION, aggregate.entitySaturationMax(), sampledAtMs);
        put(samples, IntegrationMetricSchema.IRIS_CHUNKS_GENERATED_SESSION, aggregate.generatedSession(), sampledAtMs);
        put(samples, IntegrationMetricSchema.IRIS_CHUNKS_GENERATED_TOTAL, aggregate.generatedTotal(), sampledAtMs);
        put(samples, IntegrationMetricSchema.IRIS_CHUNKS_PER_SECOND, aggregate.chunksPerSecond(), sampledAtMs);
        put(samples, IntegrationMetricSchema.IRIS_BLOCK_UPDATES_PER_SECOND, aggregate.blockUpdatesPerSecond(), sampledAtMs);
        put(samples, IntegrationMetricSchema.IRIS_ENGINE_PARALLELISM, aggregate.parallelism(), sampledAtMs);
        put(samples, IntegrationMetricSchema.IRIS_GENERATION_ACTIVE_LEASES, aggregate.activeGenerationLeases(), sampledAtMs);
        put(samples, IntegrationMetricSchema.IRIS_HOTLOADS_TOTAL, aggregate.hotloadsTotal(), sampledAtMs);
        put(samples, IntegrationMetricSchema.IRIS_MAINTENANCE_ACTIVE_TASKS, snapshot.maintenanceActiveTasks(), sampledAtMs);
        put(samples, IntegrationMetricSchema.IRIS_MAINTENANCE_WORKERS, snapshot.maintenanceWorkers(), sampledAtMs);
        put(samples, IntegrationMetricSchema.IRIS_MANTLE_RESIDENT_PLATES, aggregate.mantleResidentPlates(), sampledAtMs);
        put(samples, IntegrationMetricSchema.IRIS_MANTLE_QUEUED_PLATES, aggregate.mantleQueuedPlates(), sampledAtMs);
        put(samples, IntegrationMetricSchema.IRIS_MANTLE_IDLE_AVERAGE_MS, aggregate.mantleIdleAverageMs(), sampledAtMs);
        put(samples, IntegrationMetricSchema.IRIS_MANTLE_IDLE_MAX_MS, aggregate.mantleIdleMaxMs(), sampledAtMs);
        put(samples, IntegrationMetricSchema.IRIS_MANTLE_IDLE_MIN_MS, aggregate.mantleIdleMinMs(), sampledAtMs);
        put(samples, IntegrationMetricSchema.IRIS_MANTLE_HEAP_USAGE, snapshot.heapUsage(), sampledAtMs);
        put(samples, IntegrationMetricSchema.IRIS_MANTLE_RECLAIM_URGENCY, snapshot.reclaimUrgency(), sampledAtMs);
        putCacheSamples(samples, snapshot.caches(), sampledAtMs);
        putPregenSamples(samples, snapshot.pregenerator(), sampledAtMs);
        putTimingSamples(samples, aggregate.generationTimingMaximaMs(), sampledAtMs);
        return Map.copyOf(samples);
    }

    private Map<String, IntegrationMetricSample> worldSamples(
            EngineTelemetrySnapshot world,
            IrisTelemetrySnapshot.PregenSnapshot pregenerator
    ) {
        long sampledAtMs = world.sampledAtMs();
        Map<String, IntegrationMetricSample> samples = new LinkedHashMap<>();
        put(samples, IntegrationMetricSchema.IRIS_ENGINE_ACTIVE, world.active() ? 1 : 0, sampledAtMs);
        put(samples, IntegrationMetricSchema.IRIS_ENGINE_CLOSING, world.closing() ? 1 : 0, sampledAtMs);
        put(samples, IntegrationMetricSchema.IRIS_ENGINE_FAILED, world.failed() ? 1 : 0, sampledAtMs);
        put(samples, IntegrationMetricSchema.IRIS_ENGINE_STUDIO, world.studio() ? 1 : 0, sampledAtMs);
        put(samples, IntegrationMetricSchema.IRIS_LOADED_CHUNKS, world.loadedChunks(), sampledAtMs);
        put(samples, IntegrationMetricSchema.IRIS_LOADED_ENTITIES, world.loadedEntities(), sampledAtMs);
        put(samples, IntegrationMetricSchema.IRIS_ENTITY_SATURATION, world.entitySaturation(), sampledAtMs);
        put(samples, IntegrationMetricSchema.IRIS_CHUNKS_GENERATED_SESSION, world.generatedSession(), sampledAtMs);
        put(samples, IntegrationMetricSchema.IRIS_CHUNKS_GENERATED_TOTAL, world.generatedTotal(), sampledAtMs);
        put(samples, IntegrationMetricSchema.IRIS_CHUNKS_PER_SECOND, world.chunksPerSecond(), sampledAtMs);
        put(samples, IntegrationMetricSchema.IRIS_BLOCK_UPDATES_PER_SECOND, world.blockUpdatesPerSecond(), sampledAtMs);
        put(samples, IntegrationMetricSchema.IRIS_ENGINE_PARALLELISM, world.parallelism(), sampledAtMs);
        put(samples, IntegrationMetricSchema.IRIS_GENERATION_ACTIVE_LEASES, world.activeGenerationLeases(), sampledAtMs);
        put(samples, IntegrationMetricSchema.IRIS_HOTLOADS_TOTAL, world.hotloadsTotal(), sampledAtMs);
        put(samples, IntegrationMetricSchema.IRIS_MANTLE_RESIDENT_PLATES, world.mantleResidentPlates(), sampledAtMs);
        put(samples, IntegrationMetricSchema.IRIS_MANTLE_QUEUED_PLATES, world.mantleQueuedPlates(), sampledAtMs);
        put(samples, IntegrationMetricSchema.IRIS_MANTLE_IDLE_AVERAGE_MS, world.mantleIdleMs(), sampledAtMs);
        boolean targetsWorld = pregenerator.active()
                && world.worldIdentity().equals(pregenerator.worldIdentity());
        putPregenSamples(samples, targetsWorld ? pregenerator : IrisTelemetrySnapshot.PregenSnapshot.INACTIVE, sampledAtMs);
        putTimingSamples(samples, world.generationTimingsMs(), sampledAtMs);
        samples.keySet().retainAll(IntegrationMetricSchema.irisWorldKeys());
        return Map.copyOf(samples);
    }

    private void putCacheSamples(
            Map<String, IntegrationMetricSample> samples,
            IrisTelemetrySnapshot.CacheSnapshot caches,
            long sampledAtMs
    ) {
        putCacheBucket(samples, caches.total(), IntegrationMetricSchema.IRIS_CACHE_COUNT, IntegrationMetricSchema.IRIS_CACHE_ENTRIES, IntegrationMetricSchema.IRIS_CACHE_CAPACITY, IntegrationMetricSchema.IRIS_CACHE_USAGE, sampledAtMs);
        putCacheBucket(samples, caches.resource(), IntegrationMetricSchema.IRIS_CACHE_RESOURCE_COUNT, IntegrationMetricSchema.IRIS_CACHE_RESOURCE_ENTRIES, IntegrationMetricSchema.IRIS_CACHE_RESOURCE_CAPACITY, IntegrationMetricSchema.IRIS_CACHE_RESOURCE_USAGE, sampledAtMs);
        putCacheBucket(samples, caches.stream2d(), IntegrationMetricSchema.IRIS_CACHE_STREAM_2D_COUNT, IntegrationMetricSchema.IRIS_CACHE_STREAM_2D_ENTRIES, IntegrationMetricSchema.IRIS_CACHE_STREAM_2D_CAPACITY, IntegrationMetricSchema.IRIS_CACHE_STREAM_2D_USAGE, sampledAtMs);
        putCacheBucket(samples, caches.stream3d(), IntegrationMetricSchema.IRIS_CACHE_STREAM_3D_COUNT, IntegrationMetricSchema.IRIS_CACHE_STREAM_3D_ENTRIES, IntegrationMetricSchema.IRIS_CACHE_STREAM_3D_CAPACITY, IntegrationMetricSchema.IRIS_CACHE_STREAM_3D_USAGE, sampledAtMs);
        putCacheBucket(samples, caches.other(), IntegrationMetricSchema.IRIS_CACHE_OTHER_COUNT, IntegrationMetricSchema.IRIS_CACHE_OTHER_ENTRIES, IntegrationMetricSchema.IRIS_CACHE_OTHER_CAPACITY, IntegrationMetricSchema.IRIS_CACHE_OTHER_USAGE, sampledAtMs);
    }

    private void putCacheBucket(
            Map<String, IntegrationMetricSample> samples,
            IrisTelemetrySnapshot.CacheBucket bucket,
            String countKey,
            String entriesKey,
            String capacityKey,
            String usageKey,
            long sampledAtMs
    ) {
        put(samples, countKey, bucket.count(), sampledAtMs);
        put(samples, entriesKey, bucket.entries(), sampledAtMs);
        put(samples, capacityKey, bucket.capacity(), sampledAtMs);
        put(samples, usageKey, bucket.usage(), sampledAtMs);
    }

    private void putPregenSamples(
            Map<String, IntegrationMetricSample> samples,
            IrisTelemetrySnapshot.PregenSnapshot pregenerator,
            long sampledAtMs
    ) {
        put(samples, IntegrationMetricSchema.IRIS_PREGEN_ACTIVE, pregenerator.active() ? 1 : 0, sampledAtMs);
        if (!pregenerator.active()) {
            for (String key : Set.of(
                    IntegrationMetricSchema.IRIS_PREGEN_PAUSED,
                    IntegrationMetricSchema.IRIS_PREGEN_PROGRESS,
                    IntegrationMetricSchema.IRIS_PREGEN_GENERATED,
                    IntegrationMetricSchema.IRIS_PREGEN_TOTAL,
                    IntegrationMetricSchema.IRIS_PREGEN_QUEUE,
                    IntegrationMetricSchema.IRIS_PREGEN_THROUGHPUT,
                    IntegrationMetricSchema.IRIS_PREGEN_ETA_MS,
                    IntegrationMetricSchema.IRIS_PREGEN_ELAPSED_MS,
                    IntegrationMetricSchema.IRIS_PREGEN_FAILED)) {
                samples.put(key, unavailable(key, "pregen-inactive", sampledAtMs));
            }
            return;
        }

        put(samples, IntegrationMetricSchema.IRIS_PREGEN_PAUSED, pregenerator.paused() ? 1 : 0, sampledAtMs);
        put(samples, IntegrationMetricSchema.IRIS_PREGEN_PROGRESS, pregenerator.progressPercent(), sampledAtMs);
        put(samples, IntegrationMetricSchema.IRIS_PREGEN_GENERATED, pregenerator.generated(), sampledAtMs);
        put(samples, IntegrationMetricSchema.IRIS_PREGEN_TOTAL, pregenerator.total(), sampledAtMs);
        put(samples, IntegrationMetricSchema.IRIS_PREGEN_QUEUE, pregenerator.remaining(), sampledAtMs);
        put(samples, IntegrationMetricSchema.IRIS_PREGEN_THROUGHPUT, pregenerator.chunksPerSecond(), sampledAtMs);
        put(samples, IntegrationMetricSchema.IRIS_PREGEN_ETA_MS, pregenerator.etaMs(), sampledAtMs);
        put(samples, IntegrationMetricSchema.IRIS_PREGEN_ELAPSED_MS, pregenerator.elapsedMs(), sampledAtMs);
        put(samples, IntegrationMetricSchema.IRIS_PREGEN_FAILED, pregenerator.failed(), sampledAtMs);
    }

    private void putTimingSamples(
            Map<String, IntegrationMetricSample> samples,
            Map<String, Double> timings,
            long sampledAtMs
    ) {
        for (Map.Entry<String, String> entry : TIMING_KEYS.entrySet()) {
            Double value = timings.get(entry.getKey());
            samples.put(entry.getValue(), value == null
                    ? unavailable(entry.getValue(), "timing-not-available", sampledAtMs)
                    : available(entry.getValue(), value, sampledAtMs));
        }
    }

    private void put(Map<String, IntegrationMetricSample> samples, String key, double value, long sampledAtMs) {
        samples.put(key, available(key, value, sampledAtMs));
    }

    private IntegrationMetricSample available(String key, double value, long sampledAtMs) {
        return IntegrationMetricSample.available(IntegrationMetricSchema.descriptor(key), value, sampledAtMs);
    }

    private IntegrationMetricSample unavailable(String key, String reason, long sampledAtMs) {
        return IntegrationMetricSample.unavailable(IntegrationMetricSchema.descriptor(key), reason, sampledAtMs);
    }

    private Map<String, IntegrationMetricSample> unavailableMetrics(
            Set<String> keys,
            String reason,
            long sampledAtMs
    ) {
        Map<String, IntegrationMetricSample> samples = new LinkedHashMap<>(keys.size());
        for (String key : keys) {
            samples.put(key, unavailable(key, reason, sampledAtMs));
        }
        return Map.copyOf(samples);
    }

    private IrisTelemetrySnapshot safeSnapshot() {
        IrisTelemetrySnapshot snapshot = telemetrySupplier.get();
        return snapshot == null ? IrisTelemetrySnapshot.EMPTY : snapshot;
    }

    private static IrisTelemetrySnapshot currentTelemetry() {
        IrisEngineSVC service = IrisServices.get(IrisEngineSVC.class);
        return service == null ? IrisTelemetrySnapshot.EMPTY : service.telemetrySnapshot();
    }
}
