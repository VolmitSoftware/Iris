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

package art.arcane.iris.generation.mantle;


import art.arcane.iris.spi.IrisLogging;
import art.arcane.iris.structure.object.ObjectWaterRule;
import art.arcane.iris.world.storage.matter.TileWrapper;
import art.arcane.iris.world.storage.matter.PreObjectMatterCell;
import art.arcane.iris.configuration.IrisSettings;
import art.arcane.iris.integration.Identifier;
import art.arcane.iris.world.WorldMaintenance;
import art.arcane.iris.pack.loading.IrisData;
import art.arcane.iris.generation.runtime.Engine;
import art.arcane.iris.generation.runtime.IrisComplex;
import art.arcane.iris.generation.terrain.Terrain3DColumn;
import art.arcane.iris.world.history.TransitionGenerationPlan;
import art.arcane.iris.generation.block.TileData;
import art.arcane.iris.generation.context.ChunkContext;
import art.arcane.iris.generation.hydrology.cave.HydrologyCaveCell;
import art.arcane.iris.generation.subterrain.SubterrainCell;
import art.arcane.iris.generation.subterrain.SubterrainRasterizer;
import art.arcane.volmlib.util.documentation.ChunkCoordinates;
import art.arcane.volmlib.util.mantle.flag.MantleFlag;
import art.arcane.volmlib.util.mantle.runtime.Mantle;
import art.arcane.volmlib.util.mantle.runtime.MantleChunk;
import art.arcane.volmlib.util.matter.Matter;
import art.arcane.volmlib.util.matter.IrisMatter;
import art.arcane.volmlib.util.matter.MatterCavern;
import art.arcane.volmlib.util.matter.MatterSlice;
import art.arcane.iris.world.task.J;
import lombok.Data;
import lombok.AccessLevel;
import lombok.EqualsAndHashCode;
import lombok.Getter;
import lombok.Setter;
import lombok.ToString;
import art.arcane.volmlib.nativelib.terrain.NativeBlockState;
import art.arcane.iris.generation.block.B;

import java.util.LinkedHashMap;
import java.util.function.BooleanSupplier;
import java.util.function.Supplier;
import it.unimi.dsi.fastutil.longs.Long2ObjectMap;
import it.unimi.dsi.fastutil.longs.Long2ObjectOpenHashMap;
import java.util.Map;
import java.util.List;
import java.util.Objects;
import java.util.concurrent.atomic.AtomicReferenceArray;

import static art.arcane.iris.generation.mantle.EngineMantle.AIR;

@Data
public class MantleWriter implements ObjectPassPlacer, AutoCloseable {
    private static final int OBJECT_COMPONENT_PRIORITY = 2;
    private static final BooleanSupplier ALWAYS = () -> true;
    private static final BooleanSupplier NEVER = () -> false;

    private final EngineMantle engineMantle;
    private final Mantle<Matter> mantle;
    private final int radius;
    private final int x;
    private final int z;
    private final int windowSide;
    private final AtomicReferenceArray<MantleChunk<Matter>> window;
    private final TransitionGenerationPlan transitionGenerationPlan;
    @Getter(AccessLevel.NONE)
    @Setter(AccessLevel.NONE)
    @EqualsAndHashCode.Exclude
    @ToString.Exclude
    private final ThreadLocal<Integer> activeComponentPriority = new ThreadLocal<>();
    @Getter(AccessLevel.NONE)
    private final ThreadLocal<ObjectPlacementCapture> objectPlacementCapture = new ThreadLocal<>();
    @Getter(AccessLevel.NONE)
    @Setter(AccessLevel.NONE)
    @EqualsAndHashCode.Exclude
    @ToString.Exclude
    private final ThreadLocal<ObjectWater> objectWater = new ThreadLocal<>();
    @Getter(AccessLevel.NONE)
    @Setter(AccessLevel.NONE)
    @EqualsAndHashCode.Exclude
    @ToString.Exclude
    private TerrainAccess terrainAccess;

    public MantleWriter(EngineMantle engineMantle, Mantle<Matter> mantle, int x, int z, int radius, boolean multicore) {
        this(engineMantle, mantle, x, z, radius, radius * 2, multicore);
    }

    public MantleWriter(
            EngineMantle engineMantle,
            Mantle<Matter> mantle,
            int x,
            int z,
            int prefetchRadius,
            int accessRadius,
            boolean multicore
    ) {
        this(engineMantle, mantle, x, z, accessRadius);

        final boolean foliaMaintenance = J.isFolia()
                && WorldMaintenance.isWorldMaintenanceActive(engineMantle.getEngine().getWorld().identity());
        final int parallelism = resolvePrefetchParallelism(
                foliaMaintenance,
                multicore,
                Runtime.getRuntime().availableProcessors());
        if (foliaMaintenance && IrisSettings.get().getGeneral().isDebug()) {
            IrisLogging.info("MantleWriter using sequential chunk prefetch for maintenance regen at " + x + "," + z + ".");
        }
        // try-with-resources never calls close() when the initializer throws, so a failed
        // prefetch must release the permits already pinned into the window here.
        try {
            mantle.getChunks(
                    x - prefetchRadius,
                    x + prefetchRadius,
                    z - prefetchRadius,
                    z + prefetchRadius,
                    parallelism,
                    this::storePrefetchedChunk
            );
        } catch (Throwable e) {
            close();
            throw e;
        }
    }

