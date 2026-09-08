package art.arcane.iris.engine.hydrology.cave;

import art.arcane.iris.engine.hydrology.HydrologyCaveVoxelViewFactory;
import art.arcane.iris.engine.hydrology.HydrologyObservedPlannedSurface;
import org.junit.Test;

import java.util.ArrayDeque;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Queue;
import java.util.Set;
import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotEquals;
import static org.junit.Assert.assertThrows;
import static org.junit.Assert.assertTrue;

public class HydrologyCaveContainmentPlannerTest {
    private static final List<CavePosition> DIRECTIONS = List.of(
            new CavePosition(1, 0, 0),
            new CavePosition(-1, 0, 0),
            new CavePosition(0, 1, 0),
            new CavePosition(0, -1, 0),
            new CavePosition(0, 0, 1),
            new CavePosition(0, 0, -1)
    );

    @Test
    public void cavePositionHashSeparatesAxisExtrusions() {
        assertNotEquals(new CavePosition(0, 31, 0).hashCode(), new CavePosition(1, 0, 0).hashCode());
        assertNotEquals(new CavePosition(0, 1, 0).hashCode(), new CavePosition(0, 0, 31).hashCode());
    }

    private final HydrologyCaveContainmentPlanner planner = new HydrologyCaveContainmentPlanner();

    @Test
    public void closedComponentProducesConnectedThroatPoolAndGuards() {
        TestVoxelView view = new TestVoxelView();
        view.set(CaveVoxel.CAVE_AIR, position(0, 7, 0), position(1, 7, 0), position(1, 6, 0));
        HydrologyCaveSource source = source(14L, 10, HydrologyCaveMode.CLOSED_COMPONENT, position(0, 7, 0));

        HydrologyCavePlan plan = planner.plan(view, source, settings());

        assertTrue(plan.accepted());
        assertEquals(HydrologyCaveAction.DRY_AIR, plan.actions().get(position(0, 12, 0)));
        assertEquals(HydrologyCaveAction.DRY_AIR, plan.actions().get(position(0, 11, 0)));
        assertEquals(HydrologyCaveAction.WET_SOURCE, plan.actions().get(position(0, 10, 0)));
        assertEquals(HydrologyCaveAction.WET_SOURCE, plan.actions().get(position(1, 6, 0)));
        assertTrue(plan.actions().containsValue(HydrologyCaveAction.SEAL_GUARD));
        assertMutationConnected(plan, source.entry(), position(1, 6, 0));
        assertEquals(plan.actions().keySet(), plan.baselinePreconditions().keySet());
        assertEquals(CaveVoxel.CAVE_AIR, plan.baselinePreconditions().get(position(1, 6, 0)).voxel());
        assertEquals(CaveVoxel.SOLID, plan.baselinePreconditions().get(position(0, 12, 0)).voxel());
    }

    @Test
    public void compatibleFluidFollowsExplicitPolicy() {
        TestVoxelView view = new TestVoxelView();
        CavePosition target = position(0, 7, 0);
        view.set(CaveVoxel.COMPATIBLE_FLUID, target);
        HydrologyCaveSource source = source(1L, 10, HydrologyCaveMode.CLOSED_COMPONENT, target);

        HydrologyCavePlan allowed = planner.plan(view, source, settings(HydrologyCaveFluidPolicy.ALLOW_COMPATIBLE));
        HydrologyCavePlan rejected = planner.plan(view, source, settings(HydrologyCaveFluidPolicy.REJECT_EXISTING));

        assertTrue(allowed.accepted());
        assertEquals(HydrologyCaveAction.WET_SOURCE, allowed.actions().get(target));
        assertEquals(HydrologyCaveRejection.EXISTING_FLUID, rejected.rejection());
        assertTrue(rejected.actions().isEmpty());
    }

    @Test
    public void replacementPolicyIsDistinctAndStillRejectsLava() {
        CavePosition target = position(0, 7, 0);
        TestVoxelView compatibleView = new TestVoxelView();
        compatibleView.set(CaveVoxel.COMPATIBLE_FLUID, target);
        TestVoxelView incompatibleView = new TestVoxelView();
        incompatibleView.set(CaveVoxel.INCOMPATIBLE_FLUID, target);
        TestVoxelView lavaView = new TestVoxelView();
        lavaView.set(CaveVoxel.LAVA, target);
        HydrologyCaveSource source = source(101L, 10, HydrologyCaveMode.CLOSED_COMPONENT, target);

        HydrologyCavePlan rejectedCompatible = planner.plan(
                compatibleView,
                source,
                settings(HydrologyCaveFluidPolicy.REJECT_EXISTING)
        );
        HydrologyCavePlan allowedCompatible = planner.plan(
                compatibleView,
                source,
                settings(HydrologyCaveFluidPolicy.ALLOW_COMPATIBLE)
        );
        HydrologyCavePlan rejectedIncompatible = planner.plan(
                incompatibleView,
                source,
                settings(HydrologyCaveFluidPolicy.ALLOW_COMPATIBLE)
        );
        HydrologyCavePlan replacedIncompatible = planner.plan(
                incompatibleView,
                source,
                settings(HydrologyCaveFluidPolicy.REPLACE_CONTAINED)
        );
        HydrologyCavePlan rejectedLava = planner.plan(
                lavaView,
                source,
                settings(HydrologyCaveFluidPolicy.REPLACE_CONTAINED)
        );

        assertEquals(HydrologyCaveRejection.EXISTING_FLUID, rejectedCompatible.rejection());
        assertTrue(allowedCompatible.accepted());
        assertEquals(HydrologyCaveRejection.INCOMPATIBLE_FLUID, rejectedIncompatible.rejection());
        assertTrue(replacedIncompatible.accepted());
        assertEquals(HydrologyCaveAction.WET_SOURCE, replacedIncompatible.actions().get(target));
        assertEquals(HydrologyCaveRejection.LAVA_CONTACT, rejectedLava.rejection());
    }

    @Test
    public void throatRadiusExpandsTheConnectedBoreFootprint() {
        TestVoxelView thinView = new TestVoxelView();
        TestVoxelView thickView = new TestVoxelView();
        CavePosition target = position(0, 7, 0);
        thinView.set(CaveVoxel.CAVE_AIR, target);
        thickView.set(CaveVoxel.CAVE_AIR, target);
        HydrologyCaveSource source = source(102L, 10, HydrologyCaveMode.CLOSED_COMPONENT, target);
        HydrologyCavePlannerSettings thinSettings = detailedSettings(
                1,
                0,
                HydrologyCaveGrottoShape.ELLIPSOID,
                HydrologyCaveFluidPolicy.ALLOW_COMPATIBLE
        );
        HydrologyCavePlannerSettings thickSettings = detailedSettings(
                3,
                0,
                HydrologyCaveGrottoShape.ELLIPSOID,
                HydrologyCaveFluidPolicy.ALLOW_COMPATIBLE
        );

        HydrologyCavePlan thin = planner.plan(thinView, source, thinSettings);
        HydrologyCavePlan thick = planner.plan(thickView, source, thickSettings);

        assertTrue(thin.accepted());
        assertTrue(thick.accepted());
        assertFalse(thin.actions().containsKey(position(2, 10, 0)));
        assertEquals(HydrologyCaveAction.WET_SOURCE, thick.actions().get(position(2, 10, 0)));
        assertTrue(mutationCount(thick) > mutationCount(thin));
    }

