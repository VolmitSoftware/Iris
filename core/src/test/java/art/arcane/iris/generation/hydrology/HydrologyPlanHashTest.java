package art.arcane.iris.generation.hydrology;

import art.arcane.iris.generation.hydrology.policy.SurfaceRiverPolicy;

import art.arcane.iris.generation.hydrology.cave.HydrologyCavePlan;
import org.junit.Test;

import java.lang.reflect.Field;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.HexFormat;
import java.util.Map;
import java.util.concurrent.ForkJoinPool;
import java.util.concurrent.TimeUnit;
import java.util.function.UnaryOperator;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

public class HydrologyPlanHashTest {
    private static final String EXPECTED_PLAN_DIGEST = "ad4e63b9081878d0eac05c2aeacc80afa18a7f2bf4e06c18490211e7ae91fe99";

    private static final List<HydrologyTileKey> TILES = List.of(
            new HydrologyTileKey(0, 0),
            new HydrologyTileKey(-1, -1),
            new HydrologyTileKey(0, -1),
            new HydrologyTileKey(1, 0));

    @Test
    public void fullTilePlansAreByteIdentical() {
        PlanDigest plan = planDigest(planner -> planner);
        assertTrue("planned courses: " + plan.courses(), plan.courses() > 0);
        assertTrue("planned columns: " + plan.columns(), plan.columns() > 0);
        assertEquals(EXPECTED_PLAN_DIGEST, plan.digest());
    }

    @Test(timeout = 180000)
    public void fallbackTrialBatchSizeNeverChangesThePlan() throws Exception {
        for (int trialLimit : new int[]{1, 2, 8, 16}) {
            ForkJoinPool pool = new ForkJoinPool(16);
            try {
                PlanDigest plan = pool.submit(() -> planDigest(planner -> withTrialLimit(planner, trialLimit)))
                        .get(60L, TimeUnit.SECONDS);
                assertEquals("trial limit " + trialLimit, EXPECTED_PLAN_DIGEST, plan.digest());
            } finally {
                pool.shutdownNow();
                assertTrue(pool.awaitTermination(5L, TimeUnit.SECONDS));
            }
        }
    }

    private static PlanDigest planDigest(UnaryOperator<HydrologyPlanner> configure) {
        MessageDigest canonical = sha256();
        int courses = 0;
        int columns = 0;
        for (long seed : new long[]{77L, 19L}) {
            HydrologyPlannerSettings settings = planSettings();
            HydrologyPlanner planner = configure.apply(new HydrologyPlanner(seed, settings, rollingCoast(112)));
            for (HydrologyTileKey key : TILES) {
                HydrologyTile tile = planner.plan(key);
                courses += tile.courses().size();
                columns += tile.footprint().columns().size();
                render(tile, canonical);
            }
        }
        return new PlanDigest(HexFormat.of().formatHex(canonical.digest()), courses, columns);
    }

    private static HydrologyPlanner withTrialLimit(HydrologyPlanner planner, int trialLimit) {
        try {
            Field crossTile = HydrologyPlanner.class.getDeclaredField("crossTile");
            crossTile.setAccessible(true);
            crossTile.set(planner, new HydrologyCrossTileResolver(planner, trialLimit));
            return planner;
        } catch (ReflectiveOperationException failure) {
            throw new IllegalStateException(failure);
        }
    }

    private static void render(HydrologyTile tile, MessageDigest digest) {
        CanonicalDigest out = new CanonicalDigest(digest);
        out.append("tile ").append(tile.key()).append('\n');
        out.append("seed ").append(tile.worldSeed()).append('\n');
        out.append("fingerprint ").append(tile.settingsFingerprint()).append('\n');
        out.append("size ").append(tile.tileSize()).append('\n');
        for (DrainageNode node : tile.nodes()) {
            out.append("node ").append(node).append('\n');
        }
        for (DrainageEdge edge : tile.edges()) {
            out.append("edge ").append(edge).append('\n');
        }
        for (RiverOutlet outlet : tile.outlets()) {
            out.append("outlet ").append(outlet).append('\n');
        }
        for (RiverCourse course : tile.courses()) {
            out.append("course ").append(course).append('\n');
        }
        for (HydrologyCavePlan plan : tile.cavePlans()) {
            out.append("cave ").append(plan).append('\n');
        }
        for (HydrologyDiagnosticCandidate diagnostic : tile.localDiagnosticCandidates()) {
            out.append("diagnostic ").append(diagnostic).append('\n');
        }
        for (HydrologyFeatureRef feature : tile.features()) {
            out.append("feature ").append(feature).append('\n');
        }
        List<Map.Entry<Long, HydrologyColumnSample>> columns =
                new ArrayList<>(tile.footprint().columns().entrySet());
        columns.sort(Comparator.comparingLong(Map.Entry::getKey));
        for (Map.Entry<Long, HydrologyColumnSample> column : columns) {
            out.append("column ").append(column.getKey()).append(' ').append(column.getValue()).append('\n');
        }
    }

