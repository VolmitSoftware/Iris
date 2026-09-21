package art.arcane.iris.generation.hydrology.cave;

import org.junit.Test;

import java.util.Map;
import java.util.OptionalLong;
import java.util.LinkedHashMap;
import java.util.ArrayList;
import java.util.List;
import java.util.Collections;
import java.util.Iterator;
import java.util.NoSuchElementException;
import java.util.Random;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNull;
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

    @Test
    public void compactMapsPreserveAllCoordinateBitsAndIndependentOrders() {
        Random random = new Random(718622L);
        ArrayList<CavePosition> positions = new ArrayList<>();
        for (int x : new int[]{Integer.MIN_VALUE, -1, 0, Integer.MAX_VALUE}) {
            for (int y : new int[]{Integer.MIN_VALUE, -1, 0, Integer.MAX_VALUE}) {
                for (int z : new int[]{Integer.MIN_VALUE, -1, 0, Integer.MAX_VALUE}) {
                    positions.add(new CavePosition(x, y, z));
                }
            }
        }
        for (int index = 0; index < 4096; index++) {
            positions.add(new CavePosition(random.nextInt(), random.nextInt(), random.nextInt()));
        }
        LinkedHashMap<CavePosition, HydrologyCaveAction> actions = new LinkedHashMap<>();
        LinkedHashMap<CavePosition, CaveVoxelPrecondition> preconditions = new LinkedHashMap<>();
        HydrologyCaveAction[] actionTypes = HydrologyCaveAction.values();
        CaveVoxel[] voxels = CaveVoxel.values();
        for (int index = 0; index < positions.size(); index++) {
            if (index % 3 != 0) {
                actions.put(positions.get(index), actionTypes[index % actionTypes.length]);
            }
        }
        Collections.shuffle(positions, random);
        for (int index = 0; index < positions.size(); index++) {
            preconditions.put(positions.get(index), new CaveVoxelPrecondition(voxels[index % voxels.length], index % 2 == 0));
        }
        HydrologyCavePlan plan = new HydrologyCavePlan(source(), HydrologyCaveRejection.NONE,
                actions, preconditions, OptionalLong.empty());
        assertEquals(actions, plan.actions());
        assertEquals(plan.actions(), actions);
        assertEquals(actions.hashCode(), plan.actions().hashCode());
        assertEquals(preconditions, plan.baselinePreconditions());
        assertEquals(plan.baselinePreconditions(), preconditions);
        assertEquals(preconditions.hashCode(), plan.baselinePreconditions().hashCode());
        assertEquals(new ArrayList<>(actions.entrySet()), new ArrayList<>(plan.actions().entrySet()));
        assertEquals(new ArrayList<>(preconditions.entrySet()), new ArrayList<>(plan.baselinePreconditions().entrySet()));
        assertEquals(new ArrayList<>(actions.values()), new ArrayList<>(plan.actions().values()));
        assertEquals(new ArrayList<>(preconditions.values()), new ArrayList<>(plan.baselinePreconditions().values()));
        assertTrue(plan.allPreconditions((position, baseline) -> baseline.equals(preconditions.get(position))));
        LinkedHashMap<CavePosition, HydrologyCaveAction> visitedActions = new LinkedHashMap<>();
        LinkedHashMap<CavePosition, CaveVoxelPrecondition> visitedPreconditions = new LinkedHashMap<>();
        plan.forEachAction(visitedActions::put);
        plan.forEachPrecondition(visitedPreconditions::put);
        assertEquals(new ArrayList<>(actions.entrySet()), new ArrayList<>(visitedActions.entrySet()));
        assertEquals(new ArrayList<>(preconditions.entrySet()), new ArrayList<>(visitedPreconditions.entrySet()));
        assertNull(plan.actions().get("absent"));
        assertNull(plan.baselinePreconditions().get(new CavePosition(42, 43, 44)));
        actions.clear();
        preconditions.clear();
        assertEquals(visitedActions, plan.actions());
        assertEquals(visitedPreconditions, plan.baselinePreconditions());
        assertThrows(UnsupportedOperationException.class, () -> plan.actions().entrySet().iterator().next().setValue(HydrologyCaveAction.DRY_AIR));
        assertThrows(UnsupportedOperationException.class, () -> plan.baselinePreconditions().values().clear());
        Iterator<CavePosition> iterator = plan.actions().keySet().iterator();
        iterator.next();
        assertThrows(UnsupportedOperationException.class, iterator::remove);
        while (iterator.hasNext()) {
            iterator.next();
        }
        assertThrows(NoSuchElementException.class, iterator::next);
    }

    @Test
    public void spatialQueriesPreserveExtremeCoordinatesAndChunkOrder() {
        List<CavePosition> positions = List.of(
                new CavePosition(Integer.MIN_VALUE, Integer.MAX_VALUE, Integer.MIN_VALUE),
                new CavePosition(Integer.MAX_VALUE - 1, Integer.MIN_VALUE, Integer.MAX_VALUE - 1),
                new CavePosition(16, 1, 16),
                new CavePosition(-1, 2, 0),
                new CavePosition(0, 3, -1),
                new CavePosition(1, 4, 1),
                new CavePosition(1, 5, 1)
        );
        LinkedHashMap<CavePosition, HydrologyCaveAction> actions = new LinkedHashMap<>();
        LinkedHashMap<CavePosition, CaveVoxelPrecondition> preconditions = new LinkedHashMap<>();
        CaveVoxelPrecondition baseline = new CaveVoxelPrecondition(CaveVoxel.SOLID, false);
        for (CavePosition position : positions) {
            actions.put(position, HydrologyCaveAction.WET_SOURCE);
        }
        for (CavePosition position : positions.reversed()) {
            preconditions.put(position, baseline);
        }
        HydrologyCavePlan plan = new HydrologyCavePlan(source(), HydrologyCaveRejection.NONE,
                actions, preconditions, OptionalLong.empty());
        ArrayList<CavePosition> visited = new ArrayList<>();
        plan.forEachActionIn(Integer.MIN_VALUE, Integer.MIN_VALUE, Integer.MIN_VALUE + 1, Integer.MIN_VALUE + 1,
                (position, action) -> visited.add(position));
        assertEquals(List.of(positions.getFirst()), visited);
        visited.clear();
        plan.forEachActionIn(Integer.MAX_VALUE - 1, Integer.MAX_VALUE - 1, Integer.MAX_VALUE, Integer.MAX_VALUE,
                (position, action) -> visited.add(position));
        assertEquals(List.of(positions.get(1)), visited);
        visited.clear();
        plan.forEachActionIn(-16, -16, 32, 32, (position, action) -> visited.add(position));
        List<CavePosition> expectedOrder = List.of(positions.get(4), positions.get(3), positions.get(5), positions.get(6), positions.get(2));
        assertEquals(expectedOrder, visited);
        visited.clear();
        assertTrue(plan.allPreconditionsIn(-16, -16, 32, 32, (position, precondition) -> {
            visited.add(position);
            return true;
        }));
        assertEquals(expectedOrder, visited);
        assertFalse(plan.allPreconditionsIn(-16, -16, 32, 32, (position, precondition) -> false));
        assertTrue(plan.intersectsActions(Integer.MIN_VALUE, Integer.MIN_VALUE, Integer.MIN_VALUE + 1, Integer.MIN_VALUE + 1));
        assertFalse(plan.intersectsActions(2, 2, 16, 16));
    }

    @Test
    public void retainedSizeIncludesPrimitiveLookupAndSpatialStorage() {
        LinkedHashMap<CavePosition, HydrologyCaveAction> actions = new LinkedHashMap<>();
        LinkedHashMap<CavePosition, CaveVoxelPrecondition> preconditions = new LinkedHashMap<>();
        CaveVoxelPrecondition baseline = new CaveVoxelPrecondition(CaveVoxel.SOLID, false);
        for (int y = 0; y < 1024; y++) {
            CavePosition position = new CavePosition(0, y, 0);
            actions.put(position, HydrologyCaveAction.WET_SOURCE);
            preconditions.put(position, baseline);
        }
        HydrologyCavePlan plan = new HydrologyCavePlan(source(), HydrologyCaveRejection.NONE,
                actions, preconditions, OptionalLong.empty());
        long estimated = plan.estimatedRetainedBytes();
        assertTrue(estimated > 13L * preconditions.size());
        assertTrue(estimated < 48L * preconditions.size());
        assertEquals(estimated, plan.estimatedRetainedBytes());
        assertEquals(actions, plan.actions());
        assertEquals(preconditions, plan.baselinePreconditions());
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
