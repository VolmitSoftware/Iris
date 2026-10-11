package art.arcane.iris.generation.stage;

import art.arcane.iris.generation.context.ChunkContext;
import art.arcane.iris.generation.terrain.Terrain3DColumn;
import org.junit.Test;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;

public class IrisBlendedDecorationTest {
    @Test
    public void shiftedNaturalOpeningUsesRemainingBlendedFloorAndCeiling() {
        Terrain3DColumn natural = column(5, 15);
        ChunkContext.TransitionTerrainColumn transition = transition(column(3, 12));

        assertEquals(new IrisDecorantActuator.TerrainLedge(3, 12),
                IrisDecorantActuator.resolveTerrainLedge(natural, 0, transition));
        assertTrue(IrisDecorantActuator.isTransitionTerrainOpening(natural, transition, 4));
        assertFalse(IrisDecorantActuator.isTransitionTerrainOpening(natural, transition, 12));
    }

    @Test
    public void splitOpeningDecoratesEachSupportedBlendedLedge() {
        Terrain3DColumn natural = column(3, 20);
        Terrain3DColumn blended = Terrain3DColumn.fromOccupancy(31, 32,
                y -> y <= 5 || y >= 10 && y <= 12 || y >= 18);
        ChunkContext.TransitionTerrainColumn transition = transition(blended);

        assertEquals(new IrisDecorantActuator.TerrainLedge(5, 10),
                IrisDecorantActuator.resolveTerrainLedge(natural, 0, transition));
        assertEquals(new IrisDecorantActuator.TerrainLedge(12, 18),
                IrisDecorantActuator.resolveTerrainLedge(natural, 1, transition));
    }

    @Test
    public void unrelatedCaveOpeningRetainsCaveDecorationOwnership() {
        Terrain3DColumn natural = column(18, 25);
        ChunkContext.TransitionTerrainColumn transition = transition(column(3, 12));

        assertNull(IrisDecorantActuator.resolveTerrainLedge(natural, 0, transition));
        assertFalse(IrisDecorantActuator.isTransitionTerrainOpening(natural, transition, 8));
    }

    @Test
    public void filledOpeningDoesNotRetainTerrainDecorationTargets() {
        Terrain3DColumn natural = column(3, 12);
        ChunkContext.TransitionTerrainColumn transition = transition(
                Terrain3DColumn.fromOccupancy(31, 32, y -> true));

        assertFalse(IrisDecorantActuator.isTransitionTerrainOpening(natural, transition, 8));
    }

    private Terrain3DColumn column(int floorY, int ceilingY) {
        return Terrain3DColumn.fromOccupancy(31, 32, y -> y <= floorY || y >= ceilingY);
    }

    private ChunkContext.TransitionTerrainColumn transition(Terrain3DColumn solids) {
        return new ChunkContext.TransitionTerrainColumn(solids, solids.topY());
    }
}