    private MantleWriter(EngineMantle engineMantle, Mantle<Matter> mantle, int x, int z, int accessRadius) {
        this.engineMantle = engineMantle;
        this.mantle = mantle;
        this.radius = accessRadius;
        this.x = x;
        this.z = z;
        IrisComplex complex = engineMantle.getComplex();
        this.transitionGenerationPlan = complex == null ? null : complex.getTransitionGenerationPlan();
        // Every coordinate acquireChunk accepts lives in this window, so a flat array replaces the
        // boxed per-block map lookup on the placement and carve hot paths.
        this.windowSide = (this.radius * 2) + 1;
        this.window = new AtomicReferenceArray<>(windowSide * windowSide);
    }

    /**
     * A writer around one already loaded chunk, for generating that chunk's terrain components without a
     * prefetch. Chunks within the radius are acquired on first access.
     */
    static MantleWriter around(EngineMantle engineMantle, Mantle<Matter> mantle, MantleChunk<Matter> chunk,
                               int chunkX, int chunkZ, int radius) {
        MantleWriter writer = new MantleWriter(engineMantle, mantle, chunkX, chunkZ, radius);
        writer.window.set(writer.windowIndex(chunkX, chunkZ), chunk.use());
        return writer;
    }

    /**
     * From now on every chunk this writer hands out has its terrain components generated first. Called once
     * the terrain passes of the window are done and before any content pass reads or writes.
     */
    void requireTerrainOnAccess(List<MantleComponent> terrainComponents, ChunkContext context, IrisComplex complex) {
        terrainAccess = terrainComponents.isEmpty()
                ? null
                : new TerrainAccess(terrainComponents.toArray(new MantleComponent[0]), context, complex);
    }

    static int resolvePrefetchParallelism(boolean foliaMaintenance, boolean multicore, int availableProcessors) {
        if (foliaMaintenance) {
            return 1;
        }
        if (!multicore) {
            return 4;
        }
        return Math.max(1, availableProcessors / 2);
    }

    public void withComponentPriority(int priority, Runnable task) {
        Objects.requireNonNull(task, "task");
        Integer previous = activeComponentPriority.get();
        activeComponentPriority.set(priority);
        try {
            task.run();
        } finally {
            if (previous == null) {
                activeComponentPriority.remove();
            } else {
                activeComponentPriority.set(previous);
            }
        }
    }

    @ChunkCoordinates
    public void withChunkFence(int chunkX, int chunkZ, Runnable task) {
        Objects.requireNonNull(task, "task");
        MantleChunk<Matter> chunk = acquireChunk(chunkX, chunkZ);
        if (chunk == null) {
            throw new IllegalArgumentException("Chunk fence exceeds the prepared Mantle radius at "
                    + chunkX + "," + chunkZ);
        }
        synchronized (chunk) {
            task.run();
        }
    }

    public ObjectPlacementCapture captureObjectPlacement(ObjectContinuationBundle.PlacementKey key) {
        if (objectPlacementCapture.get() != null) {
            throw new IllegalStateException("Object placement capture is already active");
        }
        ObjectPlacementCapture capture = new ObjectPlacementCapture(key);
        objectPlacementCapture.set(capture);
        return capture;
    }

    @Override
    public int beginObjectPlacement(ObjectWaterRule rule) {
        objectWater.set(new ObjectWater(ObjectHydrologyVeto.of(getEngine()), rule));
        return -1;
    }

    @Override
    public void endObjectPlacement() {
        objectWater.remove();
    }

    private boolean keepsWater(int x, int y, int z, Object value) {
        ObjectWater water = objectWater.get();
        if (water == null) {
            return false;
        }
        if (value instanceof NativeBlockState state) {
            return water.veto().keepsBlock(x, y, z, state, water.rule());
        }
        return value instanceof MatterCavern && water.veto().keeps(x, y, z, false, water.rule());
    }

    private void recordObjectValue(int x, int y, int z, Class<?> type, Object value) {
        ObjectPlacementCapture capture = objectPlacementCapture.get();
        if (capture != null) {
            capture.set(x, y, z, type, value);
        }
    }

    public <T> void setData(int x, int y, int z, T t) {
        if (t == null || !allowsWrite(x, z)) {
            return;
        }

        int cx = x >> 4;
        int cz = z >> 4;

        if (y < 0 || y >= mantle.getWorldHeight()) {
            return;
        }

        if (y == 0 && t instanceof NativeBlockState && engineMantle.getEngine().getDimension().isBedrock()) {
            return;
        }

        MantleChunk<Matter> chunk = acquireChunk(cx, cz);
        if (chunk == null) return;

        if (protectsSubterrainPlacement(x, y, z) || keepsWater(x, y, z, t)) {
            return;
        }
        synchronized (chunk) {
            Matter matter = chunk.getOrCreate(y >> 4);
            if ((t instanceof NativeBlockState || t instanceof MatterCavern)
                    && hasProtectedHydrology(matter, x, y, z)) {
                return;
            }
            if (t instanceof NativeBlockState) {
                clearTile(matter, x, y, z);
                clearDeferredPlacement(matter, x, y, z);
            }
            Class<?> sliceType = t instanceof NativeBlockState ? NativeBlockState.class : matter.getClass(t);
            capturePreObjectOriginal(matter, x, y, z, sliceType);
            matter.slice(sliceType).set(x & 15, y & 15, z & 15, t);
            recordObjectValue(x, y, z, sliceType, t);
        }
    }