    @Test
    public void generatedGrottoRetainsConfiguredDryHeadroom() {
        TestVoxelView view = new TestVoxelView();
        HydrologyCaveSource source = source(103L, 10, HydrologyCaveMode.GENERATED_GROTTO, position(0, 8, 0));
        HydrologyCavePlannerSettings acceptedSettings = detailedSettings(
                1,
                2,
                HydrologyCaveGrottoShape.ELLIPSOID,
                HydrologyCaveFluidPolicy.ALLOW_COMPATIBLE
        );
        HydrologyCavePlannerSettings rejectedSettings = detailedSettings(
                1,
                5,
                HydrologyCaveGrottoShape.ELLIPSOID,
                HydrologyCaveFluidPolicy.ALLOW_COMPATIBLE
        );

        HydrologyCavePlan accepted = planner.plan(view, source, acceptedSettings);
        HydrologyCavePlan rejected = planner.plan(view, source, rejectedSettings);

        assertTrue(accepted.accepted());
        assertEquals(HydrologyCaveAction.FALLING_FLUID, accepted.actions().get(position(0, 11, 0)));
        assertEquals(HydrologyCaveAction.FALLING_FLUID, accepted.actions().get(position(0, 12, 0)));
        assertEquals(HydrologyCaveRejection.DRY_HEADROOM_LIMIT, rejected.rejection());
    }

    @Test
    public void configuredGrottoPredicateChangesFootprintDeterministically() {
        TestVoxelView view = new TestVoxelView();
        HydrologyCaveSource source = source(104L, 10, HydrologyCaveMode.GENERATED_GROTTO, position(0, 8, 0));
        HydrologyCavePlannerSettings ellipsoid = detailedSettings(
                1,
                0,
                HydrologyCaveGrottoShape.ELLIPSOID,
                HydrologyCaveFluidPolicy.ALLOW_COMPATIBLE
        );
        HydrologyCaveGrottoShape compactShape = (candidate, settings, dx, dy, dz) -> {
            double horizontal = settings.grottoHorizontalRadius();
            double vertical = settings.grottoVerticalRadius();
            double normalized = (dx * dx / (horizontal * horizontal))
                    + (dy * dy / (vertical * vertical))
                    + (dz * dz / (horizontal * horizontal));
            return normalized <= 0.5D;
        };
        HydrologyCavePlannerSettings compact = detailedSettings(
                1,
                0,
                compactShape,
                HydrologyCaveFluidPolicy.ALLOW_COMPATIBLE
        );

        HydrologyCavePlan full = planner.plan(view, source, ellipsoid);
        HydrologyCavePlan firstCompact = planner.plan(view, source, compact);
        HydrologyCavePlan secondCompact = planner.plan(view, source, compact);

        assertTrue(full.accepted());
        assertTrue(firstCompact.accepted());
        assertEquals(firstCompact, secondCompact);
        assertTrue(mutationCount(full) > mutationCount(firstCompact));
    }

    @Test
    public void radiusLimitRejectsWholeComponentWithoutClipping() {
        TestVoxelView view = new TestVoxelView();
        for (int x = 0; x <= 3; x++) {
            view.set(CaveVoxel.CAVE_AIR, position(x, 7, 0));
        }
        HydrologyCavePlannerSettings settings = new HydrologyCavePlannerSettings(
                2,
                20,
                64,
                32,
                1,
                1,
                HydrologyCaveFluidPolicy.ALLOW_COMPATIBLE
        );

        HydrologyCavePlan plan = planner.plan(
                view,
                source(2L, 10, HydrologyCaveMode.CLOSED_COMPONENT, position(0, 7, 0)),
                settings
        );

        assertRejectedWithoutPublication(plan, HydrologyCaveRejection.RADIUS_LIMIT);
    }

    @Test
    public void depthLimitRejectsWholeComponentWithoutClipping() {
        TestVoxelView view = new TestVoxelView();
        view.set(CaveVoxel.CAVE_AIR, position(0, 8, 0), position(0, 7, 0));
        HydrologyCavePlannerSettings settings = new HydrologyCavePlannerSettings(
                8,
                4,
                64,
                32,
                1,
                1,
                HydrologyCaveFluidPolicy.ALLOW_COMPATIBLE
        );

        HydrologyCavePlan plan = planner.plan(
                view,
                source(3L, 10, HydrologyCaveMode.CLOSED_COMPONENT, position(0, 8, 0)),
                settings
        );

        assertRejectedWithoutPublication(plan, HydrologyCaveRejection.DEPTH_LIMIT);
    }

    @Test
    public void volumeLimitRejectsWholeComponentWithoutClipping() {
        TestVoxelView view = new TestVoxelView();
        view.set(CaveVoxel.CAVE_AIR, position(0, 7, 0), position(1, 7, 0), position(2, 7, 0));
        HydrologyCavePlannerSettings settings = new HydrologyCavePlannerSettings(
                8,
                20,
                2,
                32,
                1,
                1,
                HydrologyCaveFluidPolicy.ALLOW_COMPATIBLE
        );

        HydrologyCavePlan plan = planner.plan(
                view,
                source(4L, 10, HydrologyCaveMode.CLOSED_COMPONENT, position(0, 7, 0)),
                settings
        );

        assertRejectedWithoutPublication(plan, HydrologyCaveRejection.VOLUME_LIMIT);
    }

    @Test
    public void worldBoundaryRejectsWholeComponent() {
        TestVoxelView view = new TestVoxelView(-16, 0, 0, 32, -16, 16);
        view.set(CaveVoxel.CAVE_AIR, position(0, 7, 0));

        HydrologyCavePlan plan = planner.plan(
                view,
                source(5L, 10, HydrologyCaveMode.CLOSED_COMPONENT, position(0, 7, 0)),
                settings()
        );

        assertRejectedWithoutPublication(plan, HydrologyCaveRejection.WORLD_BOUNDARY);
    }

