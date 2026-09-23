package art.arcane.iris.probe;

import art.arcane.iris.probe.NativeRegionPlan.PlannedChunk;

import net.minecraft.nbt.CompoundTag;
import org.bukkit.NamespacedKey;
import org.bukkit.persistence.PersistentDataType;
import net.minecraft.server.level.ChunkMap;
import net.minecraft.server.level.GenerationChunkHolder;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.util.StaticCache2D;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.chunk.ChunkAccess;
import net.minecraft.world.level.chunk.ProtoChunk;
import net.minecraft.world.level.chunk.UpgradeData;
import net.minecraft.world.level.chunk.status.ChunkPyramid;
import net.minecraft.world.level.chunk.status.ChunkStatus;
import net.minecraft.world.level.chunk.status.ChunkStep;
import net.minecraft.world.level.chunk.status.WorldGenContext;
import net.minecraft.world.level.chunk.storage.RegionFile;
import net.minecraft.world.level.chunk.storage.RegionStorageInfo;
import net.minecraft.world.level.chunk.storage.SerializableChunkData;

import java.io.IOException;
import java.lang.reflect.Field;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.HexFormat;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.function.Consumer;
import java.util.concurrent.atomic.AtomicInteger;

final class DetachedNativeTerrain {
    private static final NamespacedKey NATURAL_TERRAIN = new NamespacedKey("iris", "natural_terrain");

    private DetachedNativeTerrain() {
    }

    static Result generate(Request request, Consumer<String> progress) throws Exception {
        List<PlannedChunk> plan = NativeRegionPlan.plan(request.chunkX(), request.chunkZ(), request.width());
        Path output = Files.createDirectory(request.output());
        WorldGenContext context = context(request.level().getChunkSource().chunkMap);
        Map<Long, DetachedHolder> holders = new HashMap<>(plan.size());
        for (PlannedChunk planned : plan) {
            ChunkPos position = new ChunkPos(planned.x(), planned.z());
            holders.put(position.pack(), new DetachedHolder(new ProtoChunk(position, UpgradeData.EMPTY,
                    request.level(), request.level().palettedContainerFactory(), null)));
        }
        long started = System.nanoTime();
        for (ChunkStatus status : ChunkStatus.getStatusList()) {
            if (status == ChunkStatus.EMPTY) {
                continue;
            }
            if (status.isAfter(ChunkStatus.TERRAIN)) {
                break;
            }
            ChunkStep step = ChunkPyramid.GENERATION_PYRAMID.getStepTo(status);
            executeStage(new Stage(context, step, plan, holders, request.parallelism()), progress);
        }
        long serializationStarted = System.nanoTime();
        write(request.level(), output, plan, holders);
        progress.accept("stage=native_serialization seconds=" + (System.nanoTime() - serializationStarted) / 1_000_000_000.0);
        double seconds = (System.nanoTime() - started) / 1_000_000_000.0;
        return new Result(request.width() * request.width(), plan.size(), seconds);
    }

    private static void executeStage(Stage stage, Consumer<String> progress) throws Exception {
        long started = System.nanoTime();
        List<PlannedChunk> eligible = new ArrayList<>();
        for (PlannedChunk planned : stage.plan()) {
            if (planned.status().isOrAfter(stage.step().targetStatus())) {
                eligible.add(planned);
            }
        }
        AtomicInteger completed = new AtomicInteger();
        AtomicInteger targetCompleted = new AtomicInteger();
        RegionGenerationWindow.process(new RegionGenerationWindow.Request<>(eligible.size(), stage.parallelism(),
                index -> {
                    PlannedChunk planned = eligible.get(index);
                    apply(stage, planned);
                    return planned;
                }, (index, planned) -> {
                    int count = completed.incrementAndGet();
                    if (planned.target()) {
                        targetCompleted.incrementAndGet();
                    }
                    if (count % 128 == 0) {
                        progress.accept("stage=" + stage.step().targetStatus().getName() + " completed=" + count
                                + " elapsedSeconds=" + (System.nanoTime() - started) / 1_000_000_000.0);
                    }
                }));
        progress.accept("stage=" + stage.step().targetStatus().getName() + " chunks=" + completed.get()
                + " targetChunks=" + targetCompleted.get() + " haloChunks=" + (completed.get() - targetCompleted.get())
                + " seconds=" + (System.nanoTime() - started) / 1_000_000_000.0);
    }

    private static void apply(Stage stage, PlannedChunk planned) {
        DetachedHolder center = requireHolder(stage.holders(), planned.x(), planned.z());
        StaticCache2D<GenerationChunkHolder> cache = StaticCache2D.create(planned.x(), planned.z(),
                stage.step().directDependencies().size() - 1, (x, z) -> requireHolder(stage.holders(), x, z));
        ChunkAccess result = stage.step().apply(stage.context(), cache, center.chunk).join();
        if (result != center.chunk || result.getPersistedStatus() != stage.step().targetStatus()) {
            throw new IllegalStateException("Native stage did not retain its detached chunk: " + stage.step().targetStatus());
        }
    }

