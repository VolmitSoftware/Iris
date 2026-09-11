package art.arcane.iris.engine.hydrology;

import art.arcane.iris.util.project.noise.SimplexNoise;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

final class HydrologyRegionalRoute {
    private static final double SAMPLE_SPACING = 4D;
    private static final double SIMPLEX_COORDINATE_SCALE = 100D;
    private static final int MAXIMUM_RECEIVER_SAMPLES = 65536;

    private final HydrologyPlanner planner;
    private final HydrologyRegionalTerrainRefiner terrainRefiner;
    private final HydrologyRegionalHydraulics hydraulics;
    private final SimplexNoise bends;
    private final SimplexNoise details;

    HydrologyRegionalRoute(HydrologyPlanner planner) {
        this.planner = planner;
        this.terrainRefiner = new HydrologyRegionalTerrainRefiner(planner);
        this.hydraulics = new HydrologyRegionalHydraulics(planner.settings);
        this.bends = new SimplexNoise(HydrologyHash.mix(planner.worldSeed, 0x52454742454e4453L));
        this.details = new SimplexNoise(HydrologyHash.mix(planner.worldSeed, 0x5245474445544149L));
    }

    void clear() {
        terrainRefiner.clear();
    }

    Attempt select(List<HydrologyPoint> guide, String profile, boolean coastal, HydrologyTerrainSampler receiver,
                   CandidateAdmission admission) {
        HydrologyRegionalMorphology morphology = HydrologyRegionalMorphology.sample(guide, terrainRefiner::sample, planner.settings);
        Refinement topology = validateCorridor(guide, profile, coastal);
        OceanEntry resolved = topology.rejection() == null ? topology.oceanEntry() : null;
        if (resolved != null) {
            guide = topology.points();
            receiver = HydrologyOceanReceiver.forConnection(planner.settings, terrainRefiner::sample,
                    resolved.landward(), resolved.receiving());
        }
        CandidateSelection selection = new CandidateSelection(admission, resolved);
        List<HydrologyPoint> repaired = terrainRefiner.refine(guide, profile, coastal, receiver);
        if (!repaired.isEmpty() && !repaired.equals(guide)) {
            Attempt accepted = selectCurve(repaired, profile, coastal, receiver, selection, morphology);
            if (accepted != null) {
                return accepted;
            }
        }
        Attempt accepted = selectCurve(guide, profile, coastal, receiver, selection, morphology);
        return accepted == null ? selection.failure : accepted;
    }

    private Refinement retainReceivingEntry(Refinement refined, OceanEntry resolved) {
        if (resolved == null || refined.rejection() != null || refined.oceanEntry() != null) {
            return refined;
        }
        if (refined.points().getLast().distanceSquared2D(resolved.landward()) > 0L) {
            return new Refinement(List.of(), refined.points().getLast(),
                    HydrologyCandidateRejection.SURFACE_MOUTH_DISCONNECTED, 0, null);
        }
        double length = 0D;
        for (int index = 1; index < refined.points().size(); index++) {
            length += StrictMath.sqrt(refined.points().get(index - 1).distanceSquared2D(refined.points().get(index)));
        }
        if (length < planner.settings.routing().regional().minimumLength()) {
            return new Refinement(List.of(), refined.points().getLast(),
                    HydrologyCandidateRejection.COURSE_TOO_SHORT, (int) StrictMath.floor(length), null);
        }
        return new Refinement(refined.points(), null, null, 0, resolved);
    }

    private Attempt selectCurve(List<HydrologyPoint> guide, String profile, boolean coastal,
                                HydrologyTerrainSampler receiver, CandidateSelection selection, HydrologyRegionalMorphology morphology) {
        for (double strength : new double[]{1D, 0.5D, 0D}) {
            List<HydrologyPoint> points = smooth(interpolate(guide, strength, morphology));
            Attempt accepted = selection.consider(validate(points, profile, coastal, receiver));
            if (accepted != null) {
                return accepted;
            }
        }
        List<HydrologyPoint> linear = interpolateLinear(guide);
        Refinement constrained = validate(linear, profile, coastal, receiver);
        Attempt accepted = selection.consider(constrained);
        if (accepted != null) {
            return accepted;
        }
        if (constrained.rejection() == HydrologyCandidateRejection.SURFACE_SHAPE_UNSUPPORTED) {
            for (double radius : new double[]{16D, 32D, 64D, 128D, 256D}) {
                accepted = selection.consider(validateRounded(resample(roundBends(linear, radius)), profile, coastal, receiver));
                if (accepted != null) {
                    return accepted;
                }
            }
        }
        return null;
    }

