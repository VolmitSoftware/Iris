/*
 * Iris is a World Generator for Minecraft Bukkit Servers
 * Copyright (c) 2022 Arcane Arts (Volmit Software)
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License
 * along with this program.  If not, see <https://www.gnu.org/licenses/>.
 */

package art.arcane.iris.engine.framework;

import art.arcane.iris.engine.terrain.Terrain3DColumn;



import art.arcane.iris.core.IrisSettings;
import art.arcane.iris.engine.framework.render.RenderType;
import art.arcane.iris.engine.framework.render.Renderer;
import art.arcane.iris.core.loader.IrisData;
import art.arcane.iris.core.loader.IrisRegistrant;
import art.arcane.iris.core.nms.container.BlockPos;
import art.arcane.iris.core.nms.container.Pair;
import art.arcane.iris.engine.IrisComplex;
import art.arcane.iris.engine.UpperDimensionContext;
import art.arcane.iris.engine.DimensionStackContext;
import art.arcane.iris.engine.DimensionStackLayout;
import art.arcane.iris.engine.DimensionTerrainContext;
import art.arcane.iris.engine.data.chunk.TerrainChunk;
import art.arcane.iris.engine.mantle.EngineMantle;
import art.arcane.iris.engine.object.IrisBiome;
import art.arcane.iris.engine.object.IrisColor;
import art.arcane.iris.engine.object.IrisDimension;
import art.arcane.iris.engine.object.IrisDimensionCarvingEntry;
import art.arcane.iris.engine.object.IrisDimensionCarvingResolver;
import art.arcane.iris.engine.object.IrisEngineData;
import art.arcane.iris.engine.object.IrisObject;
import art.arcane.iris.engine.object.IrisObjectPlacement;
import art.arcane.iris.engine.object.IrisPosition;
import art.arcane.iris.engine.object.IrisRegion;
import art.arcane.iris.engine.object.IrisStructure;
import art.arcane.iris.engine.object.IrisWorld;
import art.arcane.iris.engine.hydrology.cave.HydrologyCaveCell;
import art.arcane.iris.engine.hydrology.cave.HydrologyCaveStorage;
import art.arcane.iris.spi.IrisLogging;
import art.arcane.iris.spi.PlatformBiome;
import art.arcane.iris.spi.PlatformBlockState;
import art.arcane.volmlib.util.collection.KList;
import art.arcane.volmlib.util.collection.KMap;
import art.arcane.iris.util.project.context.ChunkContext;
import art.arcane.iris.util.project.context.IrisContext;
import art.arcane.iris.util.common.data.DataProvider;
import art.arcane.iris.util.common.data.B;
import art.arcane.volmlib.util.documentation.BlockCoordinates;
import art.arcane.volmlib.util.documentation.ChunkCoordinates;
import art.arcane.volmlib.util.function.Function2;
import art.arcane.iris.util.project.hunk.Hunk;
import art.arcane.volmlib.util.mantle.runtime.MantleChunk;
import art.arcane.volmlib.util.mantle.flag.MantleFlag;
import art.arcane.volmlib.util.math.M;
import art.arcane.volmlib.util.math.RNG;
import art.arcane.volmlib.util.matter.Matter;
import art.arcane.volmlib.util.matter.MatterCavern;
import art.arcane.iris.util.common.parallel.BurstExecutor;
import art.arcane.iris.util.common.parallel.MultiBurst;
import art.arcane.iris.util.common.plugin.VolmitSender;
import art.arcane.volmlib.util.scheduling.ChronoLatch;
import art.arcane.iris.util.common.scheduling.J;
import art.arcane.iris.util.project.stream.ProceduralStream;
import org.jetbrains.annotations.Nullable;

import java.awt.Color;
import java.nio.file.Path;
import java.util.Optional;
import java.util.OptionalInt;
import java.util.Locale;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicLong;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.Consumer;
import java.util.stream.Collectors;

public interface Engine extends DataProvider, Fallible, BlockUpdater, Renderer, Hotloadable {
    IrisComplex getComplex();

    default BiomeEnvironment getBiomeEnvironment(int x, int y, int z) {
        return new BiomeEnvironment(0L, getBiome(x, y, z), getRegion(x, y, z), getDimension(), getData());
    }

