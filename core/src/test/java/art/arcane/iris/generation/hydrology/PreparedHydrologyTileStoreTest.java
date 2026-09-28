package art.arcane.iris.generation.hydrology;

import art.arcane.iris.generation.hydrology.policy.SurfaceRiverPolicy;
import art.arcane.iris.generation.hydrology.cave.CavePosition;
import art.arcane.iris.generation.hydrology.cave.CaveVoxel;
import art.arcane.iris.generation.hydrology.cave.CaveVoxelPrecondition;
import art.arcane.iris.generation.hydrology.cave.HydrologyCaveAction;
import art.arcane.iris.generation.hydrology.cave.HydrologyCaveMode;
import art.arcane.iris.generation.hydrology.cave.HydrologyCavePlan;
import art.arcane.iris.generation.hydrology.cave.HydrologyCaveRejection;
import art.arcane.iris.generation.hydrology.cave.HydrologyCaveSource;

import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.TemporaryFolder;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.DataInputStream;
import java.io.DataOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.nio.ByteBuffer;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Set;
import java.util.Map;
import java.util.OptionalLong;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.stream.Stream;
import java.util.zip.GZIPInputStream;
import java.util.zip.GZIPOutputStream;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertSame;
import static org.junit.Assert.assertThrows;
import static org.junit.Assert.assertTrue;

public class PreparedHydrologyTileStoreTest {
    @Rule
    public TemporaryFolder temporaryFolder = new TemporaryFolder();

    @Test
    public void roundTripsValidatedEntryTile() throws Exception {
        HydrologyTile original = tile();
        PreparedHydrologyTileStore store = store();

        store.save(original);
        HydrologyTile restored = store.load(original.key()).orElseThrow();

        assertEquals(original.key(), restored.key());
        assertEquals(original.worldSeed(), restored.worldSeed());
        assertEquals(original.settingsFingerprint(), restored.settingsFingerprint());
        assertEquals(original.nodes(), restored.nodes());
        assertEquals(original.edges(), restored.edges());
        assertEquals(original.outlets(), restored.outlets());
        assertEquals(original.courses(), restored.courses());
        assertEquals(original.regionalCourseIds(), restored.regionalCourseIds());
        assertEquals(original.cavePlans(), restored.cavePlans());
        assertEquals(original.localDiagnosticCandidates(), restored.localDiagnosticCandidates());
        assertEquals(original.footprint(), restored.footprint());
        assertNull(restored.resolvedOwner());
        assertThrows(UnsupportedOperationException.class, () -> restored.regionalCourseIds().clear());
    }

    @Test
    public void roundTripsExactOwnerOrderingAndUnpublishedCourses() throws Exception {
        HydrologyTile original = tile();
        HydrologyTerrainSample terrain = original.nodes().getFirst().terrain();
        DrainageNode unpublishedNode = new DrainageNode(18L, 4, 0, terrain, 30D, 2L);
        RiverCourse unpublishedCourse = surfaceCourse(44L);
        HydrologyDiagnosticCandidate unpublishedDiagnostic = new HydrologyDiagnosticCandidate(42L,
                HydrologyCandidateKind.SOURCE, HydrologyFeatureType.SURFACE_POOL,
                new HydrologyPoint(4, 80, 0), HydrologyCandidateRejection.SOURCE_SPACING, 16);
        HydrologyCaveCourseFilter.Result result = new HydrologyCaveCourseFilter.Result(
                List.of(unpublishedNode, original.nodes().getLast(), original.nodes().getFirst()),
                original.edges(), original.outlets(), List.of(unpublishedCourse, original.courses().getFirst()),
                List.of());
        HydrologyFootprintCompiler compiler = new HydrologyFootprintCompiler(
                HydrologyPlannerSettings.defaults(), (x, z) -> terrain);
        CrossTileResolvedOwner owner = new CrossTileResolvedOwner(new HydrologyOwnerDraft(original.key(),
                result, List.of(unpublishedDiagnostic, original.localDiagnosticCandidates().getFirst()), compiler),
                List.of(new CrossTileRejectedCourse(surfaceCourse(60L), 44L),
                        new CrossTileRejectedCourse(surfaceCourse(50L), 4L)));
        HydrologyTile attached = original.withResolvedOwner(owner);
        assertEquals(original, attached);
        assertEquals(original.hashCode(), attached.hashCode());
        assertSame(original.nodes(), attached.nodes());
        assertSame(original.courses(), attached.courses());
        assertSame(original.footprint(), attached.footprint());
        assertSame(original.features(), attached.features());
        assertSame(original.node(1L).orElseThrow(), attached.node(1L).orElseThrow());
        assertNull(attached.resolvedOwner().draft().footprintCompiler());
        assertEquals(HydrologyCacheWeights.tile(original) + HydrologyCacheWeights.owner(owner),
                HydrologyCacheWeights.tile(attached));

        PreparedHydrologyTileStore store = store();
        store.save(attached);
        HydrologyTile restored = store.load(original.key()).orElseThrow();
        assertEquals(original, restored);
        assertEquals(owner.withoutFootprintCompiler(), restored.resolvedOwner());
        assertSame(restored.nodes().getLast(), restored.resolvedOwner().draft().result().nodes().get(1));
        assertSame(restored.courses().getFirst(), restored.resolvedOwner().draft().result().courses().getLast());
        assertThrows(UnsupportedOperationException.class,
                () -> restored.resolvedOwner().draft().result().nodes().clear());
        assertThrows(UnsupportedOperationException.class,
                () -> restored.resolvedOwner().draft().diagnostics().clear());
        CrossTileResolutionContext context = new CrossTileResolutionContext(original.key(), 1L, 9);
        context.remember(original.key(), owner);
        context.remember(restored.key(), restored.resolvedOwner());
    }