    private List<HydrologyPoint> interpolateLinear(List<HydrologyPoint> guide) {
        ArrayList<HydrologyPoint> points = new ArrayList<>();
        for (int index = 0; index + 1 < guide.size(); index++) {
            HydrologyPoint start = guide.get(index);
            HydrologyPoint end = guide.get(index + 1);
            int steps = Math.max(1, (int) StrictMath.ceil(StrictMath.sqrt(start.distanceSquared2D(end)) / SAMPLE_SPACING));
            for (int step = index == 0 ? 0 : 1; step <= steps; step++) {
                double progress = step / (double) steps;
                HydrologyPoint point = new HydrologyPoint(
                        (int) StrictMath.round(start.x() + (end.x() - start.x()) * progress),
                        (int) StrictMath.round(start.y() + (end.y() - start.y()) * progress),
                        (int) StrictMath.round(start.z() + (end.z() - start.z()) * progress));
                if (points.isEmpty() || points.getLast().distanceSquared2D(point) > 0L) {
                    points.add(point);
                }
            }
        }
        return List.copyOf(points);
    }

    private List<CurvePoint> roundBends(List<HydrologyPoint> points, double radius) {
        double[] stations = new double[points.size()];
        for (int index = 1; index < points.size(); index++) {
            stations[index] = stations[index - 1] + StrictMath.sqrt(points.get(index - 1).distanceSquared2D(points.get(index)));
        }
        double length = stations[stations.length - 1];
        radius = Math.min(radius, length / 2D);
        HydrologyPoint start = points.getFirst();
        HydrologyPoint end = points.getLast();
        ArrayList<CurvePoint> rounded = new ArrayList<>(points.size());
        rounded.add(CurvePoint.of(points.getFirst()));
        int first = 0;
        for (int index = 1; index + 1 < points.size(); index++) {
            while (stations[first] < stations[index] - radius) {
                first++;
            }
            double weightedX = 0D;
            double weightedZ = 0D;
            double totalWeight = 0D;
            for (int sample = first; sample < points.size() && stations[sample] < stations[index] + radius; sample++) {
                double weight = 1D - StrictMath.abs(stations[sample] - stations[index]) / radius;
                weightedX += points.get(sample).x() * weight;
                weightedZ += points.get(sample).z() * weight;
                totalWeight += weight;
            }
            for (int sample = 1; sample < points.size() && stations[sample] + stations[index] < radius; sample++) {
                double weight = 1D - (stations[sample] + stations[index]) / radius;
                weightedX += (2D * start.x() - points.get(sample).x()) * weight;
                weightedZ += (2D * start.z() - points.get(sample).z()) * weight;
                totalWeight += weight;
            }
            for (int sample = points.size() - 2; sample >= 0 && 2D * length - stations[sample] - stations[index] < radius; sample--) {
                double weight = 1D - (2D * length - stations[sample] - stations[index]) / radius;
                weightedX += (2D * end.x() - points.get(sample).x()) * weight;
                weightedZ += (2D * end.z() - points.get(sample).z()) * weight;
                totalWeight += weight;
            }
            HydrologyPoint point = points.get(index);
            CurvePoint smoothed = new CurvePoint(weightedX / totalWeight, point.y(), weightedZ / totalWeight);
            if (rounded.getLast().distance(smoothed) > 0D) {
                rounded.add(smoothed);
            }
        }
        if (rounded.getLast().distance(CurvePoint.of(points.getLast())) > 0D) {
            rounded.add(CurvePoint.of(points.getLast()));
        }
        return List.copyOf(rounded);
    }

