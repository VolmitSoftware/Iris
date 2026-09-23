package art.arcane.iris.probe;

import ca.spottedleaf.moonrise.patches.chunk_system.io.MoonriseRegionFileIO.RegionDataController.IORunnable;
import ca.spottedleaf.moonrise.patches.chunk_system.io.MoonriseRegionFileIO.RegionDataController.WriteData;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.NbtIo;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.chunk.ProtoChunk;
import net.minecraft.world.level.chunk.status.ChunkStatus;
import net.minecraft.world.level.chunk.storage.RegionFile;
import net.minecraft.world.level.chunk.storage.RegionStorageInfo;

import java.io.DataInputStream;
import java.io.DataOutputStream;
import java.io.IOException;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;

final class OfflineRegionWriter {
    private static final int MAXIMUM_PREPARATION_WORKERS = 8;
    private static final int MAXIMUM_STATUS_REGIONS = 64;

    private final Options options;
    private final Map<Long, int[]> published = new LinkedHashMap<>(MAXIMUM_STATUS_REGIONS, 0.75F, true) {
        @Override
        protected boolean removeEldestEntry(Map.Entry<Long, int[]> eldest) {
            return size() > MAXIMUM_STATUS_REGIONS;
        }
    };

    OfflineRegionWriter(Options options) {
        this.options = Objects.requireNonNull(options, "options");
    }

    Result write(HeadlessRegionTerrain.Result generated) throws Exception {
        try (OpenRegions regions = new OpenRegions(options)) {
            List<ChunkWrite> pending = new ArrayList<>(generated.updated().size());
            for (ProtoChunk chunk : generated.updated()) {
                ChunkPos position = chunk.getPos();
                long regionKey = ChunkPos.pack(position.x() >> 5, position.z() >> 5);
                int[] statuses = published.computeIfAbsent(regionKey, ignored -> new int[1024]);
                int index = (position.x() & 31) + ((position.z() & 31) << 5);
                int status = chunk.getPersistedStatus().getIndex() + 2;
                if (statuses[index] >= status) {
                    continue;
                }
                RegionFile region = regions.get(position);
                if (statuses[index] == 0) {
                    statuses[index] = readStatus(region, position);
                    if (statuses[index] >= status) {
                        continue;
                    }
                }
                pending.add(new ChunkWrite(chunk, region, statuses, index, status));
            }
            if (pending.isEmpty()) {
                return new Result(0, 0);
            }
            int[] counts = new int[2];
            RegionGenerationWindow.process(new RegionGenerationWindow.Request<>(pending.size(),
                    Math.min(options.parallelism(), MAXIMUM_PREPARATION_WORKERS),
                    index -> prepare(pending.get(index)),
                    (index, prepared) -> {
                        ChunkWrite chunk = prepared.chunk();
                        prepared.commit().run(chunk.region());
                        chunk.statuses()[chunk.index()] = chunk.status();
                        counts[0]++;
                        if (chunk.chunk().getPersistedStatus().isOrAfter(ChunkStatus.TERRAIN)) {
                            counts[1]++;
                        }
                    }), options.policy());
            return new Result(counts[0], counts[1]);
        }
    }

    private PreparedWrite prepare(ChunkWrite chunk) throws IOException {
        CompoundTag tag = HeadlessChunkSerialization.copyOf(chunk.chunk(), options.context().serialization()).write();
        WriteData prepared = chunk.region().moonrise$startWrite(tag, chunk.chunk().getPos());
        try (DataOutputStream output = prepared.output()) {
            NbtIo.write(tag, output);
        }
        return new PreparedWrite(chunk, prepared.write());
    }

    private static int readStatus(RegionFile region, ChunkPos position) throws IOException {
        try (DataInputStream input = region.getChunkDataInputStream(position)) {
            if (input == null) {
                return 1;
            }
            ChunkStatus status = ChunkStatus.byName(NbtIo.read(input).getStringOr("Status", ""));
            if (status == null) {
                throw new IOException("Invalid native chunk status at " + position);
            }
            return status.getIndex() + 2;
        }
    }

    record Options(HeadlessTerrainContext context, Path directory, int parallelism, RegionGenerationWindow.Policy policy) {
        Options {
            Objects.requireNonNull(context, "context");
            Objects.requireNonNull(directory, "directory");
            Objects.requireNonNull(policy, "policy");
            if (parallelism < 1 || parallelism > 32) {
                throw new IllegalArgumentException("Offline writer parallelism must be 1..32");
            }
        }
    }

    record Result(int chunks, int terrainChunks) {
    }

    private record ChunkWrite(ProtoChunk chunk, RegionFile region, int[] statuses, int index, int status) {
    }

    private record PreparedWrite(ChunkWrite chunk, IORunnable commit) {
    }

    private static final class OpenRegions implements AutoCloseable {
        private final Options options;
        private final Map<Long, RegionFile> regions = new HashMap<>();

        private OpenRegions(Options options) {
            this.options = options;
        }

        private RegionFile get(ChunkPos position) throws IOException {
            long key = ChunkPos.pack(position.x() >> 5, position.z() >> 5);
            RegionFile region = regions.get(key);
            if (region != null) {
                return region;
            }
            Path path = options.directory().resolve("r." + (position.x() >> 5) + "." + (position.z() >> 5) + ".mca");
            region = new RegionFile(new RegionStorageInfo("headless-native-terrain",
                    options.context().structures().levelKey(), "chunk"), path, options.directory(), false);
            regions.put(key, region);
            return region;
        }

        @Override
        public void close() throws IOException {
            IOException failure = null;
            for (RegionFile region : regions.values()) {
                try {
                    region.close();
                } catch (IOException exception) {
                    if (failure == null) {
                        failure = exception;
                    } else {
                        failure.addSuppressed(exception);
                    }
                }
            }
            if (failure != null) {
                throw failure;
            }
        }
    }
}
