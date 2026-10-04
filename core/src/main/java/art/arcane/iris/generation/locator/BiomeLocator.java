package art.arcane.iris.generation.locator;

import art.arcane.iris.generation.biome.IrisBiome;
import art.arcane.iris.generation.hydrology.IrisRiverPolicy;
import art.arcane.iris.generation.context.ChunkContext;
import art.arcane.iris.generation.context.IrisContext;
import art.arcane.iris.generation.runtime.Engine;
import art.arcane.iris.generation.runtime.IrisEngine;
import art.arcane.iris.generation.runtime.BiomeEnvironment;
import art.arcane.iris.world.history.GenerationHistoryRuntimeRouter;
import art.arcane.iris.world.history.SavedBiomeRuntime;
import art.arcane.iris.generation.runtime.GenerationSessionException;
import art.arcane.iris.generation.runtime.GenerationSessionLease;
import art.arcane.iris.generation.runtime.WrongEngineBroException;
import art.arcane.iris.generation.subterrain.IrisSubterrainFeature;
import art.arcane.iris.generation.subterrain.SubterrainCell;
import art.arcane.iris.generation.subterrain.SubterrainPlan;
import art.arcane.iris.generation.subterrain.SubterrainPosition;
import art.arcane.iris.generation.terrain.IrisDimensionCarvingEntry;
import art.arcane.iris.generation.terrain.IrisRegion;
import art.arcane.iris.world.history.GenerationSemanticIndex;
import art.arcane.iris.world.history.GenerationSemanticQueries;
import art.arcane.iris.world.history.GenerationFindCatalog;
import art.arcane.iris.world.history.GenerationHistory;
import art.arcane.iris.world.history.ChunkGenerationSemantics;
import art.arcane.volmlib.nativelib.terrain.NativeBlockState;
import art.arcane.volmlib.nativelib.terrain.NativeWorld;
import art.arcane.volmlib.util.math.Position2;
import art.arcane.volmlib.util.matter.MatterCavern;

import java.util.ArrayDeque;
import java.util.HashSet;
import java.util.HashMap;
import java.util.Map;
import java.util.Optional;
import java.util.function.BooleanSupplier;
import java.util.Set;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CompletionException;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.Consumer;

public final class BiomeLocator implements Locator<IrisBiome> {
    private static final Set<String> HAZARDOUS_FLOORS = Set.of("minecraft:magma_block", "minecraft:campfire",
            "minecraft:soul_campfire", "minecraft:cactus", "minecraft:powder_snow", "minecraft:pointed_dripstone");

    private volatile long searchDeadline = Long.MAX_VALUE;
    private final String key;
    private final boolean authored;
    private Set<String> caveHosts = Set.of();
    private boolean configured;
    private int originWorldY;
    private boolean surfaceAllowed;
    private final Set<Position2> surfaceTargets = ConcurrentHashMap.newKeySet();
    private volatile SubterrainPosition authoredTarget;
    private final ConcurrentHashMap<Position2, SubterrainPosition> cavernTargets = new ConcurrentHashMap<>();

    public BiomeLocator(String key, boolean authored) {
        this.key = key;
        this.authored = authored;
    }