    private List<CurvePoint> resample(List<CurvePoint> points) {
        ArrayList<CurvePoint> sampled = new ArrayList<>(points.size());
        sampled.add(points.getFirst());
        double nextSample = SAMPLE_SPACING;
        for (int index = 1; index < points.size(); index++) {
            CurvePoint first = points.get(index - 1);
            CurvePoint second = points.get(index);
            double length = first.distance(second);
            if (length == 0D) {
                continue;
            }
            while (nextSample <= length) {
                double progress = nextSample / length;
                CurvePoint point = new CurvePoint(first.x() + (second.x() - first.x()) * progress,
                        first.y() + (second.y() - first.y()) * progress,
                        first.z() + (second.z() - first.z()) * progress);
                if (sampled.getLast().distance(point) > 0D) {
                    sampled.add(point);
                }
                nextSample += SAMPLE_SPACING;
            }
            nextSample -= length;
        }
        if (sampled.getLast().distance(points.getLast()) > 0D) {
            sampled.add(points.getLast());
        }
        return List.copyOf(sampled);
    }

    private List<HydrologyPoint> interpolate(List<HydrologyPoint> guide, double strength, HydrologyRegionalMorphology morphology) {
        ArrayList<HydrologyPoint> points = new ArrayList<>();
        double spacing = planner.settings.routing().regional().sampleSpacing();
        HydrologyPlannerSettings.Meanders meanders = planner.settings.geometry().meanders();
        double scale = spacing / planner.settings.routing().sampleSpacing();
        double primaryWavelength = meanders.primaryWavelength() * scale * 5D;
        double detailWavelength = meanders.detailWavelength() * scale * 8D;
        for (int index = 0; index + 1 < guide.size(); index++) {
            HydrologyPoint before = guide.get(Math.max(0, index - 1));
            HydrologyPoint start = guide.get(index);
            HydrologyPoint end = guide.get(index + 1);
            HydrologyPoint after = guide.get(Math.min(guide.size() - 1, index + 2));
            int steps = Math.max(1, (int) StrictMath.ceil(StrictMath.sqrt(start.distanceSquared2D(end)) / SAMPLE_SPACING));
            for (int step = index == 0 ? 0 : 1; step <= steps; step++) {
                double progress = step / (double) steps;
                double x = spline(before.x(), start.x(), end.x(), after.x(), progress);
                double z = spline(before.z(), start.z(), end.z(), after.z(), progress);
                double tangentX = end.x() - before.x() + (after.x() - start.x() - end.x() + before.x()) * progress;
                double tangentZ = end.z() - before.z() + (after.z() - start.z() - end.z() + before.z()) * progress;
                double tangentLength = Math.max(1D, StrictMath.hypot(tangentX, tangentZ));
                double endpointDistance = Math.min(index + progress, guide.size() - 1D - index - progress);
                double envelope = Math.min(1D, endpointDistance / 2D);
                double height = start.y() + (end.y() - start.y()) * progress;
                double primary = bends.noiseSigned(x * SIMPLEX_COORDINATE_SCALE / primaryWavelength,
                        height * SIMPLEX_COORDINATE_SCALE / 96D, z * SIMPLEX_COORDINATE_SCALE / primaryWavelength);
                double detail = details.noiseSigned(x * SIMPLEX_COORDINATE_SCALE / detailWavelength,
                        height * SIMPLEX_COORDINATE_SCALE / 48D, z * SIMPLEX_COORDINATE_SCALE / detailWavelength);
                double freedom = morphology.freedomAt(x, z);
                double broad = primary * meanders.primaryStrength() + detail * meanders.detailStrength() * 0.35D;
                double tight = detail * (meanders.primaryStrength() + meanders.detailStrength() * 0.35D);
                double signal = broad * freedom + tight * (1D - freedom);
                double displacement = Math.max(-1D, Math.min(1D, signal * 2D))
                        * spacing * meanders.maximumOffsetRatio() * envelope * strength * (0.2D + 0.8D * freedom);
                int pointX = (int) StrictMath.round(x - tangentZ / tangentLength * displacement);
                int pointZ = (int) StrictMath.round(z + tangentX / tangentLength * displacement);
                if (points.isEmpty() || points.getLast().x() != pointX || points.getLast().z() != pointZ) {
                    points.add(new HydrologyPoint(pointX, (int) StrictMath.round(height), pointZ));
                }
            }
        }
        return List.copyOf(points);
    }

