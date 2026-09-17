package art.arcane.iris.core.service;

import art.arcane.iris.api.terrain.IrisColumnField;
import art.arcane.iris.api.terrain.IrisColumnQuery;
import art.arcane.iris.api.terrain.IrisColumnSink;
import art.arcane.iris.api.terrain.IrisSurfaceKind;
import art.arcane.iris.api.terrain.IrisTerrainService;
import art.arcane.iris.api.terrain.IrisWorldInfo;
import art.arcane.iris.core.IrisSettings;
import art.arcane.iris.core.service.terrain.IrisApiFaultGuard;
import art.arcane.iris.core.service.terrain.IrisColumnWalk;
import art.arcane.iris.core.service.terrain.IrisSampleLimits;
import art.arcane.iris.core.service.terrain.IrisSurfaceClassifier;
import art.arcane.iris.core.service.terrain.IrisWorldInfoFactory;
import art.arcane.iris.engine.framework.Engine;
import art.arcane.iris.engine.object.world.biome.InferredType;
import art.arcane.iris.engine.object.world.biome.IrisBiome;
import art.arcane.iris.engine.object.world.region.IrisRegion;
import art.arcane.iris.engine.platform.PlatformChunkGenerator;
import art.arcane.iris.platform.bukkit.BukkitPlatform;
import art.arcane.iris.spi.IrisLogging;
import art.arcane.iris.spi.IrisServices;
import art.arcane.iris.util.common.plugin.IrisService;
import org.bukkit.Bukkit;
import org.bukkit.World;
import org.bukkit.generator.ChunkGenerator;
import org.bukkit.plugin.ServicePriority;

import java.util.EnumSet;
import java.util.Optional;
import java.util.OptionalInt;
import java.util.concurrent.atomic.AtomicBoolean;

public class IrisTerrainSVC implements IrisService, IrisTerrainService {
    private static final long FAULT_REPORT_INTERVAL_MILLIS = 60_000L;

    private final AtomicBoolean serviceEnabled = new AtomicBoolean();
    private final IrisApiFaultGuard queryFaults = new IrisApiFaultGuard(FAULT_REPORT_INTERVAL_MILLIS);
    private final IrisApiFaultGuard sinkFaults = new IrisApiFaultGuard(FAULT_REPORT_INTERVAL_MILLIS);

    @Override
    public void onEnable() {
        serviceEnabled.set(true);
        Bukkit.getServicesManager().register(
                IrisTerrainService.class,
                this,
                BukkitPlatform.plugin(),
                ServicePriority.Normal
        );
        IrisServices.register(IrisTerrainService.class, this);
    }

    @Override
    public void onDisable() {
        serviceEnabled.set(false);
        Bukkit.getServicesManager().unregister(IrisTerrainService.class, this);
        IrisServices.remove(IrisTerrainService.class);
    }

    @Override
    public boolean isIrisWorld(World world) {
        return generatorOf(world) != null;
    }

    @Override
    public Optional<IrisWorldInfo> worldInfo(World world) {
        PlatformChunkGenerator generator = liveGeneratorOf(world);
        if (generator == null) {
            return Optional.empty();
        }

        try {
            return Optional.ofNullable(IrisWorldInfoFactory.from(generator));
        } catch (Throwable error) {
            reportQueryFault("worldInfo", world, error);
            return Optional.empty();
        }
    }

    @Override
    public OptionalInt surfaceHeight(World world, int blockX, int blockZ) {
        Engine engine = liveEngineOf(world);
        if (engine == null) {
            return OptionalInt.empty();
        }

        try {
            return OptionalInt.of(engine.getHeight(blockX, blockZ) + engine.getMinHeight());
        } catch (Throwable error) {
            reportQueryFault("surfaceHeight", world, error);
            return OptionalInt.empty();
        }
    }

    @Override
    public IrisSurfaceKind surfaceKind(World world, int blockX, int blockZ) {
        Engine engine = liveEngineOf(world);
        if (engine == null) {
            return IrisSurfaceKind.UNKNOWN;
        }

        try {
            int surface = engine.getHeight(blockX, blockZ);
            int fluid = engine.getDimension().getFluidHeight();
            InferredType inferredType = null;
            if (IrisSurfaceClassifier.requiresSurfaceBiome(surface, fluid)) {
                IrisBiome biome = engine.getSurfaceBiome(blockX, blockZ);
                inferredType = biome == null ? null : biome.getInferredType();
            }
            return IrisSurfaceClassifier.classify(surface, fluid, inferredType);
        } catch (Throwable error) {
            reportQueryFault("surfaceKind", world, error);
            return IrisSurfaceKind.UNKNOWN;
        }
    }

    @Override
    public Optional<String> surfaceBiomeKey(World world, int blockX, int blockZ) {
        Engine engine = liveEngineOf(world);
        if (engine == null) {
            return Optional.empty();
        }

        try {
            return key(engine.getSurfaceBiome(blockX, blockZ));
        } catch (Throwable error) {
            reportQueryFault("surfaceBiomeKey", world, error);
            return Optional.empty();
        }
    }

    @Override
    public Optional<String> surfaceBiomeName(World world, int blockX, int blockZ) {
        Engine engine = liveEngineOf(world);
        if (engine == null) {
            return Optional.empty();
        }

        try {
            return name(engine.getSurfaceBiome(blockX, blockZ));
        } catch (Throwable error) {
            reportQueryFault("surfaceBiomeName", world, error);
            return Optional.empty();
        }
    }

    @Override
    public Optional<String> biomeKey(World world, int blockX, int blockY, int blockZ) {
        Engine engine = liveEngineOf(world);
        if (engine == null) {
            return Optional.empty();
        }

        try {
            return key(engine.getBiome(blockX, blockY - engine.getMinHeight(), blockZ));
        } catch (Throwable error) {
            reportQueryFault("biomeKey", world, error);
            return Optional.empty();
        }
    }