    public boolean setDataIfAbsent(int x, int y, int z, MatterCavern value) {
        if (value == null || !allowsWrite(x, z)) {
            return false;
        }

        int cx = x >> 4;
        int cz = z >> 4;

        if (y < 0 || y >= mantle.getWorldHeight()) {
            return false;
        }

        MantleChunk<Matter> chunk = acquireChunk(cx, cz);
        if (chunk == null) {
            return false;
        }

        synchronized (chunk) {
            Matter matter = chunk.getOrCreate(y >> 4);
            if (hasProtectedHydrology(matter, x, y, z) || protectsSubterrainPlacement(x, y, z)) {
                return false;
            }
            MatterSlice<MatterCavern> cavernSlice = matter.getSlice(MatterCavern.class);
            MatterCavern existing = cavernSlice == null
                    ? null
                    : cavernSlice.get(x & 15, y & 15, z & 15);
            if (existing != null) {
                return false;
            }

            capturePreObjectOriginal(matter, x, y, z, MatterCavern.class);
            if (cavernSlice == null) {
                cavernSlice = matter.slice(MatterCavern.class);
            }
            cavernSlice.set(x & 15, y & 15, z & 15, value);
            recordObjectValue(x, y, z, MatterCavern.class, value);
            return true;
        }
    }

    public boolean carveDataIfAbsent(int x, int y, int z, MatterCavern value) {
        if (value == null || !allowsWrite(x, z) || y < 0 || y >= mantle.getWorldHeight()) {
            return false;
        }

        MantleChunk<Matter> chunk = acquireChunk(x >> 4, z >> 4);
        if (chunk == null) {
            return false;
        }

        synchronized (chunk) {
            Matter matter = chunk.getOrCreate(y >> 4);
            if (hasProtectedHydrology(matter, x, y, z) || protectsSubterrainPlacement(x, y, z)) {
                return false;
            }
            MatterSlice<NativeBlockState> blockSlice = matter.getSlice(NativeBlockState.class);
            if (blockSlice != null) {
                capturePreObjectOriginal(matter, x, y, z, NativeBlockState.class);
                blockSlice.set(x & 15, y & 15, z & 15, null);
                recordObjectValue(x, y, z, NativeBlockState.class, AIR.get());
            }
            clearDeferredPlacement(matter, x, y, z);
            MatterSlice<MatterCavern> cavernSlice = matter.getSlice(MatterCavern.class);
            if (cavernSlice != null && cavernSlice.get(x & 15, y & 15, z & 15) != null) {
                return false;
            }
            capturePreObjectOriginal(matter, x, y, z, MatterCavern.class);
            if (cavernSlice == null) {
                cavernSlice = matter.slice(MatterCavern.class);
            }
            cavernSlice.set(x & 15, y & 15, z & 15, value);
            recordObjectValue(x, y, z, MatterCavern.class, value);
            return true;
        }
    }

    public void setForcedCarve(int x, int y, int z, MatterCavern value) {
        if (value == null || !allowsWrite(x, z) || y < 0 || y >= mantle.getWorldHeight()) {
            return;
        }
        MantleChunk<Matter> chunk = acquireChunk(x >> 4, z >> 4);
        if (chunk == null) {
            throw new IllegalStateException("Forced structure carve exceeded its prepared Mantle radius at "
                    + x + "," + y + "," + z);
        }
        synchronized (chunk) {
            Matter matter = chunk.getOrCreate(y >> 4);
            if (hasProtectedHydrology(matter, x, y, z) || protectsSubterrainPlacement(x, y, z)) {
                return;
            }
            capturePreObjectOriginal(matter, x, y, z, NativeBlockState.class);
            matter.<NativeBlockState>slice(NativeBlockState.class).set(x & 15, y & 15, z & 15, AIR.get());
            clearDeferredPlacement(matter, x, y, z);
            capturePreObjectOriginal(matter, x, y, z, MatterCavern.class);
            matter.<MatterCavern>slice(MatterCavern.class).set(x & 15, y & 15, z & 15, value);
            recordObjectValue(x, y, z, NativeBlockState.class, AIR.get());
            recordObjectValue(x, y, z, MatterCavern.class, value);
        }
    }

    public void clearBlock(int x, int y, int z) {
        if (!allowsWrite(x, z) || y < 0 || y >= mantle.getWorldHeight()) {
            return;
        }
        MantleChunk<Matter> chunk = acquireChunk(x >> 4, z >> 4);
        if (chunk == null) {
            return;
        }
        int section = y >> 4;
        if (!chunk.exists(section)) {
            return;
        }
        synchronized (chunk) {
            Matter matter = chunk.get(section);
            if (matter == null) {
                return;
            }
            if (hasProtectedHydrology(matter, x, y, z) || protectsSubterrainPlacement(x, y, z)) {
                return;
            }
            MatterSlice<NativeBlockState> blockSlice = matter.getSlice(NativeBlockState.class);
            if (blockSlice != null) {
                capturePreObjectOriginal(matter, x, y, z, NativeBlockState.class);
                blockSlice.set(x & 15, y & 15, z & 15, null);
                recordObjectValue(x, y, z, NativeBlockState.class, AIR.get());
            }
            clearDeferredPlacement(matter, x, y, z);
        }
    }

