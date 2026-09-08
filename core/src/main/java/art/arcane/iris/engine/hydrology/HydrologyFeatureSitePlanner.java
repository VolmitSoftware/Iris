package art.arcane.iris.engine.hydrology;

import art.arcane.iris.engine.hydrology.surface.ChannelProfile;
import art.arcane.iris.engine.hydrology.surface.ValleyProfile;
import art.arcane.iris.engine.hydrology.surface.ValleyProfileSolver;
import art.arcane.iris.engine.hydrology.surface.SurfaceCenterline;
import art.arcane.iris.engine.hydrology.surface.SurfaceTerminal;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Comparator;
import java.util.List;
import java.util.OptionalLong;

final class HydrologyFeatureSitePlanner {
    private final HydrologyPlanner planner;

    HydrologyFeatureSitePlanner(HydrologyPlanner planner) {
        this.planner = planner;
    }

    static final long DEEP_FLUID_SALT = 0x44454550464cL;
    static final long SURFACE_POOL_SALT = 0x504f4f4cL;
    static final long SEA_CAVE_SALT = 0x534541434156L;
    static final long DEEP_FLUID_X_OFFSET_SALT = 0x44454550584fL;
    static final long DEEP_FLUID_Z_OFFSET_SALT = 0x444545505a4fL;
    static final long DEEP_CHANNEL_HEADING_SALT = 0x44454550484447L;
    static final long DEEP_CHANNEL_FIRST_BEND_SALT = 0x444545504231L;
    static final long DEEP_CHANNEL_SECOND_BEND_SALT = 0x444545504232L;

    /**
     * Standing surface pools: a jittered lattice of candidate sites per pool profile, admitted where the
     * policy lists the pool, the ground is open land clear of every accepted course, and the bowl fits
     * the incision cap. Each accepted pool is an independent course with one STANDING_POOL segment.
     */
    void compileSurfacePools(
            HydrologyTileKey key,
            List<RiverCourse> courses,
            List<HydrologyDiagnosticCandidate> diagnostics
    ) {
        int tileSize = planner.settings.routing().tileSize();
        for (HydrologyPlannerSettings.SurfacePool pool : planner.settings.surfacePools()) {
            if (!pool.enabled() || pool.maximumPerTile() == 0) {
                continue;
            }
            long profileSeed = HydrologyHash.mix(planner.worldSeed, SURFACE_POOL_SALT, HydrologyHash.text(pool.id()));
            int target = Math.min(pool.maximumPerTile(), planner.sourcePlanner.expectedCount(pool.density(), HydrologyHash.mix(
                    profileSeed, key.tileX(), key.tileZ())));
            int accepted = 0;
            for (DeepSite site : poolSites(key, pool, profileSeed, tileSize)) {
                if (accepted >= target) {
                    addPoolDiagnostic(site, HydrologyCandidateRejection.SOURCE_QUOTA, diagnostics);
                    continue;
                }
                HydrologyCandidateRejection rejection = poolAdmission(pool, site, courses);
                if (rejection != null) {
                    addPoolDiagnostic(site, rejection, diagnostics);
                    continue;
                }
                RiverCourse course = buildSurfacePoolCourse(pool, site);
                if (course == null) {
                    addPoolDiagnostic(site, HydrologyCandidateRejection.SURFACE_CORRIDOR_UNSUPPORTED, diagnostics);
                    continue;
                }
                courses.add(course);
                accepted++;
            }
        }
    }

