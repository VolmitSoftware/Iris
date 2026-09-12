package art.arcane.iris.generation.hydrology;

import art.arcane.iris.generation.hydrology.policy.SurfaceRiverPolicy;

import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.TemporaryFolder;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Map;
import java.util.OptionalLong;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

public class StudioHydrologyTileStoreTest {
    @Rule
    public TemporaryFolder temporaryFolder = new TemporaryFolder();

    @Test
    public void roundTripsValidatedEntryTile() throws Exception {
        HydrologyTile original = tile();
        StudioHydrologyTileStore store = store();

        store.save(original);
        HydrologyTile restored = store.load(original.key()).orElseThrow();

        assertEquals(original.key(), restored.key());
        assertEquals(original.worldSeed(), restored.worldSeed());
        assertEquals(original.settingsFingerprint(), restored.settingsFingerprint());
        assertEquals(original.nodes(), restored.nodes());
        assertEquals(original.edges(), restored.edges());
        assertEquals(original.outlets(), restored.outlets());
        assertEquals(original.courses(), restored.courses());
        assertEquals(original.cavePlans(), restored.cavePlans());
        assertEquals(original.diagnosticCandidates(), restored.diagnosticCandidates());
        assertEquals(original.footprint(), restored.footprint());
    }

    @Test
    public void corruptEntryFallsBackToCacheMiss() throws Exception {
        HydrologyTile original = tile();
        StudioHydrologyTileStore store = store();
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
        StudioHydrologyTileStore matching = new StudioHydrologyTileStore(root, scope, 64);
        matching.save(original);
        StudioHydrologyTileStore mismatched = new StudioHydrologyTileStore(root, scope, 128);

        assertTrue(mismatched.load(original.key()).isEmpty());
    }

    private StudioHydrologyTileStore store() throws Exception {
        return new StudioHydrologyTileStore(
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
                List.of(),
                List.of(),
                new RiverFootprint(Map.of(RiverFootprint.pack(0, 0), column))
        );
    }
}