    public <T> T getData(int x, int y, int z, Class<T> type) {
        int cx = x >> 4;
        int cz = z >> 4;

        if (y < 0 || y >= mantle.getWorldHeight()) {
            return null;
        }

        if (!allowsWrite(x, z)) {
            return getDataIfPresent(x, y, z, type);
        }

        MantleChunk<Matter> chunk = acquireChunk(cx, cz);
        if (chunk == null) {
            return null;
        }

        return chunk.getOrCreate(y >> 4)
                .<T>slice(type)
                .get(x & 15, y & 15, z & 15);
    }

    public <T> T getDataIfPresent(int x, int y, int z, Class<T> type) {
        if (y < 0 || y >= mantle.getWorldHeight()) {
            return null;
        }

        MantleChunk<Matter> chunk = acquireChunk(x >> 4, z >> 4);
        int section = y >> 4;
        if (chunk == null || !chunk.exists(section)) {
            return null;
        }

        Matter matter = chunk.get(section);
        if (matter == null || !matter.hasSlice(type)) {
            return null;
        }
        return matter.<T>getSlice(type).get(x & 15, y & 15, z & 15);
    }

    public <T> T getPrerequisiteDataIfPresent(int x, int y, int z, Class<T> type) {
        Objects.requireNonNull(type, "type");
        if (y < 0 || y >= mantle.getWorldHeight()) {
            return null;
        }

        MantleChunk<Matter> chunk = acquireChunk(x >> 4, z >> 4);
        int section = y >> 4;
        if (chunk == null) {
            return null;
        }

        synchronized (chunk) {
            if (!chunk.exists(section)) {
                return null;
            }
            Matter matter = chunk.get(section);
            if (matter == null) {
                return null;
            }
            return prerequisiteValue(matter, x, y, z, type);
        }
    }



    public NativeBlockState getPrerequisiteBlock(int x, int y, int z) {
        NativeBlockState block = getPrerequisiteDataIfPresent(x, y, z, NativeBlockState.class);
        return block == null ? subterrainFallback(x, y, z) : block;
    }

    public boolean isPrerequisiteCarved(int x, int y, int z) {
        return CarveOwnership.carved(
                engineMantle.getComplex().transformedColumn(x, z),
                y,
                getPrerequisiteDataIfPresent(x, y, z, HydrologyCaveCell.class),
                getEngine().getSubterrainCell(x, y, z),
                () -> engineMantle.getComplex().isTerrain3DOpening(x, y, z),
                () -> getPrerequisiteDataIfPresent(x, y, z, MatterCavern.class) != null
        );
    }

    public byte[] getPrerequisiteCarvedColumn(int x, int z, int height) {
        int cappedHeight = Math.min(Math.max(height, 0), mantle.getWorldHeight());
        byte[] carvedColumn = new byte[cappedHeight];
        if (cappedHeight <= 0) {
            return carvedColumn;
        }

        Terrain3DColumn transformed = engineMantle.getComplex().transformedColumn(x, z);
        Terrain3DColumn terrainColumn = transformed == null ? engineMantle.getComplex().terrainColumn(x, z) : null;
        if (terrainColumn != null) {
            int maximumY = Math.min(cappedHeight, terrainColumn.topY());
            for (int y = Math.max(0, terrainColumn.minY()); y < maximumY; y++) {
                if (!terrainColumn.isSolid(y)
                        && !engineMantle.getEngine().isAdditionalTerrainOwned(x, y, z)) {
                    carvedColumn[y] = 1;
                }
            }
        }

        MantleChunk<Matter> chunk = acquireChunk(x >> 4, z >> 4);
        SubterrainCell[] subterrain = subterrainColumn(x, z, cappedHeight);
        if (chunk == null) {
            for (int y = 0; y < cappedHeight; y++) {
                carvedColumn[y] = carvedByte(transformed, y, null, subterrain, carvedColumn[y] != 0, false);
            }
            return carvedColumn;
        }

        synchronized (chunk) {
            MatterSlice<PreObjectMatterCell> journalSlice = null;
            MatterSlice<MatterCavern> cavernSlice = null;
            MatterSlice<HydrologyCaveCell> hydrologySlice = null;
            for (int y = 0; y < cappedHeight; y++) {
                int localY = y & 15;
                if (localY == 0) {
                    Matter matter = chunk.exists(y >> 4) ? chunk.get(y >> 4) : null;
                    journalSlice = matter == null ? null : matter.getSlice(PreObjectMatterCell.class);
                    cavernSlice = matter == null ? null : matter.getSlice(MatterCavern.class);
                    hydrologySlice = matter == null ? null : matter.getSlice(HydrologyCaveCell.class);
                }
                PreObjectMatterCell cell = journalSlice == null
                        ? null
                        : journalSlice.get(x & 15, localY, z & 15);
                HydrologyCaveCell hydrology = cell != null && cell.hydrologyCaptured()
                        ? cell.hydrology()
                        : hydrologySlice == null
                        ? null
                        : hydrologySlice.get(x & 15, localY, z & 15);
                MatterCavern cavern = cell != null && cell.cavernCaptured()
                        ? cell.cavern()
                        : cavernSlice == null ? null : cavernSlice.get(x & 15, localY, z & 15);
                carvedColumn[y] = carvedByte(transformed, y, hydrology, subterrain, carvedColumn[y] != 0, cavern != null);
            }
        }
        return carvedColumn;
    }