    private List<HydrologyPoint> smooth(List<HydrologyPoint> points) {
        for (int pass = 0; pass < planner.settings.geometry().meanders().smoothingPasses(); pass++) {
            ArrayList<HydrologyPoint> result = new ArrayList<>(points.size());
            result.add(points.getFirst());
            for (int index = 1; index + 1 < points.size(); index++) {
                HydrologyPoint previous = points.get(index - 1);
                HydrologyPoint point = points.get(index);
                HydrologyPoint next = points.get(index + 1);
                HydrologyPoint smoothed = new HydrologyPoint(
                        (int) StrictMath.round((previous.x() + point.x() * 2D + next.x()) / 4D), point.y(),
                        (int) StrictMath.round((previous.z() + point.z() * 2D + next.z()) / 4D));
                if (smoothed.distanceSquared2D(result.getLast()) > 0L) {
                    result.add(smoothed);
                }
            }
            if (points.getLast().distanceSquared2D(result.getLast()) > 0L) {
                result.add(points.getLast());
            }
            points = result;
        }
        return List.copyOf(points);
    }

    private Refinement validate(List<HydrologyPoint> points, String profile, boolean coastal, HydrologyTerrainSampler receiver) {
        if (points.size() < 2) {
            return new Refinement(List.of(), points.isEmpty() ? new HydrologyPoint(0, 0, 0) : points.getFirst(),
                    HydrologyCandidateRejection.COURSE_TOO_SHORT, points.size(), null);
        }
        Refinement corridor = validateHydraulics(validateCorridor(points, profile, coastal), profile, coastal, receiver);
        if (corridor.rejection() != null) {
            return corridor;
        }
        points = corridor.points();
        for (int index = 1; index + 1 < points.size(); index++) {
            double turn = turn(points, index);
            if (turn > planner.settings.geometry().meanders().maximumTurnDegrees()) {
                return new Refinement(List.of(), points.get(index), HydrologyCandidateRejection.SURFACE_SHAPE_UNSUPPORTED,
                        (int) StrictMath.ceil(turn), null);
            }
        }
        return corridor;
    }

    private Refinement validateRounded(List<CurvePoint> curve, String profile, boolean coastal, HydrologyTerrainSampler receiver) {
        ArrayList<HydrologyPoint> points = new ArrayList<>(curve.size());
        ArrayList<CurvePoint> distinct = new ArrayList<>(curve.size());
        for (CurvePoint point : curve) {
            HydrologyPoint rounded = point.rounded();
            if (points.isEmpty() || points.getLast().distanceSquared2D(rounded) > 0L) {
                points.add(rounded);
                distinct.add(point);
            }
        }
        if (points.size() < 2) {
            return new Refinement(List.of(), curve.getFirst().rounded(), HydrologyCandidateRejection.COURSE_TOO_SHORT, points.size(), null);
        }
        Refinement corridor = validateHydraulics(validateCorridor(points, profile, coastal), profile, coastal, receiver);
        if (corridor.rejection() != null) {
            return corridor;
        }
        if (corridor.oceanEntry() != null) {
            int last = corridor.points().size() - 1;
            HydrologyPoint end = corridor.points().getLast();
            if (points.get(last).distanceSquared2D(end) == 0L) {
                distinct = new ArrayList<>(distinct.subList(0, last + 1));
            } else {
                CurvePoint first = distinct.get(last - 1);
                CurvePoint second = distinct.get(last);
                double progress = StrictMath.sqrt(points.get(last - 1).distanceSquared2D(end)
                        / (double) points.get(last - 1).distanceSquared2D(points.get(last)));
                distinct = new ArrayList<>(distinct.subList(0, last));
                distinct.add(new CurvePoint(first.x() + (second.x() - first.x()) * progress,
                        first.y() + (second.y() - first.y()) * progress,
                        first.z() + (second.z() - first.z()) * progress));
            }
        }
        for (int index = 1; index + 1 < distinct.size(); index++) {
            CurvePoint previous = distinct.get(Math.max(0, index - 4));
            CurvePoint point = distinct.get(index);
            CurvePoint next = distinct.get(Math.min(distinct.size() - 1, index + 4));
            double firstX = point.x() - previous.x();
            double firstZ = point.z() - previous.z();
            double nextX = next.x() - point.x();
            double nextZ = next.z() - point.z();
            double turn = StrictMath.toDegrees(StrictMath.abs(StrictMath.atan2(firstX * nextZ - firstZ * nextX,
                    firstX * nextX + firstZ * nextZ)));
            if (turn > planner.settings.geometry().meanders().maximumTurnDegrees()) {
                return new Refinement(List.of(), point.rounded(), HydrologyCandidateRejection.SURFACE_SHAPE_UNSUPPORTED,
                        (int) StrictMath.ceil(turn), null);
            }
        }
        return corridor;
    }

