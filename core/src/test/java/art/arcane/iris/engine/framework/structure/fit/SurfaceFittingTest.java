package art.arcane.iris.engine.framework.structure.fit;

import com.volmit.vanilla.StructureStep;
import com.volmit.vanilla.TerrainAdaptation;
import org.junit.Test;

import java.util.List;
import java.util.Map;

import static art.arcane.iris.engine.framework.structure.fit.FitFixture.AIR;
import static art.arcane.iris.engine.framework.structure.fit.FitFixture.COBBLESTONE;
import static art.arcane.iris.engine.framework.structure.fit.FitFixture.DIRT;
import static art.arcane.iris.engine.framework.structure.fit.FitFixture.GRASS;
import static art.arcane.iris.engine.framework.structure.fit.FitFixture.OAK_LEAVES;
import static art.arcane.iris.engine.framework.structure.fit.FitFixture.OAK_LOG;
import static art.arcane.iris.engine.framework.structure.fit.FitFixture.STONE;
import static art.arcane.iris.engine.framework.structure.fit.FitFixture.WATER;
import static art.arcane.iris.engine.framework.structure.fit.FitFixture.state;
import static org.junit.Assert.assertArrayEquals;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

/** The surface fitting of SOURCE beard and VACUUM starts, on in-memory terrain. */
public class SurfaceFittingTest {
    private static final String SOURCE = "{\"mode\":\"SOURCE\"}";
    private static final String VACUUM = "{\"mode\":\"VACUUM\"}";
    private static final String PRESERVE = "{\"mode\":\"PRESERVE\"}";

    @Test
    public void sourceBeardAdjustmentsPrepareOnlyAtTheSurfaceStructuresStep() {
        assertTrue(SurfaceFitting.shouldPrepareSurfaceTerrain(TerrainAdaptation.BEARD_THIN, StructureStep.SURFACE_STRUCTURES));
        assertFalse(SurfaceFitting.shouldPrepareSurfaceTerrain(TerrainAdaptation.BEARD_BOX, StructureStep.FLUID_SPRINGS));
        assertFalse(SurfaceFitting.shouldPrepareSurfaceTerrain(TerrainAdaptation.BEARD_THIN, StructureStep.UNDERGROUND_STRUCTURES));
        assertFalse(SurfaceFitting.shouldPrepareSurfaceTerrain(TerrainAdaptation.BEARD_BOX, StructureStep.UNDERGROUND_DECORATION));
        assertFalse(SurfaceFitting.shouldPrepareSurfaceTerrain(TerrainAdaptation.BEARD_THIN, StructureStep.STRONGHOLDS));
        assertFalse(SurfaceFitting.shouldPrepareSurfaceTerrain(TerrainAdaptation.BURY, StructureStep.SURFACE_STRUCTURES));
        assertFalse(SurfaceFitting.shouldPrepareSurfaceTerrain(TerrainAdaptation.ENCAPSULATE, StructureStep.SURFACE_STRUCTURES));
        assertFalse(SurfaceFitting.shouldPrepareSurfaceTerrain(TerrainAdaptation.NONE, StructureStep.FLUID_SPRINGS));
    }

    @Test
    public void explicitTerrainOverrideDisablesSourceBeardFittingAndVacuumNeedsNoAdaptation() {
        FitFixture.Piece piece = FitFixture.Piece.fixed(new Box(0, 64, 0, 4, 70, 4));

        assertTrue(SurfaceFitting.requiresSurfaceTerrain(
                FitFixture.start(SOURCE, TerrainAdaptation.BEARD_BOX, StructureStep.SURFACE_STRUCTURES, piece)));
        assertFalse(SurfaceFitting.requiresSurfaceTerrain(
                FitFixture.start(PRESERVE, TerrainAdaptation.BEARD_BOX, StructureStep.SURFACE_STRUCTURES, piece)));
        assertFalse(SurfaceFitting.requiresSurfaceTerrain(
                FitFixture.start(SOURCE, TerrainAdaptation.NONE, StructureStep.SURFACE_STRUCTURES, piece)));
        assertTrue(SurfaceFitting.requiresSurfaceTerrain(
                FitFixture.start(VACUUM, TerrainAdaptation.NONE, StructureStep.SURFACE_STRUCTURES, piece)));
        assertTrue(SurfaceFitting.requiresSurfaceTerrain(
                FitFixture.start(VACUUM, TerrainAdaptation.BEARD_BOX, StructureStep.SURFACE_STRUCTURES, piece)));
        assertFalse(TerrainIntegration.clearsLegacyTemplateAir(
                FitFixture.start(VACUUM, TerrainAdaptation.NONE, StructureStep.SURFACE_STRUCTURES, piece)));
    }