    @Test
    public void invalidOwnerReferencesAreRejected() throws Exception {
        HydrologyTile original = tile();
        PreparedHydrologyTileStore store = store();
        store.save(original);
        int ownerOffset = readPayload(store.file(original.key())).length;
        CrossTileResolvedOwner owner = new CrossTileResolvedOwner(new HydrologyOwnerDraft(original.key(),
                new HydrologyCaveCourseFilter.Result(original.nodes(), original.edges(), original.outlets(),
                        original.courses(), original.cavePlans()), original.localDiagnosticCandidates(), null), List.of());
        store.save(original.withResolvedOwner(owner));
        byte[] bytes = readPayload(store.file(original.key()));
        ByteBuffer.wrap(bytes).putInt(ownerOffset + Integer.BYTES, Integer.MAX_VALUE);
        writePayload(store.file(original.key()), bytes);
        assertTrue(store.load(original.key()).isEmpty());
    }

    @Test
    public void roundTripsNonemptyCompactCavePlan() throws Exception {
        CavePosition wet = new CavePosition(0, 20, 0);
        CavePosition dry = new CavePosition(0, 21, 0);
        CavePosition guard = new CavePosition(Integer.MAX_VALUE, Integer.MIN_VALUE, Integer.MIN_VALUE);
        CavePosition boundary = new CavePosition(Integer.MIN_VALUE, Integer.MAX_VALUE, Integer.MAX_VALUE);
        LinkedHashMap<CavePosition, HydrologyCaveAction> actions = new LinkedHashMap<>();
        actions.put(guard, HydrologyCaveAction.SEAL_GUARD);
        actions.put(dry, HydrologyCaveAction.DRY_AIR);
        actions.put(wet, HydrologyCaveAction.WET_SOURCE);
        LinkedHashMap<CavePosition, CaveVoxelPrecondition> preconditions = new LinkedHashMap<>();
        preconditions.put(wet, new CaveVoxelPrecondition(CaveVoxel.SOLID, false));
        preconditions.put(boundary, new CaveVoxelPrecondition(CaveVoxel.CAVE_AIR, true));
        preconditions.put(guard, new CaveVoxelPrecondition(CaveVoxel.LAVA, false));
        preconditions.put(dry, new CaveVoxelPrecondition(CaveVoxel.CAVE_AIR, false));
        HydrologyCavePlan plan = new HydrologyCavePlan(
                new HydrologyCaveSource(4L, wet, dry, 20, HydrologyCaveMode.CLOSED_COMPONENT),
                HydrologyCaveRejection.NONE, actions, preconditions, OptionalLong.empty());
        HydraulicSegment segment = new HydraulicSegment(3L, 4L, HydrologyFeatureType.UNDERGROUND_POOL,
                20, 20, 1, 1, false, false, List.of(new HydrologyPoint(0, 20, 0)),
                new HydraulicChannelProfile(new double[]{1D}, new double[]{1D}));
        RiverCourse course = new RiverCourse(4L, RiverCourseType.SEA_CAVE, OptionalLong.empty(),
                OptionalLong.empty(), "default", 1, List.of(), List.of(segment));
        HydrologyFeatureRef feature = new HydrologyFeatureRef(5L, HydrologyFeatureType.UNDERGROUND_POOL,
                4L, 3L, 0, 20, 0, 0, 0, true);
        HydrologyColumnLayer layer = new HydrologyColumnLayer(feature, 19, 20, 21,
                true, false, false, true, false, false, true, true, false,
                "default", "plains", "plains", "plains", "plains", "plains");
        HydrologyColumnSample column = new HydrologyColumnSample(0, 0, 80, 63, false, "plains", List.of(layer));
        HydrologyTile original = new HydrologyTile(new HydrologyTileKey(0, 0), 91L, 17L, 64,
                List.of(), List.of(), List.of(), List.of(course), Set.of(), List.of(plan), List.of(),
                new RiverFootprint(Map.of(RiverFootprint.pack(0, 0), column)));
        original = original.withResolvedOwner(new CrossTileResolvedOwner(new HydrologyOwnerDraft(original.key(),
                new HydrologyCaveCourseFilter.Result(original.nodes(), original.edges(), original.outlets(),
                        original.courses(), original.cavePlans()), original.localDiagnosticCandidates(), null), List.of()));
        PreparedHydrologyTileStore store = store();
        store.save(original);
        HydrologyTile restored = store.load(original.key()).orElseThrow();
        assertEquals(original, restored);
        assertEquals(original.resolvedOwner(), restored.resolvedOwner());
        assertSame(restored.cavePlans().getFirst(), restored.resolvedOwner().draft().result().cavePlans().getFirst());
        HydrologyCavePlan restoredPlan = restored.cavePlans().getFirst();
        assertEquals(actions, restoredPlan.actions());
        assertEquals(preconditions, restoredPlan.baselinePreconditions());
        assertEquals(new ArrayList<>(actions.keySet()), new ArrayList<>(restoredPlan.actions().keySet()));
        assertEquals(new ArrayList<>(preconditions.keySet()), new ArrayList<>(restoredPlan.baselinePreconditions().keySet()));
        assertThrows(UnsupportedOperationException.class, () -> restoredPlan.actions().clear());
        assertThrows(UnsupportedOperationException.class, () -> restoredPlan.baselinePreconditions().remove(boundary));
        ArrayList<CavePosition> published = new ArrayList<>();
        restoredPlan.forEachActionIn(0, 0, 16, 16, (position, action) -> published.add(position));
        assertEquals(List.of(dry, wet), published);
        ByteArrayOutputStream buffer = new ByteArrayOutputStream();
        plan.writeCells(new DataOutputStream(buffer));
        byte[] valid = buffer.toByteArray();
        byte[] flags = valid.clone();
        flags[20] = (byte) 0x80;
        byte[] duplicatePosition = valid.clone();
        System.arraycopy(duplicatePosition, 8, duplicatePosition, 21, 12);
        byte[] duplicateOrder = valid.clone();
        System.arraycopy(duplicateOrder, 60, duplicateOrder, 64, 4);
        byte[] invalidActionCount = valid.clone();
        ByteBuffer.wrap(invalidActionCount).putInt(4, 5);
        for (byte[] invalid : List.of(flags, duplicatePosition, duplicateOrder, invalidActionCount)) {
            DataInputStream input = new DataInputStream(new ByteArrayInputStream(invalid));
            int count = input.readInt();
            assertThrows(IOException.class, () -> HydrologyCavePlan.readCells(
                    plan.source(), plan.rejection(), plan.arbitrationWinnerSourceId(), input, count));
        }
    }