    ArrayList<DeepSite> poolSites(
            HydrologyTileKey key,
            HydrologyPlannerSettings.SurfacePool pool,
            long profileSeed,
            int tileSize
    ) {
        int minimumX = key.minimumBlockX(tileSize);
        int minimumZ = key.minimumBlockZ(tileSize);
        int spacing = pool.spacing();
        int xOffset = HydrologyHash.between(HydrologyHash.mix(profileSeed, DEEP_FLUID_X_OFFSET_SALT), 0, spacing - 1);
        int zOffset = HydrologyHash.between(HydrologyHash.mix(profileSeed, DEEP_FLUID_Z_OFFSET_SALT), 0, spacing - 1);
        long firstCellX = ceilDiv((long) minimumX - xOffset, spacing);
        long firstCellZ = ceilDiv((long) minimumZ - zOffset, spacing);
        long lastCellX = Math.floorDiv((long) minimumX + tileSize - 1L - xOffset, spacing);
        long lastCellZ = Math.floorDiv((long) minimumZ + tileSize - 1L - zOffset, spacing);
        ArrayList<DeepSite> sites = new ArrayList<>();
        for (long cellZ = firstCellZ; cellZ <= lastCellZ; cellZ++) {
            for (long cellX = firstCellX; cellX <= lastCellX; cellX++) {
                long stable = HydrologyHash.mix(profileSeed, cellX, cellZ);
                // Jitter inside the cell keeps pools off a visible grid.
                int jitter = Math.max(1, spacing / 3);
                int x = Math.toIntExact(xOffset + cellX * spacing + HydrologyHash.between(HydrologyHash.mix(stable, 11), -jitter, jitter));
                int z = Math.toIntExact(zOffset + cellZ * spacing + HydrologyHash.between(HydrologyHash.mix(stable, 12), -jitter, jitter));
                HydrologyTerrainSample terrain = planner.sampleLandBasis(x, z);
                sites.add(new DeepSite(x, z, terrain == null ? 0 : terrain.naturalHeight(), stable));
            }
        }
        sites.sort(Comparator.comparingLong(DeepSite::stableId));
        return sites;
    }

    HydrologyCandidateRejection poolAdmission(
            HydrologyPlannerSettings.SurfacePool pool,
            DeepSite site,
            List<RiverCourse> courses
    ) {
        HydrologyTerrainSample terrain = planner.sampleLandBasis(site.x(), site.z());
        if (terrain == null || terrain.ocean() || terrain.naturalHeight() <= planner.settings.seaLevel() + 1) {
            return HydrologyCandidateRejection.SURFACE_EXPOSURE;
        }
        if (!terrain.transitAllowed() || !terrain.surfacePoolKeys().contains(pool.id())) {
            return HydrologyCandidateRejection.POLICY_EXCLUDED;
        }
        long clearance = pool.maximumRadius() + (long) StrictMath.ceil(planner.settings.surface().shoreWidth())
                + planner.settings.surface().banks().maximumBlendWidth() + planner.settings.surface().maximumWidth();
        long clearanceSquared = clearance * clearance;
        for (RiverCourse course : courses) {
            for (HydraulicSegment segment : course.segments()) {
                for (HydrologyPoint point : segment.centerline()) {
                    long deltaX = (long) point.x() - site.x();
                    long deltaZ = (long) point.z() - site.z();
                    if (deltaX * deltaX + deltaZ * deltaZ < clearanceSquared) {
                        return HydrologyCandidateRejection.SOURCE_SPACING;
                    }
                }
            }
        }
        return null;
    }

    RiverCourse buildSurfacePoolCourse(HydrologyPlannerSettings.SurfacePool pool, DeepSite site) {
        int radius = HydrologyHash.between(HydrologyHash.mix(site.stableId(), 3), pool.minimumRadius(), pool.maximumRadius());
        HydrologyTerrainSample terrain = planner.sampleLandBasis(site.x(), site.z());
        List<HydrologyPoint> points = List.of(
                new HydrologyPoint(site.x() - 1, site.head(), site.z()),
                new HydrologyPoint(site.x(), site.head(), site.z()),
                new HydrologyPoint(site.x() + 1, site.head(), site.z())
        );
        SurfaceCenterline centerline = SurfaceCenterline.densify(points);
        int count = centerline.size();
        double[] halfWidth = new double[count];
        double[] depth = new double[count];
        double[] bank = new double[count];
        Arrays.fill(halfWidth, radius);
        Arrays.fill(depth, pool.depth());
        Arrays.fill(bank, terrain == null ? 1D : terrain.bankMultiplier());
        ChannelProfile profile = new ChannelProfile(halfWidth, depth, bank);
        ValleyProfile valley = new ValleyProfileSolver(planner.settings.surface(), planner::sampleBasis, planner.settings.seaLevel(), 0)
                .solve(centerline, profile, SurfaceTerminal.SINKHOLE, Integer.MIN_VALUE);
        if (!valley.accepted()) {
            return null;
        }
        int center = Math.min(1, valley.exposedStations() - 1);
        int head = valley.head()[center];
        // A pool belongs in a hollow or on level ground: the whole rim must sit within a few blocks of the
        // water, or the bowl becomes a scar dug into a slope.
        int rimAllowance = pool.depth() + planner.settings.surface().banks().sink() + 2;
        for (int station = 0; station < valley.exposedStations(); station++) {
            if (valley.crossMax()[station] - valley.head()[station] > rimAllowance) {
                return null;
            }
        }
        long courseId = HydrologyHash.mix(planner.worldSeed, SURFACE_POOL_SALT, HydrologyHash.text(pool.id()), site.stableId());
        long segmentId = HydrologyHash.mix(courseId, HydrologySegmentBuilder.SEGMENT_SALT, HydrologyFeatureType.STANDING_POOL.ordinal());
        ArrayList<HydrologyPoint> centerlinePoints = new ArrayList<>(points.size());
        for (HydrologyPoint point : points) {
            centerlinePoints.add(new HydrologyPoint(point.x(), head, point.z()));
        }
        HydraulicSegment segment = new HydraulicSegment(
                segmentId,
                courseId,
                HydrologyFeatureType.STANDING_POOL,
                head,
                head,
                radius * 2,
                pool.depth(),
                false,
                false,
                centerlinePoints
        );
        return new RiverCourse(
                courseId,
                RiverCourseType.SURFACE_POOL,
                OptionalLong.empty(),
                OptionalLong.empty(),
                pool.id(),
                1,
                List.of(),
                List.of(segment)
        );
    }