    @Test
    public void openSurfaceRejectsWholeComponent() {
        TestVoxelView view = new TestVoxelView();
        CavePosition target = position(0, 7, 0);
        view.set(CaveVoxel.CAVE_AIR, target);
        view.openToSurface(target);

        HydrologyCavePlan plan = planner.plan(
                view,
                source(6L, 10, HydrologyCaveMode.CLOSED_COMPONENT, target),
                settings()
        );

        assertRejectedWithoutPublication(plan, HydrologyCaveRejection.OPEN_SURFACE);
    }

    @Test
    public void lavaAndIncompatibleFluidContactsRejectWholeComponent() {
        TestVoxelView lavaView = new TestVoxelView();
        lavaView.set(CaveVoxel.CAVE_AIR, position(0, 7, 0));
        lavaView.set(CaveVoxel.LAVA, position(1, 7, 0));
        TestVoxelView fluidView = new TestVoxelView();
        fluidView.set(CaveVoxel.CAVE_AIR, position(0, 7, 0));
        fluidView.set(CaveVoxel.INCOMPATIBLE_FLUID, position(1, 7, 0));
        HydrologyCaveSource source = source(7L, 10, HydrologyCaveMode.CLOSED_COMPONENT, position(0, 7, 0));

        HydrologyCavePlan lavaPlan = planner.plan(lavaView, source, settings());
        HydrologyCavePlan fluidPlan = planner.plan(fluidView, source, settings());

        assertRejectedWithoutPublication(lavaPlan, HydrologyCaveRejection.LAVA_CONTACT);
        assertRejectedWithoutPublication(fluidPlan, HydrologyCaveRejection.INCOMPATIBLE_FLUID);
    }

    @Test
    public void fallingHazardAbovePoolRejectsWholeComponent() {
        TestVoxelView view = new TestVoxelView();
        view.set(CaveVoxel.CAVE_AIR, position(0, 7, 0));
        view.set(CaveVoxel.LAVA, position(1, 11, 0));

        HydrologyCavePlan plan = planner.plan(
                view,
                source(15L, 10, HydrologyCaveMode.CLOSED_COMPONENT, position(0, 7, 0)),
                settings()
        );

        assertRejectedWithoutPublication(plan, HydrologyCaveRejection.LAVA_CONTACT);
    }

    @Test
    public void generatedGrottoHasBoundedWetDryAndGuardActions() {
        TestVoxelView view = new TestVoxelView();
        HydrologyCaveSource source = source(8L, 10, HydrologyCaveMode.GENERATED_GROTTO, position(0, 8, 0));

        HydrologyCavePlan plan = planner.plan(view, source, settings());

        assertTrue(plan.accepted());
        assertEquals(HydrologyCaveAction.FALLING_FLUID, plan.actions().get(position(0, 12, 0)));
        assertEquals(HydrologyCaveAction.WET_SOURCE, plan.actions().get(position(0, 8, 0)));
        assertTrue(plan.actions().containsValue(HydrologyCaveAction.SEAL_GUARD));
        assertEquals(plan.actions().keySet(), plan.baselinePreconditions().keySet());
        assertFalse(plan.actions().containsKey(position(0, 13, 0)));
        assertMutationConnected(plan, source.entry(), source.target());
    }

    @Test
    public void generatedGrottoAcceptsItsCompleteSurfaceMouth() {
        TestVoxelView view = new TestVoxelView();
        CavePosition[] wetMouth = {
                position(-1, 12, 0),
                position(1, 12, 0),
                position(0, 12, -1),
                position(0, 12, 1)
        };
        CavePosition[] dryMouth = {
                position(-1, 13, 0),
                position(1, 13, 0),
                position(0, 13, -1),
                position(0, 13, 1)
        };
        view.set(CaveVoxel.COMPATIBLE_FLUID, wetMouth);
        view.set(CaveVoxel.CAVE_AIR, dryMouth);
        for (CavePosition position : wetMouth) {
            view.openToSurface(position);
        }
        for (CavePosition position : dryMouth) {
            view.openToSurface(position);
        }
        HydrologyCaveSource source = source(108L, 10, HydrologyCaveMode.GENERATED_GROTTO, position(0, 8, 0));

        HydrologyCavePlan plan = planner.plan(view, source, detailedSettings(
                2,
                2,
                HydrologyCaveGrottoShape.ELLIPSOID,
                HydrologyCaveFluidPolicy.ALLOW_COMPATIBLE
        ));

        assertTrue(plan.rejection().toString(), plan.accepted());
        for (CavePosition position : wetMouth) {
            assertEquals(HydrologyCaveAction.FALLING_FLUID, plan.actions().get(position));
        }
    }

    @Test
    public void generatedGrottoRejectsAnOpenShell() {
        TestVoxelView view = new TestVoxelView();
        view.set(CaveVoxel.CAVE_AIR, position(3, 8, 0));

        HydrologyCavePlan plan = planner.plan(
                view,
                source(9L, 10, HydrologyCaveMode.GENERATED_GROTTO, position(0, 8, 0)),
                settings()
        );

        assertRejectedWithoutPublication(plan, HydrologyCaveRejection.GROTTO_SHELL_OPEN);
    }

    @Test
    public void deepPoolOpensOnlyAboveItsContainedFluidHead() {
        TestVoxelView view = new TestVoxelView();
        view.set(CaveVoxel.CAVE_AIR, position(0, 11, 0), position(0, 13, 0));
        HydrologyCaveSource source = new HydrologyCaveSource(
                109L,
                position(0, 10, 0),
                position(0, 8, 0),
                10,
                HydrologyCaveMode.DEEP_POOL
        );

        HydrologyCavePlan plan = planner.plan(view, source, detailedSettings(
                1,
                2,
                HydrologyCaveGrottoShape.ELLIPSOID,
                HydrologyCaveFluidPolicy.REJECT_EXISTING
        ));

        assertTrue(plan.rejection().toString(), plan.accepted());
        assertEquals(HydrologyCaveAction.WET_SOURCE, plan.actions().get(position(0, 10, 0)));
        assertEquals(HydrologyCaveAction.DRY_AIR, plan.actions().get(position(0, 11, 0)));
        assertFalse(plan.actions().containsKey(position(0, 13, 0)));
    }

    @Test
    public void deepPoolRejectsCaveLeakAtOrBelowItsFluidHead() {
        TestVoxelView view = new TestVoxelView();
        view.set(CaveVoxel.CAVE_AIR, position(5, 8, 0));
        HydrologyCaveSource source = new HydrologyCaveSource(
                110L,
                position(0, 10, 0),
                position(0, 8, 0),
                10,
                HydrologyCaveMode.DEEP_POOL
        );

        HydrologyCavePlan plan = planner.plan(view, source, detailedSettings(
                1,
                2,
                HydrologyCaveGrottoShape.ELLIPSOID,
                HydrologyCaveFluidPolicy.REJECT_EXISTING
        ));

        assertRejectedWithoutPublication(plan, HydrologyCaveRejection.GROTTO_SHELL_OPEN);
    }