    void restoreData(int x, int y, int z, Class<?> type, Object value) {
        Objects.requireNonNull(type, "type");
        if (!allowsWrite(x, z) || y < 0 || y >= mantle.getWorldHeight()) {
            return;
        }
        MantleChunk<Matter> chunk = acquireChunk(x >> 4, z >> 4);
        if (chunk == null) {
            throw new IllegalStateException("Object rollback exceeded its prepared Mantle radius at "
                    + x + "," + y + "," + z);
        }
        synchronized (chunk) {
            Matter matter = chunk.get(y >> 4);
            if (matter != null) {
                restoreRaw(matter, x, y, z, type, value);
            }
        }
    }

    public void clearData(int x, int y, int z, Class<?> type) {
        if (!allowsWrite(x, z) || y < 0 || y >= mantle.getWorldHeight() || protectsSubterrainPlacement(x, y, z)) {
            return;
        }

        MantleChunk<Matter> chunk = acquireChunk(x >> 4, z >> 4);
        int section = y >> 4;
        if (chunk == null || !chunk.exists(section)) {
            return;
        }

        synchronized (chunk) {
            Matter matter = chunk.get(section);
            if (matter == null || !matter.hasSlice(type)) {
                return;
            }
            if ((type == NativeBlockState.class || type == MatterCavern.class)
                    && (hasProtectedHydrology(matter, x, y, z) || protectsSubterrainPlacement(x, y, z))) {
                return;
            }
            capturePreObjectOriginal(matter, x, y, z, type);
            matter.getSlice(type).set(x & 15, y & 15, z & 15, null);
            recordObjectValue(x, y, z, type, null);
        }
    }

    private boolean protectsSubterrainPlacement(int x, int y, int z) {
        Integer priority = activeComponentPriority.get();
        return priority != null && priority >= OBJECT_COMPONENT_PRIORITY
                && SubterrainRasterizer.protectsPlacement(getEngine().getSubterrainCell(x, y, z));
    }

    private static boolean hasProtectedHydrology(Matter matter, int x, int y, int z) {
        MatterSlice<HydrologyCaveCell> slice = matter.getSlice(HydrologyCaveCell.class);
        if (slice == null) {
            return false;
        }
        return slice.get(x & 15, y & 15, z & 15) != null;
    }

    private static void clearTile(Matter matter, int x, int y, int z) {
        MatterSlice<TileWrapper> slice = matter.getSlice(TileWrapper.class);
        if (slice != null) {
            slice.set(x & 15, y & 15, z & 15, null);
        }
    }

    private static void clearDeferredPlacement(Matter matter, int x, int y, int z) {
        MatterSlice<Identifier> slice = matter.getSlice(Identifier.class);
        if (slice != null) {
            slice.set(x & 15, y & 15, z & 15, null);
        }
    }

    private void setCustomBlock(int x, int y, int z, NativeBlockState baseState, Identifier identifier) {
        if (!allowsWrite(x, z) || y < 0 || y >= mantle.getWorldHeight()) {
            return;
        }
        if (y == 0 && engineMantle.getEngine().getDimension().isBedrock()) {
            return;
        }

        MantleChunk<Matter> chunk = acquireChunk(x >> 4, z >> 4);
        if (chunk == null || keepsWater(x, y, z, baseState)) {
            return;
        }

        synchronized (chunk) {
            Matter matter = chunk.getOrCreate(y >> 4);
            if (hasProtectedHydrology(matter, x, y, z) || protectsSubterrainPlacement(x, y, z)) {
                return;
            }
            clearTile(matter, x, y, z);
            MatterSlice<NativeBlockState> blockSlice = matter.slice(NativeBlockState.class);
            MatterSlice<Identifier> identifierSlice = matter.slice(Identifier.class);
            capturePreObjectOriginal(matter, x, y, z, NativeBlockState.class);
            blockSlice.set(x & 15, y & 15, z & 15, baseState);
            identifierSlice.set(x & 15, y & 15, z & 15, identifier);
            recordObjectValue(x, y, z, NativeBlockState.class, baseState);
            recordObjectValue(x, y, z, Identifier.class, identifier);
        }
    }

    private void capturePreObjectOriginal(Matter matter, int x, int y, int z, Class<?> type) {
        Integer priority = activeComponentPriority.get();
        if (priority == null || priority < OBJECT_COMPONENT_PRIORITY || !isPreObjectType(type)) {
            return;
        }

        int localX = x & 15;
        int localY = y & 15;
        int localZ = z & 15;
        MatterSlice<PreObjectMatterCell> journal = matter.slice(PreObjectMatterCell.class);
        PreObjectMatterCell current = journal.get(localX, localY, localZ);
        if (current != null && current.captures(type)) {
            return;
        }
        Object original = rawValue(matter, localX, localY, localZ, type);
        PreObjectMatterCell updated;
        if (type == NativeBlockState.class) {
            updated = current == null
                    ? PreObjectMatterCell.block((NativeBlockState) original)
                    : current.captureBlock((NativeBlockState) original);
        } else if (type == String.class) {
            updated = current == null
                    ? PreObjectMatterCell.string((String) original)
                    : current.captureString((String) original);
        } else {
            updated = current == null
                    ? PreObjectMatterCell.cavern((MatterCavern) original)
                    : current.captureCavern((MatterCavern) original);
        }
        journal.set(localX, localY, localZ, updated);
    }

