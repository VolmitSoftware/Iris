package art.arcane.iris.world.history;

import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.TemporaryFolder;

import java.util.ArrayList;
import java.util.List;
import java.util.OptionalInt;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertSame;
import static org.junit.Assert.assertThrows;
import static org.junit.Assert.assertTrue;

public class TransitionGeometryBlenderTest {
    private static final BoundaryColumnGeometry.Voxel STONE = new BoundaryColumnGeometry.Voxel(
            "minecraft:stone", BoundaryColumnGeometry.Phase.SOLID, "", false);
    private static final BoundaryColumnGeometry.Voxel AIR = new BoundaryColumnGeometry.Voxel(
            "minecraft:air", BoundaryColumnGeometry.Phase.AIR, "", false);
    private static final BoundaryColumnGeometry.Voxel WATER = new BoundaryColumnGeometry.Voxel(
            "minecraft:water[level=0]", BoundaryColumnGeometry.Phase.FLUID, "minecraft:water[level=0]", false);
    @Rule
    public TemporaryFolder temporaryFolder = new TemporaryFolder();

    @Test
    public void preservesCaveIslandAndWaterCrossSectionsAtFirstNewFace() throws Exception {
        BoundaryColumnGeometry old = BoundaryColumnGeometry.fromVoxels(-4,
                List.of(STONE, AIR, WATER, AIR, STONE, STONE, AIR, AIR));
        BoundaryColumnGeometry current = filled(STONE, 8);
        TransitionGenerationPlan plan = plan(List.of(signature(15, 0, old)));

        BoundaryColumnGeometry blended = TransitionGeometryBlender.blendColumn(plan, 16, 0, current);

        assertEquals(old, blended);
        assertSame(current, TransitionGeometryBlender.blendColumn(plan, 47, 0, current));
        assertSame(current, TransitionGeometryBlender.blendColumn(plan, 15, 0, current));
    }

    @Test
    public void narrowHistoricalCaveContinuesBeforeTaperingIntoTallSolidTerrain() throws Exception {
        ArrayList<BoundaryColumnGeometry.Voxel> oldVoxels = new ArrayList<>(768);
        for (int offset = 0; offset < 768; offset++) {
            oldVoxels.add(offset == 384 ? AIR : STONE);
        }
        BoundaryColumnGeometry old = BoundaryColumnGeometry.fromVoxels(-4, oldVoxels);
        BoundaryColumnGeometry current = filled(STONE, 768);
        TransitionGenerationPlan plan = plan(List.of(signature(15, 0, old)));

        for (int x = 16; x <= 23; x++) {
            assertEquals(AIR, TransitionGeometryBlender.blendColumn(plan, x, 0, current).voxelAt(380));
        }
        assertSame(current, TransitionGeometryBlender.blendColumn(plan, 47, 0, current));
    }

    @Test
    public void giantGroundChangesKeepGroundReferenceSeparateFromUpperRoof() {
        ArrayList<BoundaryColumnGeometry.Voxel> oldVoxels = new ArrayList<>(512);
        ArrayList<BoundaryColumnGeometry.Voxel> newVoxels = new ArrayList<>(512);
        for (int offset = 0; offset < 512; offset++) {
            oldVoxels.add(offset <= 20 || offset >= 480 ? STONE : AIR);
            newVoxels.add(offset <= 380 || offset >= 480 ? STONE : AIR);
        }
        BoundaryColumnGeometry old = BoundaryColumnGeometry.fromVoxels(-64, oldVoxels);
        BoundaryColumnGeometry current = BoundaryColumnGeometry.fromVoxels(-64, newVoxels);
        BoundaryGeometryInfluence influence = new BoundaryGeometryInfluence(0.5D, 0.5D,
                List.of(new BoundaryGeometryInfluence.Contribution(old, 1D)));

        BoundaryColumnGeometry blended = TransitionGeometryBlender.blendColumn(influence, 31, 0, current);

        assertEquals(200, blended.surfaceOffsetNear(200D));
        assertEquals(STONE, blended.voxelAt(416));
        assertEquals(STONE, blended.voxelAt(447));
    }

