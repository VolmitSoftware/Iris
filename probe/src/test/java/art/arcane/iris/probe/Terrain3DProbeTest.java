package art.arcane.iris.probe;

import org.junit.Test;

import java.util.Arrays;
import java.util.BitSet;
import java.util.Map;
import java.util.Set;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertArrayEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertThrows;
import static org.junit.Assert.assertTrue;

public final class Terrain3DProbeTest {
    @Test
    public void recognizesAddedRockAboveAnAirGapWithFloorAndRoof() {
        boolean[] column = column(12, 0, 3, 7, 9);
        Terrain3DProbe.ColumnEvidence evidence = Terrain3DProbe.inspectColumn(6, column, column, column, column);

        assertEquals(3, evidence.additiveBlocks());
        assertEquals(3, evidence.retainedAdditiveBlocks());
        assertEquals(3, evidence.carvedBlocks());
        assertEquals(3, evidence.retainedCarvedBlocks());
        assertEquals(1, evidence.retainedCoveredGaps());
        assertEquals(3, evidence.maximumGapHeight());
        assertEquals(9, evidence.generatedTop());
        assertEquals(0, evidence.geometryMismatches());
    }

    @Test
    public void doesNotCountCavesOrFloatingObjectsAsVolumetricTerrain() {
        boolean[] predicted = column(16, 0, 9);
        boolean[] generated = column(16, 0, 3, 7, 9, 13, 14);
        Terrain3DProbe.ColumnEvidence evidence = Terrain3DProbe.inspectColumn(
                9, predicted, predicted, generated, generated);

        assertEquals(0, evidence.coveredGaps());
        assertEquals(0, evidence.retainedCoveredGaps());
        assertEquals(0, evidence.additiveBlocks());
        assertEquals(0, evidence.retainedAdditiveBlocks());
        assertEquals(5, evidence.finalGeometryDifferences());
    }

    @Test
    public void rejectsCoveredGapProofWhenLaterGenerationFillsAirOrRemovesTheRoof() {
        boolean[] predicted = column(12, 0, 3, 7, 9);
        boolean[] filled = predicted.clone();
        filled[5] = true;
        Terrain3DProbe.ColumnEvidence obstructed = Terrain3DProbe.inspectColumn(6, predicted, predicted, filled, predicted);
        boolean[] roofRemoved = predicted.clone();
        roofRemoved[7] = false;
        Terrain3DProbe.ColumnEvidence open = Terrain3DProbe.inspectColumn(6, predicted, predicted, roofRemoved, roofRemoved);

        assertEquals(1, obstructed.coveredGaps());
        assertEquals(0, obstructed.retainedCoveredGaps());
        assertEquals(0, open.retainedCoveredGaps());
    }

    @Test
    public void detectsOneBlockTerrainErrorsAndIgnoresSingleBlockCracksForOverhangCoverage() {
        boolean[] predicted = column(12, 0, 3, 5, 9);
        boolean[] actual = predicted.clone();
        actual[10] = true;
        Terrain3DProbe.ColumnEvidence evidence = Terrain3DProbe.inspectColumn(6, predicted, actual, actual, actual);

        assertEquals(0, evidence.coveredGaps());
        assertEquals(1, evidence.geometryMismatches());
        assertEquals(10, evidence.generatedTop());
    }

    @Test
    public void requiresPositiveBiomeCoverageAndDoesNotPassAnEmptyScan() {
        assertFalse(Terrain3DProbe.coverageFailures(Map.of(), Set.of(), false).isEmpty());
        Terrain3DProbe.BiomeEvidence biome = new Terrain3DProbe.BiomeEvidence();
        biome.shapedColumns = 200;
        biome.retainedAdditiveBlocks = 4;
        Map<String, Terrain3DProbe.BiomeEvidence> evidence = Map.of("highlands", biome);

        assertFalse(Terrain3DProbe.coverageFailures(evidence, Set.of("highlands"), false).isEmpty());
        biome.retainedCoveredGaps = 1;
        assertTrue(Terrain3DProbe.coverageFailures(evidence, Set.of("highlands"), false).isEmpty());
        assertFalse(Terrain3DProbe.coverageFailures(evidence, Set.of("missing"), false).isEmpty());
        biome.finalGeometryDifferences = 1;
        assertTrue(Terrain3DProbe.coverageFailures(evidence, Set.of("highlands"), false).isEmpty());
        assertFalse(Terrain3DProbe.coverageFailures(evidence, Set.of("highlands"), true).isEmpty());
    }

    @Test
    public void rejectsUnboundedOrMalformedProbeInputs() {
        String[] inputs = {"/tmp/pack", "overworld", "1337", "-1", "1", "-1", "1", "highlands", "/tmp/output", "false", "true"};
        Terrain3DProbe.Configuration configuration = Terrain3DProbe.Configuration.parse(inputs);
        assertEquals(9, configuration.chunkCount());
        assertTrue(configuration.studio());
        inputs[4] = "64";
        assertThrows(IllegalArgumentException.class, () -> Terrain3DProbe.Configuration.parse(inputs));
        inputs[4] = "1";
        inputs[7] = "highlands,";
        assertThrows(IllegalArgumentException.class, () -> Terrain3DProbe.Configuration.parse(inputs));
    }