    default BiomeEnvironment getBiomeOrMantleEnvironment(int x, int y, int z) {
        return new BiomeEnvironment(0L, getBiomeOrMantle(x, y, z), getRegion(x, y, z), getDimension(), getData());
    }

    default BiomeEnvironment getSurfaceBiomeEnvironment(int x, int z) {
        return new BiomeEnvironment(0L, getSurfaceBiome(x, z), getRegion(x, z), getDimension(), getData());
    }

    default BiomeEnvironment.Scope openBiomeEnvironmentScope(BiomeEnvironment environment) {
        return () -> {};
    }


    default @Nullable UpperDimensionContext getUpperContext() {
        return null;
    }

    default @Nullable DimensionStackContext getDimensionStackContext() {
        return null;
    }

    default boolean isAdditionalTerrainOwned(int x, int y, int z) {
        DimensionStackContext stack = getDimensionStackContext();
        if (stack != null && stack.getLayout(x, z).isHostFeatureProtectedY(y)) {
            return true;
        }
        UpperDimensionContext upper = getUpperContext();
        return upper != null && y >= upper.getEffectiveSurfaceY(x, z) && y < getHeight();
    }

    default boolean isTerrainSurfaceSolid(int x, int y, int z) {
        DimensionStackContext stack = getDimensionStackContext();
        if (stack != null) {
            DimensionStackLayout layout = stack.getLayout(x, z);
            if (layout.isHostFeatureProtectedY(y)) {
                return layout.isSolid(y);
            }
        }
        UpperDimensionContext upper = getUpperContext();
        if (upper != null) {
            UpperDimensionContext.Column column = upper.sampleColumn(x, z);
            if (column.ownsY(y)) {
                return column.isSolid(y);
            }
        }
        Terrain3DColumn column = getComplex().terrainColumn(x, z);
        return column == null || column.isSolid(y);
    }

    EngineMode getMode();

    EnginePlatformHooks getPlatformHooks();

    /**
     * Whether chunk generation may fan its stages and mantle components out across the burst pool.
     * Live worlds generate inline so players keep their cores; a pregeneration owns the machine and
     * spreads the work, and the engine service setting forces the same for every world.
     */
    static boolean generateMulticore(boolean forceMulticoreWrite, boolean pregeneratorActive) {
        return forceMulticoreWrite || pregeneratorActive;
    }

    default boolean shouldGenerateMulticore() {
        return generateMulticore(
                IrisSettings.get().getPerformance().getEngineSVC().isForceMulticoreWrite(),
                getPlatformHooks().isPregeneratorActive(this)
        );
    }

    /**
     * World-space native structure piece bounds overlapping the given XZ rect. The answer is a pure function of the
     * seed, the registries and this pack's structure policy, so it never depends on generation order.
     */
    default KList<NativeStructureVolume> getNativeStructureVolumes(int minX, int minZ, int maxX, int maxZ) {
        EnginePlatformHooks hooks = getPlatformHooks();
        return hooks == null ? NativeStructureVolume.NONE : hooks.nativeStructureVolumes(this, minX, minZ, maxX, maxZ);
    }

    int getBlockUpdatesPerSecond();

    void printMetrics(VolmitSender sender);

    EngineMantle getMantle();

    void hotloadSilently();

    void hotloadComplex();

    void recycle();

    void close();

    default boolean isClosing() {
        return isClosed();
    }

    default boolean isShuttingDown() {
        return isClosed() || isClosing();
    }

    double getMaxBiomeObjectDensity();

    double getMaxBiomeDecoratorDensity();

    double getMaxBiomeLayerDensity();

    boolean isClosed();

    default GenerationSessionManager getGenerationSessions() {
        return null;
    }

    default GenerationSessionLease acquireGenerationLease(String operation) throws GenerationSessionException {
        GenerationSessionManager generationSessions = getGenerationSessions();
        if (generationSessions == null) {
            return GenerationSessionLease.noop();
        }

        IrisContext context = IrisContext.get();
        if (context != null && context.getEngine() == this && context.getGenerationSessionId() != 0L) {
            return generationSessions.continueSession(operation, context.getGenerationSessionId());
        }
        return generationSessions.acquireForEngine(this, operation);
    }

    default long getGenerationSessionId() {
        GenerationSessionManager generationSessions = getGenerationSessions();
        return generationSessions == null ? 0L : generationSessions.currentSessionId();
    }

    EngineWorldManager getWorldManager();