    @Test
    public void conflictingFluidFamiliesProduceANewSideSolidPlug() throws Exception {
        BoundaryColumnGeometry.Voxel lava = new BoundaryColumnGeometry.Voxel("minecraft:lava[level=0]",
                BoundaryColumnGeometry.Phase.FLUID, "minecraft:lava[level=0]", false);
        BoundaryColumnGeometry current = filled(lava, 8);
        TransitionGenerationPlan plan = plan(List.of(signature(15, 0, filled(WATER, 8))));

        BoundaryColumnGeometry blended = TransitionGeometryBlender.blendColumn(plan, 16, 0, current);

        for (BoundaryColumnGeometry.Voxel voxel : blended.voxels()) {
            assertEquals(BoundaryColumnGeometry.Phase.SOLID, voxel.phase());
            assertEquals("minecraft:obsidian", voxel.stateKey());
        }
        assertSame(current, TransitionGeometryBlender.blendColumn(plan, 47, 0, current));
    }

    @Test
    public void differentLevelsOfTheSameFluidDoNotCreateSolidPlugs() throws Exception {
        BoundaryColumnGeometry.Voxel flowing = new BoundaryColumnGeometry.Voxel("minecraft:water[level=3]",
                BoundaryColumnGeometry.Phase.FLUID, "minecraft:water[level=3]", false);
        BoundaryColumnGeometry current = filled(flowing, 8);
        TransitionGenerationPlan plan = plan(List.of(signature(15, 0, filled(WATER, 8))));

        assertEquals(WATER, TransitionGeometryBlender.blendColumn(plan, 16, 0, current).voxelAt(0));
    }

    @Test
    public void repeatedOrReorderedChunkRequestsDoNotExtendInfluence() throws Exception {
        BoundaryColumnGeometry old = BoundaryColumnGeometry.fromVoxels(-4,
                List.of(STONE, STONE, AIR, AIR, STONE, AIR, AIR, AIR));
        BoundaryColumnGeometry current = filled(STONE, 8);
        TransitionGenerationPlan plan = plan(List.of(signature(15, 0, old), signature(0, 15, old)));
        BoundaryColumnGeometry before = TransitionGeometryBlender.blendColumn(plan, 25, 6, current);

        for (int x = 46; x >= 16; x--) {
            TransitionGeometryBlender.blendColumn(plan, x, 0, current);
        }

        assertEquals(before, TransitionGeometryBlender.blendColumn(plan, 25, 6, current));
        assertSame(current, TransitionGeometryBlender.blendColumn(plan, 80, 0, current));
    }

    @Test
    public void cornerResultsDoNotDependOnSnapshotInsertionOrder() throws Exception {
        BoundaryColumnGeometry lower = BoundaryColumnGeometry.fromVoxels(-4,
                List.of(STONE, STONE, AIR, AIR, AIR, AIR, AIR, AIR));
        BoundaryColumnGeometry upper = BoundaryColumnGeometry.fromVoxels(-4,
                List.of(AIR, AIR, AIR, AIR, STONE, STONE, AIR, AIR));
        TerrainBoundarySignature west = signature(15, 0, lower);
        TerrainBoundarySignature south = signature(0, 15, upper);
        TransitionGenerationPlan first = plan(List.of(west, south));
        TransitionGenerationPlan second = plan(List.of(south, west));
        BoundaryColumnGeometry current = filled(STONE, 8);

        assertEquals(TransitionGeometryBlender.blendColumn(first, 16, 16, current),
                TransitionGeometryBlender.blendColumn(second, 16, 16, current));
    }