    @Test
    public void largeCavePlanUsesFixedSizeCellsWithoutLosingPreconditions() throws Exception {
        int count = 100_000;
        Map<CavePosition, HydrologyCaveAction> actions = new LinkedHashMap<>();
        Map<CavePosition, CaveVoxelPrecondition> preconditions = new LinkedHashMap<>();
        CaveVoxelPrecondition baseline = new CaveVoxelPrecondition(CaveVoxel.SOLID, false);
        for (int index = 0; index < count; index++) {
            CavePosition position = new CavePosition(index % 256, index / 65536, index / 256 % 256);
            preconditions.put(position, baseline);
            if (index % 2 == 0) {
                actions.put(position, HydrologyCaveAction.SEAL_GUARD);
            }
        }
        HydrologyCaveSource source = new HydrologyCaveSource(4L, new CavePosition(0, 0, 0),
                new CavePosition(1, 0, 0), 20, HydrologyCaveMode.CLOSED_COMPONENT);
        HydrologyCavePlan original = new HydrologyCavePlan(source, HydrologyCaveRejection.NONE,
                actions, preconditions, OptionalLong.empty());
        ByteArrayOutputStream buffer = new ByteArrayOutputStream();
        original.writeCells(new DataOutputStream(buffer));
        assertEquals(8 + 17 * count, buffer.size());
        DataInputStream input = new DataInputStream(new ByteArrayInputStream(buffer.toByteArray()));
        HydrologyCavePlan restored = HydrologyCavePlan.readCells(source, HydrologyCaveRejection.NONE,
                OptionalLong.empty(), input, input.readInt());
        assertEquals(original, restored);
        assertEquals(-1, input.read());
    }