    default UUID getBiomeID(int x, int z) {
        return getComplex().getBaseBiomeIDStream().get(x, z);
    }

    int getParallelism();

    void setParallelism(int parallelism);

    EngineTarget getTarget();

    default int getMaxHeight() {
        return getTarget().getWorld().maxHeight();
    }

    default int getMinHeight() {
        return getTarget().getWorld().minHeight();
    }

    default void setMinHeight(int min) {
        getTarget().getWorld().minHeight(min);
    }

    @BlockCoordinates
    default void generate(int x, int z, TerrainChunk tc, boolean multicore) throws WrongEngineBroException {
        generate(x, z, Hunk.view(tc), Hunk.viewBiomes(tc), multicore);
    }

    @BlockCoordinates
    void generate(int x, int z, Hunk<PlatformBlockState> blocks, Hunk<PlatformBiome> biomes, boolean multicore) throws WrongEngineBroException;

    EngineMetrics getMetrics();

    default void save() {
        NativeStructureOwnershipStore.flush(this);
        getMantle().save();
        getWorldManager().onSave();
        saveEngineData();
    }

    default void saveNow() {
        NativeStructureOwnershipStore.flush(this);
        getMantle().saveAllNow();
        saveEngineData();
    }

    SeedManager getSeedManager();

    void saveEngineData();

    default String getName() {
        return getDimension().getName();
    }

    default IrisData getData() {
        return getTarget().getData();
    }

    Path getPackSource();

    default IrisWorld getWorld() {
        return getTarget().getWorld();
    }

    default IrisDimension getDimension() {
        return getTarget().getDimension();
    }

    @BlockCoordinates
    default Color draw(double x, double z) {
        return drawBiomeEnvironment((int) x, (int) z, getSurfaceBiomeEnvironment((int) x, (int) z));
    }

    @BlockCoordinates
    default Color drawForPreview(int x, int z) throws InterruptedException {
        return draw(x, z);
    }

    @BlockCoordinates
    default Color drawBiomeEnvironment(int x, int z, BiomeEnvironment environment) {
        IrisRegion region = environment.region();
        IrisBiome biome = environment.biome();
        int height = getHeight(x, z);
        double heightFactor = M.lerpInverse(0, getTarget().getHeight(), height);
        Color irc = region.getColor(environment::data, RenderType.BIOME);
        Color ibc = biome.getColor(this, RenderType.BIOME);
        Color rc = irc != null ? irc : Color.GREEN.darker();
        Color bc = ibc != null ? ibc : biome.isAquatic() ? Color.BLUE : Color.YELLOW;

        return IrisColor.blend(rc, bc, bc, Color.getHSBColor(0, 0, (float) heightFactor));
    }

    @BlockCoordinates
    default IrisRegion getRegion(int x, int z) {
        DimensionStackContext dimensionStackContext = getDimensionStackContext();
        if (dimensionStackContext != null) {
            DimensionStackLayout.Layer layer = dimensionStackContext.getLayout(x, z).surfaceLayer();
            if (layer != null && layer.region() != null) {
                return layer.region();
            }
        }
        return getComplex().getRegionStream().get(x, z);
    }

    @BlockCoordinates
    default IrisRegion getRegion(int x, int y, int z) {
        DimensionStackContext dimensionStackContext = getDimensionStackContext();
        if (dimensionStackContext != null) {
            DimensionStackLayout.Layer layer = dimensionStackContext.getLayout(x, z).layerAt(y);
            if (layer != null && layer.region() != null) {
                return layer.region();
            }
        }
        return getComplex().getRegionStream().get(x, z);
    }

    void generateMatter(int x, int z, boolean multicore, ChunkContext context);

    @BlockCoordinates
    default IrisBiome getCaveOrMantleBiome(int x, int y, int z) {
        HydrologyCaveCell hydrology = HydrologyCaveStorage.getIfPresent(
                getMantle().getMantle(), x, y, z);
        if (hydrology != null && !hydrology.floodedBiomeKey().isEmpty()) {
            IrisBiome biome = getData().getBiomeLoader().load(hydrology.floodedBiomeKey());
            if (biome != null) {
                return biome;
            }
        }
        MatterCavern m = getMantle().getMantle().get(x, y, z, MatterCavern.class);

        if (m != null && m.getCustomBiome() != null && !m.getCustomBiome().isEmpty()) {
            IrisBiome biome = getData().getBiomeLoader().load(m.getCustomBiome());

            if (biome != null) {
                return biome;
            }
        }

        return getCaveBiome(x, y, z);
    }