    @Test
    public void sourceSurfaceAnchorMeetsOneBlockBelowTheAuthoredGroundPlane() {
        SurfaceFitting.SurfaceAnchor anchor = SurfaceFitting.surfaceAnchor(new Box(0, 64, 0, 4, 90, 4), 68, 2);

        assertEquals(67, anchor.meetY());
        assertEquals(0, anchor.minX());
        assertEquals(4, anchor.maxX());
        assertEquals(0, anchor.minZ());
        assertEquals(4, anchor.maxZ());
    }

    @Test
    public void rigidFootprintsBoundTerrainAcrossGapsLargerThanTheBeardKernel() {
        assertEquals(46, SurfaceFitting.resolveSurfaceTarget(List.of(anchor(72, 2)), 2, 2, 40));
        assertEquals(46, SurfaceFitting.resolveSurfaceTarget(List.of(anchor(72, 1)), 2, 2, 40));
    }

    @Test
    public void beardSkirtBoundsAndGradesUpwardAndDownwardSymmetrically() {
        int originalY = 64;
        int[] raised = new int[13];
        int[] lowered = new int[13];
        for (int outset = 0; outset <= 12; outset++) {
            raised[outset] = SurfaceFitting.resolveSurfaceTarget(List.of(anchor(96, 2)), 4 + outset, 2, originalY);
            lowered[outset] = SurfaceFitting.resolveSurfaceTarget(List.of(anchor(32, 2)), 4 + outset, 2, originalY);
        }

        assertArrayEquals(new int[]{70, 69, 68, 67, 67, 66, 66, 65, 65, 64, 64, 64, 64}, raised);
        assertArrayEquals(new int[]{58, 59, 60, 61, 61, 62, 62, 63, 63, 64, 64, 64, 64}, lowered);
    }

    @Test
    public void surfaceAnchorCapsInsideAndIsUnchangedAtRadius() {
        SurfaceFitting.SurfaceAnchor anchor = anchor(72, 2);

        assertEquals(70, SurfaceFitting.resolveSurfaceTarget(List.of(anchor), 2, 2, 64));
        assertEquals(64, SurfaceFitting.resolveSurfaceTarget(List.of(anchor), 16, 2, 64));
        assertEquals(64, SurfaceFitting.resolveSurfaceTarget(List.of(anchor), 17, 2, 64));
    }

    @Test
    public void surfaceAnchorRaisesAndLowersThroughTheTaper() {
        assertEquals(65, SurfaceFitting.resolveSurfaceTarget(List.of(anchor(68, 2)), 10, 2, 64));
        assertEquals(67, SurfaceFitting.resolveSurfaceTarget(List.of(anchor(64, 2)), 10, 2, 68));
    }

    @Test
    public void containingRigidFloorsHaveDeterministicPriority() {
        SurfaceFitting.SurfaceAnchor rigid = anchor(70, 2);
        SurfaceFitting.SurfaceAnchor junction = anchor(74, 1);

        assertEquals(70, SurfaceFitting.resolveSurfaceTarget(List.of(rigid, junction), 2, 2, 64));
        assertEquals(70, SurfaceFitting.resolveSurfaceTarget(List.of(junction, rigid), 2, 2, 64));
        SurfaceFitting.SurfaceAnchor weakTie = anchor(58, 1);
        SurfaceFitting.SurfaceAnchor strongTie = anchor(70, 2);
        assertEquals(70, SurfaceFitting.resolveSurfaceTarget(List.of(weakTie, strongTie), 2, 2, 64));
        assertEquals(70, SurfaceFitting.resolveSurfaceTarget(List.of(strongTie, weakTie), 2, 2, 64));
    }