    private static boolean isPreObjectType(Class<?> type) {
        return type == NativeBlockState.class || type == String.class || type == MatterCavern.class;
    }

    private static PreObjectMatterCell preObjectCell(Matter matter, int x, int y, int z) {
        if (matter == null) {
            return null;
        }
        MatterSlice<PreObjectMatterCell> slice = matter.getSlice(PreObjectMatterCell.class);
        return slice == null ? null : slice.get(x & 15, y & 15, z & 15);
    }

    private static <T> T prerequisiteValue(Matter matter, int x, int y, int z, Class<T> type) {
        if (isPreObjectType(type) || type == HydrologyCaveCell.class) {
            PreObjectMatterCell cell = preObjectCell(matter, x, y, z);
            if (cell != null && cell.captures(type)) {
                return cell.original(type);
            }
        }
        MatterSlice<T> slice = matter.getSlice(type);
        return slice == null ? null : slice.get(x & 15, y & 15, z & 15);
    }

    private static Object rawValue(Matter matter, int x, int y, int z, Class<?> type) {
        MatterSlice<?> slice = matter.getSlice(type);
        return slice == null ? null : slice.get(x, y, z);
    }

    private boolean allowsWrite(int blockX, int blockZ) {
        return transitionGenerationPlan == null
                || !transitionGenerationPlan.isHistoricalBlock(blockX, blockZ);
    }

    @SuppressWarnings("unchecked")
    private static void restoreRaw(Matter matter, int x, int y, int z, Class<?> type, Object value) {
        MatterSlice<Object> slice;
        if (value == null) {
            slice = (MatterSlice<Object>) matter.getSlice(type);
            if (slice == null) {
                return;
            }
        } else {
            slice = (MatterSlice<Object>) matter.slice(type);
        }
        slice.set(x & 15, y & 15, z & 15, value);
    }

    @ChunkCoordinates
    public MantleChunk<Matter> acquireChunk(int cx, int cz) {
        int index = windowIndex(cx, cz);
        if (index < 0) {
            IrisLogging.debug("Mantle Writer Accessed chunk out of bounds" + cx + "," + cz);
            return null;
        }

        MantleChunk<Matter> chunk = window.get(index);
        while (chunk == null) {
            // Losing this race must release our own use, never the winner's: the winner is already
            // writing through the chunk it published.
            MantleChunk<Matter> acquired = mantle.useChunk(cx, cz);
            if (window.compareAndSet(index, null, acquired)) {
                chunk = acquired;
            } else {
                acquired.release();
                chunk = window.get(index);
            }
        }
        TerrainAccess access = terrainAccess;
        if (access != null && !access.ready[index]) {
            access.ensure(chunk, cx, cz);
            access.ready[index] = true;
        }
        return chunk;
    }

    @ChunkCoordinates
    private int windowIndex(int cx, int cz) {
        int localX = cx - x + radius;
        int localZ = cz - z + radius;
        if (localX < 0 || localX >= windowSide || localZ < 0 || localZ >= windowSide) {
            return -1;
        }
        return (localX * windowSide) + localZ;
    }

    @ChunkCoordinates
    private void storePrefetchedChunk(int cx, int cz, MantleChunk<Matter> chunk) {
        int index = windowIndex(cx, cz);
        if (index < 0) {
            return;
        }

        if (!window.compareAndSet(index, null, chunk.use())) {
            chunk.release();
        }
    }

    @Override
    public int getHighest(int x, int z, IrisData data) {
        return engineMantle.getHighest(x, z, data);
    }

    @Override
    public int getHighest(int x, int z, IrisData data, boolean ignoreFluid) {
        return engineMantle.getHighest(x, z, data, ignoreFluid);
    }

    @Override
    public void set(int x, int y, int z, NativeBlockState s) {
        if (s == null) {
            return;
        }
        String placementKey = s.deferredPlacementKey();
        NativeBlockState baseState = s.placementBaseState();
        if (s.isCustom() && placementKey != null && baseState != null) {
            Identifier identifier = Identifier.fromString(placementKey);
            setCustomBlock(x, y, z, baseState, identifier);
            return;
        }
        setData(x, y, z, s);
    }

    @Override
    public NativeBlockState get(int x, int y, int z) {
        // Read-only probe: getDataIfPresent returns the identical answer without materializing
        // a 16^3 section + slice on a miss the way getData's getOrCreate path does.
        NativeBlockState block = getDataIfPresent(x, y, z, NativeBlockState.class);
        return block == null ? subterrainFallback(x, y, z) : block;
    }

    private NativeBlockState subterrainFallback(int x, int y, int z) {
        SubterrainCell cell = getEngine().getSubterrainCell(x, y, z);
        return cell != null && cell.owned() ? SubterrainRasterizer.state(cell) : AIR.get();
    }

    @Override
    public boolean isPreventingDecay() {
        return getEngineMantle().isPreventingDecay();
    }

