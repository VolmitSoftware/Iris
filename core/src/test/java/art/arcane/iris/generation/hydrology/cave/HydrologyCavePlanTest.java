package art.arcane.iris.generation.hydrology.cave;

import org.junit.Test;

import java.util.Map;
import java.util.OptionalLong;
import java.util.LinkedHashMap;
import java.util.ArrayList;
import java.util.List;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertThrows;
import static org.junit.Assert.assertTrue;

public class HydrologyCavePlanTest {
    @Test
    public void everyActionRequiresABaselinePrecondition() {
        CavePosition actionPosition = new CavePosition(1, 20, 3);

        assertThrows(IllegalArgumentException.class, () -> new HydrologyCavePlan(
                source(),
                HydrologyCaveRejection.NONE,
                Map.of(actionPosition, HydrologyCaveAction.WET_SOURCE),
                Map.of(),
                OptionalLong.empty()
        ));
    }

    @Test
    public void extraDryBoundaryPreconditionsAreAllowed() {
        CavePosition actionPosition = new CavePosition(1, 20, 3);
        CavePosition dryBoundaryPosition = new CavePosition(2, 20, 3);
        CaveVoxelPrecondition solid = new CaveVoxelPrecondition(CaveVoxel.SOLID, false);
        CaveVoxelPrecondition dryBoundary = new CaveVoxelPrecondition(CaveVoxel.CAVE_AIR, false);
        HydrologyCavePlan plan = new HydrologyCavePlan(
                source(),
                HydrologyCaveRejection.NONE,
                Map.of(actionPosition, HydrologyCaveAction.WET_SOURCE),
                Map.of(actionPosition, solid, dryBoundaryPosition, dryBoundary),
                OptionalLong.empty()
        );

        assertEquals(1, plan.actions().size());
        assertEquals(2, plan.baselinePreconditions().size());
    }

    @Test
    public void boundedQueriesVisitOnlyExactPositionsAcrossNegativeChunkBoundaries() {
        CavePosition inside = new CavePosition(-16, 20, 31);
        CavePosition outsideX = new CavePosition(-17, 21, 31);
        CavePosition outsideZ = new CavePosition(-16, 22, 32);
        CavePosition preconditionOnly = new CavePosition(-15, 23, 30);
        CaveVoxelPrecondition solid = new CaveVoxelPrecondition(CaveVoxel.SOLID, false);
        LinkedHashMap<CavePosition, HydrologyCaveAction> actions = new LinkedHashMap<>();
        actions.put(outsideX, HydrologyCaveAction.DRY_AIR);
        actions.put(inside, HydrologyCaveAction.WET_SOURCE);
        actions.put(outsideZ, HydrologyCaveAction.SEAL_GUARD);
        LinkedHashMap<CavePosition, CaveVoxelPrecondition> preconditions = new LinkedHashMap<>();
        preconditions.put(outsideX, solid);
        preconditions.put(inside, solid);
        preconditions.put(outsideZ, solid);
        preconditions.put(preconditionOnly, solid);
        HydrologyCavePlan plan = new HydrologyCavePlan(
                source(),
                HydrologyCaveRejection.NONE,
                actions,
                preconditions,
                OptionalLong.empty()
        );

        assertTrue(plan.intersectsActions(-16, 30, 0, 32));
        assertFalse(plan.intersectsActions(-15, 30, 0, 31));
        ArrayList<CavePosition> visitedActions = new ArrayList<>();
        plan.forEachActionIn(-16, 30, 0, 32,
                (CavePosition position, HydrologyCaveAction action) -> visitedActions.add(position));
        assertEquals(List.of(inside), visitedActions);
        ArrayList<CavePosition> visitedPreconditions = new ArrayList<>();
        assertTrue(plan.allPreconditionsIn(-16, 30, 0, 32,
                (CavePosition position, CaveVoxelPrecondition expected) -> {
                    visitedPreconditions.add(position);
                    return true;
                }));
        assertEquals(List.of(inside, preconditionOnly), visitedPreconditions);
    }

    private HydrologyCaveSource source() {
        return new HydrologyCaveSource(
                1L,
                new CavePosition(1, 20, 3),
                new CavePosition(2, 20, 3),
                20,
                HydrologyCaveMode.CLOSED_COMPONENT
        );
    }
}
