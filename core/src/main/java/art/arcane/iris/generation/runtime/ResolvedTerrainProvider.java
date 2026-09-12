package art.arcane.iris.generation.runtime;

import art.arcane.iris.generation.stage.IrisTransitionGeometryActuator;
import art.arcane.iris.world.history.BoundaryColumnGeometry;
import art.arcane.iris.world.history.GenerationHistoryRuntimeRouter;
import art.arcane.iris.world.history.FloatingBiomeOverlay;
import art.arcane.iris.world.history.SavedTerrainChunk;
import art.arcane.iris.world.history.TerrainBoundarySignature;
import art.arcane.iris.world.history.TransitionGenerationPlan;
import art.arcane.iris.world.history.TransitionFluidContainment;
import art.arcane.iris.spi.IrisPlatforms;
import art.arcane.iris.spi.PlatformBiome;
import art.arcane.iris.spi.PlatformBlockState;
import art.arcane.iris.spi.PlatformRegistries;
import art.arcane.iris.generation.context.ChunkContext;
import art.arcane.iris.generation.context.IrisContext;
import art.arcane.volmlib.util.hunk.Hunk;

import java.io.IOException;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;

public final class ResolvedTerrainProvider {
    private static final int MAXIMUM_CACHED_CHUNKS = 64;
    private static final int MAXIMUM_HEIGHT_CHUNKS = 4_096;

    private final Engine engine;
    private final LinkedHashMap<Long, ResolvedTerrain> completed = new LinkedHashMap<>(64, 0.75F, true);
    private final ConcurrentHashMap<Long, CompletableFuture<ResolvedTerrain>> pending = new ConcurrentHashMap<>();
    private final LinkedHashMap<Long, ResolvedTerrain> rawCompleted = new LinkedHashMap<>(64, 0.75F, true);
    private final ConcurrentHashMap<Long, CompletableFuture<ResolvedTerrain>> rawPending = new ConcurrentHashMap<>();
    private final LinkedHashMap<Long, int[]> heights = new LinkedHashMap<>(64, 0.75F, true);

    public ResolvedTerrainProvider(Engine engine) {
        this.engine = engine;
    }

    public TerrainBoundarySignature column(int blockX, int blockZ) {
        return resolve(blockX >> 4, blockZ >> 4).terrain().column(blockX, blockZ);
    }

    public int height(int blockX, int blockZ, boolean ignoreFluid) {
        long key = chunkKey(blockX >> 4, blockZ >> 4);
        synchronized (heights) {
            int[] cached = heights.get(key);
            if (cached != null) {
                return cached[(ignoreFluid ? 256 : 0) + (blockX & 15) * 16 + (blockZ & 15)];
            }
        }
        TerrainBoundarySignature column = resolve(blockX >> 4, blockZ >> 4).terrain().column(blockX, blockZ);
        return ignoreFluid ? column.oceanFloorHeight() : column.surfaceHeight();
    }

    public void generate(EngineMode mode, int x, int z, Hunk<PlatformBlockState> blocks,
                         Hunk<PlatformBiome> biomes, boolean multicore, ChunkContext context) {
        SavedTerrainChunk terrain;
        if (context.getComplex().getTransitionGenerationPlan() != null
                && context.getComplex().getTransitionGenerationPlan().hasTransitionAtChunk(x >> 4, z >> 4)) {
            ResolvedTerrain resolved = resolve(x >> 4, z >> 4);
            terrain = resolved.terrain();
            context.setFloatingBiomes(resolved.floatingBiomes());
            copy(terrain, blocks, biomes, context);
        } else {
            mode.generateTerrain(x, z, blocks, biomes, multicore, context);
            terrain = capture(x, z, blocks, biomes, context, true);
        }
        if (engine instanceof IrisEngine irisEngine) {
            GenerationHistoryRuntimeRouter router = irisEngine.getGenerationHistoryRuntimeRouter().orElse(null);
            if (router != null) {
                router.recordNaturalTerrain(terrain.boundaryOnly());
                router.recordFloatingBiomes(x >> 4, z >> 4, context.getFloatingBiomes());
            }
        }
    }