    private static MessageDigest sha256() {
        try {
            return MessageDigest.getInstance("SHA-256");
        } catch (NoSuchAlgorithmException failure) {
            throw new IllegalStateException(failure);
        }
    }

    private static HydrologyTerrainSampler rollingCoast(int coastX) {
        return (int x, int z) -> {
            if (x >= coastX) {
                return oceanTerrain();
            }
            int height = 118 - Math.floorDiv(x, 12) + (int) StrictMath.round(StrictMath.sin(z / 18D) * 2D);
            return landTerrain(height, x >= 0 && x <= 24);
        };
    }

    private static HydrologyTerrainSample landTerrain(int height, boolean source) {
        return new HydrologyTerrainSample(
                height,
                1D,
                false,
                true,
                72,
                74,
                true,
                true,
                source,
                true,
                true,
                false,
                0D,
                source ? 1D : 0D,
                1D,
                1D,
                1D,
                1D,
                1D,
                1D,
                "parent",
                "surface",
                "mouth",
                "shore",
                "dry",
                "flooded",
                List.of("beta", "alpha"),
                List.of(),
                Double.NaN,
                null,
                Double.NaN,
                true,
                SurfaceRiverPolicy.INHERIT
        );
    }

    private static HydrologyTerrainSample oceanTerrain() {
        return new HydrologyTerrainSample(
                54,
                0D,
                true,
                false,
                30,
                32,
                false,
                false,
                false,
                false,
                false,
                false,
                0D,
                0D,
                0D,
                1D,
                1D,
                1D,
                1D,
                1D,
                "parent",
                "surface",
                "mouth",
                "shore",
                "dry",
                "flooded",
                List.of("beta", "alpha"),
                List.of(),
                Double.NaN,
                null,
                Double.NaN,
                true,
                SurfaceRiverPolicy.INHERIT
        );
    }

    private static HydrologyPlannerSettings planSettings() {
        HydrologyPlannerSettings.Source surfaceSources = new HydrologyPlannerSettings.Source(
                true, 4D, 80, 0, 6, 24);
        HydrologyPlannerSettings.Source undergroundSources = new HydrologyPlannerSettings.Source(
                true, 2D, Integer.MIN_VALUE, 0, 4, 32);
        HydrologyPlannerSettings.ChannelShape stableChannel =
                HydrologyPlannerSettings.ChannelShape.of(2D, 0D, 0D, 11);
        HydrologyPlannerSettings.Geometry geometry = new HydrologyPlannerSettings.Geometry(
                new HydrologyPlannerSettings.Meanders(224, 72, 0D, 0D, 0D, 0, 75D),
                stableChannel,
                stableChannel,
                stableChannel,
                HydrologyPlannerSettings.Geometry.defaults().drops());
        return new HydrologyPlannerSettings(
                63,
                new HydrologyPlannerSettings.Routing(128, 16, 512, 256, 0, 0, 0.5D, 12D, 0.5D, 0.1D, 1D, 0, HydrologyPlannerSettings.Regional.disabled()),
                new HydrologyPlannerSettings.Surface(
                        true,
                        surfaceSources,
                        4,
                        18,
                        2,
                        4,
                        10,
                        1.5D,
                        HydrologyPlannerSettings.Banks.defaults()),
                new HydrologyPlannerSettings.Hydraulics(4),
                HydrologyPlannerSettings.Underground.of(
                        true, undergroundSources, 68, 82, 4, 12, 2, 4, 5, 9, true, 0),
                HydrologyPlannerSettings.Outlets.of(
                        true,
                        new HydrologyPlannerSettings.Grotto(false, 4, 3, 3, 4096),
                        new HydrologyPlannerSettings.Grotto(false, 4, 3, 3, 4096),
                        false,
                        12,
                        32,
                        2,
                        4,
                        4),
                geometry,
                List.of(),
                List.of(),
                0D,
                HydrologyPlannerSettings.SeaCaves.disabled(),
                HydrologyPlannerSettings.SurfacePolicyBounds.NONE
        );
    }

    private record PlanDigest(String digest, int courses, int columns) {
    }

    private static final class CanonicalDigest {
        private final MessageDigest digest;

        private CanonicalDigest(MessageDigest digest) {
            this.digest = digest;
        }

        private CanonicalDigest append(Object value) {
            digest.update(String.valueOf(value).getBytes(StandardCharsets.UTF_8));
            return this;
        }

        private CanonicalDigest append(char value) {
            digest.update((byte) value);
            return this;
        }
    }
}