    void addPoolDiagnostic(
            DeepSite site,
            HydrologyCandidateRejection rejection,
            List<HydrologyDiagnosticCandidate> diagnostics
    ) {
        diagnostics.add(new HydrologyDiagnosticCandidate(
                HydrologyHash.mix(site.stableId(), HydrologySourcePlanner.DIAGNOSTIC_SALT, rejection.ordinal()),
                HydrologyCandidateKind.POOL,
                HydrologyFeatureType.STANDING_POOL,
                new HydrologyPoint(site.x(), site.head(), site.z()),
                rejection,
                0
        ));
    }

    /**
     * Sea caves: coastal grottos the sea opens into without a river. Every owned land node with a proven
     * ocean boundary is a site, ranked by how high the coast stands over the sea; each accepted site is an
     * independent course whose chamber is swept inland from the shoreline and opens only through its ocean
     * face. Rivers keep their outlets: sea caves stay clear of every mouth and grotto already planned.
     */
    void compileSeaCaves(
            HydrologySampledGrid grid,
            List<RiverCourse> courses,
            List<HydrologyDiagnosticCandidate> diagnostics
    ) {
        HydrologyPlannerSettings.SeaCaves seaCaves = planner.settings.seaCaves();
        if (!seaCaves.enabled() || seaCaves.maximumPerTile() == 0) {
            return;
        }
        HydrologyPlannerSettings.Grotto grotto = planner.settings.outlets().coastalGrotto();
        boolean fits = sweptGrottoVolume(grotto, seaCaves.depth()) <= grotto.maximumVolume();
        ArrayList<SeaCaveSite> accepted = new ArrayList<>();
        for (SeaCaveSite site : seaCaveSites(grid)) {
            if (accepted.size() >= seaCaves.maximumPerTile()) {
                addSeaCaveDiagnostic(site, HydrologyCandidateRejection.SOURCE_QUOTA, diagnostics);
                continue;
            }
            if (!fits) {
                addSeaCaveDiagnostic(site, HydrologyCandidateRejection.VOLUME_LIMIT, diagnostics);
                continue;
            }
            HydrologyCandidateRejection rejection = seaCaveAdmission(site, accepted, courses);
            if (rejection != null) {
                addSeaCaveDiagnostic(site, rejection, diagnostics);
                continue;
            }
            courses.add(buildSeaCaveCourse(site));
            accepted.add(site);
        }
    }