    @Test
    public void containingFootprintOverridesAnAdjacentPiecesFalloff() {
        SurfaceFitting.SurfaceAnchor local = new SurfaceFitting.SurfaceAnchor(0, 4, 0, 4, 66, 2);
        SurfaceFitting.SurfaceAnchor adjacent = new SurfaceFitting.SurfaceAnchor(5, 9, 0, 4, 70, 2);

        assertEquals(69, SurfaceFitting.resolveSurfaceTarget(List.of(adjacent), 4, 2, 64));
        assertEquals(66, SurfaceFitting.resolveSurfaceTarget(List.of(local, adjacent), 4, 2, 64));
        assertEquals(66, SurfaceFitting.resolveSurfaceTarget(List.of(adjacent, local), 4, 2, 64));
    }

    @Test
    public void opposingFalloffsBlendWithoutAnAbruptMidpointSeam() {
        SurfaceFitting.SurfaceAnchor high = new SurfaceFitting.SurfaceAnchor(0, 0, 0, 0, 70, 2);
        SurfaceFitting.SurfaceAnchor low = new SurfaceFitting.SurfaceAnchor(12, 12, 0, 0, 58, 2);
        int previous = SurfaceFitting.resolveSurfaceTarget(List.of(high, low), 0, 0, 64);

        for (int x = 1; x <= 12; x++) {
            int forward = SurfaceFitting.resolveSurfaceTarget(List.of(high, low), x, 0, 64);
            assertEquals(forward, SurfaceFitting.resolveSurfaceTarget(List.of(low, high), x, 0, 64));
            assertTrue(Math.abs(forward - previous) <= 6);
            previous = forward;
        }
        assertEquals(64, SurfaceFitting.resolveSurfaceTarget(List.of(high, low), 6, 0, 64));
    }

    @Test
    public void surfaceColumnsMutateTerrainAndRemoveUnsupportedDecoration() {
        FitFixture.World lowered = new FitFixture.World(new Box(0, 0, 0, 0, 0, 0));
        lowered.put(0, 61, 0, STONE);
        lowered.put(0, 62, 0, DIRT);
        lowered.put(0, 63, 0, DIRT);
        lowered.put(0, 64, 0, GRASS);
        lowered.put(0, 65, 0, state("dandelion"));

        SurfaceFitting.applySurfaceColumn(lowered.fitBlocks(), 0, 0, 64, 62, -64, 319, false);

        assertEquals(STONE, lowered.at(0, 61, 0));
        assertEquals(GRASS, lowered.at(0, 62, 0));
        assertEquals(AIR, lowered.at(0, 63, 0));
        assertEquals(AIR, lowered.at(0, 64, 0));
        assertEquals(AIR, lowered.at(0, 65, 0));

        FitFixture.World raised = new FitFixture.World(new Box(0, 0, 0, 0, 0, 0));
        raised.put(0, 63, 0, DIRT);
        raised.put(0, 64, 0, GRASS);
        raised.put(0, 65, 0, state("dandelion"));

        SurfaceFitting.applySurfaceColumn(raised.fitBlocks(), 0, 0, 64, 68, -64, 319, false);

        assertEquals(DIRT, raised.at(0, 65, 0));
        assertEquals(DIRT, raised.at(0, 66, 0));
        assertEquals(DIRT, raised.at(0, 67, 0));
        assertEquals(GRASS, raised.at(0, 68, 0));
    }

    @Test
    public void rigidSurfaceBaseClosesAOneBlockSubsurfaceGapAndRebuildsAMissingMeetLayer() {
        FitFixture.World gap = new FitFixture.World(new Box(0, 0, 0, 0, 0, 0));
        gap.put(0, 62, 0, STONE);
        gap.put(0, 64, 0, GRASS);
        SurfaceFitting.applySurfaceColumn(gap.fitBlocks(), 0, 0, 64, 64, -64, 319, true);
        assertEquals(STONE, gap.at(0, 63, 0));
        assertEquals(GRASS, gap.at(0, 64, 0));

        FitFixture.World missing = new FitFixture.World(new Box(0, 0, 0, 0, 0, 0));
        missing.put(0, 62, 0, STONE);
        SurfaceFitting.applySurfaceColumn(missing.fitBlocks(), 0, 0, 64, 64, -64, 319, true);
        assertEquals(STONE, missing.at(0, 63, 0));
        assertEquals(STONE, missing.at(0, 64, 0));

        FitFixture.World floor = new FitFixture.World(new Box(0, 0, 0, 0, 0, 0));
        SurfaceFitting.applySurfaceColumn(floor.fitBlocks(), 0, 0, -64, -64, -64, 319, true);
        assertEquals(STONE, floor.at(0, -64, 0));
    }

