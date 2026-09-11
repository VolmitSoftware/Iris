package art.arcane.iris.engine.hydrology;

import art.arcane.iris.engine.hydrology.cave.CavePosition;
import art.arcane.iris.engine.hydrology.cave.CaveVoxel;
import art.arcane.iris.engine.hydrology.cave.CaveVoxelPrecondition;
import art.arcane.iris.engine.hydrology.cave.CaveVoxelView;
import art.arcane.iris.engine.hydrology.cave.HydrologyCaveAction;
import art.arcane.iris.engine.hydrology.cave.HydrologyCaveCandidate;
import art.arcane.iris.engine.hydrology.cave.HydrologyCaveFluidPolicy;
import art.arcane.iris.engine.hydrology.cave.HydrologyCaveGrottoShape;
import art.arcane.iris.engine.hydrology.cave.HydrologyCaveMode;
import art.arcane.iris.engine.hydrology.cave.HydrologyCavePlan;
import art.arcane.iris.engine.hydrology.cave.HydrologyCavePlannerSettings;
import art.arcane.iris.engine.hydrology.cave.HydrologyCaveRejection;
import art.arcane.iris.engine.hydrology.cave.HydrologyCaveSource;
import org.junit.Test;

import java.util.ArrayList;
import java.util.Collections;
import java.util.IdentityHashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.OptionalLong;
import java.util.Set;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotSame;
import static org.junit.Assert.assertSame;
import static org.junit.Assert.assertTrue;
import static org.mockito.Mockito.mock;

public class HydrologyCaveTerminalAlignmentTest {
    private static final CavePosition SHARED = new CavePosition(0, 20, 0);
    private static final List<HydrologyCaveAction> PRIORITY = List.of(
            HydrologyCaveAction.FALLING_FLUID,
            HydrologyCaveAction.WET_SOURCE,
            HydrologyCaveAction.DRY_AIR,
            HydrologyCaveAction.SEAL_GUARD
    );

    @Test
    public void candidatesPreserveActionOrderAndExposureIdentityForEveryPriorityPair() {
        HydrologyCaveCourseFilter filter = filter();
        Map<Long, RiverCourse> courses = Map.of(1L, course(1L, 10L), 2L, course(2L, 10L));
        for (int firstPriority = 0; firstPriority < PRIORITY.size(); firstPriority++) {
            for (int secondPriority = 0; secondPriority < PRIORITY.size(); secondPriority++) {
                HydrologyCaveCandidate first = candidate(1L, PRIORITY.get(firstPriority));
                HydrologyCaveCandidate second = candidate(2L, PRIORITY.get(secondPriority));
                ArrayList<HydrologyCaveCandidate> candidates = new ArrayList<>(List.of(first, second));
                Set<HydrologyCaveCandidate> exposed = Collections.newSetFromMap(new IdentityHashMap<>());
                exposed.add(first);

                filter.alignSharedTerminalCandidateActions(candidates, courses, exposed);

                HydrologyCaveAction expected = PRIORITY.get(Math.min(firstPriority, secondPriority));
                assertEquals(expected, candidates.getFirst().actions().get(SHARED));
                assertEquals(expected, candidates.getLast().actions().get(SHARED));
                assertEquals(List.copyOf(first.actions().keySet()), List.copyOf(candidates.getFirst().actions().keySet()));
                assertEquals(List.copyOf(second.actions().keySet()), List.copyOf(candidates.getLast().actions().keySet()));
                assertEquals(PRIORITY.get(firstPriority), first.actions().get(SHARED));
                assertEquals(PRIORITY.get(secondPriority), second.actions().get(SHARED));
                assertEquals(1, exposed.size());
                assertTrue(exposed.contains(candidates.getFirst()));
                assertFalse(exposed.contains(candidates.getLast()));
                if (firstPriority == secondPriority) {
                    assertSame(first, candidates.getFirst());
                    assertSame(second, candidates.getLast());
                } else {
                    assertNotSame(first, candidates.getFirst());
                    assertNotSame(second, candidates.getLast());
                    assertFalse(exposed.contains(first));
                }
            }
        }
    }

    @Test
    public void plansPreserveActionOrderAndBaselineForEveryPriorityPair() {
        HydrologyCaveCourseFilter filter = filter();
        Map<Long, RiverCourse> courses = Map.of(1L, course(1L, 10L), 2L, course(2L, 10L));
        for (int firstPriority = 0; firstPriority < PRIORITY.size(); firstPriority++) {
            for (int secondPriority = 0; secondPriority < PRIORITY.size(); secondPriority++) {
                HydrologyCaveCandidate firstCandidate = candidate(1L, PRIORITY.get(firstPriority));
                HydrologyCaveCandidate secondCandidate = candidate(2L, PRIORITY.get(secondPriority));
                HydrologyCavePlan first = plan(firstCandidate);
                HydrologyCavePlan second = plan(secondCandidate);

                List<HydrologyCavePlan> aligned = filter.alignSharedTerminalPlanActions(
                        List.of(first, second), List.of(firstCandidate, secondCandidate), courses);

                HydrologyCaveAction expected = PRIORITY.get(Math.min(firstPriority, secondPriority));
                assertEquals(expected, aligned.getFirst().actions().get(SHARED));
                assertEquals(expected, aligned.getLast().actions().get(SHARED));
                assertEquals(List.copyOf(first.actions().keySet()), List.copyOf(aligned.getFirst().actions().keySet()));
                assertEquals(List.copyOf(second.actions().keySet()), List.copyOf(aligned.getLast().actions().keySet()));
                assertEquals(first.baselinePreconditions(), aligned.getFirst().baselinePreconditions());
                assertEquals(second.baselinePreconditions(), aligned.getLast().baselinePreconditions());
                assertEquals(List.copyOf(first.baselinePreconditions().keySet()),
                        List.copyOf(aligned.getFirst().baselinePreconditions().keySet()));
                assertEquals(List.copyOf(second.baselinePreconditions().keySet()),
                        List.copyOf(aligned.getLast().baselinePreconditions().keySet()));
                assertEquals(PRIORITY.get(firstPriority), first.actions().get(SHARED));
                assertEquals(PRIORITY.get(secondPriority), second.actions().get(SHARED));
                if (firstPriority == secondPriority) {
                    assertSame(first, aligned.getFirst());
                    assertSame(second, aligned.getLast());
                } else {
                    assertNotSame(first, aligned.getFirst());
                    assertNotSame(second, aligned.getLast());
                }
            }
        }
    }