    public static Locator<IrisBiome> forBiome(Engine engine, String key, int originWorldY) {
        boolean authored = false;
        boolean configured = GenerationFindCatalog.hasRetainedCaveBiome(engine, key);
        Set<String> caveHosts = new HashSet<>();
        boolean surfaceAllowed = GenerationFindCatalog.hasRetainedSurfaceBiome(engine, key);
        for (IrisRegion region : engine.getDimension().getAllRegions(engine)) {
            for (String root : region.getLandBiomes()) {
                surfaceAllowed |= caveBranch(engine, root).contains(key);
            }
            for (String root : region.getSeaBiomes()) {
                surfaceAllowed |= caveBranch(engine, root).contains(key);
            }
            for (String root : region.getShoreBiomes()) {
                surfaceAllowed |= caveBranch(engine, root).contains(key);
            }
            configured |= floodedBiome(region.getRiverPolicy(), key);
            for (String root : region.getCaveBiomes()) {
                Set<String> branch = caveBranch(engine, root);
                if (branch.contains(key)) {
                    caveHosts.addAll(branch);
                }
            }
        }
        for (IrisDimensionCarvingEntry entry : engine.getDimension().getCarving()) {
            if (entry.isEnabled() && !entry.getBiome().isEmpty() && caveBranch(engine, entry.getBiome()).contains(key)) {
                configured = true;
            }
        }
        for (IrisSubterrainFeature feature : engine.getDimension().getSubterrainFeatures()) {
            if (feature.isEnabled() && key.equals(feature.getBiome())) {
                authored = true;
            }
        }
        configured |= floodedBiome(engine.getDimension().getRiverPolicy(), key);
        for (IrisBiome biome : engine.getAllBiomes()) {
            configured |= key.equals(biome.getCarvingBiome()) || floodedBiome(biome.getRiverPolicy(), key);
        }
        if (!authored && !configured && caveHosts.isEmpty()) {
            return Locator.surfaceBiome(key);
        }
        BiomeLocator locator = new BiomeLocator(key, authored);
        locator.caveHosts = Set.copyOf(caveHosts);
        locator.configured = configured;
        locator.originWorldY = originWorldY;
        locator.surfaceAllowed = surfaceAllowed;
        return locator;
    }

    private static boolean floodedBiome(IrisRiverPolicy policy, String key) {
        return policy != null && policy.getFloodedCaveBiomes() != null && policy.getFloodedCaveBiomes().contains(key);
    }

    private static Set<String> caveBranch(Engine engine, String root) {
        Set<String> visited = new HashSet<>();
        ArrayDeque<String> pending = new ArrayDeque<>();
        pending.add(root);
        while (!pending.isEmpty()) {
            String candidate = pending.removeFirst();
            if (!visited.add(candidate)) {
                continue;
            }
            IrisBiome biome = engine.getData().getBiomeLoader().load(candidate);
            if (biome != null) {
                pending.addAll(biome.getChildren());
            }
        }
        return visited;
    }

    @Override
    public CompletableFuture<Position2> find(Engine engine, Position2 origin, long timeout, Consumer<Integer> checks)
            throws WrongEngineBroException {
        searchDeadline = System.nanoTime() + timeout * 1_000_000L;
        if (!authored || surfaceAllowed) {
            return Locator.super.find(engine, origin, timeout, checks);
        }
        CompletableFuture<Position2> result = new CompletableFuture<>();
        CompletableFuture.runAsync(() -> {
            long deadline = System.nanoTime() + timeout * 1_000_000L;
            try (GenerationSessionLease lease = engine.acquireGenerationLease("cave_biome_locator");
                 IrisContext.Scope ignored = IrisContext.open(engine, lease.sessionId(), null)) {
                BooleanSupplier running = () -> !result.isCancelled() && !engine.isClosing() && System.nanoTime() < deadline;
                Map<SubterrainLocator.Result, SubterrainLocator.Result> safeTargets = new HashMap<>();
                SubterrainLocator.Result found = GenerationSemanticQueries.nearestSubterrain(engine,
                        new SubterrainLocator.Query("", null, key), (origin.getX() << 4) + 8,
                        originWorldY, (origin.getZ() << 4) + 8, 32768,
                        running, candidate -> {
                            SubterrainLocator.Result safe = dryAuthoredTarget(engine, candidate, running);
                            if (safe == null) {
                                return false;
                            }
                            safeTargets.put(candidate, safe);
                            return true;
                        }).orElse(null);
                if (found != null) {
                    found = safeTargets.get(found);
                    authoredTarget = new SubterrainPosition(found.x(), found.y(), found.z());
                }
                checks.accept(1);
                result.complete(found == null ? null : new Position2(found.x() >> 4, found.z() >> 4));
            } catch (Throwable error) {
                result.completeExceptionally(error);
            }
        });
        return result;
    }

