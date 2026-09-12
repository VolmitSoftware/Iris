package art.arcane.iris.generation.hydrology.cave;

import org.junit.Test;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.OptionalLong;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertThrows;
import static org.junit.Assert.assertTrue;

public final class HydrologyCaveIterationOrderTest {
    @Test
    public void candidatePreservesActionAndOpeningOrder() {
        CavePosition first = new CavePosition(3, 20, 7);
        CavePosition second = new CavePosition(-4, 11, 2);
        LinkedHashMap<CavePosition, HydrologyCaveAction> actions = new LinkedHashMap<>();
        actions.put(first, HydrologyCaveAction.DRY_AIR);
        actions.put(second, HydrologyCaveAction.WET_SOURCE);
        LinkedHashSet<CavePosition> openings = new LinkedHashSet<>(List.of(second, first));

        HydrologyCaveCandidate candidate = new HydrologyCaveCandidate(
                source(),
                "water",
                settings(),
                false,
                actions,
                openings
        );

        assertEquals(List.of(first, second), new ArrayList<>(candidate.actions().keySet()));
        assertEquals(List.of(second, first), new ArrayList<>(candidate.intentionalOpenings()));
        actions.clear();
        openings.clear();
        assertEquals(List.of(first, second), new ArrayList<>(candidate.actions().keySet()));
        assertEquals(List.of(second, first), new ArrayList<>(candidate.intentionalOpenings()));
        assertThrows(
                UnsupportedOperationException.class,
                () -> candidate.actions().put(first, HydrologyCaveAction.WET_SOURCE)
        );
        assertThrows(UnsupportedOperationException.class, () -> candidate.intentionalOpenings().add(second));
    }

    @Test
    public void acceptedPlanAndPlanningResultPreserveMapOrder() {
        CavePosition first = new CavePosition(3, 20, 7);
        CavePosition second = new CavePosition(-4, 11, 2);
        LinkedHashMap<CavePosition, HydrologyCaveAction> actions = new LinkedHashMap<>();
        actions.put(first, HydrologyCaveAction.DRY_AIR);
        actions.put(second, HydrologyCaveAction.WET_SOURCE);
        LinkedHashMap<CavePosition, CaveVoxelPrecondition> preconditions = new LinkedHashMap<>();
        preconditions.put(second, new CaveVoxelPrecondition(CaveVoxel.SOLID, false));
        preconditions.put(first, new CaveVoxelPrecondition(CaveVoxel.CAVE_AIR, false));
        HydrologyCavePlan plan = new HydrologyCavePlan(
                source(),
                HydrologyCaveRejection.NONE,
                actions,
                preconditions,
                OptionalLong.empty()
        );
        HydrologyCavePlanningResult result = new HydrologyCavePlanningResult(
                List.of(plan),
                actions,
                preconditions
        );

        assertEquals(List.of(first, second), new ArrayList<>(plan.actions().keySet()));
        assertEquals(List.of(second, first), new ArrayList<>(plan.baselinePreconditions().keySet()));
        assertEquals(List.of(first, second), new ArrayList<>(result.actions().keySet()));
        assertEquals(List.of(second, first), new ArrayList<>(result.baselinePreconditions().keySet()));
        actions.clear();
        preconditions.clear();
        assertEquals(List.of(first, second), new ArrayList<>(plan.actions().keySet()));
        assertEquals(List.of(second, first), new ArrayList<>(plan.baselinePreconditions().keySet()));
        assertEquals(List.of(first, second), new ArrayList<>(result.actions().keySet()));
        assertEquals(List.of(second, first), new ArrayList<>(result.baselinePreconditions().keySet()));
        assertThrows(UnsupportedOperationException.class, () -> plan.actions().remove(first));
        assertThrows(UnsupportedOperationException.class, () -> result.baselinePreconditions().remove(second));
    }

    @Test
    public void packedPlanViewsImplementTheReadOnlyMapContract() {
        CavePosition dry = new CavePosition(3, 20, 7);
        CavePosition wet = new CavePosition(-4, 11, 2);
        CavePosition guardOnly = new CavePosition(9, 4, -6);
        CaveVoxelPrecondition dryBaseline = new CaveVoxelPrecondition(CaveVoxel.CAVE_AIR, false);
        CaveVoxelPrecondition wetBaseline = new CaveVoxelPrecondition(CaveVoxel.SOLID, true);
        CaveVoxelPrecondition guardBaseline = new CaveVoxelPrecondition(CaveVoxel.LAVA, false);
        Map<CavePosition, HydrologyCaveAction> actions = new LinkedHashMap<>();
        actions.put(dry, HydrologyCaveAction.DRY_AIR);
        actions.put(wet, HydrologyCaveAction.WET_SOURCE);
        Map<CavePosition, CaveVoxelPrecondition> preconditions = new LinkedHashMap<>();
        preconditions.put(guardOnly, guardBaseline);
        preconditions.put(wet, wetBaseline);
        preconditions.put(dry, dryBaseline);
        HydrologyCavePlan plan = new HydrologyCavePlan(
                source(),
                HydrologyCaveRejection.NONE,
                actions,
                preconditions,
                OptionalLong.empty()
        );

        assertEquals(HydrologyCaveAction.DRY_AIR, plan.actions().get(dry));
        assertNull(plan.actions().get(guardOnly));
        assertFalse(plan.actions().containsKey(guardOnly));
        assertTrue(plan.baselinePreconditions().containsKey(guardOnly));
        assertEquals(guardBaseline, plan.baselinePreconditions().get(guardOnly));
        assertEquals(actions, plan.actions());
        assertEquals(preconditions, plan.baselinePreconditions());
        assertEquals(
                List.of(HydrologyCaveAction.DRY_AIR, HydrologyCaveAction.WET_SOURCE),
                new ArrayList<>(plan.actions().values())
        );
        assertEquals(
                List.of(guardBaseline, wetBaseline, dryBaseline),
                new ArrayList<>(plan.baselinePreconditions().values())
        );
        assertThrows(UnsupportedOperationException.class, () -> plan.actions().clear());
        assertThrows(UnsupportedOperationException.class, () -> plan.baselinePreconditions().keySet().clear());
    }

    private HydrologyCaveSource source() {
        CavePosition source = new CavePosition(3, 20, 7);
        return new HydrologyCaveSource(17L, source, source, 20, HydrologyCaveMode.GENERATED_GROTTO);
    }

    private HydrologyCavePlannerSettings settings() {
        return new HydrologyCavePlannerSettings(
                8,
                16,
                64,
                16,
                1,
                2,
                2,
                4,
                HydrologyCaveFluidPolicy.REJECT_EXISTING,
                HydrologyCaveGrottoShape.ELLIPSOID,
                8,
                16
        );
    }
}