    private Refinement validateCorridor(List<HydrologyPoint> points, String profile, boolean coastal) {
        Set<Long> visited = new HashSet<>();
        HydrologyTerrainSample previousTerrain = null;
        HydrologyPoint previousPoint = null;
        double length = 0D;
        for (int index = 0; index + 1 < points.size(); index++) {
            HydrologyPoint first = points.get(index);
            HydrologyPoint second = points.get(index + 1);
            int steps = Math.max(Math.abs(second.x() - first.x()), Math.abs(second.z() - first.z()));
            for (int step = index == 0 ? 0 : 1; step <= steps; step++) {
                double progress = steps == 0 ? 0D : step / (double) steps;
                int x = (int) StrictMath.round(first.x() + (second.x() - first.x()) * progress);
                int z = (int) StrictMath.round(first.z() + (second.z() - first.z()) * progress);
                HydrologyTerrainSample terrain = terrainRefiner.sample(x, z);
                HydrologyPoint failure = new HydrologyPoint(x, terrain == null ? first.y() : terrain.naturalHeight(), z);
                if (!visited.add(RiverFootprint.pack(x, z))) {
                    return new Refinement(List.of(), failure, HydrologyCandidateRejection.SURFACE_SHAPE_UNSUPPORTED, 2, null);
                }
                double completedLength = length
                        + (previousPoint == null ? 0D : StrictMath.sqrt(first.distanceSquared2D(previousPoint)));
                if (terrain != null && terrain.ocean() && previousPoint != null
                        && receivingEntry(previousTerrain, terrain, profile, coastal, completedLength)) {
                    ArrayList<HydrologyPoint> prefix = new ArrayList<>(points.subList(0, index + 1));
                    if (prefix.getLast().distanceSquared2D(previousPoint) > 0L) {
                        prefix.add(previousPoint);
                    }
                    int seaLevel = planner.settings.seaLevel();
                    OceanEntry entry = new OceanEntry(new HydrologyPoint(previousPoint.x(), seaLevel, previousPoint.z()),
                            new HydrologyPoint(x, seaLevel, z));
                    return new Refinement(List.copyOf(prefix), null, null, 0, entry);
                }
                if (terrain != null && !terrain.ocean() && terrain.naturalHeight() < planner.settings.seaLevel()
                        && previousPoint != null && completedLength >= planner.settings.routing().regional().minimumLength()
                        && completedLength <= planner.settings.routing().maximumRouteLength()) {
                    return receivingTail(points, new Shore(index, step, previousPoint, previousTerrain), profile, coastal);
                }
                double traversedLength = length + StrictMath.sqrt(first.distanceSquared2D(failure));
                if (traversedLength > planner.settings.routing().maximumRouteLength()) {
                    return new Refinement(List.of(), failure, HydrologyCandidateRejection.ROUTE_LIMIT,
                            (int) StrictMath.ceil(traversedLength), null);
                }
                if (terrain == null || !terrain.transitAllowed() || !terrain.preferredProfileKeys().contains(profile)) {
                    return new Refinement(List.of(), failure, HydrologyCandidateRejection.POLICY_EXCLUDED, 0, null);
                }
                if (terrain.ocean() || terrain.naturalHeight() < planner.settings.seaLevel()) {
                    return new Refinement(List.of(), failure, HydrologyCandidateRejection.SURFACE_CORRIDOR_UNSUPPORTED, 1, null);
                }
                if (previousTerrain != null && (!previousTerrain.drainsInto(terrain)
                        || coastal && !terrain.drainsInto(previousTerrain))) {
                    return new Refinement(List.of(), failure, HydrologyCandidateRejection.CONFINED_NO_OUTLET, 0, null);
                }
                if (coastal && terrain.naturalHeight() - planner.settings.seaLevel()
                        + planner.settings.surface().minimumDepth()
                        > planner.settings.routing().regional().maximumCoastalIncision()) {
                    return new Refinement(List.of(), failure, HydrologyCandidateRejection.SURFACE_BANK_BUDGET,
                            terrain.naturalHeight() - planner.settings.seaLevel() + planner.settings.surface().minimumDepth(), null);
                }
                previousTerrain = terrain;
                previousPoint = failure;
            }
            length += StrictMath.sqrt(first.distanceSquared2D(second));
        }
        return new Refinement(points, null, null, 0, null);
    }

