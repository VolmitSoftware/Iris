package art.arcane.iris.generation.hydrology;

import art.arcane.iris.generation.hydrology.cave.CavePosition;
import art.arcane.iris.generation.hydrology.cave.CaveVoxelPrecondition;
import art.arcane.iris.generation.hydrology.cave.HydrologyCaveAction;
import art.arcane.iris.generation.hydrology.cave.HydrologyCavePlan;
import art.arcane.iris.generation.hydrology.cave.HydrologyCaveRejection;
import art.arcane.iris.generation.hydrology.cave.HydrologyCaveSource;
import com.google.gson.Gson;
import com.google.gson.GsonBuilder;

import java.io.BufferedInputStream;
import java.io.BufferedOutputStream;
import java.io.FilterInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.OutputStream;
import java.io.OutputStreamWriter;
import java.io.Reader;
import java.io.Writer;
import java.nio.charset.StandardCharsets;
import java.nio.file.AtomicMoveNotSupportedException;
import java.nio.file.Files;
import java.nio.file.LinkOption;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HexFormat;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.OptionalLong;
import java.util.zip.GZIPInputStream;
import java.util.zip.GZIPOutputStream;

final class StudioHydrologyTileStore {
    private static final int SCHEMA_VERSION = 3;
    private static final long MAXIMUM_COMPRESSED_BYTES = 128L * 1024L * 1024L;
    private static final long MAXIMUM_DECOMPRESSED_BYTES = 512L * 1024L * 1024L;
    private static final Gson GSON = new GsonBuilder()
            .disableHtmlEscaping()
            .serializeSpecialFloatingPointValues()
            .create();

    private final Path directory;
    private final HydrologyTileCache.SharedCacheScope scope;
    private final int expectedTileSize;

    StudioHydrologyTileStore(Path root, HydrologyTileCache.SharedCacheScope scope, int expectedTileSize) {
        this.scope = Objects.requireNonNull(scope, "scope");
        if (expectedTileSize < 1) {
            throw new IllegalArgumentException("Hydrology tile size must be positive.");
        }
        this.expectedTileSize = expectedTileSize;
        this.directory = Objects.requireNonNull(root, "root")
                .toAbsolutePath()
                .normalize()
                .resolve(scopeFingerprint(scope));
    }

    Optional<HydrologyTile> load(HydrologyTileKey key) {
        Path file = file(key);
        try {
            if (!Files.isRegularFile(file, LinkOption.NOFOLLOW_LINKS)
                    || Files.isSymbolicLink(file)
                    || Files.size(file) > MAXIMUM_COMPRESSED_BYTES) {
                return Optional.empty();
            }
        } catch (IOException failure) {
            return Optional.empty();
        }
        try (InputStream raw = Files.newInputStream(file);
             InputStream buffered = new BufferedInputStream(raw);
             InputStream compressed = new GZIPInputStream(buffered);
             InputStream bounded = new LimitedInputStream(compressed, MAXIMUM_DECOMPRESSED_BYTES);
             Reader reader = new InputStreamReader(bounded, StandardCharsets.UTF_8)) {
            PersistedTile persisted = GSON.fromJson(reader, PersistedTile.class);
            if (persisted == null
                    || persisted.schemaVersion() != SCHEMA_VERSION
                    || !key.equals(persisted.key())
                    || persisted.worldSeed() != scope.worldSeed()
                    || persisted.settingsFingerprint() != scope.settingsFingerprint()) {
                return Optional.empty();
            }
            HydrologyTile tile = persisted.toTile();
            return valid(tile, key) ? Optional.of(tile) : Optional.empty();
        } catch (IOException | RuntimeException failure) {
            return Optional.empty();
        }
    }

    void save(HydrologyTile tile) throws IOException {
        if (!valid(tile, tile.key())) {
            throw new IOException("Refused to persist a hydrology tile outside the active Studio cache scope.");
        }
        Files.createDirectories(directory);
        Path target = file(tile.key());
        Path staged = Files.createTempFile(directory, ".hydrology-tile-", ".tmp");
        try {
            try (OutputStream raw = Files.newOutputStream(staged);
                 OutputStream buffered = new BufferedOutputStream(raw);
                 OutputStream compressed = new GZIPOutputStream(buffered);
                 Writer writer = new OutputStreamWriter(compressed, StandardCharsets.UTF_8)) {
                GSON.toJson(PersistedTile.from(tile), writer);
            }
            if (Files.size(staged) > MAXIMUM_COMPRESSED_BYTES) {
                throw new IOException("Studio hydrology tile cache entry exceeds the size limit.");
            }
            try {
                Files.move(staged, target, StandardCopyOption.ATOMIC_MOVE, StandardCopyOption.REPLACE_EXISTING);
            } catch (AtomicMoveNotSupportedException exception) {
                Files.move(staged, target, StandardCopyOption.REPLACE_EXISTING);
            }
        } finally {
            Files.deleteIfExists(staged);
        }
    }