    @Test
    public void combinedModeUsesClosedCaveOrGeneratedGrottoFromBaseline() {
        TestVoxelView caveView = new TestVoxelView();
        CavePosition target = position(0, 8, 0);
        caveView.set(CaveVoxel.CAVE_AIR, target);
        TestVoxelView solidView = new TestVoxelView();
        HydrologyCaveSource source = source(10L, 10, HydrologyCaveMode.GROTTO_OR_CLOSED_COMPONENT, target);

        HydrologyCavePlan cavePlan = planner.plan(caveView, source, settings());
        HydrologyCavePlan grottoPlan = planner.plan(solidView, source, settings());

        assertTrue(cavePlan.accepted());
        assertTrue(grottoPlan.accepted());
        assertEquals(CaveVoxel.CAVE_AIR, cavePlan.baselinePreconditions().get(target).voxel());
        assertEquals(CaveVoxel.SOLID, grottoPlan.baselinePreconditions().get(target).voxel());
    }

    @Test
    public void waterfallKeepsColumnDistinctFromSourcePoolAndDryAir() {
        TestVoxelView view = new TestVoxelView();
        HydrologyCaveSource source = source(11L, 10, HydrologyCaveMode.WATERFALL_POOL, position(0, 8, 0));

        HydrologyCavePlan plan = planner.plan(view, source, settings());

        assertTrue(plan.accepted());
        assertEquals(HydrologyCaveAction.FALLING_FLUID, plan.actions().get(position(0, 12, 0)));
        assertEquals(HydrologyCaveAction.FALLING_FLUID, plan.actions().get(position(0, 11, 0)));
        assertEquals(HydrologyCaveAction.WET_SOURCE, plan.actions().get(position(0, 10, 0)));
        assertFalse(plan.actions().containsValue(HydrologyCaveAction.DRY_AIR));
    }

    @Test
    public void waterfallRejectsAnUnsealedFallingColumn() {
        TestVoxelView view = new TestVoxelView();
        view.set(CaveVoxel.CAVE_AIR, position(0, 8, 0), position(1, 11, 0));
        HydrologyCaveSource source = source(13L, 10, HydrologyCaveMode.WATERFALL_POOL, position(0, 8, 0));

        HydrologyCavePlan plan = planner.plan(view, source, settings());

        assertRejectedWithoutPublication(plan, HydrologyCaveRejection.WATERFALL_SHAFT_OPEN);
    }

    @Test
    public void overlapArbitrationIsOrderIndependentAndNamesWinner() {
        TestVoxelView view = new TestVoxelView();
        view.set(CaveVoxel.CAVE_AIR, position(0, 7, 0), position(1, 7, 0), position(2, 7, 0));
        HydrologyCaveSource lowerHead = new HydrologyCaveSource(
                1L,
                position(2, 12, 0),
                position(2, 7, 0),
                10,
                HydrologyCaveMode.CLOSED_COMPONENT
        );
        HydrologyCaveSource higherHead = new HydrologyCaveSource(
                20L,
                position(0, 12, 0),
                position(0, 7, 0),
                11,
                HydrologyCaveMode.CLOSED_COMPONENT
        );

        HydrologyCavePlanningResult forward = planner.planAll(view, List.of(lowerHead, higherHead), settings());
        HydrologyCavePlanningResult reverse = planner.planAll(view, List.of(higherHead, lowerHead), settings());

        assertEquals(forward, reverse);
        assertEquals(higherHead, forward.plans().get(0).source());
        assertTrue(forward.plans().get(0).accepted());
        assertEquals(HydrologyCaveRejection.OVERLAPPING_SOURCE, forward.plans().get(1).rejection());
        assertEquals(20L, forward.plans().get(1).arbitrationWinnerSourceId().getAsLong());
        assertEquals(forward.actions().keySet(), forward.baselinePreconditions().keySet());
    }

    @Test
    public void equalHeadOverlapUsesLowestStableSourceId() {
        TestVoxelView view = new TestVoxelView();
        view.set(CaveVoxel.CAVE_AIR, position(0, 7, 0), position(1, 7, 0), position(2, 7, 0));
        HydrologyCaveSource largerId = new HydrologyCaveSource(
                20L,
                position(0, 12, 0),
                position(0, 7, 0),
                10,
                HydrologyCaveMode.CLOSED_COMPONENT
        );
        HydrologyCaveSource smallerId = new HydrologyCaveSource(
                1L,
                position(2, 12, 0),
                position(2, 7, 0),
                10,
                HydrologyCaveMode.CLOSED_COMPONENT
        );

        HydrologyCavePlanningResult result = planner.planAll(view, List.of(largerId, smallerId), settings());

        assertEquals(smallerId, result.plans().get(0).source());
        assertTrue(result.plans().get(0).accepted());
        assertEquals(1L, result.plans().get(1).arbitrationWinnerSourceId().getAsLong());
    }

    @Test
    public void arbitrationUsesDirectHigherPriorityPlansEvenWhenTheyAreRejected() {
        TestVoxelView view = new TestVoxelView();
        view.set(
                CaveVoxel.CAVE_AIR,
                position(0, 7, 0),
                position(2, 7, 0),
                position(4, 7, 0)
        );
        HydrologyCaveSource lowest = new HydrologyCaveSource(
                30L,
                position(0, 12, 0),
                position(0, 7, 0),
                10,
                HydrologyCaveMode.CLOSED_COMPONENT
        );
        HydrologyCaveSource middle = new HydrologyCaveSource(
                20L,
                position(2, 12, 0),
                position(2, 7, 0),
                11,
                HydrologyCaveMode.CLOSED_COMPONENT
        );
        HydrologyCaveSource highest = new HydrologyCaveSource(
                10L,
                position(4, 12, 0),
                position(4, 7, 0),
                12,
                HydrologyCaveMode.CLOSED_COMPONENT
        );

        HydrologyCavePlanningResult forward = planner.planAll(
                view,
                List.of(lowest, middle, highest),
                settings()
        );
        HydrologyCavePlanningResult reverse = planner.planAll(
                view,
                List.of(highest, middle, lowest),
                settings()
        );

        assertEquals(forward, reverse);
        assertTrue(forward.plans().get(0).accepted());
        assertEquals(HydrologyCaveRejection.OVERLAPPING_SOURCE, forward.plans().get(1).rejection());
        assertEquals(10L, forward.plans().get(1).arbitrationWinnerSourceId().getAsLong());
        assertEquals(HydrologyCaveRejection.OVERLAPPING_SOURCE, forward.plans().get(2).rejection());
        assertEquals(20L, forward.plans().get(2).arbitrationWinnerSourceId().getAsLong());
        assertFalse(forward.actions().containsKey(position(0, 7, 0)));
        assertTrue(forward.actions().containsKey(position(4, 7, 0)));
    }

