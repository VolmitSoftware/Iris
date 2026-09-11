package art.arcane.iris.engine.hydrology;

import art.arcane.iris.engine.hydrology.policy.SurfaceRiverPolicy;

import art.arcane.iris.engine.hydrology.cave.HydrologyCavePlan;
import org.junit.Test;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

public class HydrologyPlannerSeaCaveTest {
    private static final HydrologyTileKey TILE = new HydrologyTileKey(0, 0);
    private static final int SEA = 63;
    private static final HydrologyPlannerSettings.Grotto CHAMBER = new HydrologyPlannerSettings.Grotto(true, 12, 7, 10, 32768);

    @Test
    public void seaCavesOpenFromTheSeaIntoTheCliffWithoutARiver() {
        HydrologyPlannerSettings.SeaCaves seaCaves = HydrologyPlannerSettings.SeaCaves.of(true, 2, 64, 8, 12);
        HydrologyPlannerSettings settings = settings(128, 0D, CHAMBER, 12, seaCaves);
        HydrologyTerrainSampler cliffCoast = cliffCoast(112, 92);

        HydrologyTile tile = new HydrologyPlanner(994L, settings, cliffCoast).plan(TILE);
        List<RiverCourse> caves = courses(tile, RiverCourseType.SEA_CAVE);

        assertFalse("diagnostics=" + tile.diagnosticCandidates(), caves.isEmpty());
        for (RiverCourse cave : caves) {
            assertEquals(1, cave.segments().size());
            HydraulicSegment chamber = cave.segments().getFirst();
            assertEquals(HydrologyFeatureType.COASTAL_GROTTO, chamber.type());
            assertEquals(SEA, chamber.upstreamHeadY());
            assertEquals(SEA, chamber.downstreamHeadY());
            HydrologyPoint inner = chamber.start();
            HydrologyPoint connection = chamber.end();
            assertTrue(cliffCoast.sample(connection.x(), connection.z()).ocean());
            assertFalse(cliffCoast.sample(inner.x(), inner.z()).ocean());
            assertTrue("inner=" + inner + " connection=" + connection,
                    inner.distanceSquared2D(connection) >= (long) seaCaves.depth() * seaCaves.depth());
            HydrologyCavePlan plan = tile.cavePlan(cave.id()).orElse(null);
            assertTrue("cave plan missing or rejected for " + cave.id() + " diagnostics=" + tile.diagnosticCandidates(),
                    plan != null && plan.accepted());

            int chamberColumns = 0;
            int apronColumns = 0;
            for (HydrologyColumnSample column : tile.footprint().columns().values()) {
                for (HydrologyColumnLayer layer : column.layers()) {
                    if (layer.feature().courseId() != cave.id()) {
                        continue;
                    }
                    assertEquals(HydrologyFeatureType.COASTAL_GROTTO, layer.feature().type());
                    if (layer.oceanApron()) {
                        apronColumns++;
                        assertTrue(cliffCoast.sample(column.x(), column.z()).ocean());
                        assertTrue(StrictMath.hypot(column.x() - connection.x(), column.z() - connection.z()) <= 12.25D);
                        continue;
                    }
                    if (!layer.terrainOwned() || !layer.channel()) {
                        continue;
                    }
                    chamberColumns++;
                    assertFalse(cliffCoast.sample(column.x(), column.z()).ocean());
                    assertTrue(layer.bedY() <= SEA);
                    assertEquals(SEA, layer.fluidHeadY());
                    assertTrue(layer.ceilingY() >= SEA);
                    assertTrue(layer.ceilingY() <= column.naturalHeight() - 1);
                }
            }
            assertTrue("chamber=" + chamberColumns, chamberColumns >= 100);
            assertTrue("apron=" + apronColumns, apronColumns > 0);
            HydrologyColumnLayer center = tile.columnAt(inner.x(), inner.z()).orElseThrow().layers().stream()
                    .filter((HydrologyColumnLayer layer) -> layer.feature().courseId() == cave.id() && layer.channel())
                    .findFirst()
                    .orElseThrow();
            // The chamber walls carry the grotto roughness, so the centre sits within a few blocks of the ellipsoid.
            assertTrue("bed=" + center.bedY(), center.bedY() <= SEA - CHAMBER.verticalRadius() + 3
                    && center.bedY() >= SEA - CHAMBER.verticalRadius() - 3);
            assertTrue("ceiling=" + center.ceilingY(), center.ceilingY() <= SEA + CHAMBER.headroom()
                    && center.ceilingY() >= SEA + CHAMBER.headroom() - 3);
        }
        boolean featured = false;
        for (HydrologyFeatureRef feature : tile.features()) {
            featured |= feature.type() == HydrologyFeatureType.COASTAL_GROTTO;
        }
        assertTrue(featured);

        HydrologyTile withoutCaves = new HydrologyPlanner(
                994L,
                settings(128, 0D, CHAMBER, 12, HydrologyPlannerSettings.SeaCaves.disabled()),
                cliffCoast
        ).plan(TILE);
        assertTrue(courses(withoutCaves, RiverCourseType.SEA_CAVE).isEmpty());
        assertEquals(withoutCaves.outlets(), tile.outlets());
    }

