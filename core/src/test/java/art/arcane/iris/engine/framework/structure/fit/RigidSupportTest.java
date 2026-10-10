package art.arcane.iris.engine.framework.structure.fit;

import com.volmit.block.BlockState;
import com.volmit.vanilla.StructureStep;
import com.volmit.vanilla.TerrainAdaptation;
import org.junit.Test;

import java.util.HashSet;
import java.util.List;
import java.util.Set;

import static art.arcane.iris.engine.framework.structure.fit.FitFixture.AIR;
import static art.arcane.iris.engine.framework.structure.fit.FitFixture.COBBLESTONE;
import static art.arcane.iris.engine.framework.structure.fit.FitFixture.DIRT;
import static art.arcane.iris.engine.framework.structure.fit.FitFixture.GRASS;
import static art.arcane.iris.engine.framework.structure.fit.FitFixture.WATER;
import static art.arcane.iris.engine.framework.structure.fit.FitFixture.state;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

/**
 * The support bridged between two rigid pieces of one surface start whose ground planes are two blocks apart. Each
 * test states the template cells the platform would process for the pieces.
 */
public class RigidSupportTest {
    private static final String SOURCE = "{\"mode\":\"SOURCE\"}";
    private static final String VACUUM = "{\"mode\":\"VACUUM\"}";
    private static final String PRESERVE = "{\"mode\":\"PRESERVE\"}";

    @Test
    public void upperOccupancyIsSupportedOverTheLowerPiecesGround() {
        Box area = new Box(0, 58, 0, 1, 72, 0);
        FitFixture.World world = supportTerrain(area);
        world.put(1, 63, 0, DIRT);
        FitStart start = beard(lower(0, 1, AIR), upper(0, 1, 64, COBBLESTONE));

        Set<Long> written = RigidSupport.bridgeRigidPieceSupport(world.fitBlocks(), area, List.of(start));

        assertEquals(Set.of(TemplateFootprint.packedPosition(0, 63, 0)), written);
        assertEquals(DIRT, world.at(0, 63, 0));
        assertEquals(DIRT, world.at(1, 63, 0));
    }

    @Test
    public void lowestAirVoidFluidAndRemovedCellsVetoSupport() {
        Box area = new Box(0, 58, 0, 4, 72, 0);
        FitFixture.World world = supportTerrain(area);
        FitFixture.Piece upper = FitFixture.Piece.rigid(new Box(0, 64, 0, 4, 70, 0), 1)
                .cell(0, 64, 0, COBBLESTONE)
                .cell(1, 64, 0, AIR)
                .cell(2, 64, 0, state("structure_void"))
                .cell(3, 64, 0, WATER)
                .cell(4, 64, 0, null);
        for (int x = 1; x <= 4; x++) {
            upper.cell(x, 65, 0, state("stone"));
        }
        FitStart start = beard(lower(0, 4, AIR), upper);

        Set<Long> written = RigidSupport.bridgeRigidPieceSupport(world.fitBlocks(), area, List.of(start));

        assertEquals(Set.of(TemplateFootprint.packedPosition(0, 63, 0)), written);
        for (int x = 1; x <= 4; x++) {
            assertEquals(AIR, world.at(x, 63, 0));
        }
    }

    @Test
    public void supportIsChunkClippedAndStableAcrossSplitAreas() {
        Box wideArea = new Box(0, 58, 0, 31, 72, 15);
        Box westArea = new Box(0, 58, 0, 15, 72, 15);
        Box eastArea = new Box(16, 58, 0, 31, 72, 15);
        FitFixture.World wide = supportTerrain(new Box(15, 58, 0, 16, 72, 0));
        FitFixture.World split = supportTerrain(new Box(15, 58, 0, 16, 72, 0));
        FitStart start = beard(lower(15, 16, AIR), upper(15, 16, 64, COBBLESTONE));

        Set<Long> wideWritten = RigidSupport.bridgeRigidPieceSupport(wide.fitBlocks(), wideArea, List.of(start));
        Set<Long> splitWritten = new HashSet<>(
                RigidSupport.bridgeRigidPieceSupport(split.fitBlocks(), westArea, List.of(start)));
        assertEquals(AIR, split.at(16, 63, 0));
        splitWritten.addAll(RigidSupport.bridgeRigidPieceSupport(split.fitBlocks(), eastArea, List.of(start)));

        assertEquals(Set.of(TemplateFootprint.packedPosition(15, 63, 0), TemplateFootprint.packedPosition(16, 63, 0)),
                wideWritten);
        assertEquals(wideWritten, splitWritten);
        assertEquals(wide.snapshot(), split.snapshot());
    }

    @Test
    public void supportNeverPairsRigidAnchorsAcrossStarts() {
        Box area = new Box(0, 58, 0, 0, 72, 0);
        FitFixture.World world = supportTerrain(area);

        assertTrue(RigidSupport.bridgeRigidPieceSupport(world.fitBlocks(), area,
                List.of(beard(lower(0, 0, AIR)), beard(upper(0, 0, 64, COBBLESTONE)))).isEmpty());
        assertEquals(AIR, world.at(0, 63, 0));
    }