    @ChunkCoordinates
    Set<String> getObjectsAt(int x, int z);

    @ChunkCoordinates
    Set<Pair<String, BlockPos>> getPOIsAt(int x, int z);

    @BlockCoordinates
    default IrisBiome getCaveBiome(int x, int z) {
        IrisBiome biome = getComplex().getCaveBiomeStream().get(x, z);
        return biome == null || biome.getLoadKey() == null ? getSurfaceBiome(x, z) : biome;
    }

    @BlockCoordinates
    default IrisBiome getCaveBiome(int x, int y, int z) {
        return getCaveBiome(x, y, z, null);
    }

    @BlockCoordinates
    default IrisBiome getCaveBiome(int x, int y, int z, IrisDimensionCarvingResolver.State state) {
        IrisBiome configuredBiome = resolveConfiguredCaveBiome(x, y, z, state);
        if (configuredBiome != null) {
            return configuredBiome;
        }
        boolean naturalFallback = answersFromNaturalTerrain(x, z);
        DimensionStackContext dimensionStackContext = getDimensionStackContext();
        IrisBiome surfaceBiome = naturalFallback || dimensionStackContext == null
                ? naturalFallback
                        ? getComplex().naturalSurfaceBiome(x, z)
                        : getSurfaceBiome(x, z)
                : getComplex().getTrueBiomeStream().get(x, z);
        int surfaceY = naturalFallback
                ? getComplex().naturalTrueHeight(x, z)
                : getComplex().getHeightStream().get(x, z).intValue();
        return resolveDepthCaveBiome(x, y, z, surfaceBiome, surfaceY);
    }

    @BlockCoordinates
    default IrisBiome getCaveBiome(
            int x,
            int y,
            int z,
            IrisDimensionCarvingResolver.State state,
            IrisBiome surfaceBiome,
            int surfaceY
    ) {
        IrisBiome configuredBiome = resolveConfiguredCaveBiome(x, y, z, state);
        if (configuredBiome != null) {
            return configuredBiome;
        }
        return resolveDepthCaveBiome(x, y, z, surfaceBiome, surfaceY);
    }

    private IrisBiome resolveConfiguredCaveBiome(
            int x,
            int y,
            int z,
            IrisDimensionCarvingResolver.State state
    ) {
        int worldY = y + getWorld().minHeight();
        IrisDimensionCarvingEntry rootCarvingEntry = IrisDimensionCarvingResolver.resolveRootEntry(this, worldY, state);
        if (rootCarvingEntry != null) {
            IrisDimensionCarvingEntry resolvedCarvingEntry = IrisDimensionCarvingResolver.resolveFromRoot(this, rootCarvingEntry, x, z, state);
            IrisBiome resolvedCarvingBiome = IrisDimensionCarvingResolver.resolveEntryBiome(this, resolvedCarvingEntry, state);
            if (resolvedCarvingBiome != null) {
                return resolvedCarvingBiome;
            }
        }
        return null;
    }

    private IrisBiome resolveDepthCaveBiome(
            int x,
            int y,
            int z,
            IrisBiome surfaceBiome,
            int surfaceY
    ) {
        IrisBiome caveBiome = getCaveBiome(x, z);
        if (caveBiome == null) {
            return surfaceBiome;
        }

        int depthBelowSurface = surfaceY - y;
        if (depthBelowSurface <= 0) {
            return surfaceBiome;
        }

        int minDepth = Math.max(0, caveBiome.getCaveMinDepthBelowSurface());
        if (depthBelowSurface < minDepth) {
            return surfaceBiome;
        }

        return caveBiome;
    }

    @BlockCoordinates
    default IrisBiome getSurfaceBiome(int x, int z) {
        DimensionStackContext dimensionStackContext = getDimensionStackContext();
        if (dimensionStackContext != null) {
            DimensionStackLayout.Layer layer = dimensionStackContext.getLayout(x, z).surfaceLayer();
            if (layer != null && layer.biome() != null) {
                return layer.biome();
            }
        }
        return getHostSurfaceBiome(x, z);
    }