    @Test
    public void raisedSurfaceTerrainDoesNotSliceTreeBlocks() {
        FitFixture.World world = new FitFixture.World(new Box(0, 0, 0, 0, 0, 0));
        world.put(0, 63, 0, DIRT);
        world.put(0, 64, 0, GRASS);
        world.put(0, 66, 0, OAK_LOG);
        world.put(0, 68, 0, OAK_LEAVES);

        SurfaceFitting.applySurfaceColumn(world.fitBlocks(), 0, 0, 64, 68, -64, 319, false);

        assertEquals(OAK_LOG, world.at(0, 66, 0));
        assertEquals(OAK_LEAVES, world.at(0, 68, 0));
    }

    @Test
    public void loweredFluidColumnsRemainFluidFilled() {
        FitFixture.World world = new FitFixture.World(new Box(0, 0, 0, 0, 0, 0));
        world.put(0, 62, 0, DIRT);
        world.put(0, 63, 0, DIRT);
        world.put(0, 64, 0, state("gravel"));
        world.put(0, 65, 0, WATER);

        SurfaceFitting.applySurfaceColumn(world.fitBlocks(), 0, 0, 64, 62, -64, 319, false);

        assertEquals(WATER, world.at(0, 63, 0));
        assertEquals(WATER, world.at(0, 64, 0));
        assertEquals(WATER, world.at(0, 65, 0));
        assertEquals(state("gravel"), world.at(0, 62, 0));
    }

    @Test
    public void vacuumBudgetExhaustionSkipsFittingWithoutFailing() {
        Box area = new Box(0, 54, 0, 15, 72, 15);
        FitFixture.World world = new FitFixture.World(area).flat(area, 60);
        Map<?, ?> before = world.snapshot();
        FitStart start = FitFixture.start(VACUUM, TerrainAdaptation.NONE, StructureStep.SURFACE_STRUCTURES,
                FitFixture.Piece.rigid(new Box(0, 66, 0, 0, 70, 0), 1).cell(0, 66, 0, COBBLESTONE));

        SurfaceFitting.prepareSurfaceStructures(world.fitBlocks(), area, List.of(start), (x, z) -> 60, 1);

        assertEquals(before, world.snapshot());
    }

    @Test
    public void vacuumRaisesTheTerrainToAFloatingFoundationAndRepairsItsGapAfterPlacement() {
        Box area = new Box(0, 54, 0, 3, 74, 3);
        FitFixture.World world = new FitFixture.World(area).flat(area, 60);
        FitStart start = FitFixture.start(VACUUM, TerrainAdaptation.NONE, StructureStep.SURFACE_STRUCTURES,
                FitFixture.Piece.rigid(new Box(0, 65, 0, 0, 72, 0), 1).cell(0, 65, 0, COBBLESTONE));

        SurfaceFitting.VacuumFoundationPlan plan = SurfaceFitting.prepareSurfaceStructures(
                world.fitBlocks(), area, List.of(start), (x, z) -> 60, SurfaceFitting.MAX_SURFACE_TEMPLATE_CELLS);
        assertEquals(GRASS, world.at(0, 64, 0));
        for (int y = 61; y <= 64; y++) {
            world.put(0, y, 0, state("cave_air"));
        }
        world.put(0, 65, 0, COBBLESTONE);
        SurfaceFitting.repairVacuumFoundations(world.fitBlocks(), area, plan);

        for (int y = 61; y <= 64; y++) {
            assertFalse(world.at(0, y, 0).material().path().endsWith("air"));
        }
    }

    private static SurfaceFitting.SurfaceAnchor anchor(int meetY, int strength) {
        return new SurfaceFitting.SurfaceAnchor(0, 4, 0, 4, meetY, strength);
    }
}