    @Test
    public void vacuumSupportsWithoutAnAuthoredAdaptationButLongMeetGapsStayOpen() {
        Box area = new Box(0, 58, 0, 0, 72, 0);
        FitFixture.World vacuumWorld = supportTerrain(area);

        Set<Long> written = RigidSupport.bridgeRigidPieceSupport(vacuumWorld.fitBlocks(), area,
                List.of(start(VACUUM, TerrainAdaptation.NONE, lower(0, 0, AIR), upper(0, 0, 64, COBBLESTONE))));

        assertEquals(Set.of(TemplateFootprint.packedPosition(0, 63, 0)), written);
        assertTrue(RigidSupport.bridgeRigidPieceSupport(supportTerrain(area).fitBlocks(), area,
                List.of(start(SOURCE, TerrainAdaptation.NONE, lower(0, 0, AIR), upper(0, 0, 64, COBBLESTONE)))).isEmpty());
        assertTrue(RigidSupport.bridgeRigidPieceSupport(supportTerrain(area).fitBlocks(), area,
                List.of(start(VACUUM, TerrainAdaptation.NONE, lower(0, 0, AIR), upper(0, 0, 65, COBBLESTONE)))).isEmpty());
    }

    @Test
    public void threeRigidPlanesCannotBuildAnUpwardSupportChain() {
        Box area = new Box(0, 58, 0, 0, 72, 0);
        FitFixture.World world = supportTerrain(area);
        FitFixture.Piece middle = FitFixture.Piece.rigid(new Box(0, 64, 0, 0, 69, 0), 0).cell(0, 64, 0, COBBLESTONE);
        FitFixture.Piece top = FitFixture.Piece.rigid(new Box(0, 65, 0, 0, 70, 0), 0).cell(0, 65, 0, state("stone"));

        Set<Long> written = RigidSupport.bridgeRigidPieceSupport(world.fitBlocks(), area,
                List.of(beard(lower(0, 0, AIR), middle, top)));

        assertEquals(Set.of(TemplateFootprint.packedPosition(0, 63, 0)), written);
        assertEquals(DIRT, world.at(0, 63, 0));
        assertEquals(AIR, world.at(0, 64, 0));
    }

    @Test
    public void startOrderCannotCreateCrossStartSupportChains() {
        Box area = new Box(0, 58, 0, 0, 72, 0);
        FitFixture.Piece middle = FitFixture.Piece.rigid(new Box(0, 64, 0, 0, 69, 0), 0).cell(0, 64, 0, COBBLESTONE);
        FitFixture.Piece raisedGround = FitFixture.Piece.rigid(new Box(0, 64, 0, 0, 69, 0), 0).cell(0, 64, 0, AIR);
        FitFixture.Piece top = FitFixture.Piece.rigid(new Box(0, 65, 0, 0, 70, 0), 0).cell(0, 65, 0, state("stone"));
        FitStart lowerStart = beard(lower(0, 0, AIR), middle);
        FitStart upperStart = beard(raisedGround, top);
        FitFixture.World forward = supportTerrain(area);
        FitFixture.World reverse = supportTerrain(area);

        Set<Long> forwardWritten = RigidSupport.bridgeRigidPieceSupport(forward.fitBlocks(), area,
                List.of(lowerStart, upperStart));
        Set<Long> reverseWritten = RigidSupport.bridgeRigidPieceSupport(reverse.fitBlocks(), area,
                List.of(upperStart, lowerStart));

        assertEquals(Set.of(TemplateFootprint.packedPosition(0, 63, 0)), forwardWritten);
        assertEquals(forwardWritten, reverseWritten);
        assertEquals(forward.snapshot(), reverse.snapshot());
        assertEquals(AIR, forward.at(0, 64, 0));
    }

    @Test
    public void aPreserveDuplicateCannotSuppressVacuumSupport() {
        Box area = new Box(0, 58, 0, 0, 72, 0);
        FitFixture.World world = supportTerrain(area);
        FitFixture.Piece lower = lower(0, 0, AIR);
        FitFixture.Piece upper = upper(0, 0, 64, COBBLESTONE);

        Set<Long> written = RigidSupport.bridgeRigidPieceSupport(world.fitBlocks(), area, List.of(
                start(PRESERVE, TerrainAdaptation.NONE, lower, upper),
                start(VACUUM, TerrainAdaptation.NONE, lower, upper)));

        assertEquals(Set.of(TemplateFootprint.packedPosition(0, 63, 0)), written);
    }

    /** A lower rigid piece from {@code minX} to {@code maxX} whose ground (y 62) holds {@code cell}. */
    private static FitFixture.Piece lower(int minX, int maxX, BlockState cell) {
        FitFixture.Piece piece = FitFixture.Piece.rigid(new Box(minX, 62, 0, maxX, 65, 0), 1);
        for (int x = minX; x <= maxX; x++) {
            piece.cell(x, 62, 0, cell);
        }
        return piece;
    }

    /** An upper rigid piece whose lowest layer, at {@code baseY}, holds {@code cell}; its ground is the layer under it. */
    private static FitFixture.Piece upper(int minX, int maxX, int baseY, BlockState cell) {
        FitFixture.Piece piece = FitFixture.Piece.rigid(new Box(minX, baseY, 0, maxX, baseY + 6, 0), 1);
        for (int x = minX; x <= maxX; x++) {
            piece.cell(x, baseY, 0, cell);
        }
        return piece;
    }

    private static FitStart beard(FitFixture.Piece... pieces) {
        return start(SOURCE, TerrainAdaptation.BEARD_BOX, pieces);
    }

    private static FitStart start(String terrain, TerrainAdaptation adaptation, FitFixture.Piece... pieces) {
        return FitFixture.start(terrain, adaptation, StructureStep.SURFACE_STRUCTURES, pieces);
    }

    private static FitFixture.World supportTerrain(Box columns) {
        FitFixture.World world = new FitFixture.World(columns);
        for (int x = columns.minX(); x <= columns.maxX(); x++) {
            world.put(x, 61, 0, DIRT);
            world.put(x, 62, 0, GRASS);
        }
        return world;
    }
}