    @BlockCoordinates
    default IrisBiome getHostSurfaceBiome(int x, int z) {
        if (answersFromNaturalTerrain(x, z)) {
            return getComplex().naturalSurfaceBiome(x, z);
        }
        return getComplex().getTrueBiomeStream().get(x, z);
    }

    /**
     * The server thread must never wait for a cold hydrology plan, so a height or biome query from it
     * on a column whose tiles are not planned yet is answered from natural terrain (the tiles are
     * requested from the planning pool meanwhile). Generation threads always wait for the real plan.
     */
    default boolean answersFromNaturalTerrain(int x, int z) {
        return getPlatformHooks().isMainThread() && !getComplex().isHydrologyPlanned(x, z);
    }

    /** The terrain slope at a column, answered from natural terrain when the server thread may not wait. */
    @BlockCoordinates
    default double getSlope(int x, int z) {
        if (answersFromNaturalTerrain(x, z)) {
            return getComplex().naturalSlope(x, z);
        }
        return getComplex().getSlopeStream().getDouble(x, z);
    }

    @BlockCoordinates
    default int getHeight(int x, int z) {
        return getHeight(x, z, true);
    }

    @BlockCoordinates
    default int getHeight(int x, int z, boolean ignoreFluid) {
        OptionalInt resolved = getComplex().resolvedTerrainHeight(x, z, ignoreFluid);
        if (resolved.isPresent()) {
            return resolved.getAsInt();
        }
        DimensionStackContext dimensionStackContext = getDimensionStackContext();
        if (dimensionStackContext != null) {
            return ignoreFluid
                    ? dimensionStackContext.getStackTerrainHeight(x, z)
                    : dimensionStackContext.getStackTopHeight(x, z);
        }
        if (answersFromNaturalTerrain(x, z)) {
            int natural = getComplex().naturalTrueHeight(x, z);
            return ignoreFluid ? natural : Math.max(natural, getMantle().getFluidHeight());
        }
        return getMantle().getHighest(x, z, getData(), ignoreFluid);
    }

    @BlockCoordinates
    static int hostHeight(Engine engine, int x, int z, boolean ignoreFluid) {
        EngineMantle mantle = engine.getMantle();
        if (mantle == null) {
            return engine.getHeight(x, z, ignoreFluid);
        }
        if (engine.answersFromNaturalTerrain(x, z)) {
            int naturalHeight = engine.getComplex().naturalTrueHeight(x, z);
            return ignoreFluid
                    ? naturalHeight
                    : Math.max(naturalHeight, mantle.getFluidHeight());
        }
        int terrainHeight = mantle.trueHeight(x, z);
        return ignoreFluid
                ? terrainHeight
                : Math.max(terrainHeight, mantle.getFluidHeight(x, z));
    }

    @BlockCoordinates
    @Override
    default void catchBlockUpdates(int x, int y, int z, PlatformBlockState data) {
        if (data == null) {
            return;
        }

        if (B.isUpdatable(data)) {
            getMantle().updateBlock(x, y, z);
        }
        if (data.isCustom()) {
            getMantle().getMantle().flag(x >> 4, z >> 4, MantleFlag.CUSTOM_ACTIVE, true);
        }
    }

    void blockUpdatedMetric();

    EngineEffects getEffects();

    default MultiBurst burst() {
        return getTarget().getBurster();
    }

    default void clean() {
        burst().lazy(() -> getMantle().trim(10));
    }

    IrisBiome getFocus();

    IrisRegion getFocusRegion();


    IrisEngineData getEngineData();

    default KList<IrisBiome> getAllBiomes() {
        KMap<String, IrisBiome> v = new KMap<>();

        IrisDimension dim = getDimension();
        dim.getReachableBiomes(this).forEach((i) -> v.put(i.getLoadKey(), i));
        DimensionStackContext dimensionStackContext = getDimensionStackContext();
        if (dimensionStackContext != null) {
            for (DimensionTerrainContext terrainContext : dimensionStackContext.getLayersBottomToTop()) {
                if (!terrainContext.isSelfReferencing()) {
                    terrainContext.getDimension().getReachableBiomes(terrainContext)
                            .forEach((i) -> v.put(i.getLoadKey(), i));
                }
            }
        }

        return v.v();
    }

    int getGenerated();

    CompletableFuture<Long> getHash32();