    @Test
    public void doesNotExtrudeProtectedObjectsOrOverwriteCurrentProtection() {
        BoundaryColumnGeometry.Voxel protectedBlock = new BoundaryColumnGeometry.Voxel(
                "minecraft:gold_block", BoundaryColumnGeometry.Phase.SOLID, "", true);
        BoundaryColumnGeometry old = BoundaryColumnGeometry.fromVoxels(-4, List.of(protectedBlock));
        BoundaryColumnGeometry current = BoundaryColumnGeometry.fromVoxels(-4, List.of(STONE));
        BoundaryGeometryInfluence influence = new BoundaryGeometryInfluence(0D, 0D,
                List.of(new BoundaryGeometryInfluence.Contribution(old, 1D)));

        assertSame(current, TransitionGeometryBlender.blendColumn(influence, 16, 0, current));
        BoundaryColumnGeometry protectedCurrent = BoundaryColumnGeometry.fromVoxels(-4, List.of(protectedBlock));
        BoundaryGeometryInfluence naturalInfluence = new BoundaryGeometryInfluence(0D, 0D,
                List.of(new BoundaryGeometryInfluence.Contribution(current, 1D)));
        assertSame(protectedCurrent, TransitionGeometryBlender.blendColumn(naturalInfluence, 16, 0, protectedCurrent));
    }

    @Test
    public void rejectsIncompatibleVerticalLayoutAndMissingGeometry() {
        BoundaryColumnGeometry old = filled(STONE, 8);
        BoundaryGeometryInfluence influence = new BoundaryGeometryInfluence(0.5D, 0.5D,
                List.of(new BoundaryGeometryInfluence.Contribution(old, 1D)));

        assertThrows(IllegalArgumentException.class, () -> TransitionGeometryBlender.blendColumn(
                influence, 16, 0, filled(AIR, 4)));
        assertThrows(IllegalArgumentException.class, () -> new BoundaryGeometryInfluence.Contribution(
                BoundaryColumnGeometry.empty(), 1D));
    }

    @Test
    public void roundTripsGeometryAndIncludesItInSnapshotIdentity() throws Exception {
        TerrainBoundarySignatureStore store = new TerrainBoundarySignatureStore(
                temporaryFolder.newFolder().toPath());
        BoundaryColumnGeometry old = BoundaryColumnGeometry.fromVoxels(-4,
                List.of(STONE, AIR, WATER, AIR, STONE, STONE, AIR, AIR));
        TerrainBoundarySignatureStore.Snapshot snapshot = store.publish(2L, List.of(signature(15, 0, old)));

        assertEquals(old, store.load(2L).signatureAt(15, 0).orElseThrow().geometry());
        TerrainBoundarySignatureStore other = new TerrainBoundarySignatureStore(temporaryFolder.newFolder().toPath());
        TerrainBoundarySignatureStore.Snapshot changed = other.publish(2L,
                List.of(signature(15, 0, filled(STONE, 8))));
        assertTrue(!snapshot.identity().equals(changed.identity()));
    }

    private TransitionGenerationPlan plan(List<TerrainBoundarySignature> signatures) throws Exception {
        GenerationBoundary boundary = GenerationBoundary.freeze("frontier",
                List.of(new GenerationBoundary.ChunkCoordinate(0, 0)));
        TerrainBoundarySignatureStore store = new TerrainBoundarySignatureStore(temporaryFolder.newFolder().toPath());
        TerrainBoundarySignatureStore.Snapshot snapshot = store.publish(2L, signatures);
        return new TransitionGenerationPlan(new TransitionGenerationPlan.Specification(2L, "old", "new",
                GenerationTransition.CURRENT_ALGORITHM_VERSION, 32, boundary.identity(), snapshot.identity()),
                boundary, snapshot);
    }

    private static BoundaryColumnGeometry filled(BoundaryColumnGeometry.Voxel voxel, int height) {
        ArrayList<BoundaryColumnGeometry.Voxel> voxels = new ArrayList<>(height);
        for (int index = 0; index < height; index++) {
            voxels.add(voxel);
        }
        return BoundaryColumnGeometry.fromVoxels(-4, voxels);
    }

    private static TerrainBoundarySignature signature(int x, int z, BoundaryColumnGeometry geometry) {
        return new TerrainBoundarySignature(new TerrainBoundarySignature.Column(x, z, 2, 2,
                OptionalInt.empty(), OptionalInt.empty()), new TerrainBoundarySignature.Samples(
                new TerrainBoundarySignature.VerticalLayout(-4, 4, 0),
                new TerrainBoundarySignature.BiomeEncoding(List.of(), new short[0])), geometry);
    }
}