    @Test
    public void recordsTinyPerforationsAndThinRoofsWithoutCallingThemLargeOverhangs() {
        Terrain3DProbe.ColumnMorphology morphology = Terrain3DProbe.inspectMorphology(column(16, 0, 4, 6, 6, 10, 13));

        assertEquals(3, morphology.solidSpans());
        assertEquals(4, morphology.coveredAirBlocks());
        assertArrayEquals(new long[]{1, 1, 0, 0, 0}, morphology.gapHeightBins());
        assertArrayEquals(new long[]{1, 0, 1, 0, 0}, morphology.upperSolidThicknessBins());
    }

    @Test
    public void countsOnlyFaceConnectedDetachedTerrainAndReportsRetention() {
        boolean[][][] voxels = terrainVolume(5, 5);
        voxels[1][1][7] = true;
        voxels[2][2][8] = true;
        Terrain3DProbe.TopologyEvidence evidence = inspectVolume(voxels, new VolumeOptions(false, false, true));

        assertEquals(2, evidence.detachedComponents());
        assertEquals(2, evidence.detachedBlocks());
        assertEquals(1, evidence.retainedDetachedBlocks());
        assertEquals(1, evidence.fullyRetainedSmallComponents());
        assertArrayEquals(new long[]{2, 0, 0, 0}, evidence.detachedSizeBins());
    }

    @Test
    public void followsGroundSupportAcrossAChunkBoundary() {
        boolean[][][] voxels = terrainVolume(20, 5);
        for (int x = 14; x <= 17; x++) {
            voxels[x][2][8] = true;
        }
        Arrays.fill(voxels[16][2], 0, 9, true);
        Terrain3DProbe.TopologyEvidence evidence = inspectVolume(voxels, new VolumeOptions(false, false, false));

        assertEquals(1, evidence.groundedComponents());
        assertEquals(0, evidence.detachedComponents());
    }

    @Test
    public void distinguishesThe64BlockBoundaryWithoutCullingAnything() {
        boolean[][][] voxels = terrainVolume(12, 12);
        for (int x = 1; x <= 8; x++) {
            for (int z = 1; z <= 8; z++) {
                voxels[x][z][7] = true;
            }
        }
        Terrain3DProbe.TopologyEvidence small = inspectVolume(voxels, new VolumeOptions(false, false, false));
        voxels[9][8][7] = true;
        Terrain3DProbe.TopologyEvidence large = inspectVolume(voxels, new VolumeOptions(false, false, false));

        assertArrayEquals(new long[]{0, 1, 0, 0}, small.detachedSizeBins());
        assertEquals(1, small.fullyRetainedSmallComponents());
        assertArrayEquals(new long[]{0, 0, 1, 0}, large.detachedSizeBins());
        assertEquals(0, large.fullyRetainedSmallComponents());
    }

    @Test
    public void censorsTruncatedComponentsAndExcludesUnshapedAuthoredTerrain() {
        boolean[][][] edge = terrainVolume(5, 5);
        edge[0][2][7] = true;
        Terrain3DProbe.TopologyEvidence boundary = inspectVolume(edge, new VolumeOptions(false, false, false));
        boolean[][][] owned = terrainVolume(5, 5);
        owned[2][2][7] = true;
        Terrain3DProbe.TopologyEvidence ownership = inspectVolume(owned, new VolumeOptions(true, false, false));
        Terrain3DProbe.TopologyEvidence unshaped = inspectVolume(owned, new VolumeOptions(false, true, false));

        assertEquals(1, boundary.censoredComponents());
        assertEquals(0, boundary.detachedComponents());
        assertEquals(1, ownership.censoredComponents());
        assertEquals(0, ownership.detachedComponents());
        assertEquals(0, unshaped.detachedComponents());
    }

    @Test
    public void rejectsPartialTopologyEvidence() {
        Terrain3DProbe.TerrainTopology topology = new Terrain3DProbe.TerrainTopology(3, 3);
        topology.column(0, 0, new Terrain3DProbe.TopologyColumn(column(16, 0, 1), column(16, 0, 1), new BitSet(), true));

        assertThrows(IllegalStateException.class, topology::inspect);
    }

    private static boolean[][][] terrainVolume(int width, int depth) {
        boolean[][][] result = new boolean[width][depth][16];
        for (int x = 0; x < width; x++) {
            for (int z = 0; z < depth; z++) {
                result[x][z][0] = true;
            }
        }
        return result;
    }

    private static Terrain3DProbe.TopologyEvidence inspectVolume(boolean[][][] voxels, VolumeOptions options) {
        Terrain3DProbe.TerrainTopology topology = new Terrain3DProbe.TerrainTopology(voxels.length, voxels[0].length);
        for (int x = 0; x < voxels.length; x++) {
            for (int z = 0; z < voxels[x].length; z++) {
                BitSet excluded = new BitSet();
                if (options.excludedNeighbor() && x == 3 && z == 2) {
                    excluded.set(7);
                }
                boolean[] retained = voxels[x][z].clone();
                if (options.removeOneBlock() && x == 1 && z == 1) {
                    retained[7] = false;
                }
                topology.column(x, z, new Terrain3DProbe.TopologyColumn(voxels[x][z], retained, excluded,
                        !options.unshaped()));
            }
        }
        return topology.inspect();
    }

    private record VolumeOptions(boolean excludedNeighbor, boolean unshaped, boolean removeOneBlock) {
    }

    private static boolean[] column(int height, int... boundaries) {
        boolean[] result = new boolean[height];
        for (int index = 0; index < boundaries.length; index += 2) {
            Arrays.fill(result, boundaries[index], boundaries[index + 1] + 1, true);
        }
        return result;
    }
}