    @Override
    public boolean isCarved(int x, int y, int z) {
        return CarveOwnership.carved(
                engineMantle.getComplex().transformedColumn(x, z),
                y,
                getDataIfPresent(x, y, z, HydrologyCaveCell.class),
                getEngine().getSubterrainCell(x, y, z),
                () -> engineMantle.getComplex().isTerrain3DOpening(x, y, z),
                () -> getDataIfPresent(x, y, z, MatterCavern.class) != null
        );
    }


    public byte[] getCarvedColumn(int x, int z, int height) {
        int cappedHeight = Math.min(Math.max(height, 0), mantle.getWorldHeight());
        byte[] carvedColumn = new byte[cappedHeight];
        if (cappedHeight <= 0) {
            return carvedColumn;
        }

        Terrain3DColumn transformed = engineMantle.getComplex().transformedColumn(x, z);
        Terrain3DColumn terrainColumn = transformed == null ? engineMantle.getComplex().terrainColumn(x, z) : null;
        if (terrainColumn != null) {
            int maximumY = Math.min(cappedHeight, terrainColumn.topY());
            for (int y = terrainColumn.minY(); y < maximumY; y++) {
                if (!terrainColumn.isSolid(y) && !engineMantle.getEngine().isAdditionalTerrainOwned(x, y, z)) {
                    carvedColumn[y] = 1;
                }
            }
        }

        MantleChunk<Matter> chunk = acquireChunk(x >> 4, z >> 4);
        SubterrainCell[] subterrain = subterrainColumn(x, z, cappedHeight);
        int localX = x & 15;
        int localZ = z & 15;
        MatterSlice<MatterCavern> cavernSlice = null;
        MatterSlice<HydrologyCaveCell> hydrologySlice = null;
        for (int y = 0; y < cappedHeight; y++) {
            int localY = y & 15;
            if (localY == 0) {
                Matter matter = chunk == null || !chunk.exists(y >> 4) ? null : chunk.get(y >> 4);
                cavernSlice = matter == null ? null : matter.getSlice(MatterCavern.class);
                hydrologySlice = matter == null || !matter.hasSlice(HydrologyCaveCell.class)
                        ? null
                        : matter.getSlice(HydrologyCaveCell.class);
            }
            HydrologyCaveCell hydrology = hydrologySlice == null ? null : hydrologySlice.get(localX, localY, localZ);
            boolean cavern = cavernSlice != null && cavernSlice.get(localX, localY, localZ) != null;
            carvedColumn[y] = carvedByte(transformed, y, hydrology, subterrain, carvedColumn[y] != 0, cavern);
        }
        return carvedColumn;
    }

    @Override
    public boolean isSurfaceSolid(int x, int y, int z) {
        return engineMantle.getEngine().isTerrainSurfaceSolid(x, y, z);
    }

    @Override
    public boolean isSolid(int x, int y, int z) {
        return B.isSolid(get(x, y, z));
    }

    @Override
    public boolean isUnderwater(int x, int z) {
        return getEngineMantle().isUnderwater(x, z);
    }

    @Override
    public int getFluidHeight() {
        return getEngineMantle().getFluidHeight();
    }

    @Override
    public boolean isDebugSmartBore() {
        return getEngineMantle().isDebugSmartBore();
    }

    @Override
    public void setTile(int xx, int yy, int zz, TileData tile) {
        setData(xx, yy, zz, new TileWrapper(tile));
    }

    @Override
    public Engine getEngine() {
        return getEngineMantle().getEngine();
    }

    @Override
    public void close() {
        for (int index = 0; index < window.length(); index++) {
            if (window.get(index) == null) {
                continue;
            }
            MantleChunk<Matter> chunk = window.getAndSet(index, null);
            if (chunk != null) {
                chunk.release();
            }
        }
    }

    private final class TerrainAccess {
        private final MantleComponent[] components;
        private final int[] reaches;
        private final ChunkContext context;
        private final IrisComplex complex;
        private final boolean[] ready = new boolean[window.length()];

        private TerrainAccess(MantleComponent[] components, ChunkContext context, IrisComplex complex) {
            this.components = components;
            this.reaches = terrainReaches(components);
            this.context = context;
            this.complex = complex;
        }

        private void ensure(MantleChunk<Matter> chunk, int chunkX, int chunkZ) {
            if (chunk.isFlagged(MantleFlag.PLANNED) || complex != null && !complex.allowsMantleChunkWrite(chunkX, chunkZ)) {
                return;
            }
            for (int index = 0; index < components.length; index++) {
                MantleComponent component = components[index];
                int reach = reaches[index];
                for (int offsetX = -reach; offsetX <= reach; offsetX++) {
                    for (int offsetZ = -reach; offsetZ <= reach; offsetZ++) {
                        if (offsetX == 0 && offsetZ == 0) {
                            run(chunk, component, chunkX, chunkZ);
                        } else {
                            runAround(component, chunkX + offsetX, chunkZ + offsetZ);
                        }
                    }
                }
            }
        }

        private void runAround(MantleComponent component, int chunkX, int chunkZ) {
            if (complex != null && !complex.allowsMantleChunkWrite(chunkX, chunkZ)) {
                return;
            }
            MantleChunk<Matter> chunk = mantle.useChunk(chunkX, chunkZ);
            try {
                if (!chunk.isFlagged(MantleFlag.PLANNED)) {
                    run(chunk, component, chunkX, chunkZ);
                }
            } finally {
                chunk.release();
            }
        }

