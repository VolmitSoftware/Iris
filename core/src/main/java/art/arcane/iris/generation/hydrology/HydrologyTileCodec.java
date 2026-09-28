package art.arcane.iris.generation.hydrology;

import art.arcane.iris.generation.hydrology.cave.CavePosition;
import art.arcane.iris.generation.hydrology.cave.HydrologyCaveMode;
import art.arcane.iris.generation.hydrology.cave.HydrologyCavePlan;
import art.arcane.iris.generation.hydrology.cave.HydrologyCaveRejection;
import art.arcane.iris.generation.hydrology.cave.HydrologyCaveSource;
import art.arcane.iris.generation.hydrology.policy.SurfaceRiverPolicy;

import java.io.DataInputStream;
import java.io.DataOutputStream;
import java.io.IOException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.IdentityHashMap;
import java.util.List;
import java.util.Map;
import java.util.OptionalLong;
import java.util.Set;

final class HydrologyTileCodec {
    private static final int MAGIC = 0x49524854;
    private static final int FORMAT = 2;
    private static final long MAXIMUM_ALLOCATION_BYTES = Math.max(16L * 1024L * 1024L,
            Math.min(512L * 1024L * 1024L, Runtime.getRuntime().maxMemory() / 8L));
    private static final int MAXIMUM_COLLECTION_SIZE = 2_000_000;
    private static final HydrologyFeatureType[] HYDROLOGY_FEATURE_TYPE_VALUES = HydrologyFeatureType.values();
    private static final RiverCourseType[] RIVER_COURSE_TYPE_VALUES = RiverCourseType.values();
    private static final HydrologyCandidateKind[] HYDROLOGY_CANDIDATE_KIND_VALUES = HydrologyCandidateKind.values();
    private static final HydrologyCandidateRejection[] HYDROLOGY_CANDIDATE_REJECTION_VALUES = HydrologyCandidateRejection.values();
    private static final HydrologyCaveMode[] HYDROLOGY_CAVE_MODE_VALUES = HydrologyCaveMode.values();
    private static final HydrologyCaveRejection[] HYDROLOGY_CAVE_REJECTION_VALUES = HydrologyCaveRejection.values();

    private final Map<String, String> strings = new HashMap<>();
    private long remainingAllocation = MAXIMUM_ALLOCATION_BYTES;

    void write(DataOutputStream output, HydrologyTile tile, String scopeIdentity) throws IOException {
        output.writeInt(MAGIC);
        output.writeInt(FORMAT);
        output.writeUTF(scopeIdentity);
        output.writeInt(tile.key().tileX());
        output.writeInt(tile.key().tileZ());
        output.writeLong(tile.worldSeed());
        output.writeLong(tile.settingsFingerprint());
        output.writeInt(tile.tileSize());
        writeList(output, tile.nodes(), this::writeDrainageNode);
        writeList(output, tile.edges(), this::writeDrainageEdge);
        writeList(output, tile.outlets(), this::writeRiverOutlet);
        writeList(output, tile.courses(), this::writeRiverCourse);
        writeCount(output, tile.regionalCourseIds().size(), 64);
        for (long id : tile.regionalCourseIds()) {
            output.writeLong(id);
        }
        writeList(output, tile.cavePlans(), this::writeCavePlan);
        writeList(output, tile.localDiagnosticCandidates(), this::writeHydrologyDiagnosticCandidate);
        writeCount(output, tile.footprint().columns().size(), 256);
        for (HydrologyColumnSample column : tile.footprint().columns().values()) {
            writeHydrologyColumnSample(output, column);
        }
        output.writeBoolean(tile.resolvedOwner() != null);
        if (tile.resolvedOwner() != null) {
            writeResolvedOwner(output, tile);
        }
    }