    ArrayList<SeaCaveSite> seaCaveSites(HydrologySampledGrid grid) {
        int depth = planner.settings.seaCaves().depth();
        ArrayList<SeaCaveSite> sites = new ArrayList<>();
        for (HydrologyGridNode land : grid.nodes()) {
            HydrologyTerrainSample terrain = land.terrain();
            if (!grid.owns(land.x(), land.z())
                    || terrain.ocean()
                    || !terrain.transitAllowed()
                    || !terrain.outletAllowed()) {
                continue;
            }
            HydrologyGridNode ocean = planner.outletPlanner.firstOceanNeighbor(grid, land);
            if (ocean == null) {
                continue;
            }
            HydrologyOceanBoundaryRefiner.Result boundary = planner.outletPlanner.refineOceanBoundary(land, ocean);
            if (boundary == null
                    || !boundary.landwardTerrain().transitAllowed()
                    || !boundary.landwardTerrain().outletAllowed()) {
                continue;
            }
            long stableId = HydrologyHash.mix(planner.worldSeed, SEA_CAVE_SALT, land.id(), ocean.id());
            HydrologyPoint inner = seaCaveInnerPoint(land, ocean, boundary.landwardPoint(), stableId, depth);
            int seaLevel = planner.settingsSeaLevel(ocean.terrain());
            int coastHeight = seaCaveCoastHeight(boundary, inner, seaLevel);
            sites.add(new SeaCaveSite(land, ocean, boundary, inner, coastHeight, stableId));
        }
        sites.sort(Comparator.comparingInt(SeaCaveSite::coastHeight).reversed()
                .thenComparingLong(SeaCaveSite::stableId));
        return sites;
    }

    /**
     * How high the coast stands over the sea across the chamber: the lowest land at the shoreline, at the
     * back of the chamber and at both flanks of each. Flanks in the sea are ignored (the chamber opens
     * there like its face); a back in the sea leaves no coast at all.
     */
    int seaCaveCoastHeight(HydrologyOceanBoundaryRefiner.Result boundary, HydrologyPoint inner, int seaLevel) {
        HydrologyTerrainSample innerTerrain = planner.sampleLandBasis(inner.x(), inner.z());
        if (innerTerrain == null) {
            return 0;
        }
        HydrologyPoint landward = boundary.landwardPoint();
        int lowest = Math.min(boundary.landwardTerrain().naturalHeight(), innerTerrain.naturalHeight());
        int radius = planner.settings.outlets().coastalGrotto().horizontalRadius();
        // Flanks lie along the shore, across the crossing from the landward point to the sea.
        int spanX = boundary.oceanPoint().x() - landward.x();
        int spanZ = boundary.oceanPoint().z() - landward.z();
        double length = StrictMath.hypot(spanX, spanZ);
        int flankX = (int) StrictMath.round(-spanZ / length * radius);
        int flankZ = (int) StrictMath.round(spanX / length * radius);
        int[][] flanks = {
                {landward.x() + flankX, landward.z() + flankZ},
                {landward.x() - flankX, landward.z() - flankZ},
                {inner.x() + flankX, inner.z() + flankZ},
                {inner.x() - flankX, inner.z() - flankZ}
        };
        for (int[] flank : flanks) {
            HydrologyTerrainSample terrain = planner.sampleLandBasis(flank[0], flank[1]);
            if (terrain != null) {
                lowest = Math.min(lowest, terrain.naturalHeight());
            }
        }
        return lowest - seaLevel;
    }

    // The chamber runs inland along the coast normal, turned by a stable jitter so caves do not all
    // face the same way along a straight shore.
    HydrologyPoint seaCaveInnerPoint(
            HydrologyGridNode land,
            HydrologyGridNode ocean,
            HydrologyPoint landward,
            long stableId,
            int depth
    ) {
        double normalX = land.x() - ocean.x();
        double normalZ = land.z() - ocean.z();
        double length = StrictMath.hypot(normalX, normalZ);
        double jitter = StrictMath.toRadians(
                (HydrologyHash.unit(HydrologyHash.mix(stableId, 7)) * 2D - 1D)
                        * planner.settings.seaCaves().sweepJitterDegrees()
        );
        double cos = StrictMath.cos(jitter);
        double sin = StrictMath.sin(jitter);
        double unitX = normalX / length;
        double unitZ = normalZ / length;
        double inlandX = unitX * cos - unitZ * sin;
        double inlandZ = unitX * sin + unitZ * cos;
        return new HydrologyPoint(
                (int) StrictMath.round(landward.x() + inlandX * depth),
                landward.y(),
                (int) StrictMath.round(landward.z() + inlandZ * depth)
        );
    }