    default <T> IrisPosition lookForStreamResult(T find, ProceduralStream<T> stream, Function2<T, T, Boolean> matcher, long timeout) {
        AtomicInteger checked = new AtomicInteger();
        AtomicLong time = new AtomicLong(M.ms());
        AtomicReference<IrisPosition> r = new AtomicReference<>();
        BurstExecutor b = burst().burst();

        while (M.ms() - time.get() < timeout && r.get() == null) {
            b.queue(() -> {
                for (int i = 0; i < 1000; i++) {
                    if (M.ms() - time.get() > timeout) {
                        return;
                    }

                    int x = RNG.r.i(-29999970, 29999970);
                    int z = RNG.r.i(-29999970, 29999970);
                    checked.incrementAndGet();
                    if (matcher.apply(stream.get(x, z), find)) {
                        r.set(new IrisPosition(x, 120, z));
                        time.set(0);
                    }
                }
            });
        }

        return r.get();
    }

    double getGeneratedPerSecond();

    default int getHeight() {
        return getWorld().getHeight();
    }

    boolean isStudio();

    default IrisBiome getBiome(int x, int y, int z) {
        DimensionStackContext dimensionStackContext = getDimensionStackContext();
        if (dimensionStackContext != null) {
            DimensionStackLayout layout = dimensionStackContext.getLayout(x, z);
            IrisBiome stackedBiome = dimensionStackBiome(layout, y);
            if (stackedBiome != null) {
                return stackedBiome;
            }
            DimensionStackLayout.Layer bottomLayer = layout.layersBottomToTop().get(0);
            if (y > bottomLayer.renderMaxY()) {
                return bottomLayer.biome();
            }
            if (y <= bottomLayer.clippedSurfaceY() - 2 && !getComplex().isTerrain3DSurface(x, y, z)) {
                return getCaveBiome(x, y, z);
            }
            return bottomLayer.biome();
        }
        if (y <= getHeight(x, z) - 2 && !getComplex().isTerrain3DSurface(x, y, z)) {
            return getCaveBiome(x, y, z);
        }

        return getSurfaceBiome(x, z);
    }

    default IrisBiome getBiomeOrMantle(int x, int y, int z) {
        DimensionStackContext dimensionStackContext = getDimensionStackContext();
        if (dimensionStackContext != null) {
            DimensionStackLayout layout = dimensionStackContext.getLayout(x, z);
            IrisBiome stackedBiome = dimensionStackBiome(layout, y);
            if (stackedBiome != null) {
                return stackedBiome;
            }
            DimensionStackLayout.Layer bottomLayer = layout.layersBottomToTop().get(0);
            if (y > bottomLayer.renderMaxY()) {
                return bottomLayer.biome();
            }
            if (y <= bottomLayer.clippedSurfaceY() - 2 && !getComplex().isTerrain3DSurface(x, y, z)) {
                return getCaveOrMantleBiome(x, y, z);
            }
            return bottomLayer.biome();
        }
        if (y <= getHeight(x, z) - 2 && !getComplex().isTerrain3DSurface(x, y, z)) {
            return getCaveOrMantleBiome(x, y, z);
        }

        return getSurfaceBiome(x, z);
    }

    private static IrisBiome dimensionStackBiome(DimensionStackLayout layout, int y) {
        DimensionStackLayout.Layer bottom = layout.layersBottomToTop().get(0);
        DimensionStackLayout.Layer owner = layout.layerAt(y);
        return owner == bottom ? null : owner.biome();
    }

    default String getObjectPlacementKey(int x, int y, int z) {
        PlacedObject o = getObjectPlacement(x, y, z);

        if (o != null && o.getObject() != null) {
            return o.getObject().getLoadKey() + "@" + o.getId();
        }

        MantleChunk<Matter> chunk = getMantle().getMantle().getChunk(x >> 4, z >> 4).use();
        try {
            String raw = chunk.get(x & 15, y, z & 15, String.class);
            return (raw == null || raw.isEmpty()) ? null : raw;
        } finally {
            chunk.release();
        }
    }

    default PlacedObject getObjectPlacement(int x, int y, int z) {
        MantleChunk<Matter> chunk = getMantle().getMantle().getChunk(x >> 4, z >> 4).use();
        try {
            return getObjectPlacement(x, y, z, chunk);
        } finally {
            chunk.release();
        }
    }

