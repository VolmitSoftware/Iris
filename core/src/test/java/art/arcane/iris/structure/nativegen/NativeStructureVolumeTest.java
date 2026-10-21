package art.arcane.iris.structure.nativegen;

import art.arcane.volmlib.nativelib.terrain.structure.NativeStructureVolume;

import org.junit.Test;

import java.util.List;
import java.util.ArrayList;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

public class NativeStructureVolumeTest {
    private static final NativeStructureVolume PIECE =
            new NativeStructureVolume("minecraft:village_plains", 10, 64, 20, 25, 78, 40, null);

    @Test
    public void rectQueriesIgnoreTheVerticalAxis() {
        assertTrue(PIECE.intersectsRect(0, 0, 10, 20));
        assertTrue(PIECE.intersectsRect(25, 40, 60, 60));
        assertFalse(PIECE.intersectsRect(26, 20, 60, 40));
        assertFalse(PIECE.intersectsRect(10, 41, 25, 60));
    }

    @Test
    public void boxQueriesSeparateStackedVolumes() {
        assertTrue(PIECE.intersects(0, 60, 0, 40, 70, 50));
        assertFalse(PIECE.intersects(0, 0, 0, 40, 63, 50));
        assertFalse(PIECE.intersects(0, 79, 0, 40, 200, 50));
    }

    @Test
    public void containmentIsInclusiveOnEveryFace() {
        assertTrue(PIECE.contains(10, 64, 20));
        assertTrue(PIECE.contains(25, 78, 40));
        assertFalse(PIECE.contains(9, 64, 20));
        assertFalse(PIECE.contains(25, 79, 40));
    }

    @Test
    public void marginWidensContainmentSymmetrically() {
        assertFalse(PIECE.containsWithin(8, 64, 20, 1));
        assertTrue(PIECE.containsWithin(8, 64, 20, 2));
        assertTrue(PIECE.containsWithin(25, 80, 42, 2));
    }

    @Test
    public void factoryNormalizesCornerOrder() {
        NativeStructureVolume normalized = NativeStructureVolume.of("test:piece", 25, 78, 40, 10, 64, 20);

        assertEquals(PIECE.minX(), normalized.minX());
        assertEquals(PIECE.minY(), normalized.minY());
        assertEquals(PIECE.minZ(), normalized.minZ());
        assertEquals(PIECE.maxX(), normalized.maxX());
        assertEquals(PIECE.maxY(), normalized.maxY());
        assertEquals(PIECE.maxZ(), normalized.maxZ());
    }

    @Test
    public void terrainInfluenceDoesNotChangePhysicalIntersection() {
        NativeStructureVolume volume = adjustedPoint(0, 90, 0, 24);
        assertTrue(volume.intersectsInfluenceRect(-1, 0, -1, 0));
        assertFalse(volume.intersects(-1, 40, 0, -1, 160, 0));
        assertFalse(volume.intersectsRect(-1, 0, -1, 0));
    }

    @Test
    public void oppositeGradesBlendWithoutCuttingTheSurface() {
        List<NativeStructureVolume> volumes = List.of(adjustedPoint(0, 90, 0, 20), adjustedPoint(20, 110, 0, 20));
        assertEquals(100, NativeStructureVolume.surfaceCutY(volumes, 10, 0, 100));
    }

    @Test
    public void halfBlockCutsRoundTheirSignedDepthLikeTheFitter() {
        assertEquals(99, NativeStructureVolume.surfaceCutY(List.of(adjustedPoint(0, 99, 0, 24)), 12, 0, 100));
    }

    @Test
    public void realVillageJunctionsLowerTheReportedPineGround() {
        int[][] junctions = {{-2591,144,-7062},{-2591,144,-7061},{-2593,141,-7055},{-2592,141,-7055},
                {-2591,163,-7070},{-2591,163,-7071},{-2608,149,-7075},{-2609,149,-7075},
                {-2591,136,-7051},{-2591,137,-7051},{-2602,126,-7047},{-2591,136,-7050},
                {-2591,137,-7050},{-2602,126,-7046},{-2602,122,-7042},{-2602,122,-7041},
                {-2624,124,-7067},{-2624,124,-7068}};
        List<NativeStructureVolume.SurfaceAnchor> anchors = new ArrayList<>();
        for (int[] junction : junctions) {
            anchors.add(new NativeStructureVolume.SurfaceAnchor(junction[0], junction[0], junction[2], junction[2], junction[1], 1));
        }
        NativeStructureVolume volume = new NativeStructureVolume("minecraft:village_plains", -2600, 144, -7070, -2590, 145, -7062,
                new NativeStructureVolume.SurfaceAdjustment(anchors, 24, 64));
        assertEquals(151, NativeStructureVolume.surfaceCutY(List.of(volume), -2601, -7063, 162));
        assertEquals(162, NativeStructureVolume.surfaceCutY(List.of(volume), -2700, -7063, 162));
    }

    private static NativeStructureVolume adjustedPoint(int x, int groundY, int z, int padding) {
        return new NativeStructureVolume("minecraft:village_plains", x, groundY, z, x, groundY, z,
                new NativeStructureVolume.SurfaceAdjustment(List.of(
                        new NativeStructureVolume.SurfaceAnchor(x, x, z, z, groundY, 1)), padding, 64));
    }
}