    @Test
    public void unrelatedOutletsRetainCandidateIdentity() {
        HydrologyCaveCandidate first = candidate(1L, HydrologyCaveAction.WET_SOURCE);
        HydrologyCaveCandidate second = candidate(2L, HydrologyCaveAction.DRY_AIR);
        ArrayList<HydrologyCaveCandidate> candidates = new ArrayList<>(List.of(first, second));

        filter().alignSharedTerminalCandidateActions(candidates,
                Map.of(1L, course(1L, 10L), 2L, course(2L, 20L)), Set.of());

        assertSame(first, candidates.getFirst());
        assertSame(second, candidates.getLast());
    }

    @Test
    public void incompatibleOrRejectedPlansRetainIdentity() {
        HydrologyCaveCandidate firstCandidate = candidate(1L, HydrologyCaveAction.WET_SOURCE);
        HydrologyCaveCandidate secondCandidate = candidate(2L, HydrologyCaveAction.DRY_AIR);
        HydrologyCaveCandidate incompatible = new HydrologyCaveCandidate(secondCandidate.source(), "lava",
                secondCandidate.settings(), false, secondCandidate.actions(), secondCandidate.intentionalOpenings());
        HydrologyCavePlan first = plan(firstCandidate);
        HydrologyCavePlan second = plan(secondCandidate);
        HydrologyCavePlan rejected = new HydrologyCavePlan(secondCandidate.source(), HydrologyCaveRejection.OPEN_SURFACE,
                Map.of(), Map.of(), OptionalLong.empty());
        Map<Long, RiverCourse> courses = Map.of(1L, course(1L, 10L), 2L, course(2L, 10L));

        List<HydrologyCavePlan> incompatibleResult = filter().alignSharedTerminalPlanActions(
                List.of(first, second), List.of(firstCandidate, incompatible), courses);
        List<HydrologyCavePlan> rejectedResult = filter().alignSharedTerminalPlanActions(
                List.of(first, rejected), List.of(firstCandidate, secondCandidate), courses);

        assertSame(first, incompatibleResult.getFirst());
        assertSame(second, incompatibleResult.getLast());
        assertSame(first, rejectedResult.getFirst());
        assertSame(rejected, rejectedResult.getLast());
    }

    private HydrologyCaveCourseFilter filter() {
        return new HydrologyCaveCourseFilter(mock(CaveVoxelView.class),
                new HydrologyCaveCourseFilter.Options(false, 8192, 8192));
    }

    private HydrologyCaveCandidate candidate(long id, HydrologyCaveAction sharedAction) {
        CavePosition own = new CavePosition((int) id, 20, 0);
        LinkedHashMap<CavePosition, HydrologyCaveAction> actions = new LinkedHashMap<>();
        if (id == 2L) {
            actions.put(SHARED, sharedAction);
        }
        actions.put(own, HydrologyCaveAction.DRY_AIR);
        actions.put(SHARED, sharedAction);
        return new HydrologyCaveCandidate(
                new HydrologyCaveSource(id, SHARED, SHARED, 20, HydrologyCaveMode.GENERATED_GROTTO),
                "water", new HydrologyCavePlannerSettings(8, 8, 32, 1, 1, 1, 1, 0,
                HydrologyCaveFluidPolicy.REJECT_EXISTING, HydrologyCaveGrottoShape.ELLIPSOID, 8, 8),
                false, actions, Set.of(own));
    }

    private HydrologyCavePlan plan(HydrologyCaveCandidate candidate) {
        LinkedHashMap<CavePosition, CaveVoxelPrecondition> baseline = new LinkedHashMap<>();
        for (CavePosition position : candidate.actions().keySet()) {
            baseline.put(position, new CaveVoxelPrecondition(CaveVoxel.SOLID, false));
        }
        baseline.put(new CavePosition(-(int) candidate.source().sourceId(), 20, 0),
                new CaveVoxelPrecondition(CaveVoxel.SOLID, false));
        return new HydrologyCavePlan(candidate.source(), HydrologyCaveRejection.NONE, candidate.actions(),
                baseline, OptionalLong.empty());
    }

    private RiverCourse course(long id, long outletId) {
        HydraulicSegment segment = new HydraulicSegment(id, id, HydrologyFeatureType.UNDERGROUND_POOL,
                20, 20, 2, 2, false, false, List.of(new HydrologyPoint(0, 20, 0)),
                HydraulicChannelProfile.uniform(2, 2));
        return new RiverCourse(id, RiverCourseType.UNDERGROUND, OptionalLong.of(id), OptionalLong.of(outletId),
                "water", 1, List.of(), List.of(segment));
    }
}