    @Test
    public void seaCavesAreDeterministicAndOwnedByExactlyOneTile() {
        HydrologyPlannerSettings settings = settings(128, 0D, CHAMBER, 12,
                HydrologyPlannerSettings.SeaCaves.of(true, 2, 64, 8, 12));
        HydrologyTerrainSampler cliffCoast = cliffCoast(112, 92);
        List<HydrologyTileKey> keys = List.of(
                TILE,
                new HydrologyTileKey(0, -1),
                new HydrologyTileKey(0, 1),
                new HydrologyTileKey(-1, 0),
                new HydrologyTileKey(1, 0)
        );
        HydrologyPlanner forward = new HydrologyPlanner(41L, settings, cliffCoast);
        HydrologyPlanner reversed = new HydrologyPlanner(41L, settings, cliffCoast);
        List<HydrologyTile> forwardTiles = new ArrayList<>();
        for (HydrologyTileKey key : keys) {
            forwardTiles.add(forward.plan(key));
        }
        List<HydrologyTile> reversedTiles = new ArrayList<>();
        for (HydrologyTileKey key : keys.reversed()) {
            reversedTiles.add(reversed.plan(key));
        }

        for (int index = 0; index < keys.size(); index++) {
            assertEquals(keys.get(index).toString(), forwardTiles.get(index), reversedTiles.get(keys.size() - 1 - index));
        }
        List<RiverCourse> caves = courses(forwardTiles.getFirst(), RiverCourseType.SEA_CAVE);
        assertFalse(caves.isEmpty());
        Set<Long> ownedIds = new HashSet<>();
        for (RiverCourse cave : caves) {
            ownedIds.add(cave.id());
        }
        for (int index = 1; index < keys.size(); index++) {
            for (RiverCourse course : forwardTiles.get(index).courses()) {
                assertFalse(keys.get(index) + " republished " + course.id(), ownedIds.contains(course.id()));
            }
        }
    }