    public void clear() {
        synchronized (completed) {
            completed.clear();
        }
        synchronized (rawCompleted) {
            rawCompleted.clear();
        }
        synchronized (heights) {
            heights.clear();
        }
    }

    private ResolvedTerrain resolve(int chunkX, int chunkZ) {
        return resolve(chunkX, chunkZ, false);
    }

    private ResolvedTerrain resolve(int chunkX, int chunkZ, boolean raw) {
        LinkedHashMap<Long, ResolvedTerrain> cache = raw ? rawCompleted : completed;
        ConcurrentHashMap<Long, CompletableFuture<ResolvedTerrain>> loads = raw ? rawPending : pending;
        long key = chunkKey(chunkX, chunkZ);
        synchronized (cache) {
            ResolvedTerrain cached = cache.get(key);
            if (cached != null) {
                return cached;
            }
        }
        CompletableFuture<ResolvedTerrain> result = new CompletableFuture<>();
        CompletableFuture<ResolvedTerrain> existing = loads.putIfAbsent(key, result);
        if (existing != null) {
            return existing.join();
        }
        try {
            synchronized (cache) {
                ResolvedTerrain published = cache.get(key);
                if (published != null) {
                    result.complete(published);
                    return published;
                }
            }
            ResolvedTerrain terrain = raw ? compute(chunkX, chunkZ) : contain(chunkX, chunkZ);
            if (!raw) {
                cacheHeights(key, terrain.terrain());
            }
            synchronized (cache) {
                cache.put(key, terrain);
                while (cache.size() > MAXIMUM_CACHED_CHUNKS) {
                    cache.pollFirstEntry();
                }
            }
            result.complete(terrain);
            return terrain;
        } catch (RuntimeException | Error failure) {
            result.completeExceptionally(failure);
            throw failure;
        } finally {
            loads.remove(key, result);
        }
    }

    private static long chunkKey(int chunkX, int chunkZ) {
        return ((long) chunkX << 32) | (chunkZ & 0xffffffffL);
    }

    private void cacheHeights(long key, SavedTerrainChunk terrain) {
        int[] values = new int[512];
        for (int x = 0; x < 16; x++) {
            for (int z = 0; z < 16; z++) {
                TerrainBoundarySignature column = terrain.column((terrain.chunkX() << 4) + x,
                        (terrain.chunkZ() << 4) + z);
                int index = x * 16 + z;
                values[index] = column.surfaceHeight();
                values[256 + index] = column.oceanFloorHeight();
            }
        }
        synchronized (heights) {
            heights.put(key, values);
            while (heights.size() > MAXIMUM_HEIGHT_CHUNKS) {
                heights.pollFirstEntry();
            }
        }
    }

    private ResolvedTerrain contain(int chunkX, int chunkZ) {
        ResolvedTerrain raw = resolve(chunkX, chunkZ, true);
        TransitionGenerationPlan plan = engine.getComplex().getTransitionGenerationPlan();
        if (plan == null || !plan.hasTransitionAtChunk(chunkX, chunkZ)) {
            return raw;
        }
        try {
            return new ResolvedTerrain(TransitionFluidContainment.contain(raw.terrain(), plan,
                    (x, z) -> rawColumn(plan, raw.terrain(), x, z)), raw.floatingBiomes());
        } catch (IOException failure) {
            throw new IllegalStateException("Unable to contain transition fluids at " + chunkX + "," + chunkZ, failure);
        }
    }

    private BoundaryColumnGeometry rawColumn(TransitionGenerationPlan plan, SavedTerrainChunk current, int x, int z)
            throws IOException {
        if (plan.isHistoricalBlock(x, z)) {
            BoundaryColumnGeometry geometry = plan.terrainSignatures().signatureAt(x, z)
                    .orElseThrow(() -> new IOException("Missing saved terrain boundary column at " + x + "," + z))
                    .geometry();
            if (geometry.minimumY() != engine.getMinHeight() || geometry.height() != engine.getHeight()) {
                throw new IOException("Saved terrain boundary column has the wrong vertical layout at " + x + "," + z);
            }
            return geometry;
        }
        return (x >> 4) == current.chunkX() && (z >> 4) == current.chunkZ()
                ? current.column(x, z).geometry() : resolve(x >> 4, z >> 4, true).terrain().column(x, z).geometry();
    }