    @Override
    public SearchCandidate nearestRecordedCandidate(Engine engine, Position2 origin, int maximumRadius) {
        GenerationHistory history = GenerationLocatorPolicy.history(engine).orElse(null);
        if (history == null) {
            return null;
        }
        Set<Position2> rejected = new HashSet<>();
        GenerationSemanticIndex.Query query = GenerationSemanticIndex.Query.acrossActivations(
                GenerationSemanticIndex.SemanticKind.CAVE_BIOME, key,
                new ChunkGenerationSemantics.BlockPosition((origin.getX() << 4) + 8, originWorldY,
                        (origin.getZ() << 4) + 8), maximumRadius);
        while (!engine.isClosing() && !Thread.currentThread().isInterrupted() && System.nanoTime() < searchDeadline) {
            GenerationSemanticIndex.Match match = history.findRecorded(query,
                    record -> !rejected.contains(new Position2(record.chunkX(), record.chunkZ()))).orElse(null);
            if (match == null) {
                return null;
            }
            Position2 chunk = new Position2(match.chunk().chunkX(), match.chunk().chunkZ());
            SubterrainPosition target = position(engine, chunk);
            if (target != null) {
                cavernTargets.put(chunk, target);
                return new SearchCandidate(chunk, target.x(), target.z());
            }
            rejected.add(chunk);
        }
        return null;
    }

    @Override
    public boolean matches(Engine engine, Position2 chunk) {
        if (surfaceAllowed && Locator.chunkContainsSurfaceBiome(engine, chunk, key)) {
            surfaceTargets.add(chunk);
            return true;
        }
        if (authored) {
            return position(engine, chunk) != null;
        }
        if (position(engine, chunk) == null) {
            return false;
        }
        try (GenerationSessionLease lease = engine.acquireGenerationLease("cave_biome_matter")) {
            ChunkContext context = new ChunkContext(chunk.getX() << 4, chunk.getZ() << 4, engine.getComplex(),
                    lease.sessionId(), false, ChunkContext.PrefillPlan.NONE, null);
            try (IrisContext.Scope ignored = IrisContext.open(engine, lease.sessionId(), context)) {
                engine.generateMatter(chunk.getX(), chunk.getZ(), true, context);
                AtomicReference<SubterrainPosition> target = new AtomicReference<>();
                engine.getMantle().getMantle().iterateChunk(chunk.getX(), chunk.getZ(), MatterCavern.class, (x, y, z, cavern) -> {
                    if (target.get() != null || !dryCavern(cavern)) {
                        return;
                    }
                    int blockX = (chunk.getX() << 4) + x;
                    int blockZ = (chunk.getZ() << 4) + z;
                    SubterrainPosition candidate = cavernPosition(engine, blockX, y, blockZ, cavern);
                    if (candidate != null) {
                        target.set(candidate);
                    }
                });
                if (target.get() == null) {
                    return false;
                }
                cavernTargets.put(chunk, target.get());
                return true;
            }
        } catch (GenerationSessionException error) {
            throw new IllegalStateException(error);
        }
    }

    public SubterrainPosition position(Engine engine, Position2 chunk) {
        if (authoredTarget != null || cavernTargets.containsKey(chunk) || surfaceTargets.contains(chunk) || authored
                || activePredictionWithoutHistory(engine, chunk)) {
            return position(engine, chunk, Optional.empty());
        }
        try {
            CompletableFuture<Optional<SavedBiomeRuntime.ReadSession>> preparation = prepareRead(engine, chunk);
            Optional<SavedBiomeRuntime.ReadSession> read = searchDeadline == Long.MAX_VALUE
                    ? preparation.get() : preparation.get(Math.max(1L, searchDeadline - System.nanoTime()), TimeUnit.NANOSECONDS);
            return position(engine, chunk, read);
        } catch (TimeoutException error) {
            return null;
        } catch (InterruptedException error) {
            Thread.currentThread().interrupt();
            throw new CompletionException(error);
        } catch (ExecutionException error) {
            throw new CompletionException(error.getCause());
        }
    }

    private SubterrainPosition position(Engine engine, Position2 chunk, Optional<SavedBiomeRuntime.ReadSession> read) {
        return GenerationLocatorPolicy.evaluateValueScoped(engine, chunk, () -> positionScoped(engine, chunk, read));
    }