    HydrologyCandidateRejection seaCaveAdmission(
            SeaCaveSite site,
            List<SeaCaveSite> accepted,
            List<RiverCourse> courses
    ) {
        HydrologyPlannerSettings.SeaCaves seaCaves = planner.settings.seaCaves();
        if (site.coastHeight() < seaCaves.minimumCoastHeight()) {
            return HydrologyCandidateRejection.SURFACE_HEAD_RANGE;
        }
        HydrologyPoint landward = site.boundary().landwardPoint();
        long spacingSquared = (long) seaCaves.minimumSpacing() * seaCaves.minimumSpacing();
        for (SeaCaveSite other : accepted) {
            if (landward.distanceSquared2D(other.boundary().landwardPoint()) < spacingSquared) {
                return HydrologyCandidateRejection.SOURCE_SPACING;
            }
        }
        long clearance = 2L * planner.settings.outlets().coastalGrotto().horizontalRadius() + seaCaves.depth();
        long clearanceSquared = clearance * clearance;
        for (RiverCourse course : courses) {
            if (course.type() == RiverCourseType.SEA_CAVE) {
                continue;
            }
            for (HydraulicSegment segment : course.segments()) {
                if (segment.type() != HydrologyFeatureType.MOUTH
                        && segment.type() != HydrologyFeatureType.COASTAL_GROTTO
                        && segment.type() != HydrologyFeatureType.INLAND_GROTTO) {
                    continue;
                }
                for (HydrologyPoint point : segment.centerline()) {
                    if (landward.distanceSquared2D(point) < clearanceSquared) {
                        return HydrologyCandidateRejection.SOURCE_SPACING;
                    }
                }
            }
        }
        return null;
    }

    RiverCourse buildSeaCaveCourse(SeaCaveSite site) {
        HydrologyPlannerSettings.Grotto grotto = planner.settings.outlets().coastalGrotto();
        long courseId = HydrologyHash.mix(planner.worldSeed, HydrologySurfaceCoursePlanner.COURSE_SALT, SEA_CAVE_SALT, site.stableId());
        String profileKey = planner.segments.chooseProfile(site.boundary().landwardTerrain(), courseId);
        int seaLevel = planner.settingsSeaLevel(site.ocean().terrain());
        // The last point is the ocean connection: the footprint opens the chamber there and nowhere else.
        List<HydrologyPoint> centerline = planner.segments.line(
                HydrologyPlanner.withY(site.inner(), seaLevel),
                HydrologyPlanner.withY(site.boundary().oceanPoint(), seaLevel),
                planner.settings.routing().refinementSpacing()
        );
        ArrayList<HydraulicSegment> segments = new ArrayList<>(1);
        planner.segments.addFlatSegment(
                courseId,
                HydrologyFeatureType.COASTAL_GROTTO,
                seaLevel,
                grotto.horizontalRadius() * 2,
                grotto.verticalRadius(),
                centerline,
                segments
        );
        return new RiverCourse(
                courseId,
                RiverCourseType.SEA_CAVE,
                OptionalLong.empty(),
                OptionalLong.empty(),
                profileKey,
                1,
                List.of(),
                segments
        );
    }

    void addSeaCaveDiagnostic(
            SeaCaveSite site,
            HydrologyCandidateRejection rejection,
            List<HydrologyDiagnosticCandidate> diagnostics
    ) {
        diagnostics.add(new HydrologyDiagnosticCandidate(
                HydrologyHash.mix(site.stableId(), HydrologySourcePlanner.DIAGNOSTIC_SALT, rejection.ordinal()),
                HydrologyCandidateKind.OUTLET,
                HydrologyFeatureType.COASTAL_GROTTO,
                site.boundary().landwardPoint(),
                rejection,
                0
        ));
    }

    /**
     * The raster a swept grotto occupies: one ellipsoid plus its central cross-section extruded over the
     * sweep. The containment filter measures the real carve; this only refuses chambers that cannot fit.
     */
    static long sweptGrottoVolume(HydrologyPlannerSettings.Grotto grotto, int depth) {
        int radius = grotto.horizontalRadius();
        long section = 0L;
        for (int deltaX = -radius; deltaX <= radius; deltaX++) {
            double normalized = Math.min(1D, Math.abs(deltaX) / Math.max(1D, radius));
            double scale = StrictMath.sqrt(Math.max(0D, 1D - normalized * normalized));
            section += (long) StrictMath.ceil(grotto.verticalRadius() * scale)
                    + (long) StrictMath.floor(grotto.headroom() * scale)
                    + 1L;
        }
        return HydrologyPlannerSettings.ellipsoidVolume(radius, grotto.verticalRadius(), grotto.headroom())
                + section * depth;
    }

    record SeaCaveSite(
            HydrologyGridNode land,
            HydrologyGridNode ocean,
            HydrologyOceanBoundaryRefiner.Result boundary,
            HydrologyPoint inner,
            int coastHeight,
            long stableId
    ) {
    }