    private ResolvedTerrain compute(int chunkX, int chunkZ) {
        int x = Math.multiplyExact(chunkX, 16);
        int z = Math.multiplyExact(chunkZ, 16);
        try (GenerationHistoryRuntimeRouter.CoordinateScope runtimeScope = engine instanceof IrisEngine irisEngine
                ? irisEngine.openGenerationHistoryCoordinateScope(x, z) : null) {
            IrisContext active = IrisContext.get();
            long sessionId = active == null ? 0L : active.getGenerationSessionId();
            ChunkContext context = new ChunkContext(x, z, engine.getComplex(), sessionId, true,
                ChunkContext.PrefillPlan.NATURAL_TERRAIN, engine.getMetrics(), engine.getDimensionStackContext());
            context.beginSpeculativeTerrain();
            Hunk<PlatformBlockState> blocks = Hunk.newArrayHunk(16, engine.getHeight(), 16);
            Hunk<PlatformBiome> biomes = Hunk.newArrayHunk(16, engine.getHeight(), 16);
            try (IrisContext.Scope ignored = IrisContext.open(engine, sessionId, context)) {
                engine.getMode().generateTerrain(x, z, blocks, biomes, false, context);
                return new ResolvedTerrain(capture(x, z, blocks, biomes, context, false), context.getFloatingBiomes());
            }
        } catch (IOException failure) {
            throw new IllegalStateException("Unable to scope natural terrain at " + chunkX + "," + chunkZ, failure);
        }
    }

    private SavedTerrainChunk capture(int x, int z, Hunk<PlatformBlockState> blocks,
                                      Hunk<PlatformBiome> biomes, ChunkContext context, boolean boundaryOnly) {
        FloatingBiomeOverlay floating = context.getFloatingBiomes();
        if (floating != null) {
            floating.retainHighestSurfaces((localX, localZ) -> highestSolid(blocks, localX, localZ));
        }
        try {
            return IrisTransitionGeometryActuator.capture(x, z, blocks, biomes, engine.getMinHeight(), context, boundaryOnly);
        } catch (IOException failure) {
            throw new IllegalStateException("Unable to capture natural terrain at " + (x >> 4) + "," + (z >> 4), failure);
        }
    }

    private static int highestSolid(Hunk<PlatformBlockState> blocks, int localX, int localZ) {
        for (int y = blocks.getHeight() - 1; y >= 0; y--) {
            PlatformBlockState state = blocks.getRaw(localX, y, localZ);
            if (state != null && !state.isAir() && !state.isFluid()) {
                return y;
            }
        }
        return -1;
    }

    private static void copy(SavedTerrainChunk terrain, Hunk<PlatformBlockState> blocks,
                             Hunk<PlatformBiome> biomes, ChunkContext context) {
        PlatformRegistries registries = IrisPlatforms.get().registries();
        Map<String, PlatformBlockState> states = new HashMap<>();
        Map<String, PlatformBiome> physicalBiomes = new HashMap<>();
        for (int x = 0; x < 16; x++) {
            for (int z = 0; z < 16; z++) {
                TerrainBoundarySignature column = terrain.column((terrain.chunkX() << 4) + x, (terrain.chunkZ() << 4) + z);
                BoundaryColumnGeometry geometry = column.geometry();
                for (int offset = 0; offset < blocks.getHeight(); offset++) {
                    String stateKey = geometry.voxelAt(geometry.minimumY() + offset).stateKey();
                    PlatformBlockState state = states.computeIfAbsent(stateKey, registries::blockOrNull);
                    if (state == null) {
                        throw new IllegalStateException("Resolved terrain requires unavailable block state " + stateKey);
                    }
                    blocks.setRaw(x, offset, z, state);
                    String biomeKey = column.biomeAtSample(offset / 4);
                    biomes.setRaw(x, offset, z, physicalBiomes.computeIfAbsent(biomeKey, registries::biome));
                }
                context.setTerrainHeight(x, z, column.oceanFloorHeight());
            }
        }
    }

    private record ResolvedTerrain(SavedTerrainChunk terrain, FloatingBiomeOverlay floatingBiomes) {
    }

}