    @Override
    public Optional<String> regionKey(World world, int blockX, int blockZ) {
        Engine engine = liveEngineOf(world);
        if (engine == null) {
            return Optional.empty();
        }

        try {
            IrisRegion region = engine.getRegion(blockX, blockZ);
            String loadKey = region == null ? null : region.getLoadKey();
            return loadKey == null || loadKey.isEmpty() ? Optional.empty() : Optional.of(loadKey);
        } catch (Throwable error) {
            reportQueryFault("regionKey", world, error);
            return Optional.empty();
        }
    }

    @Override
    public Optional<String> regionName(World world, int blockX, int blockZ) {
        Engine engine = liveEngineOf(world);
        if (engine == null) {
            return Optional.empty();
        }

        try {
            IrisRegion region = engine.getRegion(blockX, blockZ);
            String name = region == null ? null : region.getName();
            return name == null || name.isEmpty() ? Optional.empty() : Optional.of(name);
        } catch (Throwable error) {
            reportQueryFault("regionName", world, error);
            return Optional.empty();
        }
    }

    @Override
    public int maxSampleColumns() {
        return IrisSampleLimits.maxColumns(noiseCacheChunks());
    }

    @Override
    public int maxSampleChunks() {
        return IrisSampleLimits.maxChunks(noiseCacheChunks());
    }

    @Override
    public boolean sampleColumns(World world, IrisColumnQuery query, IrisColumnSink sink) {
        if (query == null || sink == null) {
            return false;
        }

        Engine engine = liveEngineOf(world);
        if (engine == null) {
            return false;
        }

        int noiseCacheChunks = noiseCacheChunks();
        if (!IrisSampleLimits.withinLimits(
                query,
                IrisSampleLimits.maxColumns(noiseCacheChunks),
                IrisSampleLimits.maxChunks(noiseCacheChunks))) {
            return false;
        }

        EnumSet<IrisColumnField> fields = query.fields();
        boolean wantHeight = fields.contains(IrisColumnField.SURFACE_HEIGHT);
        boolean wantKind = fields.contains(IrisColumnField.SURFACE_KIND);
        boolean wantBiome = fields.contains(IrisColumnField.BIOME_KEY);

        try {
            int minHeight = engine.getMinHeight();
            int fluid = engine.getDimension().getFluidHeight();
            long visited = IrisColumnWalk.walk(query, (int blockX, int blockZ) -> {
                if (engine.isClosed()) {
                    return false;
                }

                int surface = wantHeight || wantKind ? engine.getHeight(blockX, blockZ) : 0;
                boolean needsBiome = wantBiome
                        || (wantKind && IrisSurfaceClassifier.requiresSurfaceBiome(surface, fluid));
                IrisBiome biome = needsBiome ? engine.getSurfaceBiome(blockX, blockZ) : null;
                IrisSurfaceKind kind = wantKind
                        ? IrisSurfaceClassifier.classify(surface, fluid, biome == null ? null : biome.getInferredType())
                        : IrisSurfaceKind.UNKNOWN;
                String biomeKey = wantBiome && biome != null ? biome.getLoadKey() : null;
                sink.accept(blockX, blockZ, wantHeight ? surface + minHeight : -1, kind, biomeKey);
                return true;
            });
            return visited == query.columnCount();
        } catch (Throwable error) {
            reportSinkFault(world, error);
            return false;
        }
    }

    private static Optional<String> key(IrisBiome biome) {
        String loadKey = biome == null ? null : biome.getLoadKey();
        return loadKey == null || loadKey.isEmpty() ? Optional.empty() : Optional.of(loadKey);
    }

    private static Optional<String> name(IrisBiome biome) {
        String name = biome == null ? null : biome.getName();
        return name == null || name.isEmpty() ? Optional.empty() : Optional.of(name);
    }

    private static int noiseCacheChunks() {
        return IrisSettings.get().getPerformance().getNoiseCacheSize();
    }

    static boolean answerable(boolean serviceEnabled, boolean worldPresent) {
        return serviceEnabled && worldPresent;
    }

    private PlatformChunkGenerator generatorOf(World world) {
        if (!answerable(serviceEnabled.get(), world != null)) {
            return null;
        }

        ChunkGenerator generator = world.getGenerator();
        return generator instanceof PlatformChunkGenerator platform ? platform : null;
    }

    private PlatformChunkGenerator liveGeneratorOf(World world) {
        PlatformChunkGenerator generator = generatorOf(world);
        return generator == null || generator.isClosing() ? null : generator;
    }

    private static Engine engineOf(PlatformChunkGenerator generator) {
        if (generator == null) {
            return null;
        }

        Engine engine = generator.getEngine();
        return engine == null || engine.isClosed() ? null : engine;
    }

    private Engine liveEngineOf(World world) {
        return engineOf(liveGeneratorOf(world));
    }

    private void reportQueryFault(String operation, World world, Throwable error) {
        if (queryFaults.record(System.currentTimeMillis())) {
            IrisLogging.reportError("Iris terrain API query \"" + operation + "\" failed for world \""
                    + (world == null ? "null" : world.getName()) + "\" (" + queryFaults.faults()
                    + " terrain API query faults so far).", error);
        }
    }

    private void reportSinkFault(World world, Throwable error) {
        if (sinkFaults.record(System.currentTimeMillis())) {
            IrisLogging.reportError("Iris terrain API column sample failed for world \""
                    + (world == null ? "null" : world.getName()) + "\" (" + sinkFaults.faults()
                    + " terrain API sample faults so far). A third-party sink that throws is treated as a refusal.", error);
        }
    }
}