    void compileDeepFluidCourses(
            HydrologyTileKey key,
            List<RiverCourse> courses,
            List<HydrologyDiagnosticCandidate> diagnostics
    ) {
        for (HydrologyPlannerSettings.DeepFluid deepFluid : planner.settings.deepFluids()) {
            if (!deepFluid.enabled() || deepFluid.maximumPerTile() == 0) {
                continue;
            }
            ArrayList<DeepSite> sites = deepSites(key, deepFluid, diagnostics);
            int target = Math.min(
                    deepFluid.maximumPerTile(),
                    planner.sourcePlanner.expectedCount(deepFluid.density(), HydrologyHash.mix(
                            planner.worldSeed,
                            DEEP_FLUID_SALT,
                            HydrologyHash.text(deepFluid.id()),
                            key.tileX(),
                            key.tileZ()
                    ))
            );
            for (int index = 0; index < sites.size(); index++) {
                DeepSite site = sites.get(index);
                if (index >= target) {
                    addDeepDiagnostic(
                            deepFluid,
                            site,
                            HydrologyCandidateRejection.SOURCE_QUOTA,
                            diagnostics
                    );
                    continue;
                }
                RiverCourse course = buildDeepFluidCourse(deepFluid, site, index);
                if (course != null) {
                    courses.add(course);
                } else {
                    addDeepDiagnostic(
                            deepFluid,
                            site,
                            HydrologyCandidateRejection.VOLUME_LIMIT,
                            diagnostics
                    );
                }
            }
        }
    }

    ArrayList<DeepSite> deepSites(
            HydrologyTileKey key,
            HydrologyPlannerSettings.DeepFluid deepFluid,
            List<HydrologyDiagnosticCandidate> diagnostics
    ) {
        int tileSize = planner.settings.routing().tileSize();
        int minimumX = key.minimumBlockX(tileSize);
        int minimumZ = key.minimumBlockZ(tileSize);
        int spacing = deepFluid.spacing();
        long profileSeed = HydrologyHash.mix(planner.worldSeed, DEEP_FLUID_SALT, HydrologyHash.text(deepFluid.id()));
        int xOffset = HydrologyHash.between(
                HydrologyHash.mix(profileSeed, DEEP_FLUID_X_OFFSET_SALT),
                0,
                spacing - 1
        );
        int zOffset = HydrologyHash.between(
                HydrologyHash.mix(profileSeed, DEEP_FLUID_Z_OFFSET_SALT),
                0,
                spacing - 1
        );
        long firstCellX = ceilDiv((long) minimumX - xOffset, spacing);
        long firstCellZ = ceilDiv((long) minimumZ - zOffset, spacing);
        long lastCellX = Math.floorDiv((long) minimumX + tileSize - 1L - xOffset, spacing);
        long lastCellZ = Math.floorDiv((long) minimumZ + tileSize - 1L - zOffset, spacing);
        ArrayList<DeepSite> sites = new ArrayList<>();
        for (long cellZ = firstCellZ; cellZ <= lastCellZ; cellZ++) {
            for (long cellX = firstCellX; cellX <= lastCellX; cellX++) {
                long stable = HydrologyHash.mix(
                        planner.worldSeed,
                        DEEP_FLUID_SALT,
                        HydrologyHash.text(deepFluid.id()),
                        cellX,
                        cellZ
                );
                int x = Math.toIntExact(xOffset + cellX * spacing);
                int z = Math.toIntExact(zOffset + cellZ * spacing);
                int head = planner.sampleGeometry(
                        HydrologyGeometrySampler.Field.DEEP_FLUID_HEIGHT,
                        deepFluid.id(),
                        x,
                        z,
                        stable,
                        deepFluid.minimumY(),
                        deepFluid.maximumY()
                );
                DeepSite site = new DeepSite(x, z, head, stable);
                HydrologyTerrainSample terrain = planner.sampleLandBasis(x, z);
                int verticalRadius = HydrologyHash.between(
                        HydrologyHash.mix(stable, 3),
                        deepFluid.minimumVerticalRadius(),
                        deepFluid.maximumVerticalRadius()
                );
                if (!deepSiteFits(terrain, deepFluid, head, verticalRadius, planner.minimumY)) {
                    if (terrain != null && !terrain.ocean()) {
                        addDeepDiagnostic(
                                deepFluid,
                                site,
                                HydrologyCandidateRejection.CAVE_CONTAINMENT,
                                diagnostics
                        );
                    }
                    continue;
                }
                sites.add(site);
            }
        }
        sites.sort(Comparator.comparingLong(DeepSite::stableId));
        return sites;
    }