    private SubterrainPosition positionScoped(Engine engine, Position2 chunk, Optional<SavedBiomeRuntime.ReadSession> read) {
        if (surfaceTargets.contains(chunk)) {
            int x = (chunk.getX() << 4) + 8;
            int z = (chunk.getZ() << 4) + 8;
            return new SubterrainPosition(x, engine.getMinHeight() + engine.getHeight(x, z, false) + 2, z);
        }
        SubterrainPosition cavern = cavernTargets.get(chunk);
        if (cavern != null) {
            return cavern;
        }
        int x = (chunk.getX() << 4) + 8;
        int z = (chunk.getZ() << 4) + 8;
        if (authored) {
            if (authoredTarget != null && authoredTarget.x() >> 4 == chunk.getX()
                    && authoredTarget.z() >> 4 == chunk.getZ()) {
                return authoredTarget;
            }
            for (SubterrainPlan plan : engine.getComplex().getSubterrainPlanner().plansForBounds(x - 8, z - 8, x + 7, z + 7)) {
                if (!key.equals(plan.biome())) {
                    continue;
                }
                SubterrainPosition anchor = plan.anchor();
                SubterrainCell owner = engine.getComplex().getSubterrainPlanner().sample(anchor.x(), anchor.y(), anchor.z());
                if (owner.occupied() && plan.id().equals(owner.room().featureId())) {
                    SubterrainLocator.Result safe = dryAuthoredTarget(engine,
                            new SubterrainLocator.Result(plan.id(), plan.family(), plan.biome(), anchor.x(), anchor.y(), anchor.z()),
                            () -> !engine.isClosing());
                    if (safe != null) {
                        return new SubterrainPosition(safe.x(), safe.y(), safe.z());
                    }
                }
            }
            return null;
        }
        if (read.isEmpty() && !configured && !caveHosts.isEmpty()) {
            IrisBiome host = engine.getCaveBiome(x, z);
            if (host == null || !caveHosts.contains(host.getLoadKey())) {
                return null;
            }
        }
        for (int radius = 0; radius <= (read.isPresent() ? 8 : 0); radius++) {
            for (int columnX = Math.max(x - 8, x - radius); columnX <= Math.min(x + 7, x + radius); columnX++) {
                for (int columnZ = Math.max(z - 8, z - radius); columnZ <= Math.min(z + 7, z + radius); columnZ++) {
                    if (Math.max(Math.abs(columnX - x), Math.abs(columnZ - z)) != radius) {
                        continue;
                    }
                    int top = Math.min(engine.getHeight() - 2, engine.getHeight(columnX, columnZ) - 2);
                    for (int y = top; y >= 1; y--) {
                        if (Thread.currentThread().isInterrupted() || engine.isClosing() || System.nanoTime() >= searchDeadline) {
                            return null;
                        }
                        IrisBiome saved = savedBiomeAt(columnX, y + engine.getMinHeight(), columnZ, read);
                        IrisBiome biome = saved != null ? saved : engine.getCaveBiome(columnX, y, columnZ);
                        if (biome != null && key.equals(biome.getLoadKey())) {
                            return new SubterrainPosition(columnX, y + engine.getMinHeight(), columnZ);
                        }
                    }
                }
            }
        }
        return null;
    }

    private static SubterrainLocator.Result dryAuthoredTarget(Engine engine, SubterrainLocator.Result candidate,
                                                               BooleanSupplier running) {
        return GenerationSemanticQueries.drySubterrainLanding(engine, candidate, running).orElse(null);
    }

    private static boolean activePredictionWithoutHistory(Engine engine, Position2 chunk) {
        if (!(engine instanceof IrisEngine irisEngine)) {
            return true;
        }
        GenerationHistoryRuntimeRouter router = irisEngine.getGenerationHistoryRuntimeRouter().orElse(null);
        if (router == null) {
            return true;
        }
        GenerationHistory history = router.history();
        return history.isActiveUnowned(chunk.getX(), chunk.getZ()) && history.semantics(chunk.getX(), chunk.getZ()).isEmpty();
    }

