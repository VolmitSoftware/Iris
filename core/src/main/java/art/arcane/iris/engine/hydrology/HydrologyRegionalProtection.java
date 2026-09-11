package art.arcane.iris.engine.hydrology;

import art.arcane.iris.engine.hydrology.cave.CavePosition;
import art.arcane.iris.engine.hydrology.cave.HydrologyCaveAction;
import art.arcane.iris.engine.hydrology.cave.HydrologyCaveConflictPolicy;
import art.arcane.iris.engine.hydrology.cave.HydrologyCavePlan;

import java.util.HashMap;
import java.util.HashSet;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Set;

final class HydrologyRegionalProtection {
    private HydrologyRegionalProtection() {
    }

    static void rejectFootprints(List<RiverCourse> courses, HydrologyFootprintCompiler compiler,
                                 List<HydrologyDiagnosticCandidate> diagnostics) {
        HydrologyRegionalNetwork regional = compiler.regionalNetwork;
        if (regional.cavePlans().isEmpty()) {
            return;
        }
        Map<Long, String> profiles = profiles(regional);
        Iterator<RiverCourse> iterator = courses.iterator();
        while (iterator.hasNext()) {
            RiverCourse course = iterator.next();
            RiverFootprint footprint = compiler.compileCourse(course);
            for (HydrologyCavePlan plan : regional.cavePlans()) {
                if (changesWitness(course.profileKey(), footprint, profiles.get(plan.source().sourceId()), plan)) {
                    diagnostics.add(diagnostic(course, plan.source().sourceId()));
                    iterator.remove();
                    break;
                }
            }
        }
    }

    static HydrologyCaveCourseFilter.Result rejectPlans(HydrologyCaveCourseFilter.Result result,
                                                         HydrologyRegionalNetwork regional,
                                                         List<HydrologyDiagnosticCandidate> diagnostics) {
        if (regional.cavePlans().isEmpty() || result.cavePlans().isEmpty()) {
            return result;
        }
        Map<Long, String> profiles = profiles(regional);
        Map<Long, RiverCourse> courses = new HashMap<>();
        for (RiverCourse course : result.courses()) {
            courses.put(course.id(), course);
        }
        Set<Long> rejected = new HashSet<>();
        for (HydrologyCavePlan local : result.cavePlans()) {
            RiverCourse course = courses.get(local.source().sourceId());
            for (HydrologyCavePlan fixed : regional.cavePlans()) {
                String regionalProfile = profiles.get(fixed.source().sourceId());
                if (HydrologyCaveConflictPolicy.hasIncompatibleOverlap(course.profileKey(), local.actions(),
                        regionalProfile, fixed.actions()) || changesWitness(course.profileKey(), local, regionalProfile, fixed)) {
                    rejected.add(course.id());
                    diagnostics.add(diagnostic(course, fixed.source().sourceId()));
                    break;
                }
            }
        }
        return HydrologyCaveCourseFilter.withoutCourses(result, rejected);
    }

    static boolean changesWitness(String profile, RiverFootprint footprint, String regionalProfile, HydrologyCavePlan plan) {
        for (HydrologyColumnSample column : footprint.columns().values()) {
            if (!plan.allPreconditionsIn(column.x(), column.z(), Math.addExact(column.x(), 1), Math.addExact(column.z(), 1),
                    (position, precondition) -> preservesColumn(profile, column, regionalProfile, plan, position))) {
                return true;
            }
        }
        return false;
    }

    private static boolean preservesColumn(String profile, HydrologyColumnSample column, String regionalProfile,
                                           HydrologyCavePlan plan, CavePosition position) {
        for (HydrologyColumnLayer layer : column.layers()) {
            HydrologyCaveAction action = actionAt(column, layer, position.y());
            if (action != null && (!profile.equals(regionalProfile) || plan.actions().get(position) != action)) {
                return false;
            }
        }
        return true;
    }

    private static HydrologyCaveAction actionAt(HydrologyColumnSample column, HydrologyColumnLayer layer, int y) {
        if (y <= layer.bedY() || layer.oceanApron()) {
            return null;
        }
        if (layer.channel() && layer.fluidOwned() && (layer.terrainOwned() || layer.fallingFluid())
                && y <= layer.fluidHeadY()) {
            return layer.fallingFluid() && y < layer.fluidHeadY()
                    ? HydrologyCaveAction.FALLING_FLUID : HydrologyCaveAction.WET_SOURCE;
        }
        if (!layer.terrainOwned()) {
            return null;
        }
        int ceiling = layer.feature().type().isSurface() ? column.naturalHeight() : layer.ceilingY();
        return y <= ceiling ? HydrologyCaveAction.DRY_AIR : null;
    }

    private static boolean changesWitness(String profile, HydrologyCavePlan local, String regionalProfile, HydrologyCavePlan regional) {
        for (Map.Entry<CavePosition, HydrologyCaveAction> entry : local.actions().entrySet()) {
            if (entry.getValue() != HydrologyCaveAction.SEAL_GUARD && regional.baselinePreconditions().containsKey(entry.getKey())
                    && (!profile.equals(regionalProfile) || regional.actions().get(entry.getKey()) != entry.getValue())) {
                return true;
            }
        }
        return false;
    }

    private static Map<Long, String> profiles(HydrologyRegionalNetwork regional) {
        Map<Long, String> profiles = new HashMap<>();
        for (RiverCourse course : regional.courses()) {
            profiles.put(course.id(), course.profileKey());
        }
        return profiles;
    }

    private static HydrologyDiagnosticCandidate diagnostic(RiverCourse course, long regionalId) {
        HydraulicSegment segment = course.segments().getFirst();
        return new HydrologyDiagnosticCandidate(HydrologyHash.mix(course.id(), regionalId, 0x52454750524f5445L),
                HydrologyCandidateKind.SOURCE, segment.type(), segment.start(), HydrologyCandidateRejection.CAVE_CONTAINMENT, 0);
    }
}