    long ceilDiv(long value, int divisor) {
        long quotient = Math.floorDiv(value, divisor);
        return quotient * divisor == value ? quotient : quotient + 1L;
    }

    static boolean deepSiteFits(
            HydrologyTerrainSample terrain,
            HydrologyPlannerSettings.DeepFluid deepFluid,
            int head,
            int verticalRadius,
            int minimumY
    ) {
        if (terrain == null || terrain.ocean()) {
            return false;
        }
        int lowerEnvelope = deepFluid.containedPools()
                ? Math.max(deepFluid.channelDepth(), verticalRadius)
                : deepFluid.channelDepth();
        return (long) head - lowerEnvelope > minimumY
                && head + deepFluid.headroom() < terrain.naturalHeight();
    }

    void addDeepDiagnostic(
            HydrologyPlannerSettings.DeepFluid deepFluid,
            DeepSite site,
            HydrologyCandidateRejection rejection,
            List<HydrologyDiagnosticCandidate> diagnostics
    ) {
        diagnostics.add(new HydrologyDiagnosticCandidate(
                HydrologyHash.mix(site.stableId(), HydrologySourcePlanner.DIAGNOSTIC_SALT, rejection.ordinal()),
                HydrologyCandidateKind.DEEP_FLUID,
                deepFluid.containedPools() ? HydrologyFeatureType.DEEP_POOL : HydrologyFeatureType.DEEP_CHANNEL,
                new HydrologyPoint(site.x(), site.head(), site.z()),
                rejection, 0
        ));
    }

    RiverCourse buildDeepFluidCourse(
            HydrologyPlannerSettings.DeepFluid deepFluid,
            DeepSite site,
            int siteIndex
    ) {
        long courseId = HydrologyHash.mix(
                planner.worldSeed,
                HydrologySurfaceCoursePlanner.COURSE_SALT,
                DEEP_FLUID_SALT,
                HydrologyHash.text(deepFluid.id()),
                site.stableId(),
                siteIndex
        );
        int horizontalRadius = HydrologyHash.between(
                HydrologyHash.mix(site.stableId(), 2),
                deepFluid.minimumHorizontalRadius(),
                deepFluid.maximumHorizontalRadius()
        );
        int verticalRadius = HydrologyHash.between(
                HydrologyHash.mix(site.stableId(), 3),
                deepFluid.minimumVerticalRadius(),
                deepFluid.maximumVerticalRadius()
        );
        ArrayList<HydraulicSegment> segments = new ArrayList<>();
        HydrologyPoint center = new HydrologyPoint(site.x(), site.head(), site.z());
        long usedVolume = 0L;
        if (deepFluid.containedPools()) {
            long poolVolume = HydrologyPlannerSettings.ellipsoidVolume(
                    horizontalRadius,
                    Math.max(deepFluid.channelDepth(), verticalRadius),
                    deepFluid.headroom()
            );
            if (poolVolume > deepFluid.maximumVolume()) {
                return null;
            }
            usedVolume = poolVolume;
            long poolId = HydrologyHash.mix(planner.worldSeed, HydrologySegmentBuilder.SEGMENT_SALT, courseId, HydrologyFeatureType.DEEP_POOL.ordinal());
            segments.add(new HydraulicSegment(
                    poolId,
                    courseId,
                    HydrologyFeatureType.DEEP_POOL,
                    site.head(),
                    site.head(),
                    horizontalRadius * 2 + 1,
                    Math.max(deepFluid.channelDepth(), verticalRadius),
                    false,
                    false,
                    List.of(center)
            ));
        }
        if (deepFluid.shortChannels() && deepFluid.maximumChannelLength() > 0) {
            int volumeBoundedMaximum = deepFluid.maximumChannelLength();
            while (volumeBoundedMaximum >= deepFluid.minimumChannelLength()
                    && usedVolume + deepChannelVolumeBound(deepFluid, volumeBoundedMaximum)
                    > deepFluid.maximumVolume()) {
                volumeBoundedMaximum--;
            }
            if (volumeBoundedMaximum >= deepFluid.minimumChannelLength()) {
                int length = HydrologyHash.between(
                    HydrologyHash.mix(site.stableId(), 4),
                    deepFluid.minimumChannelLength(),
                    volumeBoundedMaximum
                );
                long channelId = HydrologyHash.mix(planner.worldSeed, HydrologySegmentBuilder.SEGMENT_SALT, courseId, HydrologyFeatureType.DEEP_CHANNEL.ordinal());
                segments.add(new HydraulicSegment(
                        channelId,
                        courseId,
                        HydrologyFeatureType.DEEP_CHANNEL,
                        site.head(),
                        site.head(),
                        deepFluid.channelWidth(),
                        deepFluid.channelDepth(),
                        false,
                        false,
                        organicDeepChannelCenterline(
                                center,
                                length,
                                planner.settings.routing().refinementSpacing(),
                                site.stableId()
                        )
                ));
            }
        }
        if (segments.isEmpty()) {
            return null;
        }
        return new RiverCourse(
                courseId,
                RiverCourseType.DEEP_FLUID,
                OptionalLong.empty(),
                OptionalLong.empty(),
                deepFluid.id(),
                1,
                List.of(),
                segments
        );
    }

