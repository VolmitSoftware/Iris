package art.arcane.iris.engine.hydrology;

import art.arcane.iris.engine.hydrology.surface.SurfaceFootprint;
import art.arcane.iris.engine.hydrology.surface.SurfaceLayerColumn;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.Iterator;
import java.util.List;

final class HydrologySurfaceProfiles {
    private HydrologySurfaceProfiles() {
    }

    static boolean sharesProfile(HydrologyTerrainSample first, HydrologyTerrainSample second) {
        if (first == null || second == null) {
            return false;
        }
        for (String profile : first.preferredProfileKeys()) {
            if (second.preferredProfileKeys().contains(profile)) {
                return true;
            }
        }
        return false;
    }

    static String chooseProfile(
            HydrologyPlanner planner,
            HydrologyCoursePath path,
            HydrologyTerrainSample source
    ) {
        if (path.points().isEmpty()) {
            return null;
        }
        HydrologyPoint anchor = path.points().getFirst();
        HydrologyTerrainSample terrain = planner.sampleBasisWithoutSlope(anchor.x(), anchor.z());
        if (terrain == null) {
            return null;
        }
        ArrayList<String> profiles = new ArrayList<>(terrain.preferredProfileKeys());
        profiles.retainAll(source.preferredProfileKeys());
        if (!retainPathProfiles(planner, path.points(), profiles)
                || !retainOutletProfiles(planner, path, profiles)) {
            return null;
        }
        int index = HydrologyHash.between(
                HydrologyHash.mix(planner.worldSeed, path.outlet().id(), HydrologyHash.text("profile")),
                0,
                profiles.size() - 1
        );
        return profiles.get(index);
    }

    static boolean allowsProfile(HydrologyPlanner planner, HydrologyCoursePath path, String profile) {
        ArrayList<String> profiles = new ArrayList<>(List.of(profile));
        return retainPathProfiles(planner, path.points(), profiles)
                && retainOutletProfiles(planner, path, profiles);
    }

    static boolean allowsProfile(HydrologyPlanner planner, List<HydrologyPoint> points, String profile) {
        return retainPathProfiles(planner, points, new ArrayList<>(List.of(profile)));
    }

    static void rejectExcludedWetFootprints(
            HydrologyFootprintCompiler compiler,
            List<RiverCourse> courses,
            List<HydrologyDiagnosticCandidate> diagnostics
    ) {
        HashSet<Long> rejectedOutlets = new HashSet<>();
        Iterator<RiverCourse> iterator = courses.iterator();
        while (iterator.hasNext()) {
            RiverCourse course = iterator.next();
            if (course.type() != RiverCourseType.SURFACE) {
                continue;
            }
            SurfaceFootprint footprint = compiler.surfaceFootprint(course);
            if (!footprint.accepted()) {
                iterator.remove();
                rejectedOutlets.add(course.outletId().orElseThrow());
                diagnostics.add(new HydrologyDiagnosticCandidate(
                        HydrologyHash.mix(course.id(), HydrologySourcePlanner.DIAGNOSTIC_SALT,
                                footprint.rejection().ordinal()),
                        HydrologyCandidateKind.SOURCE,
                        HydrologyFeatureType.SURFACE_POOL,
                        course.segments().getFirst().start(),
                        footprint.rejection(),
                        footprint.rejectionDetail()
                ));
                continue;
            }
            int excluded = 0;
            for (SurfaceLayerColumn column : footprint.columns()) {
                HydrologyColumnLayer layer = column.layer();
                if (layer.fluidOwned() && layer.bedY() < layer.fluidHeadY()
                        && !column.terrain().preferredProfileKeys().contains(course.profileKey())) {
                    excluded++;
                }
            }
            if (excluded == 0) {
                continue;
            }
            iterator.remove();
            HydrologyFeatureType terminal = course.segments().getLast().type();
            if (terminal == HydrologyFeatureType.MOUTH || terminal == HydrologyFeatureType.COASTAL_GROTTO
                    || terminal == HydrologyFeatureType.INLAND_GROTTO) {
                rejectedOutlets.add(course.outletId().orElseThrow());
            }
            diagnostics.add(new HydrologyDiagnosticCandidate(
                    HydrologyHash.mix(course.id(), HydrologySourcePlanner.DIAGNOSTIC_SALT,
                            HydrologyCandidateRejection.POLICY_EXCLUDED.ordinal()),
                    HydrologyCandidateKind.SOURCE,
                    HydrologyFeatureType.SURFACE_POOL,
                    course.segments().getFirst().start(),
                    HydrologyCandidateRejection.POLICY_EXCLUDED,
                    excluded
            ));
        }
        if (rejectedOutlets.isEmpty()) {
            return;
        }
        iterator = courses.iterator();
        while (iterator.hasNext()) {
            RiverCourse course = iterator.next();
            if (course.type() != RiverCourseType.SURFACE
                    || !rejectedOutlets.contains(course.outletId().orElseThrow())) {
                continue;
            }
            iterator.remove();
            diagnostics.add(new HydrologyDiagnosticCandidate(
                    HydrologyHash.mix(course.id(), HydrologySourcePlanner.DIAGNOSTIC_SALT,
                            HydrologyCandidateRejection.NO_DRAINAGE_PATH.ordinal()),
                    HydrologyCandidateKind.SOURCE,
                    HydrologyFeatureType.SURFACE_POOL,
                    course.segments().getFirst().start(),
                    HydrologyCandidateRejection.NO_DRAINAGE_PATH,
                    0
            ));
        }
    }

    private static boolean retainPathProfiles(
            HydrologyPlanner planner,
            List<HydrologyPoint> points,
            ArrayList<String> profiles
    ) {
        if (points.isEmpty() || !retainPointProfiles(planner, points.getFirst(), profiles)) {
            return false;
        }
        for (int pointIndex = 1; pointIndex < points.size(); pointIndex++) {
            for (HydrologyPoint point : planner.segments.rasterLine(points.get(pointIndex - 1), points.get(pointIndex))) {
                if (!retainPointProfiles(planner, point, profiles)) {
                    return false;
                }
            }
        }
        return true;
    }

    private static boolean retainOutletProfiles(
            HydrologyPlanner planner,
            HydrologyCoursePath path,
            ArrayList<String> profiles
    ) {
        if (!path.reachesOutlet()) {
            return true;
        }
        RiverOutlet outlet = path.outlet();
        return retainPointProfiles(planner, outlet.landwardPoint(), profiles)
                && (!outlet.directOcean() || retainPointProfiles(planner, outlet.connectionPoint(), profiles));
    }

    private static boolean retainPointProfiles(
            HydrologyPlanner planner,
            HydrologyPoint point,
            ArrayList<String> profiles
    ) {
        HydrologyTerrainSample terrain = planner.sampleBasisWithoutSlope(point.x(), point.z());
        if (terrain == null) {
            return false;
        }
        profiles.retainAll(terrain.preferredProfileKeys());
        return !profiles.isEmpty();
    }
}