    HydrologyTile read(DataInputStream input, String scopeIdentity, HydrologyTileKey key,
                       HydrologyTileCache.SharedCacheScope scope, int expectedTileSize) throws IOException {
        if (input.readInt() != MAGIC || input.readInt() != FORMAT || !scopeIdentity.equals(input.readUTF())
                || input.readInt() != key.tileX() || input.readInt() != key.tileZ()
                || input.readLong() != scope.worldSeed() || input.readLong() != scope.settingsFingerprint()
                || input.readInt() != expectedTileSize) {
            throw new IOException("Prepared hydrology tile header does not match its scope.");
        }
        List<DrainageNode> nodes = readList(input, this::readDrainageNode);
        List<DrainageEdge> edges = readList(input, this::readDrainageEdge);
        List<RiverOutlet> outlets = readList(input, this::readRiverOutlet);
        List<RiverCourse> courses = readList(input, this::readRiverCourse);
        int regionalCount = readCount(input, 64);
        Set<Long> regionalCourseIds = HashSet.newHashSet(regionalCount);
        for (int index = 0; index < regionalCount; index++) {
            if (!regionalCourseIds.add(input.readLong())) {
                throw new IOException("Duplicate regional hydrology course.");
            }
        }
        List<HydrologyCavePlan> cavePlans = readList(input, this::readCavePlan);
        List<HydrologyDiagnosticCandidate> candidates = readList(input, this::readHydrologyDiagnosticCandidate);
        int columnCount = readCount(input, 256);
        Map<Long, HydrologyColumnSample> columns = HashMap.newHashMap(columnCount);
        for (int index = 0; index < columnCount; index++) {
            HydrologyColumnSample column = readHydrologyColumnSample(input);
            if (columns.put(RiverFootprint.pack(column.x(), column.z()), column) != null) {
                throw new IOException("Duplicate hydrology footprint column.");
            }
        }
        HydrologyTile tile = new HydrologyTile(key, scope.worldSeed(), scope.settingsFingerprint(), expectedTileSize,
                nodes, edges, outlets, courses, regionalCourseIds, cavePlans, candidates,
                new RiverFootprint(columns));
        if (input.readBoolean()) {
            tile = tile.withResolvedOwner(readResolvedOwner(input, tile));
        }
        if (input.read() != -1) {
            throw new IOException("Unexpected data after prepared hydrology tile.");
        }
        return tile;
    }

    private void writeResolvedOwner(DataOutputStream output, HydrologyTile tile) throws IOException {
        allocate(256L);
        CrossTileResolvedOwner owner = tile.resolvedOwner();
        HydrologyCaveCourseFilter.Result result = owner.draft().result();
        writeOwnerList(output, result.nodes(), tile.nodes(), this::writeDrainageNode);
        writeOwnerList(output, result.edges(), tile.edges(), this::writeDrainageEdge);
        writeOwnerList(output, result.outlets(), tile.outlets(), this::writeRiverOutlet);
        writeOwnerList(output, result.courses(), tile.courses(), this::writeRiverCourse);
        writeOwnerList(output, result.cavePlans(), tile.cavePlans(), this::writeCavePlan);
        writeOwnerList(output, owner.draft().diagnostics(), tile.localDiagnosticCandidates(),
                this::writeHydrologyDiagnosticCandidate);
        writeList(output, owner.observedRejections(), this::writeRejectedCourse);
    }

    private CrossTileResolvedOwner readResolvedOwner(DataInputStream input, HydrologyTile tile) throws IOException {
        allocate(256L);
        HydrologyCaveCourseFilter.Result result = new HydrologyCaveCourseFilter.Result(
                readOwnerList(input, tile.nodes(), this::readDrainageNode),
                readOwnerList(input, tile.edges(), this::readDrainageEdge),
                readOwnerList(input, tile.outlets(), this::readRiverOutlet),
                readOwnerList(input, tile.courses(), this::readRiverCourse),
                readOwnerList(input, tile.cavePlans(), this::readCavePlan));
        List<HydrologyDiagnosticCandidate> diagnostics = readOwnerList(input, tile.localDiagnosticCandidates(),
                this::readHydrologyDiagnosticCandidate);
        return new CrossTileResolvedOwner(new HydrologyOwnerDraft(tile.key(), result, diagnostics, null),
                readList(input, this::readRejectedCourse));
    }