    @Test
    public void planOutputsAreImmutable() {
        TestVoxelView view = new TestVoxelView();
        view.set(CaveVoxel.CAVE_AIR, position(0, 7, 0));
        HydrologyCaveSource source = source(12L, 10, HydrologyCaveMode.CLOSED_COMPONENT, position(0, 7, 0));
        HydrologyCavePlanningResult result = planner.planAll(view, List.of(source), settings());
        HydrologyCavePlan plan = result.plans().get(0);

        assertThrows(
                UnsupportedOperationException.class,
                () -> plan.actions().put(position(9, 9, 9), HydrologyCaveAction.WET_SOURCE)
        );
        assertThrows(
                UnsupportedOperationException.class,
                () -> plan.baselinePreconditions().put(
                        position(9, 9, 9),
                        new CaveVoxelPrecondition(CaveVoxel.SOLID, false)
                )
        );
        assertThrows(UnsupportedOperationException.class, () -> result.plans().add(plan));
        assertThrows(
                UnsupportedOperationException.class,
                () -> result.actions().put(position(9, 9, 9), HydrologyCaveAction.WET_SOURCE)
        );
    }

    @Test
    public void plannedVolumesRejectHazardsSurfaceExposureAndOverflowWithoutActions() {
        CavePosition center = position(0, 10, 0);
        HydrologyCaveCandidate acceptedCandidate = candidate(
                201L,
                10,
                "water",
                Map.of(center, HydrologyCaveAction.WET_SOURCE),
                settings()
        );
        TestVoxelView lava = new TestVoxelView();
        lava.set(CaveVoxel.LAVA, center);
        TestVoxelView incompatible = new TestVoxelView();
        incompatible.set(CaveVoxel.INCOMPATIBLE_FLUID, center);
        TestVoxelView exposed = new TestVoxelView();
        exposed.set(CaveVoxel.CAVE_AIR, center);
        exposed.openToSurface(center);
        exposed.aboveTerrain(center);
        HydrologyCavePlannerSettings oneVoxel = new HydrologyCavePlannerSettings(
                12,
                20,
                1,
                32,
                2,
                2,
                HydrologyCaveFluidPolicy.ALLOW_COMPATIBLE
        );
        HydrologyCaveCandidate overflow = candidate(
                202L,
                10,
                "water",
                Map.of(
                        center,
                        HydrologyCaveAction.WET_SOURCE,
                        position(0, 9, 0),
                        HydrologyCaveAction.WET_SOURCE
                ),
                oneVoxel
        );

        assertRejectedWithoutPublication(
                planner.validate(lava, acceptedCandidate),
                HydrologyCaveRejection.LAVA_CONTACT
        );
        assertRejectedWithoutPublication(
                planner.validate(incompatible, acceptedCandidate),
                HydrologyCaveRejection.INCOMPATIBLE_FLUID
        );
        assertRejectedWithoutPublication(
                planner.validate(exposed, acceptedCandidate),
                HydrologyCaveRejection.OPEN_SURFACE
        );
        assertRejectedWithoutPublication(
                planner.validate(new TestVoxelView(), overflow),
                HydrologyCaveRejection.VOLUME_LIMIT
        );
    }

    @Test
    public void validationCacheReusesStableUniqueObservationsAndInvalidatesChangedVoxels() {
        CavePosition center = position(0, 10, 0);
        HydrologyCaveCandidate candidate = candidate(
                203L,
                10,
                "water",
                Map.of(center, HydrologyCaveAction.WET_SOURCE),
                settings()
        );
        TestVoxelView view = new TestVoxelView();
        HydrologyCaveContainmentPlanner.ValidationCache cache =
                new HydrologyCaveContainmentPlanner.ValidationCache();

        HydrologyCavePlan initial = planner.validateAll(view, List.of(candidate), cache).plans().getFirst();
        HydrologyCavePlan cached = planner.validateAll(view, List.of(candidate), cache).plans().getFirst();

        assertTrue(initial.accepted());
        assertEquals(initial, cached);
        assertEquals(1L, cache.hits());
        assertEquals(1L, cache.misses());

        view.set(CaveVoxel.LAVA, center);
        HydrologyCavePlan changed = planner.validateAll(view, List.of(candidate), cache).plans().getFirst();

        assertEquals(HydrologyCaveRejection.LAVA_CONTACT, changed.rejection());
        assertEquals(1L, cache.hits());
        assertEquals(2L, cache.misses());
    }

    @Test
    public void oversizedPlannedVolumeRejectsBeforeLoadingVoxels() {
        AtomicInteger voxelLoads = new AtomicInteger();
        CaveVoxelView view = new CaveVoxelView() {
            @Override
            public boolean isInWorld(CavePosition position) {
                voxelLoads.incrementAndGet();
                return true;
            }

            @Override
            public CaveVoxel voxelAt(CavePosition position) {
                voxelLoads.incrementAndGet();
                return CaveVoxel.SOLID;
            }

            @Override
            public boolean isOpenToSurface(CavePosition position) {
                voxelLoads.incrementAndGet();
                return false;
            }

            @Override
            public boolean isAboveTerrainSurface(CavePosition position) {
                voxelLoads.incrementAndGet();
                return false;
            }
        };
        HydrologyCavePlannerSettings oneVoxel = new HydrologyCavePlannerSettings(
                12,
                20,
                1,
                32,
                2,
                2,
                HydrologyCaveFluidPolicy.ALLOW_COMPATIBLE
        );
        HydrologyCaveCandidate candidate = candidate(
                205L,
                10,
                "water",
                Map.of(
                        position(0, 10, 0), HydrologyCaveAction.WET_SOURCE,
                        position(0, 9, 0), HydrologyCaveAction.WET_SOURCE
                ),
                oneVoxel
        );

        HydrologyCavePlan plan = planner.validate(view, candidate);

        assertRejectedWithoutPublication(plan, HydrologyCaveRejection.VOLUME_LIMIT);
        assertEquals(0, voxelLoads.get());
    }