    @Test(timeout = 2000)
    public void invalidCollectionLengthsFailBeforeAllocation() throws Exception {
        HydrologyTile original = tile();
        PreparedHydrologyTileStore store = store();
        store.save(original);
        Path file = store.file(original.key());
        byte[] bytes = readPayload(file);
        DataInputStream input = new DataInputStream(new ByteArrayInputStream(bytes));
        input.readInt();
        input.readInt();
        input.readUTF();
        input.skipNBytes(28);
        int countOffset = bytes.length - input.available();
        for (int count : new int[]{-1, Integer.MAX_VALUE, 1_000_000}) {
            byte[] invalid = Arrays.copyOf(bytes, countOffset + Integer.BYTES);
            ByteBuffer.wrap(invalid).putInt(countOffset, count);
            writePayload(file, invalid);
            assertTrue(store.load(original.key()).isEmpty());
        }
    }

    @Test
    public void truncatedAndTrailingPayloadsAreRejected() throws Exception {
        HydrologyTile original = tile();
        PreparedHydrologyTileStore store = store();
        store.save(original);
        Path file = store.file(original.key());
        byte[] bytes = readPayload(file);
        writePayload(file, Arrays.copyOf(bytes, bytes.length - 1));
        assertTrue(store.load(original.key()).isEmpty());
        writePayload(file, Arrays.copyOf(bytes, bytes.length + 1));
        assertTrue(store.load(original.key()).isEmpty());
    }

    @Test
    public void presenceCheckDoesNotDecodeTileContents() throws Exception {
        HydrologyTile original = tile();
        PreparedHydrologyTileStore store = store();
        store.save(original);
        Files.writeString(store.file(original.key()), "invalid", StandardCharsets.UTF_8);
        assertTrue(store.contains(original.key()));
        assertTrue(store.load(original.key()).isEmpty());
    }

    @Test
    public void allocationLimitDoesNotReplaceAnExistingTile() throws Exception {
        HydrologyTile original = tile();
        PreparedHydrologyTileStore store = store();
        store.save(original);
        HydrologyTile oversized = new HydrologyTile(original.key(), original.worldSeed(),
                original.settingsFingerprint(), original.tileSize(), original.nodes(), original.edges(),
                original.outlets(), original.courses(), original.regionalCourseIds(), original.cavePlans(),
                Collections.nCopies(600_000, original.localDiagnosticCandidates().getFirst()), original.footprint());
        assertThrows(IOException.class, () -> store.save(oversized));
        assertEquals(original, store.load(original.key()).orElseThrow());
        try (Stream<Path> files = Files.list(store.file(original.key()).getParent())) {
            assertEquals(List.of(store.file(original.key())), files.toList());
        }
    }

    private byte[] readPayload(Path file) throws Exception {
        try (InputStream input = new GZIPInputStream(Files.newInputStream(file))) {
            return input.readAllBytes();
        }
    }

    private void writePayload(Path file, byte[] bytes) throws Exception {
        try (OutputStream output = new GZIPOutputStream(Files.newOutputStream(file))) {
            output.write(bytes);
        }
    }

    @Test
    public void corruptEntryFallsBackToCacheMiss() throws Exception {
        HydrologyTile original = tile();
        PreparedHydrologyTileStore store = store();
        store.save(original);
        Path file = store.file(original.key());
        Files.writeString(file, "invalid", StandardCharsets.UTF_8);

        assertTrue(store.load(original.key()).isEmpty());
    }

    @Test
    public void mismatchedTileSizeFallsBackToCacheMiss() throws Exception {
        Path root = temporaryFolder.newFolder().toPath();
        HydrologyTileCache.SharedCacheScope scope = scope();
        HydrologyTile original = tile();
        PreparedHydrologyTileStore matching = new PreparedHydrologyTileStore(root, scope, 64);
        matching.save(original);
        PreparedHydrologyTileStore mismatched = new PreparedHydrologyTileStore(root, scope, 128);

        assertTrue(mismatched.load(original.key()).isEmpty());
    }