    private void writeRejectedCourse(DataOutputStream output, CrossTileRejectedCourse rejection) throws IOException {
        writeRiverCourse(output, rejection.course());
        output.writeLong(rejection.winnerSourceId());
    }

    private CrossTileRejectedCourse readRejectedCourse(DataInputStream input) throws IOException {
        return new CrossTileRejectedCourse(readRiverCourse(input), input.readLong());
    }

    private <T> void writeOwnerList(DataOutputStream output, List<T> values, List<T> published,
                                    ElementWriter<T> writer) throws IOException {
        writeCount(output, values.size(), 16);
        Map<T, Integer> indexes = new IdentityHashMap<>(published.size());
        for (int index = 0; index < published.size(); index++) {
            indexes.put(published.get(index), index);
        }
        for (T value : values) {
            Integer index = indexes.get(value);
            output.writeInt(index == null ? -1 : index);
            if (index == null) {
                allocate(256L);
                writer.write(output, value);
            }
        }
    }

    private <T> List<T> readOwnerList(DataInputStream input, List<T> published, ElementReader<T> reader) throws IOException {
        int count = readCount(input, 16);
        List<T> values = new ArrayList<>(Math.min(count, 4096));
        for (int index = 0; index < count; index++) {
            int reference = input.readInt();
            if (reference == -1) {
                allocate(256L);
                values.add(reader.read(input));
            } else if (reference >= 0 && reference < published.size()) {
                values.add(published.get(reference));
            } else {
                throw new IOException("Invalid prepared hydrology owner reference.");
            }
        }
        return List.copyOf(values);
    }

    private void writeCavePlan(DataOutputStream output, HydrologyCavePlan plan) throws IOException {
        writeHydrologyCaveSource(output, plan.source());
        output.writeByte(plan.rejection().ordinal());
        writeOptionalLong(output, plan.arbitrationWinnerSourceId());
        validateCount(plan.baselinePreconditions().size(), 96);
        plan.writeCells(output);
    }

    private HydrologyCavePlan readCavePlan(DataInputStream input) throws IOException {
        HydrologyCaveSource source = readHydrologyCaveSource(input);
        HydrologyCaveRejection rejection = readEnum(input, HYDROLOGY_CAVE_REJECTION_VALUES);
        OptionalLong winner = readOptionalLong(input);
        int cells = readCount(input, 96);
        return HydrologyCavePlan.readCells(source, rejection, winner, input, cells);
    }

    private void writeHydraulicChannelProfile(DataOutputStream output, HydraulicChannelProfile profile) throws IOException {
        writeCount(output, profile.size(), 32);
        for (int station = 0; station < profile.size(); station++) {
            output.writeDouble(profile.widthAt(station));
            output.writeDouble(profile.depthAt(station));
        }
    }

    private HydraulicChannelProfile readHydraulicChannelProfile(DataInputStream input) throws IOException {
        int count = readCount(input, 32);
        double[] widths = new double[count];
        double[] depths = new double[count];
        for (int station = 0; station < count; station++) {
            widths[station] = input.readDouble();
            depths[station] = input.readDouble();
        }
        return new HydraulicChannelProfile(widths, depths);
    }

    private void writeDrainageNode(DataOutputStream output, DrainageNode value) throws IOException {
        output.writeLong(value.id());
        output.writeInt(value.x());
        output.writeInt(value.z());
        writeHydrologyTerrainSample(output, value.terrain());
        output.writeDouble(value.potential());
        output.writeLong(value.outletId());
    }

    private DrainageNode readDrainageNode(DataInputStream input) throws IOException {
        return new DrainageNode(
                input.readLong(),
                input.readInt(),
                input.readInt(),
                readHydrologyTerrainSample(input),
                input.readDouble(),
                input.readLong()
        );
    }

    private void writeDrainageEdge(DataOutputStream output, DrainageEdge value) throws IOException {
        output.writeLong(value.id());
        output.writeLong(value.upstreamNodeId());
        output.writeLong(value.downstreamNodeId());
        output.writeLong(value.outletId());
        output.writeDouble(value.cost());
        output.writeInt(value.contributingSurfaceSources());
        output.writeInt(value.contributingUndergroundSources());
        writeList(output, value.centerline(), this::writeHydrologyPoint);
    }