    private Refinement receivingTail(List<HydrologyPoint> points, Shore shore, String profile, boolean coastal) {
        HydrologyPoint previousPoint = shore.point();
        HydrologyTerrainSample previous = shore.terrain();
        HydrologyPoint firstWet = null;
        int samples = 0;
        for (int index = shore.segment(); index + 1 < points.size(); index++) {
            HydrologyPoint first = points.get(index);
            HydrologyPoint last = points.get(index + 1);
            int steps = Math.max(Math.abs(last.x() - first.x()), Math.abs(last.z() - first.z()));
            for (int step = index == shore.segment() ? shore.step() : 1; step <= steps; step++) {
                double progress = steps == 0 ? 0D : step / (double) steps;
                int x = (int) StrictMath.round(first.x() + (last.x() - first.x()) * progress);
                int z = (int) StrictMath.round(first.z() + (last.z() - first.z()) * progress);
                if (++samples > MAXIMUM_RECEIVER_SAMPLES) {
                    return new Refinement(List.of(), previousPoint, HydrologyCandidateRejection.ROUTE_LIMIT, samples, null);
                }
                HydrologyTerrainSample terrain = terrainRefiner.sample(x, z);
                HydrologyPoint point = new HydrologyPoint(x, terrain == null ? first.y() : terrain.naturalHeight(), z);
                if (terrain == null || terrain.naturalHeight() >= planner.settings.seaLevel()) {
                    return new Refinement(List.of(), point, HydrologyCandidateRejection.SURFACE_CORRIDOR_UNSUPPORTED, 1, null);
                }
                if (!terrain.preferredProfileKeys().contains(profile)) {
                    return new Refinement(List.of(), point, HydrologyCandidateRejection.POLICY_EXCLUDED, 0, null);
                }
                if (!previous.drainsInto(terrain) || coastal && !terrain.drainsInto(previous)) {
                    return new Refinement(List.of(), point, HydrologyCandidateRejection.CONFINED_NO_OUTLET, 0, null);
                }
                if (previousPoint.x() != x && previousPoint.z() != z) {
                    samples += 2;
                    if (samples > MAXIMUM_RECEIVER_SAMPLES
                            || !wetBridge(previousPoint.x(), z, previous, terrain, profile, coastal)
                            && !wetBridge(x, previousPoint.z(), previous, terrain, profile, coastal)) {
                        return new Refinement(List.of(), point, HydrologyCandidateRejection.SURFACE_MOUTH_DISCONNECTED, 0, null);
                    }
                }
                if (firstWet == null) {
                    firstWet = point;
                }
                if (terrain.ocean()) {
                    return completedReceivingTail(points, shore, firstWet, point, profile, coastal);
                }
                previousPoint = point;
                previous = terrain;
            }
        }
        return new Refinement(List.of(), previousPoint, HydrologyCandidateRejection.SURFACE_MOUTH_DISCONNECTED, 0, null);
    }

    private boolean wetBridge(int x, int z, HydrologyTerrainSample previous, HydrologyTerrainSample next,
                              String profile, boolean coastal) {
        HydrologyTerrainSample bridge = terrainRefiner.sample(x, z);
        return bridge != null && bridge.naturalHeight() < planner.settings.seaLevel()
                && bridge.preferredProfileKeys().contains(profile) && previous.drainsInto(bridge) && bridge.drainsInto(next)
                && (!coastal || next.drainsInto(bridge) && bridge.drainsInto(previous));
    }

