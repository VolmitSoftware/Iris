package art.arcane.iris.engine.mantle.components;

import art.arcane.iris.engine.IrisComplex;
import art.arcane.iris.engine.framework.Engine;
import art.arcane.iris.engine.hydrology.HydrologyCaveVoxelViewFactory;
import art.arcane.iris.engine.hydrology.HydrologyColumnSample;
import art.arcane.iris.engine.hydrology.RiverFootprint;
import art.arcane.iris.engine.hydrology.cave.CavePosition;
import art.arcane.iris.engine.hydrology.cave.CaveVoxel;
import art.arcane.iris.engine.hydrology.cave.CaveVoxelView;
import art.arcane.iris.engine.mantle.EngineMantle;
import art.arcane.iris.engine.mantle.MantleComponent;
import art.arcane.iris.engine.mantle.MantleWriter;
import art.arcane.iris.engine.mantle.TerrainMatterView;
import art.arcane.iris.engine.object.IrisProceduralBlocks;
import art.arcane.iris.spi.PlatformBlockState;
import art.arcane.iris.util.project.context.ChunkContext;
import art.arcane.volmlib.util.mantle.flag.ReservedFlag;
import art.arcane.volmlib.util.mantle.runtime.Mantle;
import art.arcane.volmlib.util.mantle.runtime.MantleChunk;
import art.arcane.volmlib.util.matter.Matter;
import art.arcane.volmlib.util.matter.MatterCavern;
import it.unimi.dsi.fastutil.longs.Long2IntOpenHashMap;
import it.unimi.dsi.fastutil.longs.Long2ObjectOpenHashMap;
import it.unimi.dsi.fastutil.longs.LongOpenHashSet;

import java.util.Objects;
import java.util.function.BiConsumer;

public final class MantleHydrologyCaveVoxelView implements CaveVoxelView {
    private static final int CLOSED_COLUMN = Integer.MAX_VALUE;
    private static final int CACHE_MISS = Integer.MIN_VALUE;

    private final Mantle<Matter> mantle;
    private final int worldHeight;
    private final ColumnSource terrainColumns;
    private final SolidSource naturalSolid;
    private final BiConsumer<Integer, Integer> chunkLoader;
    private final LongOpenHashSet loadedChunks;
    private final Long2IntOpenHashMap openFloorCache;
    private final Long2ObjectOpenHashMap<TerrainColumn> terrainCache;

    public MantleHydrologyCaveVoxelView(
            Engine engine,
            IrisComplex complex,
            RiverFootprint footprint
    ) {
        this(
                Objects.requireNonNull(engine).getMantle(),
                Objects.requireNonNull(complex),
                Objects.requireNonNull(footprint)
        );
    }

    public MantleHydrologyCaveVoxelView(
            Engine engine,
            IrisComplex complex,
            HydrologyCaveVoxelViewFactory.PlannedSurface plannedSurface
    ) {
        this(
                Objects.requireNonNull(engine).getMantle(),
                Objects.requireNonNull(complex),
                Objects.requireNonNull(plannedSurface)
        );
    }

    private MantleHydrologyCaveVoxelView(
            EngineMantle engineMantle,
            IrisComplex complex,
            RiverFootprint footprint
    ) {
        this(
                engineMantle.getMantle(),
                engineMantle.getMantle().getWorldHeight(),
                new TerrainSources(
                        (x, z) -> terrainColumn(complex, footprint.sample(x, z).orElse(null), x, z),
                        complex::isNaturalTerrainSolid,
                        (chunkX, chunkZ) -> generateCarvingInput(engineMantle, complex, chunkX, chunkZ))
        );
    }

    private MantleHydrologyCaveVoxelView(
            EngineMantle engineMantle,
            IrisComplex complex,
            HydrologyCaveVoxelViewFactory.PlannedSurface plannedSurface
    ) {
        this(
                engineMantle.getMantle(),
                engineMantle.getMantle().getWorldHeight(),
                new TerrainSources((x, z) -> new TerrainColumn(plannedSurface.resolve(
                        x,
                        z,
                        (int) Math.round(complex.getNaturalHeightStream().getDouble(x, z))
                ), plannedSurface.ownsTerrain(x, z)), complex::isNaturalTerrainSolid,
                        (chunkX, chunkZ) -> generateCarvingInput(engineMantle, complex, chunkX, chunkZ))
        );
    }

    MantleHydrologyCaveVoxelView(
            Mantle<Matter> mantle,
            int worldHeight,
            TerrainSources sources
    ) {
        this.mantle = Objects.requireNonNull(mantle);
        if (worldHeight < 3) {
            throw new IllegalArgumentException("worldHeight must be at least three");
        }
        this.worldHeight = worldHeight;
        this.terrainColumns = Objects.requireNonNull(sources.columns());
        this.naturalSolid = Objects.requireNonNull(sources.naturalSolid());
        this.chunkLoader = Objects.requireNonNull(sources.chunkLoader());
        this.loadedChunks = new LongOpenHashSet();
        this.openFloorCache = new Long2IntOpenHashMap();
        this.openFloorCache.defaultReturnValue(CACHE_MISS);
        this.terrainCache = new Long2ObjectOpenHashMap<>();
    }

    @Override
    public boolean isInWorld(CavePosition position) {
        return position.y() > 0 && position.y() < worldHeight - 1;
    }