    private DrainageEdge readDrainageEdge(DataInputStream input) throws IOException {
        return new DrainageEdge(
                input.readLong(),
                input.readLong(),
                input.readLong(),
                input.readLong(),
                input.readDouble(),
                input.readInt(),
                input.readInt(),
                readList(input, this::readHydrologyPoint)
        );
    }

    private void writeHydrologyTerrainSample(DataOutputStream output, HydrologyTerrainSample value) throws IOException {
        output.writeInt(value.naturalHeight());
        output.writeDouble(value.slope());
        output.writeBoolean(value.ocean());
        output.writeBoolean(value.caveAvailable());
        output.writeInt(value.caveFloorY());
        output.writeInt(value.caveFluidY());
        output.writeBoolean(value.transitAllowed());
        output.writeBoolean(value.outletAllowed());
        output.writeBoolean(value.surfaceSourceAllowed());
        output.writeBoolean(value.surfaceSourceRequired());
        output.writeBoolean(value.undergroundSourceAllowed());
        output.writeBoolean(value.undergroundSourceRequired());
        output.writeDouble(value.routingCost());
        output.writeDouble(value.surfaceSourceWeight());
        output.writeDouble(value.undergroundSourceWeight());
        output.writeDouble(value.widthMultiplier());
        output.writeDouble(value.depthMultiplier());
        output.writeDouble(value.incisionMultiplier());
        output.writeDouble(value.routingMultiplier());
        output.writeDouble(value.bankMultiplier());
        writeString(output, value.parentBiomeKey());
        writeString(output, value.surfaceBiomeKey());
        writeString(output, value.mouthBiomeKey());
        writeString(output, value.shoreBiomeKey());
        writeString(output, value.bankBiomeKey());
        writeString(output, value.floodedCaveBiomeKey());
        writeList(output, value.preferredProfileKeys(), this::writeString);
        writeList(output, value.surfacePoolKeys(), this::writeString);
        output.writeDouble(value.shoreBiomeWidth());
        writeString(output, value.confinesKey());
        output.writeDouble(value.shoreWidth());
        output.writeBoolean(value.erosion());
        writeSurfaceRiverPolicy(output, value.surfacePolicy());
    }

    private HydrologyTerrainSample readHydrologyTerrainSample(DataInputStream input) throws IOException {
        return new HydrologyTerrainSample(
                input.readInt(),
                input.readDouble(),
                input.readBoolean(),
                input.readBoolean(),
                input.readInt(),
                input.readInt(),
                input.readBoolean(),
                input.readBoolean(),
                input.readBoolean(),
                input.readBoolean(),
                input.readBoolean(),
                input.readBoolean(),
                input.readDouble(),
                input.readDouble(),
                input.readDouble(),
                input.readDouble(),
                input.readDouble(),
                input.readDouble(),
                input.readDouble(),
                input.readDouble(),
                readString(input),
                readString(input),
                readString(input),
                readString(input),
                readString(input),
                readString(input),
                readList(input, this::readString),
                readList(input, this::readString),
                input.readDouble(),
                readString(input),
                input.readDouble(),
                input.readBoolean(),
                readSurfaceRiverPolicy(input)
        );
    }

    private void writeRiverOutlet(DataOutputStream output, RiverOutlet value) throws IOException {
        output.writeLong(value.id());
        output.writeByte(value.type().ordinal());
        output.writeLong(value.drainageNodeId());
        writeHydrologyPoint(output, value.landwardPoint());
        writeHydrologyPoint(output, value.connectionPoint());
        output.writeInt(value.seaLevel());
        output.writeBoolean(value.directOcean());
    }

    private RiverOutlet readRiverOutlet(DataInputStream input) throws IOException {
        return new RiverOutlet(
                input.readLong(),
                readEnum(input, HYDROLOGY_FEATURE_TYPE_VALUES),
                input.readLong(),
                readHydrologyPoint(input),
                readHydrologyPoint(input),
                input.readInt(),
                input.readBoolean()
        );
    }