    @Test
    public void plannedSurfaceCacheTracksObservedColumnsWithoutRetainingVoxelObservations() {
        CavePosition center = position(0, 10, 0);
        HydrologyCaveCandidate candidate = candidate(
                204L,
                10,
                "water",
                Map.of(center, HydrologyCaveAction.WET_SOURCE),
                settings()
        );
        HydrologyCaveContainmentPlanner.ValidationCache cache =
                new HydrologyCaveContainmentPlanner.ValidationCache();
        HydrologyObservedPlannedSurface initialSurface = observedSurface(32, true);
        HydrologyObservedPlannedSurface matchingSurface = observedSurface(32, true);
        HydrologyObservedPlannedSurface changedSurface = observedSurface(9, true);

        HydrologyCavePlan initial = planner.validateAll(
                plannedSurfaceView(initialSurface),
                List.of(candidate),
                cache,
                initialSurface
        ).plans().getFirst();
        HydrologyCavePlan cached = planner.validateAll(
                plannedSurfaceView(matchingSurface),
                List.of(candidate),
                cache,
                matchingSurface
        ).plans().getFirst();

        assertTrue(initial.accepted());
        assertEquals(initial, cached);
        assertEquals(1L, cache.hits());
        assertEquals(1L, cache.misses());

        HydrologyCavePlan changed = planner.validateAll(
                plannedSurfaceView(changedSurface),
                List.of(candidate),
                cache,
                changedSurface
        ).plans().getFirst();

        assertEquals(HydrologyCaveRejection.OPEN_SURFACE, changed.rejection());
        assertEquals(1L, cache.hits());
        assertEquals(2L, cache.misses());
    }

    @Test
    public void plannedSurfaceCacheInvalidatesEqualHeightWhenTerrainOwnershipChanges() {
        CavePosition center = position(0, 10, 0);
        HydrologyCaveCandidate candidate = candidate(
                204L, 10, "water", Map.of(center, HydrologyCaveAction.WET_SOURCE), settings());
        HydrologyCaveContainmentPlanner.ValidationCache cache =
                new HydrologyCaveContainmentPlanner.ValidationCache();
        HydrologyObservedPlannedSurface initialSurface = observedSurface(32, true);
        HydrologyObservedPlannedSurface matchingSurface = observedSurface(32, true);
        HydrologyObservedPlannedSurface changedSurface = observedSurface(32, false);

        HydrologyCavePlan initial = planner.validateAll(plannedSurfaceView(initialSurface),
                List.of(candidate), cache, initialSurface).plans().getFirst();
        HydrologyCavePlan cached = planner.validateAll(plannedSurfaceView(matchingSurface),
                List.of(candidate), cache, matchingSurface).plans().getFirst();
        HydrologyCavePlan changed = planner.validateAll(plannedSurfaceView(changedSurface),
                List.of(candidate), cache, changedSurface).plans().getFirst();

        assertTrue(initial.accepted());
        assertEquals(initial, cached);
        assertEquals(HydrologyCaveRejection.OPEN_SURFACE, changed.rejection());
        assertEquals(1L, cache.hits());
        assertEquals(2L, cache.misses());
    }

    @Test
    public void validationCacheEvictsItsPreviousWorkingSetWithoutResettingStatistics() {
        CavePosition center = position(0, 10, 0);
        HydrologyCaveCandidate first = candidate(
                206L,
                10,
                "water",
                Map.of(center, HydrologyCaveAction.WET_SOURCE),
                settings()
        );
        HydrologyCaveCandidate second = candidate(
                207L,
                10,
                "water",
                Map.of(center, HydrologyCaveAction.WET_SOURCE),
                settings()
        );
        HydrologyCaveContainmentPlanner.ValidationCache cache =
                new HydrologyCaveContainmentPlanner.ValidationCache(1, Long.MAX_VALUE);
        TestVoxelView view = new TestVoxelView();

        planner.validateAll(view, List.of(first), cache);
        planner.validateAll(view, List.of(first), cache);
        planner.validateAll(view, List.of(second), cache);
        planner.validateAll(view, List.of(first), cache);

        assertEquals(1L, cache.hits());
        assertEquals(3L, cache.misses());
    }

    @Test
    public void geometricallyExposedPlannedVolumeRejectsBeforeVoxelLoading() {
        CavePosition center = position(0, 10, 0);
        AtomicInteger voxelLoads = new AtomicInteger();
        CaveVoxelView view = new CaveVoxelView() {
            @Override
            public boolean isInWorld(CavePosition position) {
                return true;
            }

            @Override
            public CaveVoxel voxelAt(CavePosition position) {
                voxelLoads.incrementAndGet();
                return CaveVoxel.SOLID;
            }

            @Override
            public boolean isOpenToSurface(CavePosition position) {
                return true;
            }

            @Override
            public boolean isAboveTerrainSurface(CavePosition position) {
                return position.equals(center);
            }
        };

        HydrologyCavePlan plan = planner.validate(
                view,
                candidate(
                        2021L,
                        10,
                        "water",
                        Map.of(center, HydrologyCaveAction.WET_SOURCE),
                        settings()
                )
        );

        assertRejectedWithoutPublication(plan, HydrologyCaveRejection.OPEN_SURFACE);
        assertEquals(0, voxelLoads.get());
    }

    @Test
    public void plannedWetVolumeSealsAClosedCaveContact() {
        CavePosition center = position(0, 10, 0);
        CavePosition closedCaveContact = position(1, 10, 0);
        TestVoxelView view = new TestVoxelView();
        view.set(CaveVoxel.CAVE_AIR, closedCaveContact);
        HydrologyCavePlan plan = planner.validate(
                view,
                candidate(
                        203L,
                        10,
                        "water",
                        Map.of(center, HydrologyCaveAction.WET_SOURCE),
                        settings()
                )
        );

        assertTrue(plan.rejection().toString(), plan.accepted());
        assertEquals(HydrologyCaveAction.SEAL_GUARD, plan.actions().get(closedCaveContact));
        assertEquals(CaveVoxel.CAVE_AIR, plan.baselinePreconditions().get(closedCaveContact).voxel());
    }

    @Test
    public void plannedWetVolumeSealsGeneratedSurfaceConnectedCaveBelowTerrain() {
        CavePosition center = position(0, 10, 0);
        CavePosition generatedOpening = position(1, 10, 0);
        TestVoxelView view = new TestVoxelView();
        view.set(CaveVoxel.CAVE_AIR, generatedOpening);
        view.openToSurface(generatedOpening);

        HydrologyCavePlan plan = planner.validate(
                view,
                candidate(
                        2031L,
                        10,
                        "water",
                        Map.of(center, HydrologyCaveAction.WET_SOURCE),
                        settings()
                )
        );

        assertTrue(plan.rejection().toString(), plan.accepted());
        assertEquals(HydrologyCaveAction.SEAL_GUARD, plan.actions().get(generatedOpening));
        assertTrue(plan.baselinePreconditions().get(generatedOpening).openToSurface());
    }

