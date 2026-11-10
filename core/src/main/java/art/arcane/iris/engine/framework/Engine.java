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

import art.arcane.iris.core.modules.PlacementMarkers;


import art.arcane.iris.core.IrisSettings;
import art.arcane.iris.engine.framework.render.RenderType;
import art.arcane.iris.engine.framework.render.Renderer;
import art.arcane.iris.core.loader.IrisData;
import art.arcane.iris.core.nms.container.BlockPos;
import art.arcane.iris.core.nms.container.Pair;
import art.arcane.iris.core.modules.CaveBiomeAnswers;
import art.arcane.iris.core.modules.ModuleServices;
import art.arcane.iris.engine.IrisComplex;
import art.arcane.iris.engine.UpperDimensionContext;
import art.arcane.iris.engine.data.chunk.TerrainChunk;
import art.arcane.iris.engine.mantle.EngineMantle;
import art.arcane.iris.engine.object.world.biome.IrisBiome;
import art.arcane.iris.engine.object.value.IrisColor;
import art.arcane.iris.engine.object.world.dimension.IrisDimension;
import art.arcane.iris.engine.object.runtime.IrisEngineData;
import art.arcane.iris.engine.object.value.IrisPosition;
import art.arcane.iris.engine.object.world.region.IrisRegion;
import art.arcane.iris.engine.object.world.IrisWorld;
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
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicLong;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.Consumer;

public interface Engine extends DataProvider, Fallible, BlockUpdater, Renderer, Hotloadable {
    IrisComplex getComplex();

    default @Nullable UpperDimensionContext getUpperContext() {
        return null;
    }

    EngineGeneration getGeneration();

    /**
     * The pack's resources were read again while this engine runs, such as after a tool rewrote some of its files:
     * modules restart their world with the new snapshot (see {@link EngineGeneration#restartModules()}). Does nothing
     * before the engine has a runtime, which starts with the new snapshot anyway.
     */
    void packResourcesReloaded();

    EnginePlatformHooks getPlatformHooks();

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
        if (isClosing() || isClosed()) {
            throw new GenerationSessionException("Generation session rejected new work for " + operation
                    + " while the Iris engine is closing.", isClosed());
        }

        return generationSessions.acquire(operation);
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

    default IrisWorld getWorld() {
        return getTarget().getWorld();
    }

    default IrisDimension getDimension() {
        return getTarget().getDimension();
    }

    @BlockCoordinates
    default Color draw(double x, double z) {
        IrisRegion region = getRegion((int) x, (int) z);
        IrisBiome biome = getSurfaceBiome((int) x, (int) z);
        int height = getHeight((int) x, (int) z);
        double heightFactor = M.lerpInverse(0, getTarget().getHeight(), height);
        Color irc = region.getColor(this.getComplex(), RenderType.BIOME);
        Color ibc = biome.getColor(this, RenderType.BIOME);
        Color rc = irc != null ? irc : Color.GREEN.darker();
        Color bc = ibc != null ? ibc : biome.isAquatic() ? Color.BLUE : Color.YELLOW;

        return IrisColor.blend(rc, bc, bc, Color.getHSBColor(0, 0, (float) heightFactor));
    }

    @BlockCoordinates
    default IrisRegion getRegion(int x, int z) {
        return getComplex().getRegionStream().get(x, z);
    }

    void generateMatter(int x, int z, boolean multicore, ChunkContext context);

    @BlockCoordinates
    default IrisBiome getCaveOrMantleBiome(int x, int y, int z) {
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
        return getComplex().getCaveBiomeStream().get(x, z);
    }

    @BlockCoordinates
    default IrisBiome getCaveBiome(int x, int y, int z) {
        IrisBiome surfaceBiome = getSurfaceBiome(x, z);
        CaveBiomeAnswers caves = ModuleServices.caveBiomes(this);
        IrisBiome overriddenBiome = caves.overriddenBiome(x, y, z);
        if (overriddenBiome != null) {
            return overriddenBiome;
        }

        IrisBiome caveBiome = getCaveBiome(x, z);
        if (caveBiome == null || isCaveBiomePlaceholder(caveBiome)) {
            return surfaceBiome;
        }

        int surfaceY = getComplex().getHeightStream().get(x, z).intValue();
        int depthBelowSurface = surfaceY - y;
        if (depthBelowSurface <= 0) {
            return surfaceBiome;
        }

        int minDepth = Math.max(0, caves.minDepthBelowSurface(caveBiome.getLoadKey()));
        if (depthBelowSurface < minDepth) {
            return surfaceBiome;
        }

        return caveBiome;
    }

    /** The cave biome stream answers with a biome no pack file defines where the region lists no cave biomes. */
    private static boolean isCaveBiomePlaceholder(IrisBiome caveBiome) {
        return caveBiome.getLoadKey() == null;
    }

    @BlockCoordinates
    default IrisBiome getSurfaceBiome(int x, int z) {
        return getComplex().getTrueBiomeStream().get(x, z);
    }

    @BlockCoordinates
    default int getHeight(int x, int z) {
        return getHeight(x, z, true);
    }

    @BlockCoordinates
    default int getHeight(int x, int z, boolean ignoreFluid) {
        return getMantle().getHighest(x, z, getData(), ignoreFluid);
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

    /** Whether the world generates {@code region}: a world locked to a focus region generates only that one, which the dimension's region list need not name. */
    default boolean generatesRegion(IrisRegion region) {
        IrisRegion focusRegion = getFocusRegion();
        if (focusRegion != null) {
            return focusRegion.getLoadKey().equals(region.getLoadKey());
        }
        return getDimension().getRegionKeys().contains(region.getLoadKey());
    }


    IrisEngineData getEngineData();

    default KList<IrisBiome> getAllBiomes() {
        KMap<String, IrisBiome> v = new KMap<>();

        IrisDimension dim = getDimension();
        dim.getReachableBiomes(this).forEach((i) -> v.put(i.getLoadKey(), i));

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
        if (y <= getHeight(x, z) - 2) {
            return getCaveBiome(x, y, z);
        }

        return getSurfaceBiome(x, z);
    }

    default IrisBiome getBiomeOrMantle(int x, int y, int z) {
        if (y <= getHeight(x, z) - 2) {
            return getCaveOrMantleBiome(x, y, z);
        }

        return getSurfaceBiome(x, z);
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
        if (objectAt == null || objectAt.isEmpty()) {
            return null;
        }

        PlacementMarkers.Decoded marker = PlacementMarkers.decode(objectAt);
        if (marker == null) {
            return null;
        }
        String object = marker.objectKey();
        if (object.startsWith("procedural/")) {
            return null;
        }
        return new PlacedObject(getData().getObjectLoader().load(object), marker.placementId(), x, z);
    }

    int getCacheID();

    default void cleanupMantleChunk(int x, int z) {
        if (getPlatformHooks().shouldSkipMantleCleanup(this)) {
            return;
        }
        if (IrisSettings.get().getPerformance().isTrimMantleInStudio() || !isStudio()) {
            getMantle().cleanupChunk(x, z);
        }
    }
}