    Path file(HydrologyTileKey key) {
        return directory.resolve("tile-" + key.tileX() + "-" + key.tileZ() + ".json.gz");
    }

    private boolean valid(HydrologyTile tile, HydrologyTileKey key) {
        return tile != null
                && key.equals(tile.key())
                && tile.worldSeed() == scope.worldSeed()
                && tile.settingsFingerprint() == scope.settingsFingerprint()
                && tile.tileSize() == expectedTileSize;
    }

    private static String scopeFingerprint(HydrologyTileCache.SharedCacheScope scope) {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            update(digest, scope.runtimeIdentity());
            update(digest, Long.toString(scope.worldSeed()));
            update(digest, Integer.toString(scope.worldHeight()));
            update(digest, scope.dimensionKey());
            update(digest, Long.toString(scope.settingsFingerprint()));
            return HexFormat.of().formatHex(digest.digest());
        } catch (NoSuchAlgorithmException exception) {
            throw new IllegalStateException("SHA-256 is unavailable.", exception);
        }
    }

    private static void update(MessageDigest digest, String value) {
        byte[] bytes = value.getBytes(StandardCharsets.UTF_8);
        digest.update((byte) (bytes.length >>> 24));
        digest.update((byte) (bytes.length >>> 16));
        digest.update((byte) (bytes.length >>> 8));
        digest.update((byte) bytes.length);
        digest.update(bytes);
    }

    private record PersistedTile(
            int schemaVersion,
            HydrologyTileKey key,
            long worldSeed,
            long settingsFingerprint,
            int tileSize,
            List<DrainageNode> nodes,
            List<DrainageEdge> edges,
            List<RiverOutlet> outlets,
            List<PersistedCourse> courses,
            List<PersistedCavePlan> cavePlans,
            List<HydrologyDiagnosticCandidate> diagnosticCandidates,
            List<HydrologyColumnSample> columns
    ) {
        private static PersistedTile from(HydrologyTile tile) {
            ArrayList<PersistedCourse> courses = new ArrayList<>(tile.courses().size());
            for (RiverCourse course : tile.courses()) {
                courses.add(PersistedCourse.from(course));
            }
            ArrayList<PersistedCavePlan> cavePlans = new ArrayList<>(tile.cavePlans().size());
            for (HydrologyCavePlan cavePlan : tile.cavePlans()) {
                cavePlans.add(PersistedCavePlan.from(cavePlan));
            }
            return new PersistedTile(
                    SCHEMA_VERSION,
                    tile.key(),
                    tile.worldSeed(),
                    tile.settingsFingerprint(),
                    tile.tileSize(),
                    tile.nodes(),
                    tile.edges(),
                    tile.outlets(),
                    courses,
                    cavePlans,
                    tile.diagnosticCandidates(),
                    new ArrayList<>(tile.footprint().columns().values())
            );
        }

        private HydrologyTile toTile() {
            requireCollection(nodes, "nodes");
            requireCollection(edges, "edges");
            requireCollection(outlets, "outlets");
            requireCollection(courses, "courses");
            requireCollection(cavePlans, "cavePlans");
            requireCollection(diagnosticCandidates, "diagnosticCandidates");
            requireCollection(columns, "columns");
            ArrayList<RiverCourse> restoredCourses = new ArrayList<>(courses.size());
            for (PersistedCourse course : courses) {
                restoredCourses.add(Objects.requireNonNull(course, "course").toCourse());
            }
            ArrayList<HydrologyCavePlan> restoredCavePlans = new ArrayList<>(cavePlans.size());
            for (PersistedCavePlan cavePlan : cavePlans) {
                restoredCavePlans.add(Objects.requireNonNull(cavePlan, "cavePlan").toPlan());
            }
            HashMap<Long, HydrologyColumnSample> restoredColumns = HashMap.newHashMap(columns.size());
            for (HydrologyColumnSample column : columns) {
                HydrologyColumnSample required = Objects.requireNonNull(column, "column");
                HydrologyColumnSample existing = restoredColumns.put(
                        RiverFootprint.pack(required.x(), required.z()), required);
                if (existing != null) {
                    throw new IllegalArgumentException("Duplicate hydrology footprint column.");
                }
            }
            return new HydrologyTile(
                    key,
                    worldSeed,
                    settingsFingerprint,
                    tileSize,
                    nodes,
                    edges,
                    outlets,
                    restoredCourses,
                    restoredCavePlans,
                    diagnosticCandidates,
                    new RiverFootprint(restoredColumns)
            );
        }

        private static void requireCollection(List<?> values, String name) {
            Objects.requireNonNull(values, name);
        }
    }

    private record PersistedCourse(
            long id,
            RiverCourseType type,
            Long sourceNodeId,
            Long outletId,
            String profileKey,
            int discharge,
            List<DrainageEdge> drainageEdges,
            List<HydraulicSegment> segments
    ) {
        private static PersistedCourse from(RiverCourse course) {
            return new PersistedCourse(
                    course.id(),
                    course.type(),
                    course.sourceNodeId().isPresent() ? course.sourceNodeId().getAsLong() : null,
                    course.outletId().isPresent() ? course.outletId().getAsLong() : null,
                    course.profileKey(),
                    course.discharge(),
                    course.drainageEdges(),
                    course.segments()
            );
        }

        private RiverCourse toCourse() {
            return new RiverCourse(
                    id,
                    type,
                    sourceNodeId == null ? OptionalLong.empty() : OptionalLong.of(sourceNodeId),
                    outletId == null ? OptionalLong.empty() : OptionalLong.of(outletId),
                    profileKey,
                    discharge,
                    drainageEdges,
                    segments
            );
        }
    }

    private record PersistedCavePlan(
            HydrologyCaveSource source,
            HydrologyCaveRejection rejection,
            List<PersistedCaveCell> cells,
            Long arbitrationWinnerSourceId
    ) {
        private static PersistedCavePlan from(HydrologyCavePlan plan) {
            ArrayList<PersistedCaveCell> cells = new ArrayList<>(plan.baselinePreconditions().size());
            plan.forEachPrecondition((CavePosition position, CaveVoxelPrecondition precondition) -> cells.add(
                    new PersistedCaveCell(position, plan.actions().get(position), precondition)));
            cells.sort(Comparator.comparingInt((PersistedCaveCell cell) -> cell.position().x())
                    .thenComparingInt(cell -> cell.position().z())
                    .thenComparingInt(cell -> cell.position().y()));
            return new PersistedCavePlan(
                    plan.source(),
                    plan.rejection(),
                    cells,
                    plan.arbitrationWinnerSourceId().isPresent()
                            ? plan.arbitrationWinnerSourceId().getAsLong()
                            : null
            );
        }

        private HydrologyCavePlan toPlan() {
            LinkedHashMap<CavePosition, HydrologyCaveAction> actions = new LinkedHashMap<>();
            LinkedHashMap<CavePosition, CaveVoxelPrecondition> preconditions = new LinkedHashMap<>();
            for (PersistedCaveCell cell : Objects.requireNonNull(cells, "cells")) {
                PersistedCaveCell required = Objects.requireNonNull(cell, "cell");
                CaveVoxelPrecondition existing = preconditions.put(required.position(), required.precondition());
                if (existing != null) {
                    throw new IllegalArgumentException("Duplicate hydrology cave cell.");
                }
                if (required.action() != null) {
                    actions.put(required.position(), required.action());
                }
            }
            return new HydrologyCavePlan(
                    source,
                    rejection,
                    actions,
                    preconditions,
                    arbitrationWinnerSourceId == null
                            ? OptionalLong.empty()
                            : OptionalLong.of(arbitrationWinnerSourceId)
            );
        }
    }

    private record PersistedCaveCell(
            CavePosition position,
            HydrologyCaveAction action,
            CaveVoxelPrecondition precondition
    ) {
        private PersistedCaveCell {
            Objects.requireNonNull(position, "position");
            Objects.requireNonNull(precondition, "precondition");
        }
    }

    private static final class LimitedInputStream extends FilterInputStream {
        private final long limit;
        private long count;

        private LimitedInputStream(InputStream input, long limit) {
            super(input);
            this.limit = limit;
        }

        @Override
        public int read() throws IOException {
            int value = super.read();
            if (value >= 0) {
                advance(1L);
            }
            return value;
        }

        @Override
        public int read(byte[] bytes, int offset, int length) throws IOException {
            int read = super.read(bytes, offset, length);
            if (read > 0) {
                advance(read);
            }
            return read;
        }

        private void advance(long amount) throws IOException {
            count += amount;
            if (count > limit) {
                throw new IOException("Studio hydrology tile cache entry exceeds the decompressed size limit.");
            }
        }
    }
}