    @Test
    public void intentionalOpeningIsValidatedAndCapturedTransactionally() {
        CavePosition center = position(0, 10, 0);
        CavePosition opening = position(1, 10, 0);
        HydrologyCaveSource source = new HydrologyCaveSource(
                2032L,
                center,
                center,
                10,
                HydrologyCaveMode.GENERATED_GROTTO
        );
        HydrologyCaveCandidate candidate = new HydrologyCaveCandidate(
                source,
                "water",
                settings(),
                true,
                Map.of(center, HydrologyCaveAction.WET_SOURCE),
                Set.of(opening)
        );
        TestVoxelView acceptedView = new TestVoxelView();
        acceptedView.set(CaveVoxel.CAVE_AIR, opening);
        acceptedView.openToSurface(opening);
        acceptedView.aboveTerrain(opening);

        HydrologyCavePlan accepted = planner.validate(acceptedView, candidate);
        assertTrue(accepted.rejection().toString(), accepted.accepted());
        assertEquals(
                new CaveVoxelPrecondition(CaveVoxel.CAVE_AIR, true),
                accepted.baselinePreconditions().get(opening)
        );

        TestVoxelView hazardousView = new TestVoxelView();
        hazardousView.set(CaveVoxel.LAVA, opening);
        assertRejectedWithoutPublication(
                planner.validate(hazardousView, candidate),
                HydrologyCaveRejection.LAVA_CONTACT
        );
    }

    @Test
    public void unconditionalGeneratedOpeningRetainsIntentionalSurfaceExposureWithoutGuards() {
        CavePosition center = position(0, 10, 0);
        CavePosition opening = position(1, 10, 0);
        HydrologyCaveCandidate candidate = new HydrologyCaveCandidate(
                new HydrologyCaveSource(
                        2033L,
                        center,
                        center,
                        10,
                        HydrologyCaveMode.GENERATED_GROTTO
                ),
                "water",
                settings(),
                false,
                Map.of(center, HydrologyCaveAction.WET_SOURCE),
                Set.of(opening)
        );
        CaveVoxelView generatedView = new CaveVoxelView() {
            @Override
            public boolean isInWorld(CavePosition position) {
                return true;
            }

            @Override
            public CaveVoxel voxelAt(CavePosition position) {
                return CaveVoxel.UNCONDITIONAL;
            }

            @Override
            public boolean isOpenToSurface(CavePosition position) {
                return false;
            }

            @Override
            public boolean isAboveTerrainSurface(CavePosition position) {
                return false;
            }
        };

        HydrologyCavePlan plan = planner.validate(generatedView, candidate);

        assertTrue(plan.rejection().toString(), plan.accepted());
        assertEquals(
                new CaveVoxelPrecondition(CaveVoxel.UNCONDITIONAL, true),
                plan.baselinePreconditions().get(opening)
        );
        assertTrue(plan.actions().values().stream()
                .noneMatch((HydrologyCaveAction action) -> action == HydrologyCaveAction.SEAL_GUARD));
    }

    @Test
    public void plannedDryHeadroomRetainsConfiguredClosedCaveConnection() {
        CavePosition dryHeadroom = position(0, 11, 0);
        CavePosition closedCaveContact = position(1, 11, 0);
        TestVoxelView view = new TestVoxelView();
        view.set(CaveVoxel.CAVE_AIR, closedCaveContact);
        HydrologyCavePlan plan = planner.validate(
                view,
                candidate(
                        204L,
                        10,
                        "water",
                        Map.of(dryHeadroom, HydrologyCaveAction.DRY_AIR),
                        detailedSettings(
                                2,
                                2,
                                HydrologyCaveGrottoShape.ELLIPSOID,
                                HydrologyCaveFluidPolicy.ALLOW_COMPATIBLE
                        )
                )
        );

        assertTrue(plan.rejection().toString(), plan.accepted());
        assertFalse(plan.actions().containsKey(closedCaveContact));
        assertEquals(
                new CaveVoxelPrecondition(CaveVoxel.CAVE_AIR, false),
                plan.baselinePreconditions().get(closedCaveContact)
        );
    }

    @Test
    public void plannedVolumeOverlapArbitrationIsDeterministicAndTransactional() {
        CavePosition center = position(0, 10, 0);
        HydrologyCaveCandidate lower = candidate(
                220L,
                10,
                "water",
                Map.of(center, HydrologyCaveAction.WET_SOURCE),
                settings()
        );
        HydrologyCaveCandidate higher = candidate(
                210L,
                11,
                "lava",
                Map.of(center, HydrologyCaveAction.WET_SOURCE),
                settings()
        );
        TestVoxelView view = new TestVoxelView();

        HydrologyCavePlanningResult forward = planner.validateAll(view, List.of(lower, higher));
        HydrologyCavePlanningResult reverse = planner.validateAll(view, List.of(higher, lower));
        List<HydrologyCavePlan> plansOnlyForward = planner.validateAllPlans(view, List.of(lower, higher));
        List<HydrologyCavePlan> plansOnlyReverse = planner.validateAllPlans(view, List.of(higher, lower));

        assertEquals(forward, reverse);
        assertEquals(forward.plans(), plansOnlyForward);
        assertEquals(forward.plans(), plansOnlyReverse);
        assertTrue(forward.plans().get(0).accepted());
        assertEquals(HydrologyCaveRejection.OVERLAPPING_SOURCE, forward.plans().get(1).rejection());
        assertEquals(210L, forward.plans().get(1).arbitrationWinnerSourceId().getAsLong());
        assertEquals(HydrologyCaveAction.WET_SOURCE, forward.actions().get(center));
    }

    @Test
    public void plannedGuardAndMutationOverlapArbitrationIsDeterministic() {
        CavePosition firstPosition = position(0, 10, 0);
        CavePosition secondPosition = position(1, 10, 0);
        HydrologyCaveCandidate first = candidate(
                230L,
                12,
                "water",
                Map.of(firstPosition, HydrologyCaveAction.WET_SOURCE),
                settings()
        );
        HydrologyCaveCandidate second = candidate(
                240L,
                11,
                "water",
                Map.of(secondPosition, HydrologyCaveAction.WET_SOURCE),
                settings()
        );
        TestVoxelView view = new TestVoxelView();

        HydrologyCavePlanningResult forward = planner.validateAll(view, List.of(first, second));
        HydrologyCavePlanningResult reverse = planner.validateAll(view, List.of(second, first));
        List<HydrologyCavePlan> plansOnlyForward = planner.validateAllPlans(view, List.of(first, second));
        List<HydrologyCavePlan> plansOnlyReverse = planner.validateAllPlans(view, List.of(second, first));

        assertEquals(forward, reverse);
        assertEquals(forward.plans(), plansOnlyForward);
        assertEquals(forward.plans(), plansOnlyReverse);
        assertTrue(forward.plans().get(0).accepted());
        assertEquals(HydrologyCaveRejection.OVERLAPPING_SOURCE, forward.plans().get(1).rejection());
        assertEquals(230L, forward.plans().get(1).arbitrationWinnerSourceId().orElseThrow());
        assertEquals(HydrologyCaveAction.SEAL_GUARD, forward.actions().get(secondPosition));
    }