    @Override
    public CaveVoxel voxelAt(CavePosition position) {
        Objects.requireNonNull(position);
        MatterCavern cavern = dataIfPresent(position, MatterCavern.class);
        if (cavern != null) {
            if (cavern.isLava()) {
                return CaveVoxel.LAVA;
            }
            if (cavern.getLiquid() == 1) {
                return CaveVoxel.COMPATIBLE_FLUID;
            }
            return CaveVoxel.CAVE_AIR;
        }
        PlatformBlockState block = dataIfPresent(position, PlatformBlockState.class);
        if (block != null) {
            if (!block.isFluid()) {
                return CaveVoxel.SOLID;
            }
            return IrisProceduralBlocks.materialKey(block).endsWith(":lava")
                    ? CaveVoxel.LAVA
                    : CaveVoxel.INCOMPATIBLE_FLUID;
        }
        TerrainColumn terrain = column(position.x(), position.z());
        return position.y() > terrain.surfaceHeight()
                || !terrain.terrainOwned() && !naturalSolid.test(position.x(), position.y(), position.z())
                ? CaveVoxel.CAVE_AIR
                : CaveVoxel.SOLID;
    }

    @Override
    public boolean isOpenToSurface(CavePosition position) {
        if (!isInWorld(position) || voxelAt(position) == CaveVoxel.SOLID) {
            return false;
        }
        if (isAboveTerrainSurface(position)) {
            return true;
        }
        long key = RiverFootprint.pack(position.x(), position.z());
        int openFloor = openFloorCache.get(key);
        if (openFloor == CACHE_MISS) {
            openFloor = resolveOpenFloor(position.x(), position.z());
            openFloorCache.put(key, openFloor);
        }
        return openFloor != CLOSED_COLUMN && position.y() >= openFloor;
    }

    @Override
    public boolean isAboveTerrainSurface(CavePosition position) {
        return position.y() > surfaceY(position.x(), position.z());
    }

    @Override
    public boolean hasAboveTerrainSurface(int x, int z, int minimumY, int maximumY) {
        int firstY = Math.max(minimumY, 1);
        int lastY = Math.min(maximumY, worldHeight - 2);
        return firstY <= lastY && lastY > surfaceY(x, z);
    }

    private int resolveOpenFloor(int x, int z) {
        int top = surfaceY(x, z);
        if (voxelAt(new CavePosition(x, top, z)) == CaveVoxel.SOLID) {
            return CLOSED_COLUMN;
        }
        int y = top;
        while (y > 0 && voxelAt(new CavePosition(x, y - 1, z)) != CaveVoxel.SOLID) {
            y--;
        }
        return y;
    }

    private int surfaceY(int x, int z) {
        return column(x, z).surfaceHeight();
    }

    private TerrainColumn column(int x, int z) {
        long key = RiverFootprint.pack(x, z);
        TerrainColumn cached = terrainCache.get(key);
        if (cached != null) {
            return cached;
        }
        TerrainColumn sampled = Objects.requireNonNull(terrainColumns.sample(x, z));
        int height = Math.clamp(sampled.surfaceHeight(), 1, worldHeight - 2);
        TerrainColumn bounded = height == sampled.surfaceHeight() ? sampled
                : new TerrainColumn(height, sampled.terrainOwned());
        terrainCache.put(key, bounded);
        return bounded;
    }

    private <T> T dataIfPresent(CavePosition position, Class<T> type) {
        int chunkX = position.x() >> 4;
        int chunkZ = position.z() >> 4;
        long chunkKey = Mantle.key(chunkX, chunkZ);
        if (loadedChunks.add(chunkKey)) {
            chunkLoader.accept(chunkX, chunkZ);
        }
        return TerrainMatterView.get(mantle, position.x(), position.y(), position.z(), type);
    }

    static TerrainColumn terrainColumn(
            IrisComplex complex,
            HydrologyColumnSample sample,
            int x,
            int z
    ) {
        int surfaceHeight = sample == null
                ? (int) Math.round(complex.getNaturalHeightStream().getDouble(x, z))
                : sample.terrainHeight();
        return new TerrainColumn(surfaceHeight, sample != null && sample.primarySurfaceLayer().isPresent());
    }

    static void generateCarvingInput(
            EngineMantle engineMantle,
            IrisComplex complex,
            int chunkX,
            int chunkZ
    ) {
        MantleComponent carving = engineMantle.getRegisteredComponents().get(ReservedFlag.CARVED);
        if (carving == null || !carving.isEnabled()) {
            throw new IllegalStateException("Hydrology containment requires the carving component");
        }
        if (!requiresCarvingInput(engineMantle.getMantle(), chunkX, chunkZ)) {
            return;
        }
        ChunkContext context = new ChunkContext(
                chunkX << 4,
                chunkZ << 4,
                complex,
                false,
                ChunkContext.PrefillPlan.NONE,
                null
        );
        try (MantleWriter writer = new MantleWriter(
                engineMantle,
                engineMantle.getMantle(),
                chunkX,
                chunkZ,
                0,
                false
        )) {
            MantleChunk<Matter> chunk = writer.acquireChunk(chunkX, chunkZ);
            if (chunk == null) {
                throw new IllegalStateException("Hydrology containment could not acquire carving input at "
                        + chunkX + "," + chunkZ);
            }
            chunk.raiseFlagSuspend(
                    ReservedFlag.CARVED,
                    () -> carving.generateLayer(writer, chunkX, chunkZ, context)
            );
        }
    }

    static boolean requiresCarvingInput(Mantle<Matter> mantle, int chunkX, int chunkZ) {
        return !mantle.hasFlag(chunkX, chunkZ, ReservedFlag.CARVED);
    }

    record TerrainSources(ColumnSource columns,
                          SolidSource naturalSolid, BiConsumer<Integer, Integer> chunkLoader) {
    }

    record TerrainColumn(int surfaceHeight, boolean terrainOwned) {
    }

    @FunctionalInterface
    interface ColumnSource {
        TerrainColumn sample(int x, int z);
    }

    @FunctionalInterface
    interface SolidSource {
        boolean test(int x, int y, int z);
    }
}