        private void run(MantleChunk<Matter> chunk, MantleComponent component, int chunkX, int chunkZ) {
            if (chunk.isFlagged(component.getFlag()) || !hasPrerequisites(chunk, component)) {
                return;
            }
            int radius = chunkReach(Math.max(component.getWriteRadius(), component.getInputRadius()));
            chunk.raiseFlagSuspend(component.getFlag(), () -> {
                try (MantleWriter terrainWriter = around(engineMantle, mantle, chunk, chunkX, chunkZ, radius)) {
                    terrainWriter.withComponentPriority(component.getPriority(),
                            () -> component.generateLayer(terrainWriter, chunkX, chunkZ, context));
                }
            });
        }

        private static boolean hasPrerequisites(MantleChunk<Matter> chunk, MantleComponent component) {
            for (MantleFlag prerequisite : component.getPrerequisiteFlags()) {
                if (!chunk.isFlagged(prerequisite)) {
                    return false;
                }
            }
            return true;
        }
    }

    static int[] terrainReaches(MantleComponent[] components) {
        int[] reaches = new int[components.length];
        int downstream = 0;
        for (int index = components.length - 1; index >= 0; index--) {
            MantleComponent component = components[index];
            reaches[index] = chunkReach(downstream + component.getWriteRadius());
            downstream += component.getWriteRadius() + component.getInputRadius();
        }
        return reaches;
    }

    private static int chunkReach(int blocks) {
        return blocks > 0 ? Math.ceilDiv(blocks, 16) : 0;
    }

    private SubterrainCell[] subterrainColumn(int x, int z, int height) {
        Engine engine = getEngine();
        if (engine.subterrainAbsentInChunk(x >> 4, z >> 4)) {
            return null;
        }
        SubterrainCell[] cells = new SubterrainCell[height];
        for (int y = 0; y < height; y++) {
            cells[y] = engine.getSubterrainCell(x, y, z);
        }
        return cells;
    }

    private static byte carvedByte(Terrain3DColumn transformed, int y, HydrologyCaveCell hydrology, SubterrainCell[] subterrain,
                                   boolean naturalOpening, boolean cavern) {
        return CarveOwnership.carved(transformed, y, hydrology, subterrain == null ? null : subterrain[y],
                naturalOpening ? ALWAYS : NEVER, cavern ? ALWAYS : NEVER) ? (byte) 1 : 0;
    }

    private record ObjectWater(ObjectHydrologyVeto veto, ObjectWaterRule rule) {
    }

    public final class ObjectPlacementCapture implements AutoCloseable {
        private final ObjectContinuationBundle.PlacementKey key;
        private final Long2ObjectOpenHashMap<Matter> fragments = new Long2ObjectOpenHashMap<>();
        private int minimumX = Integer.MAX_VALUE;
        private int minimumZ = Integer.MAX_VALUE;
        private int maximumX = Integer.MIN_VALUE;
        private int maximumZ = Integer.MIN_VALUE;
        private boolean closed;

        private ObjectPlacementCapture(ObjectContinuationBundle.PlacementKey key) {
            this.key = Objects.requireNonNull(key);
        }

        public void commit() {
            close();
            if (fragments.size() > 1) {
                Map<ObjectContinuationBundle.ChunkPosition, Supplier<Matter>> pending = new LinkedHashMap<>();
                for (Long2ObjectMap.Entry<Matter> entry : fragments.long2ObjectEntrySet()) {
                    long coordinate = entry.getLongKey();
                    Matter matter = entry.getValue();
                    pending.put(new ObjectContinuationBundle.ChunkPosition((int) (coordinate >> 32), (int) coordinate), () -> matter);
                }
                ObjectContinuationPersistence.persist(mantle, key,
                        new ObjectContinuationBundle.Bounds(minimumX, minimumZ, maximumX, maximumZ), pending);
            }
        }

        @Override
        public void close() {
            if (closed) {
                return;
            }
            if (objectPlacementCapture.get() != this) {
                throw new IllegalStateException("Object placement capture closed outside its owner");
            }
            objectPlacementCapture.remove();
            closed = true;
        }

        private void set(int x, int y, int z, Class<?> type, Object value) {
            long coordinate = ((long) (x >> 4) << 32) | ((z >> 4) & 0xffffffffL);
            Matter fragment = fragments.get(coordinate);
            if (fragment == null) {
                fragment = new IrisMatter(16, mantle.getWorldHeight(), 16);
                fragments.put(coordinate, fragment);
            }
            minimumX = Math.min(minimumX, x);
            minimumZ = Math.min(minimumZ, z);
            maximumX = Math.max(maximumX, x);
            maximumZ = Math.max(maximumZ, z);
            if (type == NativeBlockState.class) {
                MatterSlice<TileWrapper> tiles = fragment.getSlice(TileWrapper.class);
                if (tiles != null) {
                    tiles.set(x & 15, y, z & 15, null);
                }
                fragment.slice(Identifier.class).set(x & 15, y, z & 15, null);
                fragment.slice(type).set(x & 15, y, z & 15, value == null ? AIR.get() : value);
            } else {
                fragment.slice(type).set(x & 15, y, z & 15, value);
            }
        }
    }

}
