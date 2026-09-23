package art.arcane.iris.probe;

import art.arcane.iris.generation.context.IrisContext;
import art.arcane.iris.generation.runtime.Engine;
import art.arcane.iris.generation.runtime.GenerationSessionLease;
import art.arcane.iris.generation.runtime.IrisEngine;
import art.arcane.iris.structure.nativegen.NativeStructureOwnershipRecord;
import art.arcane.iris.world.history.GenerationHistoryRuntimeRouter;
import art.arcane.iris.world.history.NativeTerrainReceipt;
import art.arcane.volmlib.nativelib.terrain.NativeBiome;
import art.arcane.volmlib.nativelib.terrain.NativeBiomeSourcePolicy;
import art.arcane.volmlib.nativelib.terrain.NativeBlockState;
import art.arcane.volmlib.nativelib.minecraft26_2.modded.ModdedBlockState;
import art.arcane.volmlib.util.hunk.Hunk;
import art.arcane.volmlib.nativelib.v26_3_R1.terrain.NativeTerrainPipeline;
import art.arcane.volmlib.nativelib.v26_3_R1.terrain.WorldgenTerrainHeightmaps;
import net.minecraft.core.Holder;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.biome.Biome;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.chunk.ProtoChunk;
import net.minecraft.world.level.chunk.status.ChunkStatus;
import net.minecraft.world.level.levelgen.Heightmap;
import org.bukkit.NamespacedKey;
import org.bukkit.persistence.PersistentDataType;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Set;
import java.util.EnumSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.Consumer;

final class HeadlessRegionTerrain {
    private static final NamespacedKey STRUCTURE_ACTIVATION = new NamespacedKey("iris", "structure_activation");
    private static final NamespacedKey NATURAL_TERRAIN = new NamespacedKey("iris", "natural_terrain");

    private HeadlessRegionTerrain() {
    }

    static final class Session {
        private final Map<Long, HeadlessStructurePlanner.PlannedChunk<NativeStructureOwnershipRecord>> chunks = new HashMap<>();
        private final RegionGenerationWindow.Policy workerPolicy;
        private HeadlessTerrainContext context;

        Session(RegionGenerationWindow.Policy workerPolicy) {
            this.workerPolicy = Objects.requireNonNull(workerPolicy, "workerPolicy");
        }

        Result generate(Request request, Consumer<String> progress) throws Exception {
            if (context != null && context != request.context()) {
                throw new IllegalArgumentException("Rolling regions require one native terrain context");
            }
            context = request.context();
            List<NativeRegionPlan.PlannedChunk> plan = NativeRegionPlan.plan(
                    request.chunkX(), request.chunkZ(), request.width());
            Set<Long> retained = new HashSet<>(plan.size());
            for (NativeRegionPlan.PlannedChunk planned : plan) {
                retained.add(ChunkPos.pack(planned.x(), planned.z()));
            }
            chunks.keySet().retainAll(retained);
            Map<Long, ChunkStatus> previous = new HashMap<>(chunks.size());
            for (Map.Entry<Long, HeadlessStructurePlanner.PlannedChunk<NativeStructureOwnershipRecord>> entry : chunks.entrySet()) {
                previous.put(entry.getKey(), entry.getValue().chunk().getPersistedStatus());
            }
            for (ChunkStatus status : List.of(ChunkStatus.STRUCTURE_STARTS, ChunkStatus.STRUCTURE_REFERENCES,
                    ChunkStatus.BIOMES, ChunkStatus.TERRAIN)) {
                List<NativeRegionPlan.PlannedChunk> eligible = new ArrayList<>();
                for (NativeRegionPlan.PlannedChunk planned : plan) {
                    HeadlessStructurePlanner.PlannedChunk<NativeStructureOwnershipRecord> existing =
                            chunks.get(ChunkPos.pack(planned.x(), planned.z()));
                    if (planned.status().isOrAfter(status)
                            && (status != ChunkStatus.BIOMES || planned.status() == ChunkStatus.BIOMES)
                            && (existing == null || existing.chunk().getPersistedStatus().isBefore(status))) {
                        eligible.add(planned);
                    }
                }
                if (eligible.isEmpty()) {
                    continue;
                }
                long started = System.nanoTime();
                AtomicInteger completed = new AtomicInteger();
                RegionGenerationWindow.process(new RegionGenerationWindow.Request<>(eligible.size(), request.parallelism(),
                        index -> apply(request.context(), eligible.get(index), status, chunks),
                        (index, result) -> {
                            if (status == ChunkStatus.STRUCTURE_STARTS) {
                                chunks.put(result.chunk().getPos().pack(), result);
                            }
                            int count = completed.incrementAndGet();
                            if (count % 128 == 0 || count == eligible.size()) {
                                progress.accept("stage=" + status.getName() + " chunks=" + count + "/" + eligible.size()
                                        + " seconds=" + (System.nanoTime() - started) / 1_000_000_000.0);
                            }
                        }), workerPolicy);
                failReported();
            }
            List<ProtoChunk> ordered = new ArrayList<>(plan.size());
            List<ProtoChunk> updated = new ArrayList<>();
            for (NativeRegionPlan.PlannedChunk planned : plan) {
                ProtoChunk chunk = chunks.get(ChunkPos.pack(planned.x(), planned.z())).chunk();
                if (chunk.getPersistedStatus().isBefore(planned.status())) {
                    throw new IllegalStateException("Incomplete native stage at " + chunk.getPos());
                }
                ordered.add(chunk);
                ChunkStatus prior = previous.get(chunk.getPos().pack());
                if (prior == null || chunk.getPersistedStatus().isAfter(prior)) {
                    updated.add(chunk);
                }
            }
            return new Result(List.copyOf(ordered), List.copyOf(updated), request.width() * request.width());
        }
    }