    private void writeRiverCourse(DataOutputStream output, RiverCourse value) throws IOException {
        output.writeLong(value.id());
        output.writeByte(value.type().ordinal());
        writeOptionalLong(output, value.sourceNodeId());
        writeOptionalLong(output, value.outletId());
        writeString(output, value.profileKey());
        output.writeInt(value.discharge());
        writeList(output, value.drainageEdges(), this::writeDrainageEdge);
        writeList(output, value.segments(), this::writeHydraulicSegment);
    }

    private RiverCourse readRiverCourse(DataInputStream input) throws IOException {
        return new RiverCourse(
                input.readLong(),
                readEnum(input, RIVER_COURSE_TYPE_VALUES),
                readOptionalLong(input),
                readOptionalLong(input),
                readString(input),
                input.readInt(),
                readList(input, this::readDrainageEdge),
                readList(input, this::readHydraulicSegment)
        );
    }

    private void writeHydraulicSegment(DataOutputStream output, HydraulicSegment value) throws IOException {
        output.writeLong(value.id());
        output.writeLong(value.courseId());
        output.writeByte(value.type().ordinal());
        output.writeInt(value.upstreamHeadY());
        output.writeInt(value.downstreamHeadY());
        output.writeInt(value.width());
        output.writeInt(value.depth());
        output.writeBoolean(value.fallingFluid());
        output.writeBoolean(value.receivingPool());
        writeList(output, value.centerline(), this::writeHydrologyPoint);
        writeHydraulicChannelProfile(output, value.channelProfile());
    }

    private HydraulicSegment readHydraulicSegment(DataInputStream input) throws IOException {
        return new HydraulicSegment(
                input.readLong(),
                input.readLong(),
                readEnum(input, HYDROLOGY_FEATURE_TYPE_VALUES),
                input.readInt(),
                input.readInt(),
                input.readInt(),
                input.readInt(),
                input.readBoolean(),
                input.readBoolean(),
                readList(input, this::readHydrologyPoint),
                readHydraulicChannelProfile(input)
        );
    }

    private void writeHydrologyDiagnosticCandidate(DataOutputStream output, HydrologyDiagnosticCandidate value) throws IOException {
        output.writeLong(value.id());
        output.writeByte(value.kind().ordinal());
        output.writeByte(value.projectedType().ordinal());
        writeHydrologyPoint(output, value.point());
        output.writeByte(value.rejection().ordinal());
        output.writeInt(value.detail());
    }

    private HydrologyDiagnosticCandidate readHydrologyDiagnosticCandidate(DataInputStream input) throws IOException {
        return new HydrologyDiagnosticCandidate(
                input.readLong(),
                readEnum(input, HYDROLOGY_CANDIDATE_KIND_VALUES),
                readEnum(input, HYDROLOGY_FEATURE_TYPE_VALUES),
                readHydrologyPoint(input),
                readEnum(input, HYDROLOGY_CANDIDATE_REJECTION_VALUES),
                input.readInt()
        );
    }

    private void writeHydrologyColumnSample(DataOutputStream output, HydrologyColumnSample value) throws IOException {
        output.writeInt(value.x());
        output.writeInt(value.z());
        output.writeInt(value.naturalHeight());
        output.writeInt(value.seaLevel());
        output.writeBoolean(value.ocean());
        writeString(output, value.parentBiomeKey());
        writeList(output, value.layers(), this::writeHydrologyColumnLayer);
    }

    private HydrologyColumnSample readHydrologyColumnSample(DataInputStream input) throws IOException {
        return new HydrologyColumnSample(
                input.readInt(),
                input.readInt(),
                input.readInt(),
                input.readInt(),
                input.readBoolean(),
                readString(input),
                readList(input, this::readHydrologyColumnLayer)
        );
    }