    @Test
    public void seaCavesPreferTheSteeperCoastAndKeepTheirSpacing() {
        // The high stretch is 128 blocks of coast; the spacing leaves room for a second cave wherever the first lands.
        HydrologyPlannerSettings.SeaCaves seaCaves = HydrologyPlannerSettings.SeaCaves.of(true, 2, 32, 4, 12);
        HydrologyPlannerSettings settings = settings(256, 0D, CHAMBER, 12, seaCaves);
        HydrologyTerrainSampler coast = (int x, int z) -> x >= 240
                ? HydrologyTerrainSample.ocean(54, "ocean")
                : HydrologyTerrainSample.openLand(Math.floorDiv(z, 128) % 2 == 0 ? 70 : 90, 2D, "land");

        HydrologyTile tile = new HydrologyPlanner(17L, settings, coast).plan(TILE);
        List<RiverCourse> caves = courses(tile, RiverCourseType.SEA_CAVE);

        assertEquals("diagnostics=" + tile.diagnosticCandidates(), 2, caves.size());
        for (RiverCourse cave : caves) {
            HydrologyPoint connection = cave.segments().getFirst().end();
            assertTrue("connection=" + connection + " diagnostics=" + tile.diagnosticCandidates(),
                    connection.z() >= 128 && connection.z() < 256);
        }
        HydrologyPoint first = caves.get(0).segments().getFirst().end();
        HydrologyPoint second = caves.get(1).segments().getFirst().end();
        long minimum = seaCaves.minimumSpacing() - 2L;
        assertTrue(first + " vs " + second, first.distanceSquared2D(second) >= minimum * minimum);
        int lowCoastRejections = 0;
        for (HydrologyDiagnosticCandidate candidate : tile.diagnosticCandidates()) {
            if (candidate.kind() != HydrologyCandidateKind.OUTLET
                    || candidate.projectedType() != HydrologyFeatureType.COASTAL_GROTTO
                    || candidate.point().z() < 0 || candidate.point().z() >= 128) {
                continue;
            }
            lowCoastRejections++;
            assertEquals(candidate.toString(), HydrologyCandidateRejection.SOURCE_QUOTA, candidate.rejection());
        }
        assertTrue(lowCoastRejections > 0);
    }

    @Test
    public void seaCavesKeepClearOfRiverMouthsAndLowCoasts() {
        HydrologyPlannerSettings settings = settings(128, 2D, CHAMBER, 12,
                HydrologyPlannerSettings.SeaCaves.of(true, 4, 32, 8, 12));
        HydrologyTerrainSampler riverCoast = (int x, int z) -> x >= 112
                ? HydrologyTerrainSample.ocean(54, "ocean")
                : land(92, x <= 16);

        HydrologyTile tile = new HydrologyPlanner(994L, settings, riverCoast).plan(TILE);
        List<HydrologyPoint> outletPoints = new ArrayList<>();
        for (RiverCourse course : tile.courses()) {
            if (course.type() == RiverCourseType.SEA_CAVE) {
                continue;
            }
            for (HydraulicSegment segment : course.segments()) {
                if (segment.type() == HydrologyFeatureType.MOUTH
                        || segment.type() == HydrologyFeatureType.COASTAL_GROTTO
                        || segment.type() == HydrologyFeatureType.INLAND_GROTTO) {
                    outletPoints.addAll(segment.centerline());
                }
            }
        }
        List<RiverCourse> caves = courses(tile, RiverCourseType.SEA_CAVE);

        assertFalse("diagnostics=" + tile.diagnosticCandidates(), outletPoints.isEmpty());
        assertFalse("diagnostics=" + tile.diagnosticCandidates(), caves.isEmpty());
        long clearance = 2L * CHAMBER.horizontalRadius() + 12L;
        for (RiverCourse cave : caves) {
            for (HydrologyPoint point : cave.segments().getFirst().centerline()) {
                for (HydrologyPoint outlet : outletPoints) {
                    assertTrue(point + " within " + clearance + " of " + outlet,
                            point.distanceSquared2D(outlet) >= clearance * clearance);
                }
            }
        }

        HydrologyTile lowCoast = new HydrologyPlanner(
                994L,
                settings(128, 0D, CHAMBER, 12, HydrologyPlannerSettings.SeaCaves.of(true, 4, 32, 8, 12)),
                cliffCoast(112, 66)
        ).plan(TILE);
        assertTrue(courses(lowCoast, RiverCourseType.SEA_CAVE).isEmpty());
        assertTrue(lowCoast.diagnosticCandidates().toString(),
                hasDiagnostic(lowCoast, HydrologyCandidateRejection.SURFACE_HEAD_RANGE));
    }