    @Test
    public void copiedTileCannotCrossRuntimeIdentityScopes() throws Exception {
        Path root = temporaryFolder.newFolder().toPath();
        PreparedHydrologyTileStore source = new PreparedHydrologyTileStore(root, scope(), 64);
        PreparedHydrologyTileStore target = new PreparedHydrologyTileStore(root,
                new HydrologyTileCache.SharedCacheScope("another-kernel", 91L, 384, "overworld", 17L), 64);
        HydrologyTile tile = tile();
        source.save(tile);
        Files.createDirectories(target.file(tile.key()).getParent());
        Files.copy(source.file(tile.key()), target.file(tile.key()));
        assertTrue(target.load(tile.key()).isEmpty());
    }

    private PreparedHydrologyTileStore store() throws Exception {
        return new PreparedHydrologyTileStore(
                temporaryFolder.newFolder().toPath(),
                scope(),
                64
        );
    }

    private HydrologyTileCache.SharedCacheScope scope() {
        return new HydrologyTileCache.SharedCacheScope(
                "runtime-identity",
                91L,
                384,
                "overworld",
                17L
        );
    }

    private HydrologyTile tile() {
        HydrologyTerrainSample terrain = HydrologyTerrainSample.openLand(80, 0.25D, "plains")
                .withSurfacePolicy(new SurfaceRiverPolicy("region:tropical", 8D, 160, 3, 3, 4, 128, 24));
        DrainageNode node = new DrainageNode(1L, 0, 0, terrain, 10D, 2L);
        DrainageNode upstream = new DrainageNode(8L, 2, 0, terrain, 20D, 2L);
        DrainageEdge edge = new DrainageEdge(7L, 8L, 1L, 2L, 1D, 1, 0,
                List.of(new HydrologyPoint(2, 80, 0), new HydrologyPoint(0, 80, 0)));
        HydrologyDiagnosticCandidate candidate = new HydrologyDiagnosticCandidate(9L,
                HydrologyCandidateKind.SOURCE, HydrologyFeatureType.SURFACE_POOL,
                new HydrologyPoint(5, 80, 0), HydrologyCandidateRejection.SOURCE_SPACING, 16);
        RiverOutlet outlet = new RiverOutlet(
                2L,
                HydrologyFeatureType.MOUTH,
                1L,
                new HydrologyPoint(0, 80, 0),
                new HydrologyPoint(1, 63, 0),
                63,
                true
        );
        HydraulicSegment segment = new HydraulicSegment(
                3L,
                4L,
                HydrologyFeatureType.SURFACE_POOL,
                72,
                72,
                4,
                2,
                false,
                false,
                List.of(new HydrologyPoint(0, 72, 0), new HydrologyPoint(1, 72, 0)),
                new HydraulicChannelProfile(new double[]{2.5D, 4D}, new double[]{1D, 2D})
        );
        RiverCourse course = new RiverCourse(
                4L,
                RiverCourseType.SURFACE,
                OptionalLong.of(1L),
                OptionalLong.of(2L),
                "default",
                1,
                List.of(edge),
                List.of(segment)
        );
        HydrologyFeatureRef feature = new HydrologyFeatureRef(
                5L,
                HydrologyFeatureType.SURFACE_POOL,
                4L,
                3L,
                0,
                72,
                0,
                1,
                0,
                true
        );
        HydrologyColumnLayer layer = new HydrologyColumnLayer(
                feature,
                70,
                72,
                72,
                true,
                false,
                false,
                true,
                false,
                false,
                true,
                true,
                false,
                "default",
                "plains",
                "plains",
                "plains",
                "plains",
                "plains"
        );
        HydrologyColumnSample column = new HydrologyColumnSample(
                0,
                0,
                80,
                63,
                false,
                "plains",
                List.of(layer)
        );
        return new HydrologyTile(
                new HydrologyTileKey(0, 0),
                91L,
                17L,
                64,
                List.of(node, upstream),
                List.of(edge),
                List.of(outlet),
                List.of(course),
                Set.of(course.id()),
                List.of(),
                List.of(candidate),
                new RiverFootprint(Map.of(RiverFootprint.pack(0, 0), column))
        );
    }

    private RiverCourse surfaceCourse(long id) {
        HydraulicSegment segment = new HydraulicSegment(id + 1L, id, HydrologyFeatureType.SURFACE_POOL,
                72, 72, 4, 2, false, false, List.of(new HydrologyPoint(0, 72, 0)),
                HydraulicChannelProfile.uniform(4D, 2D));
        return new RiverCourse(id, RiverCourseType.SURFACE, OptionalLong.of(1L), OptionalLong.of(2L),
                "default", 1, List.of(), List.of(segment));
    }
}