    private void writeHydrologyColumnLayer(DataOutputStream output, HydrologyColumnLayer value) throws IOException {
        writeHydrologyFeatureRef(output, value.feature());
        output.writeInt(value.bedY());
        output.writeInt(value.fluidHeadY());
        output.writeInt(value.ceilingY());
        output.writeBoolean(value.channel());
        output.writeBoolean(value.shore());
        output.writeBoolean(value.grading());
        output.writeBoolean(value.connectedFluid());
        output.writeBoolean(value.fallingFluid());
        output.writeBoolean(value.receivingPool());
        output.writeBoolean(value.terrainOwned());
        output.writeBoolean(value.fluidOwned());
        output.writeBoolean(value.oceanApron());
        writeString(output, value.profileKey());
        writeString(output, value.surfaceBiomeKey());
        writeString(output, value.mouthBiomeKey());
        writeString(output, value.shoreBiomeKey());
        writeString(output, value.bankBiomeKey());
        writeString(output, value.floodedCaveBiomeKey());
    }

    private HydrologyColumnLayer readHydrologyColumnLayer(DataInputStream input) throws IOException {
        return new HydrologyColumnLayer(
                readHydrologyFeatureRef(input),
                input.readInt(),
                input.readInt(),
                input.readInt(),
                input.readBoolean(),
                input.readBoolean(),
                input.readBoolean(),
                input.readBoolean(),
                input.readBoolean(),
                input.readBoolean(),
                input.readBoolean(),
                input.readBoolean(),
                input.readBoolean(),
                readString(input),
                readString(input),
                readString(input),
                readString(input),
                readString(input),
                readString(input)
        );
    }

    private void writeHydrologyFeatureRef(DataOutputStream output, HydrologyFeatureRef value) throws IOException {
        output.writeLong(value.id());
        output.writeByte(value.type().ordinal());
        output.writeLong(value.courseId());
        output.writeLong(value.segmentId());
        output.writeInt(value.x());
        output.writeInt(value.y());
        output.writeInt(value.z());
        output.writeInt(value.flowDeltaX());
        output.writeInt(value.flowDeltaZ());
        output.writeBoolean(value.source());
    }

    private HydrologyFeatureRef readHydrologyFeatureRef(DataInputStream input) throws IOException {
        return new HydrologyFeatureRef(
                input.readLong(),
                readEnum(input, HYDROLOGY_FEATURE_TYPE_VALUES),
                input.readLong(),
                input.readLong(),
                input.readInt(),
                input.readInt(),
                input.readInt(),
                input.readInt(),
                input.readInt(),
                input.readBoolean()
        );
    }

    private void writeHydrologyPoint(DataOutputStream output, HydrologyPoint value) throws IOException {
        output.writeInt(value.x());
        output.writeInt(value.y());
        output.writeInt(value.z());
    }

    private HydrologyPoint readHydrologyPoint(DataInputStream input) throws IOException {
        return new HydrologyPoint(
                input.readInt(),
                input.readInt(),
                input.readInt()
        );
    }

    private void writeHydrologyCaveSource(DataOutputStream output, HydrologyCaveSource value) throws IOException {
        output.writeLong(value.sourceId());
        writeCavePosition(output, value.entry());
        writeCavePosition(output, value.target());
        output.writeInt(value.waterHeadY());
        output.writeByte(value.mode().ordinal());
    }

    private HydrologyCaveSource readHydrologyCaveSource(DataInputStream input) throws IOException {
        return new HydrologyCaveSource(
                input.readLong(),
                readCavePosition(input),
                readCavePosition(input),
                input.readInt(),
                readEnum(input, HYDROLOGY_CAVE_MODE_VALUES)
        );
    }

    private void writeCavePosition(DataOutputStream output, CavePosition value) throws IOException {
        output.writeInt(value.x());
        output.writeInt(value.y());
        output.writeInt(value.z());
    }

    private CavePosition readCavePosition(DataInputStream input) throws IOException {
        return new CavePosition(
                input.readInt(),
                input.readInt(),
                input.readInt()
        );
    }

    private void writeSurfaceRiverPolicy(DataOutputStream output, SurfaceRiverPolicy value) throws IOException {
        writeString(output, value.areaKey());
        writeDouble(output, value.sourceDensity());
        writeInteger(output, value.sourceSpacing());
        writeInteger(output, value.tributaries());
        writeInteger(output, value.inlandOutlets());
        writeInteger(output, value.coastalOutlets());
        writeInteger(output, value.minimumCourseLength());
        writeInteger(output, value.maximumIncision());
    }

