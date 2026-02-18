package art.arcane.iris.core.service;

import art.arcane.iris.Iris;
import art.arcane.iris.core.gui.PregeneratorJob;
import art.arcane.iris.core.pregenerator.LazyPregenerator;
import art.arcane.iris.core.pregenerator.TurboPregenerator;
import art.arcane.iris.util.common.plugin.IrisService;
import art.arcane.volmlib.integration.IntegrationHandshakeRequest;
import art.arcane.volmlib.integration.IntegrationHandshakeResponse;
import art.arcane.volmlib.integration.IntegrationHeartbeat;
import art.arcane.volmlib.integration.IntegrationMetricDescriptor;
import art.arcane.volmlib.integration.IntegrationMetricSample;
import art.arcane.volmlib.integration.IntegrationMetricSchema;
import art.arcane.volmlib.integration.IntegrationProtocolNegotiator;
import art.arcane.volmlib.integration.IntegrationProtocolVersion;
import art.arcane.volmlib.integration.IntegrationServiceContract;
import org.bukkit.Bukkit;
import org.bukkit.plugin.ServicePriority;

import java.util.HashMap;
import java.util.Map;
import java.util.Optional;
import java.util.Set;

public class IrisIntegrationService implements IrisService, IntegrationServiceContract {
    private static final Set<IntegrationProtocolVersion> SUPPORTED_PROTOCOLS = Set.of(
            new IntegrationProtocolVersion(1, 0),
            new IntegrationProtocolVersion(1, 1)
    );

    private static final Set<String> CAPABILITIES = Set.of(
            "handshake",
            "heartbeat",
            "metrics",
            "iris-engine-metrics"
    );

    private volatile IntegrationProtocolVersion negotiatedProtocol = new IntegrationProtocolVersion(1, 1);

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
        return IntegrationMetricSchema.descriptors().stream()
                .filter(descriptor -> descriptor.key().startsWith("iris."))
                .collect(java.util.stream.Collectors.toSet());
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

        negotiatedProtocol = negotiated.get();
        return new IntegrationHandshakeResponse(
                pluginId(),
                pluginVersion(),
                true,
                negotiatedProtocol,
                SUPPORTED_PROTOCOLS,
                CAPABILITIES,
                "ok",
                now
        );
    }

    @Override
    public IntegrationHeartbeat heartbeat() {
        long now = System.currentTimeMillis();
        return new IntegrationHeartbeat(negotiatedProtocol, true, now, "ok");
    }

    @Override
    public Map<String, IntegrationMetricSample> sampleMetrics(Set<String> metricKeys) {
        Set<String> requested = metricKeys == null || metricKeys.isEmpty()
                ? IntegrationMetricSchema.irisKeys()
                : metricKeys;
        long now = System.currentTimeMillis();
        Map<String, IntegrationMetricSample> out = new HashMap<>();

        for (String key : requested) {
            switch (key) {
                case IntegrationMetricSchema.IRIS_CHUNK_STREAM_MS -> out.put(key, sampleChunkStreamMetric(now));
                case IntegrationMetricSchema.IRIS_PREGEN_QUEUE -> out.put(key, samplePregenQueueMetric(now));
                case IntegrationMetricSchema.IRIS_BIOME_CACHE_HIT_RATE -> out.put(key, sampleBiomeCacheHitRateMetric(now));
                default -> out.put(key, IntegrationMetricSample.unavailable(
                        IntegrationMetricSchema.descriptor(key),
                        "unsupported-key",
                        now
                ));
            }
        }

        return out;
    }

    private IntegrationMetricSample sampleChunkStreamMetric(long now) {
        IntegrationMetricDescriptor descriptor = IntegrationMetricSchema.descriptor(IntegrationMetricSchema.IRIS_CHUNK_STREAM_MS);

        double chunksPerSecond = PregeneratorJob.chunksPerSecond();
        if (chunksPerSecond <= 0D) {
            chunksPerSecond = TurboPregenerator.chunksPerSecond();
        }
        if (chunksPerSecond <= 0D) {
            chunksPerSecond = LazyPregenerator.chunksPerSecond();
        }

        if (chunksPerSecond > 0D) {
            return IntegrationMetricSample.available(descriptor, 1000D / chunksPerSecond, now);
        }

        IrisEngineSVC engineService = Iris.service(IrisEngineSVC.class);
        if (engineService != null) {
            double idle = engineService.getAverageIdleDuration();
            if (idle > 0D && Double.isFinite(idle)) {
                return IntegrationMetricSample.available(descriptor, idle, now);
            }
        }

        return IntegrationMetricSample.available(descriptor, 0D, now);
    }

    private IntegrationMetricSample samplePregenQueueMetric(long now) {
        IntegrationMetricDescriptor descriptor = IntegrationMetricSchema.descriptor(IntegrationMetricSchema.IRIS_PREGEN_QUEUE);
        long totalQueue = 0L;
        boolean hasAnySource = false;

        long pregenRemaining = PregeneratorJob.chunksRemaining();
        if (pregenRemaining >= 0L) {
            totalQueue += pregenRemaining;
            hasAnySource = true;
        }

        long turboRemaining = TurboPregenerator.remainingChunks();
        if (turboRemaining >= 0L) {
            totalQueue += turboRemaining;
            hasAnySource = true;
        }

        long lazyRemaining = LazyPregenerator.remainingChunks();
        if (lazyRemaining >= 0L) {
            totalQueue += lazyRemaining;
            hasAnySource = true;
        }

        IrisEngineSVC engineService = Iris.service(IrisEngineSVC.class);
        if (engineService != null) {
            totalQueue += Math.max(0, engineService.getQueuedTectonicPlateCount());
            hasAnySource = true;
        }

        if (!hasAnySource) {
            return IntegrationMetricSample.unavailable(descriptor, "queue-not-available", now);
        }

        return IntegrationMetricSample.available(descriptor, totalQueue, now);
    }

    private IntegrationMetricSample sampleBiomeCacheHitRateMetric(long now) {
        IntegrationMetricDescriptor descriptor = IntegrationMetricSchema.descriptor(IntegrationMetricSchema.IRIS_BIOME_CACHE_HIT_RATE);
        IrisEngineSVC engineService = Iris.service(IrisEngineSVC.class);
        if (engineService == null) {
            return IntegrationMetricSample.unavailable(descriptor, "engine-service-unavailable", now);
        }

        double ratio = engineService.getBiomeCacheUsageRatio();
        if (!Double.isFinite(ratio)) {
            return IntegrationMetricSample.unavailable(descriptor, "biome-cache-ratio-invalid", now);
        }

        return IntegrationMetricSample.available(descriptor, Math.max(0D, Math.min(1D, ratio)), now);
    }
}