    private Refinement completedReceivingTail(List<HydrologyPoint> points, Shore shore,
                                              HydrologyPoint firstWet, HydrologyPoint receiving, String profile, boolean coastal) {
        int seaLevel = planner.settings.seaLevel();
        HydrologyPoint landward = new HydrologyPoint(shore.point().x(), seaLevel, shore.point().z());
        receiving = new HydrologyPoint(receiving.x(), seaLevel, receiving.z());
        HydrologyTerrainSampler proof = HydrologyOceanReceiver.forConnection(planner.settings,
                terrainRefiner::sample, landward, receiving);
        if (!shore.terrain().outletAllowed() || !proof.receivingWater(firstWet.x(), firstWet.z(), seaLevel)
                || !receivingChordAllowed(shore, receiving, profile, coastal)) {
            return new Refinement(List.of(), firstWet, HydrologyCandidateRejection.SURFACE_MOUTH_DISCONNECTED, 0, null);
        }
        ArrayList<HydrologyPoint> prefix = new ArrayList<>(points.subList(0, shore.segment() + 1));
        if (prefix.getLast().distanceSquared2D(shore.point()) > 0L) {
            prefix.add(shore.point());
        }
        return new Refinement(List.copyOf(prefix), null, null, 0, new OceanEntry(landward, receiving));
    }

    private boolean receivingChordAllowed(Shore shore, HydrologyPoint end, String profile, boolean coastal) {
        HydrologyPoint start = shore.point();
        int steps = Math.max(Math.abs(end.x() - start.x()), Math.abs(end.z() - start.z()));
        if (steps > MAXIMUM_RECEIVER_SAMPLES) {
            return false;
        }
        HydrologyTerrainSample previous = shore.terrain();
        HydrologyPoint previousPoint = start;
        for (int step = 1; step <= steps; step++) {
            double progress = step / (double) steps;
            int x = (int) StrictMath.round(start.x() + (end.x() - start.x()) * progress);
            int z = (int) StrictMath.round(start.z() + (end.z() - start.z()) * progress);
            HydrologyTerrainSample terrain = terrainRefiner.sample(x, z);
            if (terrain == null || terrain.naturalHeight() >= planner.settings.seaLevel()
                    || !terrain.preferredProfileKeys().contains(profile) || !previous.drainsInto(terrain)
                    || coastal && !terrain.drainsInto(previous)) {
                return false;
            }
            if (previousPoint.x() != x && previousPoint.z() != z
                    && !wetBridge(previousPoint.x(), z, previous, terrain, profile, coastal)
                    && !wetBridge(x, previousPoint.z(), previous, terrain, profile, coastal)) {
                return false;
            }
            previous = terrain;
            previousPoint = new HydrologyPoint(x, terrain.naturalHeight(), z);
        }
        return true;
    }

    private Refinement validateHydraulics(Refinement corridor, String profile, boolean coastal, HydrologyTerrainSampler receiver) {
        if (corridor.rejection() != null || coastal) {
            return corridor;
        }
        List<HydrologyPoint> points = corridor.points();
        int count = 1;
        for (int index = 1; index < points.size(); index++) {
            count += Math.max(Math.abs(points.get(index).x() - points.get(index - 1).x()),
                    Math.abs(points.get(index).z() - points.get(index - 1).z()));
        }
        HydrologyPlannerSettings.Inlet inlet = planner.settings.surface().banks().inlet();
        boolean oceanMouth = corridor.oceanEntry() != null || terrainRefiner.receivingTerminal(points.getLast(), profile, false, receiver);
        int reach = oceanMouth ? Math.min(inlet.length(), (int) StrictMath.floor(count * inlet.courseFraction())) : 0;
        int rampStart = count - reach - reach / 2;
        int availableHead = Integer.MAX_VALUE;
        int station = 0;
        HydrologyTerrainSampler bankSampler = terrainRefiner::sample;
        for (int index = 0; index + 1 < points.size(); index++) {
            HydrologyPoint first = points.get(index);
            HydrologyPoint second = points.get(index + 1);
            int steps = Math.max(Math.abs(second.x() - first.x()), Math.abs(second.z() - first.z()));
            double distance = StrictMath.sqrt(first.distanceSquared2D(second));
            double tangentX = distance == 0D ? 1D : (second.x() - first.x()) / distance;
            double tangentZ = distance == 0D ? 0D : (second.z() - first.z()) / distance;
            for (int step = index == 0 ? 0 : 1; step <= steps; step++) {
                double progress = steps == 0 ? 0D : step / (double) steps;
                int x = (int) StrictMath.round(first.x() + (second.x() - first.x()) * progress);
                int z = (int) StrictMath.round(first.z() + (second.z() - first.z()) * progress);
                HydrologyTerrainSample terrain = terrainRefiner.sample(x, z);
                availableHead = hydraulics.supportedHead(new HydrologyRegionalHydraulics.HeadStation(
                        x, z, tangentX, tangentZ, terrain, availableHead), bankSampler);
                int requiredHead = station >= rampStart ? hydraulics.inletMinimumHead(terrain) : hydraulics.minimumHead(terrain);
                if (requiredHead > availableHead) {
                    return new Refinement(List.of(), new HydrologyPoint(x, terrain.naturalHeight(), z),
                            HydrologyCandidateRejection.SURFACE_CORRIDOR_UNSUPPORTED, requiredHead - availableHead, null);
                }
                station++;
            }
        }
        return corridor;
    }