    public CompletableFuture<Optional<SavedBiomeRuntime.ReadSession>> prepareRead(Engine engine, Position2 chunk) {
        if (engine instanceof IrisEngine irisEngine) {
            GenerationHistoryRuntimeRouter router = irisEngine.getGenerationHistoryRuntimeRouter().orElse(null);
            if (router != null) {
                return router.biomes().readChunkAsync(chunk.getX(), chunk.getZ()).thenApply(Optional::of);
            }
        }
        return CompletableFuture.completedFuture(Optional.empty());
    }

    public SubterrainPosition landing(Engine engine, NativeWorld world, SubterrainPosition target,
                                     Optional<SavedBiomeRuntime.ReadSession> read) {
        return GenerationLocatorPolicy.evaluateValueScoped(engine, new Position2(target.x() >> 4, target.z() >> 4),
                () -> landingScoped(engine, world, target, read));
    }

    private SubterrainPosition landingScoped(Engine engine, NativeWorld world, SubterrainPosition target,
                                             Optional<SavedBiomeRuntime.ReadSession> read) {
        if (surfaceTargets.contains(new Position2(target.x() >> 4, target.z() >> 4))) {
            return target;
        }
        int chunkX = target.x() >> 4;
        int chunkZ = target.z() >> 4;
        int minimumX = chunkX << 4;
        int minimumZ = chunkZ << 4;
        for (int radius = 0; radius <= 15; radius++) {
            for (int x = Math.max(minimumX, target.x() - radius); x <= Math.min(minimumX + 15, target.x() + radius); x++) {
                for (int z = Math.max(minimumZ, target.z() - radius); z <= Math.min(minimumZ + 15, target.z() + radius); z++) {
                    if (Math.max(Math.abs(x - target.x()), Math.abs(z - target.z())) != radius) {
                        continue;
                    }
                    int top = Math.min(world.maxHeight() - 2, engine.getMinHeight() + engine.getHeight(x, z) - 2);
                    for (int y = top; y > world.minHeight(); y--) {
                        NativeBlockState feet = world.getBlock(x, y, z);
                        if (!feet.isAir() || !world.getBlock(x, y + 1, z).isAir()) {
                            continue;
                        }
                        NativeBlockState floor = world.getBlock(x, y - 1, z);
                        if (!floor.isSolid() || floor.isFluid() || floor.isWaterLogged() || hazardous(floor)) {
                            continue;
                        }
                        IrisBiome biome = biomeAt(engine, x, y, z, read);
                        if (biome != null && key.equals(biome.getLoadKey())) {
                            return new SubterrainPosition(x, y, z);
                        }
                    }
                }
            }
        }
        return null;
    }

    private static IrisBiome biomeAt(Engine engine, int x, int worldY, int z,
                                      Optional<SavedBiomeRuntime.ReadSession> read) {
        IrisBiome saved = savedBiomeAt(x, worldY, z, read);
        return saved != null ? saved : engine.getBiomeOrMantle(x, worldY - engine.getMinHeight(), z);
    }

    private static IrisBiome savedBiomeAt(int x, int worldY, int z,
                                           Optional<SavedBiomeRuntime.ReadSession> read) {
        return read.flatMap(session -> session.resolve(x, worldY, z, false)).map(BiomeEnvironment::biome).orElse(null);
    }

    SubterrainPosition cavernPosition(Engine engine, int x, int y, int z, MatterCavern cavern) {
        if (!dryCavern(cavern)) {
            return null;
        }
        IrisBiome biome = engine.getBiomeOrMantle(x, y, z);
        return biome != null && key.equals(biome.getLoadKey())
                ? new SubterrainPosition(x, y + engine.getMinHeight(), z) : null;
    }

    private static boolean dryCavern(MatterCavern cavern) {
        return cavern != null && cavern.isCavern() && (cavern.isAir() || cavern.getLiquid() == 3);
    }

    private static boolean hazardous(NativeBlockState state) {
        String key = state.materialKey();
        if (key == null) {
            key = state.key().split("\\[", 2)[0];
        }
        return HAZARDOUS_FLOORS.contains(key);
    }
}