    private SurfaceRiverPolicy readSurfaceRiverPolicy(DataInputStream input) throws IOException {
        return new SurfaceRiverPolicy(
                readString(input),
                readDouble(input),
                readInteger(input),
                readInteger(input),
                readInteger(input),
                readInteger(input),
                readInteger(input),
                readInteger(input)
        );
    }

    private void writeString(DataOutputStream output, String value) throws IOException {
        output.writeBoolean(value != null);
        if (value != null) {
            allocate(40L + (long) value.length() * Character.BYTES);
            output.writeUTF(value);
        }
    }

    private String readString(DataInputStream input) throws IOException {
        if (!input.readBoolean()) {
            return null;
        }
        String value = input.readUTF();
        allocate(40L + (long) value.length() * Character.BYTES);
        if (strings.size() >= 4096) {
            return value;
        }
        String existing = strings.putIfAbsent(value, value);
        return existing == null ? value : existing;
    }

    private void writeInteger(DataOutputStream output, Integer value) throws IOException {
        output.writeBoolean(value != null);
        if (value != null) {
            output.writeInt(value);
        }
    }

    private Integer readInteger(DataInputStream input) throws IOException {
        return input.readBoolean() ? input.readInt() : null;
    }

    private void writeDouble(DataOutputStream output, Double value) throws IOException {
        output.writeBoolean(value != null);
        if (value != null) {
            output.writeDouble(value);
        }
    }

    private Double readDouble(DataInputStream input) throws IOException {
        return input.readBoolean() ? input.readDouble() : null;
    }

    private void writeOptionalLong(DataOutputStream output, OptionalLong value) throws IOException {
        output.writeBoolean(value.isPresent());
        if (value.isPresent()) {
            output.writeLong(value.getAsLong());
        }
    }

    private OptionalLong readOptionalLong(DataInputStream input) throws IOException {
        return input.readBoolean() ? OptionalLong.of(input.readLong()) : OptionalLong.empty();
    }

    private <T> void writeList(DataOutputStream output, List<T> values, ElementWriter<T> writer) throws IOException {
        writeCount(output, values.size(), 256);
        for (T value : values) {
            writer.write(output, value);
        }
    }

    private <T> List<T> readList(DataInputStream input, ElementReader<T> reader) throws IOException {
        int count = readCount(input, 256);
        List<T> values = new ArrayList<>(Math.min(count, 4096));
        for (int index = 0; index < count; index++) {
            values.add(reader.read(input));
        }
        return values;
    }

    private int readCount(DataInputStream input, int bytesPerElement) throws IOException {
        int count = input.readInt();
        validateCount(count, bytesPerElement);
        return count;
    }

    private void writeCount(DataOutputStream output, int count, int bytesPerElement) throws IOException {
        validateCount(count, bytesPerElement);
        output.writeInt(count);
    }

    private void validateCount(int count, int bytesPerElement) throws IOException {
        if (count < 0 || count > MAXIMUM_COLLECTION_SIZE) {
            throw new IOException("Invalid hydrology collection length: " + count);
        }
        allocate((long) count * bytesPerElement);
    }

    private void allocate(long bytes) throws IOException {
        if (bytes > remainingAllocation) {
            throw new IOException("Prepared hydrology tile exceeds its allocation budget.");
        }
        remainingAllocation -= bytes;
    }

    private <T extends Enum<T>> T readEnum(DataInputStream input, T[] values) throws IOException {
        int ordinal = input.readUnsignedByte();
        if (ordinal >= values.length) {
            throw new IOException("Invalid hydrology enum value: " + ordinal);
        }
        return values[ordinal];
    }

    @FunctionalInterface
    private interface ElementWriter<T> {
        void write(DataOutputStream output, T value) throws IOException;
    }

    @FunctionalInterface
    private interface ElementReader<T> {
        T read(DataInputStream input) throws IOException;
    }
}