    private boolean receivingEntry(HydrologyTerrainSample land, HydrologyTerrainSample ocean,
                                   String profile, boolean coastal, double length) {
        return length >= planner.settings.routing().regional().minimumLength()
                && ocean.naturalHeight() < planner.settings.seaLevel()
                && ocean.preferredProfileKeys().contains(profile)
                && land.outletAllowed() && land.drainsInto(ocean) && (!coastal || ocean.drainsInto(land));
    }

    private double turn(List<HydrologyPoint> points, int index) {
        HydrologyPoint previous = points.get(Math.max(0, index - 4));
        HydrologyPoint point = points.get(index);
        HydrologyPoint next = points.get(Math.min(points.size() - 1, index + 4));
        double firstX = point.x() - previous.x();
        double firstZ = point.z() - previous.z();
        double nextX = next.x() - point.x();
        double nextZ = next.z() - point.z();
        return StrictMath.toDegrees(StrictMath.abs(StrictMath.atan2(firstX * nextZ - firstZ * nextX,
                firstX * nextX + firstZ * nextZ)));
    }

    private static double spline(double before, double start, double end, double after, double progress) {
        return 0.5D * ((2D * start) + (-before + end) * progress
                + (2D * before - 5D * start + 4D * end - after) * progress * progress
                + (-before + 3D * start - 3D * end + after) * progress * progress * progress);
    }

    private final class CandidateSelection {
        private final CandidateAdmission admission;
        private final OceanEntry resolved;
        private final Set<Refinement> attempted = new HashSet<>();
        private Attempt failure;
        private boolean finalFailure;

        private CandidateSelection(CandidateAdmission admission, OceanEntry resolved) {
            this.admission = admission;
            this.resolved = resolved;
        }

        private Attempt consider(Refinement candidate) {
            candidate = retainReceivingEntry(candidate, resolved);
            if (candidate.rejection() != null) {
                if (!finalFailure) {
                    failure = new Attempt(candidate, HydrologyRegionalNetwork.EMPTY, null);
                }
                return null;
            }
            if (!attempted.add(candidate)) {
                return null;
            }
            Attempt result = admission.evaluate(candidate);
            if (result.accepted()) {
                return result;
            }
            failure = result;
            finalFailure = true;
            return null;
        }
    }

    interface CandidateAdmission {
        Attempt evaluate(Refinement candidate);
    }

    record Attempt(Refinement refinement, HydrologyRegionalNetwork network, HydrologyDiagnosticCandidate rejection) {
        boolean accepted() {
            return refinement.rejection() == null && rejection == null;
        }
    }

    private record Shore(int segment, int step, HydrologyPoint point, HydrologyTerrainSample terrain) {
    }

    private record CurvePoint(double x, double y, double z) {
        private static CurvePoint of(HydrologyPoint point) {
            return new CurvePoint(point.x(), point.y(), point.z());
        }

        private double distance(CurvePoint other) {
            return StrictMath.hypot(x - other.x(), z - other.z());
        }

        private HydrologyPoint rounded() {
            return new HydrologyPoint((int) StrictMath.round(x), (int) StrictMath.round(y), (int) StrictMath.round(z));
        }
    }

    record OceanEntry(HydrologyPoint landward, HydrologyPoint receiving) {
    }

    record Refinement(List<HydrologyPoint> points, HydrologyPoint failure,
                      HydrologyCandidateRejection rejection, int detail, OceanEntry oceanEntry) {
    }
}