    private static DetachedHolder requireHolder(Map<Long, DetachedHolder> holders, int x, int z) {
        DetachedHolder holder = holders.get(ChunkPos.pack(x, z));
        if (holder == null) {
            throw new IllegalStateException("Native dependency escaped the terrain halo at " + x + "," + z);
        }
        return holder;
    }

    private static WorldGenContext context(ChunkMap map) throws ReflectiveOperationException {
        for (Field field : ChunkMap.class.getDeclaredFields()) {
            if (field.getType() == WorldGenContext.class) {
                field.setAccessible(true);
                return (WorldGenContext) field.get(map);
            }
        }
        throw new IllegalStateException("Native world generation context is unavailable");
    }

    private static void write(ServerLevel level, Path output, List<PlannedChunk> plan,
                              Map<Long, DetachedHolder> holders) throws IOException {
        List<PlannedChunk> ordered = new ArrayList<>(plan);
        ordered.sort(Comparator.comparingInt((PlannedChunk chunk) -> chunk.z() >> 5)
                .thenComparingInt(chunk -> chunk.x() >> 5).thenComparingInt(PlannedChunk::z).thenComparingInt(PlannedChunk::x));
        StringBuilder receipts = new StringBuilder();
        RegionFile region = null;
        long currentRegion = Long.MIN_VALUE;
        try {
            for (PlannedChunk planned : ordered) {
                if (planned.status() == ChunkStatus.EMPTY) {
                    continue;
                }
                long key = ChunkPos.pack(planned.x() >> 5, planned.z() >> 5);
                if (key != currentRegion) {
                    if (region != null) {
                        region.close();
                    }
                    Path file = output.resolve("r." + (planned.x() >> 5) + "." + (planned.z() >> 5) + ".mca");
                    region = new RegionFile(new RegionStorageInfo(level.serverLevelData.getLevelName(),
                            level.dimension(), "chunk"), file, output, false);
                    currentRegion = key;
                }
                ChunkAccess chunk = holders.get(ChunkPos.pack(planned.x(), planned.z())).chunk;
                if (planned.status() == ChunkStatus.TERRAIN) {
                    receipts.append(planned.x()).append(",").append(planned.z()).append("=")
                            .append(receiptHash(chunk)).append("\n");
                }
                CompoundTag tag = SerializableChunkData.copyOf(level, chunk).write();
                NativeRegionTerrainWriter.write(region, tag);
            }
            Files.writeString(output.resolve("receipts.properties"), receipts.toString(), StandardCharsets.UTF_8);
        } finally {
            if (region != null) {
                region.close();
            }
        }
    }


    static String receiptHash(ChunkAccess chunk) {
        byte[] receipt = chunk.persistentDataContainer.get(NATURAL_TERRAIN,
                PersistentDataType.BYTE_ARRAY);
        if (receipt == null || receipt.length == 0) {
            throw new IllegalStateException("Missing Iris natural terrain receipt at " + chunk.getPos());
        }
        try {
            return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(receipt));
        } catch (NoSuchAlgorithmException failure) {
            throw new IllegalStateException(failure);
        }
    }

    record Request(ServerLevel level, int chunkX, int chunkZ, int width, int parallelism, Path output) {
        Request {
            if (parallelism < 1 || parallelism > 16) {
                throw new IllegalArgumentException("Native terrain worker count must be 1..16");
            }
        }
    }

    private record Stage(WorldGenContext context, ChunkStep step, List<PlannedChunk> plan,
                         Map<Long, DetachedHolder> holders, int parallelism) {
    }

    record Result(int terrainChunks, int dependencyChunks, double seconds) {
    }


    private static final class DetachedHolder extends GenerationChunkHolder {
        private final ProtoChunk chunk;

        private DetachedHolder(ProtoChunk chunk) {
            super(chunk.getPos());
            this.chunk = chunk;
        }

        @Override
        public ChunkAccess getChunkIfPresentUnchecked(ChunkStatus status) {
            if (chunk.getPersistedStatus().isBefore(status)) {
                throw new IllegalStateException("Native dependency is not ready: " + getPos() + " needs " + status);
            }
            return chunk;
        }

        @Override
        protected void addSaveDependency(CompletableFuture<?> dependency) {
            throw new UnsupportedOperationException("Detached chunks have no live save queue");
        }

        @Override
        public int getTicketLevel() {
            throw new UnsupportedOperationException("Detached chunks have no live tickets");
        }

        @Override
        public int getQueueLevel() {
            throw new UnsupportedOperationException("Detached chunks have no live queue");
        }
    }
}
