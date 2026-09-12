package art.arcane.iris.platform.bukkit.api;

import art.arcane.iris.api.terrain.IrisColumnField;
import art.arcane.iris.api.terrain.IrisColumnQuery;
import art.arcane.iris.api.terrain.IrisColumnSample;
import art.arcane.iris.api.terrain.IrisColumnSink;
import art.arcane.iris.api.terrain.IrisRiverState;
import art.arcane.iris.api.terrain.IrisSurfaceKind;
import art.arcane.iris.api.terrain.IrisTerrainService;
import art.arcane.iris.api.terrain.IrisWorldInfo;
import art.arcane.iris.configuration.IrisSettings;
import art.arcane.iris.platform.bukkit.terrain.IrisApiFaultGuard;
import art.arcane.iris.platform.bukkit.terrain.IrisColumnWalk;
import art.arcane.iris.platform.bukkit.terrain.IrisSampleLimits;
import art.arcane.iris.platform.bukkit.terrain.IrisSurfaceClassifier;
import art.arcane.iris.platform.bukkit.terrain.IrisWorldInfoFactory;
import art.arcane.iris.generation.runtime.Engine;
import art.arcane.iris.generation.hydrology.HydrologyColumnLayer;
import art.arcane.iris.generation.hydrology.HydrologyColumnSample;
import art.arcane.iris.generation.terrain.InferredType;
import art.arcane.iris.generation.biome.IrisBiome;
import art.arcane.iris.generation.terrain.IrisRegion;
import art.arcane.iris.platform.generation.PlatformChunkGenerator;
import art.arcane.iris.platform.bukkit.BukkitPlatform;
import art.arcane.iris.spi.IrisLogging;
import art.arcane.iris.spi.IrisServices;
import art.arcane.iris.platform.bukkit.plugin.IrisService;
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
            HydrologyColumnSample hydrology = hydrologySample(engine, blockX, blockZ);
            int fluid = (int) Math.round(
                    engine.getComplex().getRiverWaterSurfaceStream().get(blockX, blockZ));
            InferredType inferredType = null;
            if (IrisSurfaceClassifier.requiresSurfaceBiome(surface, fluid)) {
                IrisBiome biome = engine.getSurfaceBiome(blockX, blockZ);
                inferredType = biome == null ? null : biome.getInferredType();
            }
            return IrisSurfaceClassifier.classify(surface, fluid, inferredType, hydrology);
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
        boolean wantNaturalHeight = fields.contains(IrisColumnField.NATURAL_HEIGHT);
        boolean wantKind = fields.contains(IrisColumnField.SURFACE_KIND);
        boolean wantBiome = fields.contains(IrisColumnField.BIOME_KEY);
        boolean wantRiverState = fields.contains(IrisColumnField.RIVER_STATE);
        boolean wantRiverDistance = fields.contains(IrisColumnField.RIVER_DISTANCE);
        boolean wantRiverFlow = fields.contains(IrisColumnField.RIVER_FLOW);
        boolean wantRiverWaterSurface = fields.contains(IrisColumnField.RIVER_WATER_SURFACE_Y);
        boolean wantRiver = wantKind || wantRiverState || wantRiverDistance || wantRiverFlow
                || wantRiverWaterSurface;

        try {
            int minHeight = engine.getMinHeight();
            long visited = IrisColumnWalk.walk(query, (int blockX, int blockZ) -> {
                if (engine.isClosed()) {
                    return false;
                }

                HydrologyColumnSample hydrology = wantRiver
                        ? hydrologySample(engine, blockX, blockZ)
                        : null;
                int surface = wantHeight || wantKind ? engine.getHeight(blockX, blockZ) : 0;
                int fluid = wantRiver
                        ? (int) Math.round(engine.getComplex().getRiverWaterSurfaceStream().get(blockX, blockZ))
                        : engine.getDimension().getFluidHeight();
                boolean needsBiome = wantBiome
                        || (wantKind && IrisSurfaceClassifier.requiresSurfaceBiome(surface, fluid));
                IrisBiome biome = needsBiome ? engine.getSurfaceBiome(blockX, blockZ) : null;
                IrisSurfaceKind kind = wantKind
                        ? IrisSurfaceClassifier.classify(
                                surface,
                                fluid,
                                biome == null ? null : biome.getInferredType(),
                                hydrology
                        )
                        : IrisSurfaceKind.UNKNOWN;
                String biomeKey = wantBiome && biome != null ? biome.getLoadKey() : null;
                int natural = wantNaturalHeight
                        ? (int) Math.round(engine.getComplex().getNaturalHeightStream().get(blockX, blockZ)) + minHeight
                        : IrisColumnSample.UNAVAILABLE_HEIGHT;
                HydrologyColumnLayer surfaceLayer = hydrology == null
                        ? null
                        : hydrology.primarySurfaceLayer().orElse(null);
                boolean riverPresent = surfaceLayer != null;
                IrisRiverState riverState = wantRiverState
                        ? riverState(hydrology)
                        : IrisRiverState.NONE;
                double riverDistance = wantRiverDistance && riverPresent
                        ? engine.getComplex().getRiverDistanceStream().get(blockX, blockZ)
                        : IrisColumnSample.UNAVAILABLE_RIVER_DISTANCE;
                int riverFlow = wantRiverFlow && riverPresent
                        ? (int) Math.round(engine.getComplex().getRiverFlowStream().get(blockX, blockZ))
                        : IrisColumnSample.UNAVAILABLE_RIVER_FLOW;
                int riverWaterSurfaceY = wantRiverWaterSurface
                        && surfaceLayer != null
                        && surfaceLayer.connectedFluid()
                        ? fluid + minHeight
                        : IrisColumnSample.UNAVAILABLE_HEIGHT;
                sink.accept(new IrisColumnSample(
                        blockX,
                        blockZ,
                        wantHeight ? surface + minHeight : IrisColumnSample.UNAVAILABLE_HEIGHT,
                        natural,
                        kind,
                        biomeKey,
                        riverState,
                        riverDistance,
                        riverFlow,
                        riverWaterSurfaceY
                ));
                return true;
            });
            return visited == query.columnCount();
        } catch (Throwable error) {
            reportSinkFault(world, error);
            return false;
        }
    }

    static IrisRiverState riverState(HydrologyColumnSample hydrology) {
        HydrologyColumnLayer layer = hydrology == null
                ? null
                : hydrology.primarySurfaceLayer().orElse(null);
        if (layer == null || !layer.channel()) {
            return IrisRiverState.NONE;
        }
        return layer.connectedFluid() ? IrisRiverState.WET : IrisRiverState.DRY;
    }

    private static HydrologyColumnSample hydrologySample(Engine engine, int blockX, int blockZ) {
        if (engine.getComplex().getHydrologyRuntime() == null) {
            return null;
        }
        return engine.getComplex().getHydrologyRuntime().sample(blockX, blockZ).orElse(null);
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