    @Test
    public void seaCaveChamberFitsTheVolumeCap() {
        HydrologyPlannerSettings settings = settings(128, 0D,
                new HydrologyPlannerSettings.Grotto(true, 12, 7, 10, 1000), 12,
                HydrologyPlannerSettings.SeaCaves.of(true, 2, 64, 8, 12));

        HydrologyTile tile = new HydrologyPlanner(994L, settings, cliffCoast(112, 92)).plan(TILE);

        assertTrue(courses(tile, RiverCourseType.SEA_CAVE).isEmpty());
        assertTrue(tile.diagnosticCandidates().toString(),
                hasDiagnostic(tile, HydrologyCandidateRejection.VOLUME_LIMIT));
    }

    private static boolean hasDiagnostic(HydrologyTile tile, HydrologyCandidateRejection rejection) {
        for (HydrologyDiagnosticCandidate candidate : tile.diagnosticCandidates()) {
            if (candidate.kind() == HydrologyCandidateKind.OUTLET
                    && candidate.projectedType() == HydrologyFeatureType.COASTAL_GROTTO
                    && candidate.rejection() == rejection) {
                return true;
            }
        }
        return false;
    }

    private static List<RiverCourse> courses(HydrologyTile tile, RiverCourseType type) {
        ArrayList<RiverCourse> selected = new ArrayList<>();
        for (RiverCourse course : tile.courses()) {
            if (course.type() == type) {
                selected.add(course);
            }
        }
        return selected;
    }

    private static HydrologyTerrainSampler cliffCoast(int coastX, int height) {
        return (int x, int z) -> x >= coastX
                ? HydrologyTerrainSample.ocean(54, "ocean")
                : HydrologyTerrainSample.openLand(height, 2D, "land");
    }

    private static HydrologyTerrainSample land(int height, boolean surfaceSource) {
        return new HydrologyTerrainSample(
                height,
                2D,
                false,
                true,
                72,
                74,
                true,
                true,
                surfaceSource,
                surfaceSource,
                false,
                false,
                0D,
                surfaceSource ? 1D : 0D,
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
                List.of("default"),
                List.of(),
                Double.NaN,
                null,
                Double.NaN,
                true,
                SurfaceRiverPolicy.INHERIT
        );
    }

    private static HydrologyPlannerSettings settings(
            int tileSize,
            double surfaceDensity,
            HydrologyPlannerSettings.Grotto coastal,
            int maximumOceanApron,
            HydrologyPlannerSettings.SeaCaves seaCaves
    ) {
        HydrologyPlannerSettings.Source surfaceSources = new HydrologyPlannerSettings.Source(
                true, surfaceDensity, 80, 0, 6, 24);
        HydrologyPlannerSettings.Source undergroundSources = new HydrologyPlannerSettings.Source(
                true, 0D, Integer.MIN_VALUE, 0, 4, 32);
        HydrologyPlannerSettings.Surface surface = new HydrologyPlannerSettings.Surface(
                true, surfaceSources, 4, 18, 2, 4, 10, 1.5D, HydrologyPlannerSettings.Banks.defaults());
        return new HydrologyPlannerSettings(
                SEA,
                new HydrologyPlannerSettings.Routing(tileSize, 16, 512, 256, 0, 0, 0.5D, 12D, 0.5D, 0.1D, 1D, 0, HydrologyPlannerSettings.Regional.disabled()),
                surface,
                new HydrologyPlannerSettings.Hydraulics(4),
                HydrologyPlannerSettings.Underground.of(
                        false, undergroundSources, 68, 82, 4, 12, 2, 4, 5, 9, true, 0),
                HydrologyPlannerSettings.Outlets.of(
                        true,
                        coastal,
                        new HydrologyPlannerSettings.Grotto(false, 4, 3, 3, 4096),
                        false,
                        12,
                        32,
                        maximumOceanApron,
                        4,
                        4
                ),
                HydrologyPlannerSettings.Geometry.defaults(),
                List.of(),
                List.of(),
                surface.shoreWidth(),
                seaCaves,
                HydrologyPlannerSettings.SurfacePolicyBounds.NONE
        );
    }
}