    long deepChannelVolumeBound(HydrologyPlannerSettings.DeepFluid deepFluid, int length) {
        int radius = Math.max(1, deepFluid.channelWidth() / 2);
        int points = (int) StrictMath.ceil(
                length * StrictMath.sqrt(2D) / planner.settings.routing().refinementSpacing()
        ) + 1;
        long horizontalEnvelope = (long) (radius * 2 + 1) * (radius * 2 + 1);
        long verticalEnvelope = deepFluid.channelDepth() + deepFluid.headroom() + 1L;
        return horizontalEnvelope * verticalEnvelope * points;
    }

    List<HydrologyPoint> organicDeepChannelCenterline(
            HydrologyPoint start,
            int length,
            int spacing,
            long stableId
    ) {
        double heading = HydrologyHash.unit(HydrologyHash.mix(stableId, DEEP_CHANNEL_HEADING_SALT))
                * StrictMath.PI * 2D;
        double forwardX = StrictMath.cos(heading);
        double forwardZ = StrictMath.sin(heading);
        double lateralX = -forwardZ;
        double lateralZ = forwardX;
        double bendEnvelope = Math.max(1.5D, Math.min(8D, length * 0.3D));
        double firstBend = planner.segments.signedOrganicOffset(stableId, DEEP_CHANNEL_FIRST_BEND_SALT, bendEnvelope);
        double secondBend = planner.segments.signedOrganicOffset(stableId, DEEP_CHANNEL_SECOND_BEND_SALT, bendEnvelope);
        if (StrictMath.signum(firstBend) == StrictMath.signum(secondBend)) {
            secondBend = -secondBend;
        }

        double endX = start.x() + forwardX * length;
        double endZ = start.z() + forwardZ * length;
        double firstControlX = start.x() + forwardX * length / 3D + lateralX * firstBend;
        double firstControlZ = start.z() + forwardZ * length / 3D + lateralZ * firstBend;
        double secondControlX = start.x() + forwardX * length * 2D / 3D + lateralX * secondBend;
        double secondControlZ = start.z() + forwardZ * length * 2D / 3D + lateralZ * secondBend;
        int steps = Math.max(4, (int) StrictMath.ceil(length / (double) Math.max(2, spacing)) * 2);
        ArrayList<HydrologyPoint> points = new ArrayList<>(steps + 1);
        for (int step = 0; step <= steps; step++) {
            double progress = step / (double) steps;
            double inverse = 1D - progress;
            double x = inverse * inverse * inverse * start.x()
                    + 3D * inverse * inverse * progress * firstControlX
                    + 3D * inverse * progress * progress * secondControlX
                    + progress * progress * progress * endX;
            double z = inverse * inverse * inverse * start.z()
                    + 3D * inverse * inverse * progress * firstControlZ
                    + 3D * inverse * progress * progress * secondControlZ
                    + progress * progress * progress * endZ;
            HydrologyPoint point = new HydrologyPoint(
                    (int) StrictMath.round(x),
                    start.y(),
                    (int) StrictMath.round(z)
            );
            if (points.isEmpty() || point.x() != points.getLast().x() || point.z() != points.getLast().z()) {
                points.add(point);
            }
        }
        return List.copyOf(points);
    }

    record DeepSite(int x, int z, int head, long stableId) {
    }
}