    private static HeadlessStructurePlanner.PlannedChunk<NativeStructureOwnershipRecord> apply(
            HeadlessTerrainContext context, NativeRegionPlan.PlannedChunk planned, ChunkStatus status,
            Map<Long, HeadlessStructurePlanner.PlannedChunk<NativeStructureOwnershipRecord>> chunks) throws Exception {
        Engine engine = context.engine();
        GenerationHistoryRuntimeRouter router = ((IrisEngine) engine).getGenerationHistoryRuntimeRouter().orElseThrow();
        try (GenerationHistoryRuntimeRouter.RuntimeRoute route = router.openRoute(planned.x(), planned.z());
             GenerationHistoryRuntimeRouter.RuntimeRoute.RuntimeScope scope = route.openRuntimeScope();
             GenerationSessionLease lease = engine.acquireGenerationLease("headless_native_terrain");
             IrisContext.Scope generation = IrisContext.open(engine, lease.sessionId(), null)) {
            if (status == ChunkStatus.STRUCTURE_STARTS) {
                HeadlessStructurePlanner.PlannedChunk<NativeStructureOwnershipRecord> result = HeadlessStructurePlanner.plan(
                        context.structures(), context.policies(), new ChunkPos(planned.x(), planned.z()));
                result.chunk().persistentDataContainer.set(STRUCTURE_ACTIVATION,
                        PersistentDataType.LONG, route.activation().activationId());
                return result;
            }
            HeadlessStructurePlanner.PlannedChunk<NativeStructureOwnershipRecord> result = Objects.requireNonNull(
                    chunks.get(ChunkPos.pack(planned.x(), planned.z())), "planned structure chunk");
            if (status == ChunkStatus.STRUCTURE_REFERENCES) {
                HeadlessStructurePlanner.references(context.policies(), result, chunks);
            } else if (status == ChunkStatus.BIOMES) {
                biomes(context, result.chunk());
            } else if (status == ChunkStatus.TERRAIN) {
                if (result.chunk().getPersistedStatus().isBefore(ChunkStatus.BIOMES)) {
                    biomes(context, result.chunk());
                }
                terrain(context, result.chunk(), route);
            } else {
                throw new IllegalArgumentException("Unsupported headless native stage " + status);
            }
            return result;
        }
    }

    private static void biomes(HeadlessTerrainContext context, ProtoChunk chunk) {
        context.biomes().prepareVisibleBiomeBatch();
        NativeBiomeSourcePolicy.VisibleResolver<Holder<Biome>> resolver = context.biomes().visibleResolver();
        chunk.fillBiomesFromNoise(resolver::biome);
        chunk.setPersistedStatus(ChunkStatus.BIOMES);
    }

    private static void terrain(HeadlessTerrainContext context, ProtoChunk target,
                                GenerationHistoryRuntimeRouter.RuntimeRoute route) throws Exception {
        Engine engine = context.engine();
        ChunkPos position = target.getPos();
        Hunk<NativeBlockState> blocks = Hunk.newArrayHunk(16, engine.getHeight(), 16);
        Hunk<NativeBiome> biomes = Hunk.newArrayHunk(16, engine.getHeight(), 16);
        engine.generate(position.x() << 4, position.z() << 4, blocks, biomes, true);
        context.writer().fill(target, new NativeRegionTerrainWriter.TerrainInput(position.x(), position.z(),
                engine.getMinHeight(), engine.getHeight(),
                (x, y, z) -> blockState(blocks.getRaw(x, y, z)),
                (x, y, z) -> Objects.requireNonNull(biomes.getRaw(x, y, z), "Generated native biome").key(), List.of()));
        NativeTerrainPipeline.claimGeneratedSemantics(route, target, engine.getMinHeight());
        target.persistentDataContainer.set(NATURAL_TERRAIN, PersistentDataType.BYTE_ARRAY,
                NativeTerrainReceipt.encode(route.naturalTerrain().orElseThrow(),
                        route.activation().activationId(), route.epoch().epochId()));
        WorldgenTerrainHeightmaps.primeTerrain(target,
                (x, z) -> engine.getHeight(x, z, false) + engine.getMinHeight() + 1,
                (x, z) -> engine.getHeight(x, z, true) + engine.getMinHeight() + 1);
        Heightmap.primeHeightmaps(target, EnumSet.of(Heightmap.Types.MOTION_BLOCKING,
                Heightmap.Types.MOTION_BLOCKING_NO_LEAVES, Heightmap.Types.OCEAN_FLOOR, Heightmap.Types.WORLD_SURFACE));
        target.setPersistedStatus(ChunkStatus.TERRAIN);
        target.markUnsaved();
    }

    private static BlockState blockState(NativeBlockState state) {
        return state == null ? Blocks.AIR.defaultBlockState() : ((ModdedBlockState) state).handle();
    }

    private static void failReported() {
        List<Throwable> reports = RealPackProbeSupport.drainReported();
        if (!reports.isEmpty()) {
            IllegalStateException failure = new IllegalStateException("Native terrain generation reported errors");
            for (Throwable report : reports) {
                failure.addSuppressed(report);
            }
            throw failure;
        }
    }

    record Request(HeadlessTerrainContext context, int chunkX, int chunkZ, int width, int parallelism) {
        Request {
            Objects.requireNonNull(context, "context");
            if (parallelism < 1 || parallelism > 32) {
                throw new IllegalArgumentException("Headless native parallelism must be 1..32");
            }
        }
    }

    record Result(List<ProtoChunk> chunks, List<ProtoChunk> updated, int targetChunks) {
    }
}