    default PlacedObject getObjectPlacement(int x, int y, int z, MantleChunk<Matter> chunk) {
        String objectAt = chunk.get(x & 15, y, z & 15, String.class);
        return resolveObjectPlacementMarker(x, z, objectAt);
    }

    default PlacedObject resolveObjectPlacementMarker(int x, int z, @Nullable String objectAt) {
        if (objectAt == null || objectAt.isEmpty()) {
            return null;
        }

        StructurePlacementMarker.Decoded marker = StructurePlacementMarker.decode(objectAt);
        if (marker == null) {
            return null;
        }
        String object = marker.objectKey();
        if (object.startsWith("procedural/")) {
            return null;
        }
        int id = marker.placementId();

        if (marker.structureAware()) {
            IrisObject placedObject = getData().getObjectLoader().load(object);
            IrisStructure structure = getData().load(IrisStructure.class, marker.structureKey(), false);
            IrisObjectPlacement placement = placedObject == null || structure == null
                    ? null
                    : structure.createLootPlacement(object);
            return new PlacedObject(placement, placedObject, id, x, z);
        }


        IrisRegion region = getComplex().getRegionStream().get(x, z);

        for (IrisObjectPlacement i : region.getObjects()) {
            if (i.compatPlace(getData()).contains(object)) {
                return new PlacedObject(i, getData().getObjectLoader().load(object), id, x, z);
            }
        }

        IrisBiome biome = getComplex().getTrueBiomeStream().get(x, z);

        for (IrisObjectPlacement i : biome.getObjects()) {
            if (i.compatPlace(getData()).contains(object)) {
                return new PlacedObject(i, getData().getObjectLoader().load(object), id, x, z);
            }
        }

        return new PlacedObject(null, getData().getObjectLoader().load(object), id, x, z);
    }

    int getCacheID();

    default boolean hasObjectPlacement(String objectKey) {
        String normalizedObjectKey = normalizeObjectPlacementKey(objectKey);
        if (normalizedObjectKey.isBlank()) {
            return false;
        }

        IrisData data = getData();
        Set<String> biomeKeys = getDimension().getReachableBiomes(this).stream()
                .filter((i) -> containsObjectPlacement(i.getObjects(), normalizedObjectKey, data))
                .map(IrisRegistrant::getLoadKey)
                .collect(Collectors.toSet());
        Set<String> regionKeys = getDimension().getAllRegions(this).stream()
                .filter((i) -> i.getAllBiomeIds().stream().anyMatch(biomeKeys::contains)
                        || containsObjectPlacement(i.getObjects(), normalizedObjectKey, data))
                .map(IrisRegistrant::getLoadKey)
                .collect(Collectors.toSet());
        return !regionKeys.isEmpty();
    }

    private static boolean containsObjectPlacement(KList<IrisObjectPlacement> placements, String normalizedObjectKey, IrisData data) {
        if (placements == null || placements.isEmpty() || normalizedObjectKey.isBlank()) {
            return false;
        }

        for (IrisObjectPlacement placement : placements) {
            if (placement == null || placement.getPlace() == null || placement.getPlace().isEmpty()) {
                continue;
            }

            for (String placedObject : placement.compatPlace(data)) {
                String normalizedPlacedObject = normalizeObjectPlacementKey(placedObject);
                if (!normalizedPlacedObject.isBlank() && normalizedPlacedObject.equals(normalizedObjectKey)) {
                    return true;
                }
            }
        }

        return false;
    }

    private static String normalizeObjectPlacementKey(String objectKey) {
        if (objectKey == null) {
            return "";
        }

        String normalized = objectKey.trim().replace('\\', '/');
        while (normalized.startsWith("/")) {
            normalized = normalized.substring(1);
        }
        while (normalized.endsWith("/")) {
            normalized = normalized.substring(0, normalized.length() - 1);
        }
        if (normalized.endsWith(".iob")) {
            normalized = normalized.substring(0, normalized.length() - 4);
        }
        return normalized.toLowerCase(Locale.ROOT);
    }

    default void cleanupMantleChunk(int x, int z) {
        if (getPlatformHooks().shouldSkipMantleCleanup(this)) {
            return;
        }
        if (IrisSettings.get().getPerformance().isTrimMantleInStudio() || !isStudio()) {
            getMantle().cleanupChunksCoveredBy(
                    x,
                    z,
                    false,
                    EngineMantle.ChunkCleanupCallback.NONE
            );
        }
    }
}
