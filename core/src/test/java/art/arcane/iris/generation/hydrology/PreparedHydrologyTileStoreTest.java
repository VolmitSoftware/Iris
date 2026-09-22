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
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;

import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.TemporaryFolder;

import java.io.InputStream;
import java.io.OutputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Set;
import java.util.Map;
import java.util.OptionalLong;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.zip.GZIPInputStream;
import java.util.zip.GZIPOutputStream;

import static org.junit.Assert.assertEquals;
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
        assertThrows(UnsupportedOperationException.class, () -> restored.regionalCourseIds().clear());
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
        PreparedHydrologyTileStore store = store();
        store.save(original);
        HydrologyTile restored = store.load(original.key()).orElseThrow();
        assertEquals(original, restored);
        HydrologyCavePlan restoredPlan = restored.cavePlans().getFirst();
        assertEquals(actions, restoredPlan.actions());
        assertEquals(preconditions, restoredPlan.baselinePreconditions());
        Comparator<CavePosition> persistedOrder = Comparator.comparingInt(CavePosition::x)
                .thenComparingInt(CavePosition::z).thenComparingInt(CavePosition::y);
        ArrayList<CavePosition> expectedActions = new ArrayList<>(actions.keySet());
        expectedActions.sort(persistedOrder);
        ArrayList<CavePosition> expectedPreconditions = new ArrayList<>(preconditions.keySet());
        expectedPreconditions.sort(persistedOrder);
        assertEquals(expectedActions, new ArrayList<>(restoredPlan.actions().keySet()));
        assertEquals(expectedPreconditions, new ArrayList<>(restoredPlan.baselinePreconditions().keySet()));
        assertThrows(UnsupportedOperationException.class, () -> restoredPlan.actions().clear());
        assertThrows(UnsupportedOperationException.class, () -> restoredPlan.baselinePreconditions().remove(boundary));
        ArrayList<CavePosition> published = new ArrayList<>();
        restoredPlan.forEachActionIn(0, 0, 16, 16, (position, action) -> published.add(position));
        assertEquals(List.of(wet, dry), published);
    }

    @Test
    public void entryRequiresRegionalOwnershipMetadata() throws Exception {
        HydrologyTile original = tile();
        PreparedHydrologyTileStore store = store();
        store.save(original);
        Path file = store.file(original.key());
        JsonObject persisted;
        try (InputStream input = new GZIPInputStream(Files.newInputStream(file))) {
            persisted = JsonParser.parseString(new String(input.readAllBytes(), StandardCharsets.UTF_8)).getAsJsonObject();
        }
        persisted.remove("regionalCourseIds");
        try (OutputStream output = new GZIPOutputStream(Files.newOutputStream(file))) {
            output.write(persisted.toString().getBytes(StandardCharsets.UTF_8));
        }

        assertTrue(store.load(original.key()).isEmpty());
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
                List.of(),
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
                List.of(node),
                List.of(),
                List.of(outlet),
                List.of(course),
                Set.of(course.id()),
                List.of(),
                List.of(),
                new RiverFootprint(Map.of(RiverFootprint.pack(0, 0), column))
        );
    }
}