    private void assertMutationConnected(HydrologyCavePlan plan, CavePosition start, CavePosition expected) {
        Set<CavePosition> mutations = new HashSet<>();
        for (Map.Entry<CavePosition, HydrologyCaveAction> entry : plan.actions().entrySet()) {
            if (entry.getValue() != HydrologyCaveAction.SEAL_GUARD) {
                mutations.add(entry.getKey());
            }
        }
        Queue<CavePosition> queue = new ArrayDeque<>();
        Set<CavePosition> visited = new HashSet<>();
        queue.add(start);
        visited.add(start);
        while (!queue.isEmpty()) {
            CavePosition position = queue.remove();
            for (CavePosition direction : DIRECTIONS) {
                CavePosition neighbor = position.offset(direction.x(), direction.y(), direction.z());
                if (mutations.contains(neighbor) && visited.add(neighbor)) {
                    queue.add(neighbor);
                }
            }
        }
        assertTrue(visited.contains(expected));
        assertEquals(mutations, visited);
    }

    private int mutationCount(HydrologyCavePlan plan) {
        int count = 0;
        for (HydrologyCaveAction action : plan.actions().values()) {
            if (action != HydrologyCaveAction.SEAL_GUARD) {
                count++;
            }
        }
        return count;
    }

    private void assertRejectedWithoutPublication(HydrologyCavePlan plan, HydrologyCaveRejection rejection) {
        assertFalse(plan.accepted());
        assertEquals(rejection, plan.rejection());
        assertTrue(plan.actions().isEmpty());
        assertTrue(plan.baselinePreconditions().isEmpty());
    }

    private HydrologyCaveSource source(long sourceId, int waterHeadY, HydrologyCaveMode mode, CavePosition target) {
        return new HydrologyCaveSource(sourceId, position(0, 12, 0), target, waterHeadY, mode);
    }

    private HydrologyCaveCandidate candidate(
            long sourceId,
            int waterHeadY,
            String profileKey,
            Map<CavePosition, HydrologyCaveAction> actions,
            HydrologyCavePlannerSettings settings
    ) {
        CavePosition sourcePosition = position(0, waterHeadY, 0);
        return new HydrologyCaveCandidate(
                new HydrologyCaveSource(
                        sourceId,
                        sourcePosition,
                        sourcePosition,
                        waterHeadY,
                        HydrologyCaveMode.GENERATED_GROTTO
                ),
                profileKey,
                settings,
                true,
                actions,
                Set.of()
        );
    }

    private HydrologyCavePlannerSettings settings() {
        return settings(HydrologyCaveFluidPolicy.ALLOW_COMPATIBLE);
    }

    private HydrologyCavePlannerSettings settings(HydrologyCaveFluidPolicy policy) {
        return new HydrologyCavePlannerSettings(12, 20, 64, 32, 2, 2, policy);
    }

    private HydrologyCavePlannerSettings detailedSettings(
            int throatRadius,
            int dryHeadroom,
            HydrologyCaveGrottoShape shape,
            HydrologyCaveFluidPolicy policy
    ) {
        return new HydrologyCavePlannerSettings(
                12,
                20,
                2048,
                32,
                throatRadius,
                4,
                4,
                dryHeadroom,
                policy,
                shape
        );
    }

    private CavePosition position(int x, int y, int z) {
        return new CavePosition(x, y, z);
    }

    private HydrologyObservedPlannedSurface observedSurface(int resolvedHeight, boolean terrainOwned) {
        HydrologyCaveVoxelViewFactory.PlannedSurface surface = new HydrologyCaveVoxelViewFactory.PlannedSurface() {
            @Override
            public int resolve(int x, int z, int naturalHeight) {
                return resolvedHeight;
            }

            @Override
            public boolean ownsTerrain(int x, int z) {
                return terrainOwned;
            }
        };
        return new HydrologyObservedPlannedSurface(surface);
    }

    private CaveVoxelView plannedSurfaceView(HydrologyObservedPlannedSurface surface) {
        return new CaveVoxelView() {
            @Override
            public boolean isInWorld(CavePosition position) {
                return true;
            }

            @Override
            public CaveVoxel voxelAt(CavePosition position) {
                return CaveVoxel.SOLID;
            }

            @Override
            public boolean isOpenToSurface(CavePosition position) {
                return isAboveTerrainSurface(position);
            }

            @Override
            public boolean isAboveTerrainSurface(CavePosition position) {
                return !surface.ownsTerrain(position.x(), position.z())
                        || position.y() > surface.resolve(position.x(), position.z(), 32);
            }
        };
    }

    private static final class TestVoxelView implements CaveVoxelView {
        private final int minX;
        private final int maxX;
        private final int minY;
        private final int maxY;
        private final int minZ;
        private final int maxZ;
        private final Map<CavePosition, CaveVoxel> voxels = new HashMap<>();
        private final Set<CavePosition> surfaceOpenings = new HashSet<>();
        private final Set<CavePosition> aboveTerrain = new HashSet<>();

        private TestVoxelView() {
            this(-32, 32, 0, 32, -32, 32);
        }

        private TestVoxelView(int minX, int maxX, int minY, int maxY, int minZ, int maxZ) {
            this.minX = minX;
            this.maxX = maxX;
            this.minY = minY;
            this.maxY = maxY;
            this.minZ = minZ;
            this.maxZ = maxZ;
        }

        @Override
        public boolean isInWorld(CavePosition position) {
            return position.x() >= minX
                    && position.x() <= maxX
                    && position.y() >= minY
                    && position.y() <= maxY
                    && position.z() >= minZ
                    && position.z() <= maxZ;
        }

        @Override
        public CaveVoxel voxelAt(CavePosition position) {
            return voxels.getOrDefault(position, CaveVoxel.SOLID);
        }

        @Override
        public boolean isOpenToSurface(CavePosition position) {
            return surfaceOpenings.contains(position);
        }

        @Override
        public boolean isAboveTerrainSurface(CavePosition position) {
            return aboveTerrain.contains(position);
        }

        private void set(CaveVoxel voxel, CavePosition... positions) {
            for (CavePosition position : positions) {
                voxels.put(position, voxel);
            }
        }

        private void openToSurface(CavePosition position) {
            surfaceOpenings.add(position);
        }

        private void aboveTerrain(CavePosition position) {
            aboveTerrain.add(position);
        }
    }
}
