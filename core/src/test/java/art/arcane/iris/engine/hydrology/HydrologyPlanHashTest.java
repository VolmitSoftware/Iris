package art.arcane.iris.engine.hydrology;

import art.arcane.iris.engine.hydrology.cave.HydrologyCavePlan;
import org.junit.Test;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Map;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

public class HydrologyPlanHashTest {
    private static final String EXPECTED_PLAN_DIGEST = "de765d4be6c95954f8df344ee2d1dd72c97e8640fc28c92c3ec4717cff0d88e4";

    private static final List<HydrologyTileKey> TILES = List.of(
            new HydrologyTileKey(0, 0),
            new HydrologyTileKey(-1, -1),
            new HydrologyTileKey(0, -1),
            new HydrologyTileKey(1, 0));

    @Test
    public void fullTilePlansAreByteIdentical() {
        StringBuilder canonical = new StringBuilder();
        int courses = 0;
        int columns = 0;
        for (long seed : new long[]{77L, 19L}) {
            HydrologyPlannerSettings settings = planSettings();
            HydrologyPlanner planner = new HydrologyPlanner(seed, settings, rollingCoast(112));
            for (HydrologyTileKey key : TILES) {
                HydrologyTile tile = planner.plan(key);
                courses += tile.courses().size();
                columns += tile.footprint().columns().size();
                canonical.append(render(tile));
            }
        }
        assertTrue("planned courses: " + courses, courses > 0);
        assertTrue("planned columns: " + columns, columns > 0);
        assertEquals(EXPECTED_PLAN_DIGEST, digest(canonical.toString()));
    }

    private static String render(HydrologyTile tile) {
        StringBuilder out = new StringBuilder();
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
        for (HydrologyDiagnosticCandidate diagnostic : tile.diagnosticCandidates()) {
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
        return out.toString();
    }

    private static String digest(String value) {
        MessageDigest sha256;
        try {
            sha256 = MessageDigest.getInstance("SHA-256");
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException(e);
        }
        byte[] hash = sha256.digest(value.getBytes(StandardCharsets.UTF_8));
        StringBuilder hex = new StringBuilder(hash.length * 2);
        for (byte b : hash) {
            hex.append(Character.forDigit((b >> 4) & 0xF, 16));
            hex.append(Character.forDigit(b & 0xF, 16));
        }
        return hex.toString();
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
                true
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
                true
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
                new HydrologyPlannerSettings.Routing(128, 16, 512, 256, 0, 0, 0.5D, 12D, 0.5D, 0.1D, 1D, 0),
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
                HydrologyPlannerSettings.SeaCaves.disabled());
    }
}
