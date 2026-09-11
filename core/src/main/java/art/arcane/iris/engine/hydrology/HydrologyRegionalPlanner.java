package art.arcane.iris.engine.hydrology;

import art.arcane.iris.engine.hydrology.cave.HydrologyCavePlan;
import art.arcane.iris.engine.hydrology.surface.SurfaceBounds;
import art.arcane.iris.engine.hydrology.surface.SurfaceCenterline;
import art.arcane.iris.engine.hydrology.surface.SurfaceCourseBuilder;
import art.arcane.iris.engine.hydrology.surface.SurfaceCourseResult;
import art.arcane.iris.engine.hydrology.surface.SurfaceExcavationMetrics;
import art.arcane.iris.engine.hydrology.surface.SurfaceFootprint;
import art.arcane.iris.engine.hydrology.surface.SurfaceFootprintCompiler;
import art.arcane.iris.engine.hydrology.surface.SurfaceLayerColumn;
import art.arcane.iris.engine.hydrology.surface.SurfaceTerminal;
import com.github.benmanes.caffeine.cache.Cache;
import com.github.benmanes.caffeine.cache.Caffeine;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.OptionalLong;
import java.util.OptionalInt;
import java.util.Set;

final class HydrologyRegionalPlanner {
    private static final long BASIN_SALT = 0x524547424153494eL;
    private static final long COURSE_SALT = 0x524547434f555253L;
    private static final long NODE_SALT = 0x5245474e4f444553L;
    private static final int MAXIMUM_SOURCE_TRIALS = 24;
    private static final int MAXIMUM_DIAGNOSTICS = 64;

    private final HydrologyPlanner planner;
    private final HydrologyPlannerSettings.Regional settings;
    private final HydrologyRegionalGraph graph;
    private final HydrologyRegionalRoute routes;
    private final Cache<HydrologyTileKey, HydrologyRegionalNetwork> drafts;

    HydrologyRegionalPlanner(HydrologyPlanner planner) {
        this.planner = planner;
        this.settings = planner.settings.routing().regional();
        this.graph = new HydrologyRegionalGraph(planner);
        this.routes = new HydrologyRegionalRoute(planner);
        int minimumWeight = Math.max(1, Math.ceilDiv(settings.maximumCachedStations(), settings.maximumCachedBasins()));
        this.drafts = Caffeine.newBuilder()
                .maximumWeight(settings.maximumCachedStations())
                .weigher((HydrologyTileKey key, HydrologyRegionalNetwork network) -> Math.max(minimumWeight, network.stations()))
                .build();
    }

    boolean enabled() {
        return settings.enabled() && planner.settings.surface().enabled()
                && planner.settings.surface().sources().enabled() && planner.settings.outlets().oceanEnabled()
                && settings.minimumLength() <= planner.settings.routing().maximumRouteLength();
    }

    void clear() {
        drafts.invalidateAll();
        routes.clear();
    }

    private HydrologyTerrainSample sample(int x, int z) {
        return planner.naturalSampler == null ? planner.sampler.sample(x, z) : planner.naturalSampler.sampleBasisWithoutSlope(x, z);
    }

    HydrologyRegionalNetwork coursesIn(SurfaceBounds bounds) {
        if (!enabled()) {
            return HydrologyRegionalNetwork.EMPTY;
        }
        int basinSize = settings.basinSize(planner.settings.routing());
        int reach = settings.sampleSpacing() * 2 + footprintRadius();
        int minimumX = Math.floorDiv(bounds.minimumX() - reach, basinSize);
        int maximumX = Math.floorDiv(bounds.maximumX() + reach, basinSize);
        int minimumZ = Math.floorDiv(bounds.minimumZ() - reach, basinSize);
        int maximumZ = Math.floorDiv(bounds.maximumZ() + reach, basinSize);
        HydrologyTileKey primary = HydrologyTileKey.fromBlock(
                (int) (((long) bounds.minimumX() + bounds.maximumX()) / 2L),
                (int) (((long) bounds.minimumZ() + bounds.maximumZ()) / 2L), basinSize);
        ArrayList<HydrologyRegionalNetwork> networks = new ArrayList<>();
        for (int z = minimumZ; z <= maximumZ; z++) {
            for (int x = minimumX; x <= maximumX; x++) {
                HydrologyTileKey key = new HydrologyTileKey(x, z);
                HydrologyRegionalNetwork draft = draft(key);
                ArrayList<RiverCourse> accepted = new ArrayList<>();
                for (RiverCourse course : draft.courses()) {
                    if (intersects(course, bounds, footprintRadius()) && admitted(key, course)) {
                        accepted.add(course);
                    }
                }
                if (!accepted.isEmpty() || key.equals(primary)) {
                    HydrologyRegionalNetwork selected = select(draft, accepted);
                    networks.add(new HydrologyRegionalNetwork(selected.nodes(), selected.edges(), selected.outlets(),
                            selected.courses(), key.equals(primary) ? draft.diagnostics() : List.of(), selected.cavePlans()));
                }
            }
        }
        return merge(networks);
    }

    RiverFootprint materialize(HydrologyRegionalNetwork network, SurfaceBounds bounds) {
        if (network.courses().isEmpty()) {
            return RiverFootprint.empty();
        }
        SurfaceFootprintCompiler compiler = new SurfaceFootprintCompiler(planner.settings,
                this::sample, planner.geometrySampler);
        LinkedHashMap<Long, HydrologyColumnSample> columns = new LinkedHashMap<>();
        for (RiverCourse course : network.courses()) {
            SurfaceFootprint footprint = compiler.compile(course, bounds);
            if (!footprint.accepted()) {
                throw new IllegalStateException("An accepted regional course changed its bounded surface admission: " + course.id());
            }
            for (SurfaceLayerColumn column : footprint.columns()) {
                HydrologyTerrainSample terrain = column.terrain();
                HydrologyColumnSample sample = new HydrologyColumnSample(column.x(), column.z(), terrain.naturalHeight(),
                        planner.settings.seaLevel(), terrain.ocean(), terrain.parentBiomeKey(), List.of(column.layer()));
                columns.merge(RiverFootprint.pack(column.x(), column.z()), sample, HydrologyRegionalPlanner::mergeColumn);
            }
            HydrologySurfaceDropRaster drops = HydrologySurfaceDropRaster.compile(planner.settings,
                    HydrologyOceanReceiver.forCourse(planner.settings, this::sample, course), planner.geometrySampler, course, bounds);
            for (HydrologyColumnSample column : drops.columns()) {
                columns.merge(RiverFootprint.pack(column.x(), column.z()), column, HydrologyRegionalPlanner::mergeColumn);
            }
        }
        return new RiverFootprint(columns);
    }

    HydrologyRegionalNetwork draft(HydrologyTileKey key) {
        return drafts.get(key, this::compile);
    }

    private HydrologyRegionalNetwork compile(HydrologyTileKey key) {
        HydrologySampledGrid grid = sample(key);
        ArrayList<HydrologyDiagnosticCandidate> diagnostics = new ArrayList<>();
        HydrologyGridNode center = grid.nodes().get(grid.nodes().size() / 2);
        ArrayList<OutletCandidate> outlets = planner.outletPlanner.oceanOutletCandidates(grid, true);
        outlets.removeIf(outlet -> outlet.outlet().type() != HydrologyFeatureType.MOUTH);
        outlets.sort(Comparator.comparingLong(outlet -> HydrologyHash.mix(planner.worldSeed, BASIN_SALT,
                key.tileX(), key.tileZ(), outlet.outlet().id())));
        if (outlets.isEmpty()) {
            diagnostic(diagnostics, center.id(), center.naturalPoint(), false, HydrologyCandidateRejection.NO_LEGAL_OUTLET, 0);
            return new HydrologyRegionalNetwork(List.of(), List.of(), List.of(), List.of(), diagnostics, List.of());
        }
        ArrayList<HydrologyRegionalNetwork> accepted = new ArrayList<>();
        long basinSeed = HydrologyHash.mix(planner.worldSeed, BASIN_SALT, key.tileX(), key.tileZ());
        if (settings.coastalChannels() && HydrologyHash.unit(basinSeed) < settings.coastalChannelChance()) {
            ArrayList<HydrologyDiagnosticCandidate> coastalDiagnostics = new ArrayList<>();
            HydrologyRegionalNetwork coastal = coastalChannel(grid, outlets, basinSeed, coastalDiagnostics);
            diagnostics.addAll(coastalDiagnostics.subList(0, Math.min(16, coastalDiagnostics.size())));
            if (!coastal.courses().isEmpty()) {
                accepted.add(coastal);
            }
        }
        ArrayList<OutletCandidate> roots = spacedOutlets(outlets, settings.maximumTrunks() * 2);
        HydrologyRegionalGraph.Tree tree = graph.route(grid, roots, false);
        ArrayList<Integer> sources = new ArrayList<>();
        for (HydrologyGridNode node : grid.nodes()) {
            if (grid.owns(node.x(), node.z()) && tree.root(node.index()) >= 0
                    && tree.length(node.index()) >= settings.minimumLength()
                    && planner.sourcePlanner.rawSourceEligible(node.terrain(), planner.settings.surface().sources(), true)) {
                sources.add(node.index());
            }
        }
        if (sources.isEmpty()) {
            double longest = 0D;
            for (HydrologyGridNode node : grid.nodes()) {
                if (grid.owns(node.x(), node.z()) && tree.root(node.index()) >= 0) {
                    longest = Math.max(longest, tree.length(node.index()));
                }
            }
            diagnostic(diagnostics, center.id(), center.naturalPoint(), false,
                    longest == 0D ? HydrologyCandidateRejection.NO_DRAINAGE_PATH
                            : longest < settings.minimumLength() ? HydrologyCandidateRejection.COURSE_TOO_SHORT
                            : HydrologyCandidateRejection.POLICY_EXCLUDED, (int) StrictMath.round(longest));
        }
        Set<Long> usedOutlets = new HashSet<>();
        for (HydrologyRegionalNetwork network : accepted) {
            for (RiverOutlet outlet : network.outlets()) {
                usedOutlets.add(outlet.id());
            }
        }
        HydrologyRegionalSourceTrials sourceTrials = new HydrologyRegionalSourceTrials(grid, tree, sources);
        for (int trials = 0; trials < MAXIMUM_SOURCE_TRIALS; trials++) {
            if (accepted.size() >= settings.maximumTrunks()) {
                break;
            }
            OptionalInt selected = sourceTrials.next(usedOutlets);
            if (selected.isEmpty()) {
                break;
            }
            int source = selected.getAsInt();
            OutletCandidate outlet = tree.outlets().get(tree.root(source));
            HydrologyRegionalNetwork candidate = build(grid, tree, source, null, basinSeed, diagnostics);
            if (!candidate.courses().isEmpty() && !conflicts(candidate.courses().getFirst(), merge(accepted).courses())) {
                accepted.add(candidate);
                usedOutlets.add(outlet.outlet().id());
            }
        }
        HydrologyRegionalNetwork network = merge(accepted);
        return new HydrologyRegionalNetwork(network.nodes(), network.edges(), network.outlets(), network.courses(), diagnostics, network.cavePlans());
    }

    private HydrologyRegionalNetwork coastalChannel(HydrologySampledGrid grid, List<OutletCandidate> outlets,
                                                     long basinSeed, List<HydrologyDiagnosticCandidate> diagnostics) {
        int maximum = Math.min(8, outlets.size());
        int pairs = 0;
        double longest = 0D;
        for (int destination = 0; destination < maximum; destination++) {
            if (!bidirectionalMouth(outlets.get(destination).outlet())) {
                continue;
            }
            HydrologyRegionalGraph.Tree tree = graph.route(grid, List.of(outlets.get(destination)), true);
            ArrayList<OutletCandidate> origins = new ArrayList<>(outlets);
            origins.sort(Comparator.comparingDouble((OutletCandidate outlet) -> tree.length(outlet.landIndex())).reversed()
                    .thenComparingLong(outlet -> outlet.outlet().id()));
            int trials = 0;
            for (OutletCandidate origin : origins) {
                if (trials++ >= 8) {
                    break;
                }
                int source = origin.landIndex();
                if (!bidirectionalMouth(origin.outlet())) {
                    continue;
                }
                if (grid.owns(grid.node(source).x(), grid.node(source).z())
                        && opposingCoasts(origin.outlet(), outlets.get(destination).outlet())) {
                    pairs++;
                    longest = Math.max(longest, tree.length(source));
                }
                if (!grid.owns(grid.node(source).x(), grid.node(source).z()) || tree.root(source) < 0
                        || !opposingCoasts(origin.outlet(), outlets.get(destination).outlet())
                        || tree.length(source) < settings.minimumLength()
                        || origin.outlet().connectionPoint().distanceSquared2D(outlets.get(destination).outlet().connectionPoint())
                        < (long) settings.minimumLength() * settings.minimumLength() / 4L) {
                    continue;
                }
                HydrologyRegionalNetwork network = build(grid, tree, source, origin, basinSeed, diagnostics);
                if (!network.courses().isEmpty()) {
                    return network;
                }
            }
        }
        if (diagnostics.isEmpty()) {
            HydrologyGridNode center = grid.nodes().get(grid.nodes().size() / 2);
            diagnostic(diagnostics, basinSeed, center.naturalPoint(), true,
                    pairs == 0 ? HydrologyCandidateRejection.NO_LEGAL_OUTLET
                            : longest == 0D ? HydrologyCandidateRejection.NO_DRAINAGE_PATH
                            : HydrologyCandidateRejection.COURSE_TOO_SHORT,
                    (int) StrictMath.round(longest));
        }
        return HydrologyRegionalNetwork.EMPTY;
    }

    boolean bidirectionalMouth(RiverOutlet outlet) {
        HydrologyTerrainSample land = sample(outlet.landwardPoint().x(), outlet.landwardPoint().z());
        HydrologyTerrainSample ocean = sample(outlet.connectionPoint().x(), outlet.connectionPoint().z());
        return land != null && ocean != null && land.drainsInto(ocean) && ocean.drainsInto(land);
    }

    private static boolean opposingCoasts(RiverOutlet first, RiverOutlet second) {
        double firstX = first.connectionPoint().x() - first.landwardPoint().x();
        double firstZ = first.connectionPoint().z() - first.landwardPoint().z();
        double secondX = second.connectionPoint().x() - second.landwardPoint().x();
        double secondZ = second.connectionPoint().z() - second.landwardPoint().z();
        return firstX * secondX + firstZ * secondZ < 0D;
    }

    private HydrologyRegionalNetwork build(HydrologySampledGrid grid, HydrologyRegionalGraph.Tree tree,
                                           int source, OutletCandidate origin, long basinSeed,
                                           List<HydrologyDiagnosticCandidate> diagnostics) {
        List<Integer> indices = graph.path(tree, source);
        OutletCandidate destination = tree.outlets().get(tree.root(source));
        RiverOutlet outlet = destination.outlet();
        ArrayList<HydrologyPoint> guide = new ArrayList<>(indices.size() + 1);
        ArrayList<String> profiles = new ArrayList<>(grid.node(source).terrain().preferredProfileKeys());
        for (int index : indices) {
            HydrologyGridNode node = grid.node(index);
            guide.add(node.naturalPoint());
            profiles.retainAll(node.terrain().preferredProfileKeys());
        }
        profiles.retainAll(sample(outlet.connectionPoint().x(), outlet.connectionPoint().z()).preferredProfileKeys());
        if (origin != null) {
            profiles.retainAll(sample(origin.outlet().connectionPoint().x(), origin.outlet().connectionPoint().z()).preferredProfileKeys());
            guide.set(0, origin.outlet().landwardPoint());
        }
        if (profiles.isEmpty() || guide.size() < 2) {
            diagnostic(diagnostics, grid.node(source).id(), grid.node(source).naturalPoint(), origin != null,
                    HydrologyCandidateRejection.POLICY_EXCLUDED, guide.size());
            return HydrologyRegionalNetwork.EMPTY;
        }
        guide.set(guide.size() - 1, outlet.landwardPoint());
        String profile = profiles.get(HydrologyHash.between(HydrologyHash.mix(basinSeed, source), 0, profiles.size() - 1));
        BuildContext context = new BuildContext(grid.node(source), origin, basinSeed, outlet, profile,
                new HydrologyRegionalFlow(guide, tree.contributions(source)),
                HydrologyRegionalMorphology.sample(guide, this::sample, planner.settings));
        HydrologyRegionalRoute.Attempt selected = routes.select(guide, profile, origin != null,
                HydrologyOceanReceiver.forOutlet(planner.settings, this::sample, outlet), candidate -> attempt(context, candidate));
        if (selected.rejection() != null) {
            if (diagnostics.size() < MAXIMUM_DIAGNOSTICS) {
                diagnostics.add(selected.rejection());
            }
        } else if (selected.refinement().rejection() != null) {
            HydrologyRegionalRoute.Refinement rejected = selected.refinement();
            diagnostic(diagnostics, grid.node(source).id(), rejected.failure(), origin != null, rejected.rejection(), rejected.detail());
        }
        return selected.network();
    }

    private HydrologyRegionalRoute.Attempt attempt(BuildContext context, HydrologyRegionalRoute.Refinement refinement) {
        ArrayList<HydrologyDiagnosticCandidate> diagnostics = new ArrayList<>();
        HydrologyRegionalNetwork network = build(context, refinement, diagnostics);
        if (network.courses().isEmpty() && diagnostics.isEmpty()) {
            diagnostic(diagnostics, context.source().id(), refinement.points().getFirst(), context.origin() != null,
                    HydrologyCandidateRejection.NO_DRAINAGE_PATH, 0);
        }
        return new HydrologyRegionalRoute.Attempt(refinement, network, network.courses().isEmpty() ? diagnostics.getFirst() : null);
    }

    private HydrologyRegionalNetwork build(BuildContext context, HydrologyRegionalRoute.Refinement refinement,
                                           List<HydrologyDiagnosticCandidate> diagnostics) {
        RiverOutlet outlet = context.outlet();
        OutletCandidate origin = context.origin();
        String profile = context.profile();
        List<HydrologyPoint> path = refinement.points();
        if (refinement.oceanEntry() != null) {
            HydrologyRegionalRoute.OceanEntry entry = refinement.oceanEntry();
            outlet = new RiverOutlet(HydrologyHash.mix(outlet.id(), entry.landward().x(), entry.landward().z(),
                    entry.receiving().x(), entry.receiving().z()), HydrologyFeatureType.MOUTH, outlet.drainageNodeId(),
                    entry.landward(), entry.receiving(), planner.settings.seaLevel(), true);
        }
        long courseId = HydrologyHash.mix(context.basinSeed(), COURSE_SALT, context.source().id(), outlet.id(), origin == null ? 0L : origin.outlet().id());
        HydrologyRegionalFlow flow = context.flow();
        HydrologyGeometrySampler geometry = request -> regionalGeometry(request, context);
        ArrayList<HydraulicSegment> segments;
        if (origin == null) {
            SurfaceCourseResult built = new SurfaceCourseBuilder(planner.settings.surface(),
                    HydrologyOceanReceiver.forOutlet(planner.settings, this::sample, outlet), geometry, planner.settings.seaLevel()).build(
                    planner.worldSeed, courseId, profile, path, SurfaceTerminal.OCEAN_MOUTH,
                    planner.settings.seaLevel(), settings.minimumLength());
            if (!built.accepted()) {
                diagnostic(diagnostics, courseId, path.getFirst(), false, built.rejection(), built.rejectionDetail());
                return HydrologyRegionalNetwork.EMPTY;
            }
            segments = new ArrayList<>(built.segments());
            segments.add(mouth(courseId, built.pathEnd(), outlet.connectionPoint(), built.lastWidth(), built.lastDepth(), 1));
        } else {
            segments = coastalSegments(courseId, profile, path, origin.outlet(), outlet, geometry);
        }
        HydrologyRegionalCourseGraph published = HydrologyRegionalCourseGraph.create(this::sample,
                new HydrologyRegionalCourseGraph.Options(courseId, outlet.id(), settings.sampleSpacing(), path, flow));
        List<DrainageNode> nodes = published.nodes();
        List<DrainageEdge> edges = published.edges();
        RiverCourse course = new RiverCourse(courseId, RiverCourseType.SURFACE, OptionalLong.of(nodes.getFirst().id()),
                OptionalLong.of(outlet.id()), profile, published.discharge(), edges, segments);
        HydrologyRegionalFalls falls = new HydrologyRegionalFalls(planner, course);
        if (!validFootprints(course, diagnostics, falls)) {
            return HydrologyRegionalNetwork.EMPTY;
        }
        RiverOutlet publishedOutlet = publishedOutlet(outlet, nodes.getLast().id());
        HydrologyRegionalNetwork network = new HydrologyRegionalNetwork(nodes, edges, origin == null ? List.of(publishedOutlet)
                : List.of(publishedOutlet(origin.outlet(), nodes.getFirst().id()), publishedOutlet),
                List.of(course), List.of(), List.of());
        List<HydrologyCavePlan> plans = falls.validate(network, diagnostics);
        return plans == null ? HydrologyRegionalNetwork.EMPTY : new HydrologyRegionalNetwork(nodes, edges,
                network.outlets(), network.courses(), List.of(), plans);
    }

    private static RiverOutlet publishedOutlet(RiverOutlet outlet, long nodeId) {
        return new RiverOutlet(outlet.id(), outlet.type(), nodeId, outlet.landwardPoint(), outlet.connectionPoint(),
                outlet.seaLevel(), outlet.directOcean());
    }

    private int regionalGeometry(HydrologyGeometrySampler.Request request, BuildContext context) {
        if (request.field() != HydrologyGeometrySampler.Field.SURFACE_WIDTH) {
            return planner.geometrySampler.sample(request);
        }
        int authored = planner.geometrySampler.sample(request);
        double fraction = 0.25D + 0.75D * context.flow().fraction(request.x(), request.z());
        double sampled = request.minimum() + (request.maximum() - request.minimum()) * fraction;
        double width = sampled * 0.75D + authored * 0.25D;
        double freedom = context.morphology().freedomAt(request.x(), request.z());
        double constrained = request.minimum() + (width - request.minimum()) * freedom;
        return Math.max(request.minimum(), Math.min(request.maximum(), (int) StrictMath.round(constrained)));
    }

    private ArrayList<HydraulicSegment> coastalSegments(long courseId, String profile, List<HydrologyPoint> path,
                                                        RiverOutlet origin, RiverOutlet destination, HydrologyGeometrySampler geometry) {
        ArrayList<HydraulicSegment> segments = new ArrayList<>();
        int seaLevel = planner.settings.seaLevel();
        HydrologyPlannerSettings.Surface surface = planner.settings.surface();
        HydrologyPoint first = HydrologyPlanner.withY(path.getFirst(), seaLevel);
        double width = coastalWidth(profile, first, geometry);
        int depth = geometry.sample(HydrologyGeometrySampler.Field.SURFACE_DEPTH, profile, first.x(), first.z(), surface.minimumDepth(), surface.maximumDepth());
        segments.add(mouth(courseId, origin.connectionPoint(), first, width, depth, 0));
        for (int index = 0; index + 1 < path.size(); index++) {
            HydrologyPoint point = path.get(index);
            HydrologyPoint next = path.get(index + 1);
            depth = geometry.sample(HydrologyGeometrySampler.Field.SURFACE_DEPTH, profile, point.x(), point.z(), surface.minimumDepth(), surface.maximumDepth());
            double nextWidth = coastalWidth(profile, next, geometry);
            int nextDepth = geometry.sample(HydrologyGeometrySampler.Field.SURFACE_DEPTH, profile, next.x(), next.z(), surface.minimumDepth(), surface.maximumDepth());
            segments.add(new HydraulicSegment(HydrologyHash.mix(courseId, index, COURSE_SALT), courseId,
                    HydrologyFeatureType.SURFACE_POOL, seaLevel, seaLevel, (int) StrictMath.ceil(Math.max(width, nextWidth)), Math.max(depth, nextDepth), false, false,
                    List.of(HydrologyPlanner.withY(point, seaLevel), HydrologyPlanner.withY(next, seaLevel)),
                    new HydraulicChannelProfile(new double[] {width, nextWidth}, new double[] {depth, nextDepth})));
            width = nextWidth;
            depth = nextDepth;
        }
        segments.add(mouth(courseId, HydrologyPlanner.withY(path.getLast(), seaLevel), destination.connectionPoint(), width, depth, 1));
        return segments;
    }

    private double coastalWidth(String profile, HydrologyPoint point, HydrologyGeometrySampler geometry) {
        HydrologyPlannerSettings.Surface surface = planner.settings.surface();
        int authored = geometry.sample(HydrologyGeometrySampler.Field.SURFACE_WIDTH, profile, point.x(), point.z(),
                surface.minimumWidth(), surface.maximumWidth());
        return Math.max(surface.minimumWidth(), Math.min(surface.maximumWidth() * 2D,
                authored * sample(point.x(), point.z()).widthMultiplier()));
    }

    private HydraulicSegment mouth(long courseId, HydrologyPoint start, HydrologyPoint end, double width, int depth, int side) {
        int head = planner.settings.seaLevel();
        return new HydraulicSegment(HydrologyHash.mix(courseId, HydrologyFeatureType.MOUTH.ordinal(), side), courseId,
                HydrologyFeatureType.MOUTH, head, head, (int) StrictMath.ceil(width), depth, false, false,
                List.of(HydrologyPlanner.withY(start, head), HydrologyPlanner.withY(end, head)), HydraulicChannelProfile.uniform(width, depth));
    }

    private boolean validFootprints(RiverCourse course, List<HydrologyDiagnosticCandidate> diagnostics, HydrologyRegionalFalls falls) {
        double length = 0D;
        ArrayList<HydrologyPoint> points = new ArrayList<>();
        for (HydraulicSegment segment : course.segments()) {
            points.addAll(segment.centerline());
            for (int index = 1; index < segment.centerline().size(); index++) {
                length += StrictMath.sqrt(segment.centerline().get(index).distanceSquared2D(segment.centerline().get(index - 1)));
            }
        }
        boolean coastal = course.segments().getFirst().type() == HydrologyFeatureType.MOUTH;
        HydrologyPoint source = course.segments().getFirst().start();
        if (length < settings.minimumLength() || length > planner.settings.routing().maximumRouteLength()) {
            diagnostic(diagnostics, course.id(), source, coastal, length < settings.minimumLength()
                    ? HydrologyCandidateRejection.COURSE_TOO_SHORT : HydrologyCandidateRejection.ROUTE_LIMIT,
                    (int) StrictMath.ceil(length));
            return false;
        }
        SurfaceFootprintCompiler compiler = new SurfaceFootprintCompiler(planner.settings,
                this::sample, planner.geometrySampler);
        SurfaceCenterline centerline = SurfaceCenterline.densify(points);
        int[] excavation = new int[centerline.size()];
        HydrologyRegionalConnectivity connectivity = new HydrologyRegionalConnectivity();
        HydrologyTerrainSampler receiver = HydrologyOceanReceiver.forCourse(planner.settings, this::sample, course);
        for (SurfaceBounds bounds : courseWindows(course)) {
            SurfaceFootprint footprint = compiler.compile(course, bounds);
            if (!footprint.accepted()) {
                diagnostic(diagnostics, course.id(), source, coastal, footprint.rejection(), footprint.rejectionDetail());
                return false;
            }
            falls.retain(footprint);
            connectivity.add(footprint.columns());
            HydrologySurfaceDropRaster drops = HydrologySurfaceDropRaster.compile(planner.settings,
                    receiver, planner.geometrySampler, course, bounds);
            SurfaceExcavationMetrics.accumulate(course, centerline, footprint, drops, bounds, excavation);
            for (HydrologyColumnSample column : drops.columns()) {
                connectivity.add(column);
                for (HydrologyColumnLayer layer : column.layers()) {
                    if (layer.fluidOwned() && !sample(column.x(), column.z()).preferredProfileKeys().contains(course.profileKey())) {
                        diagnostic(diagnostics, course.id(), source, coastal, HydrologyCandidateRejection.POLICY_EXCLUDED, 0);
                        return false;
                    }
                }
            }
            for (SurfaceLayerColumn column : footprint.columns()) {
                if (column.layer().fluidOwned() && !column.terrain().preferredProfileKeys().contains(course.profileKey())) {
                    diagnostic(diagnostics, course.id(), source, coastal, HydrologyCandidateRejection.POLICY_EXCLUDED, column.station());
                    return false;
                }
                if (coastal && column.layer().fluidOwned()
                        && column.terrain().naturalHeight() - column.layer().bedY() > settings.maximumCoastalIncision()) {
                    diagnostic(diagnostics, course.id(), source, true, HydrologyCandidateRejection.SURFACE_BANK_BUDGET,
                            column.terrain().naturalHeight() - column.layer().bedY());
                    return false;
                }
            }
        }
        int volume = SurfaceExcavationMetrics.maximumVolumePerBlock(centerline, excavation);
        if (volume > planner.settings.surface().banks().erosion().excavation().maximumVolumePerBlock()) {
            diagnostic(diagnostics, course.id(), source, coastal, HydrologyCandidateRejection.SURFACE_BANK_BUDGET, volume);
            return false;
        }
        if (!connectivity.connected(course, this::sample, planner.settings)) {
            diagnostic(diagnostics, course.id(), source, coastal, HydrologyCandidateRejection.SURFACE_MOUTH_DISCONNECTED, 0);
            return false;
        }
        return true;
    }

    private List<SurfaceBounds> courseWindows(RiverCourse course) {
        int tileSize = planner.settings.routing().tileSize();
        int radius = footprintRadius();
        Set<HydrologyTileKey> keys = new HashSet<>();
        for (HydraulicSegment segment : course.segments()) {
            for (HydrologyPoint point : segment.centerline()) {
                for (int z = Math.floorDiv(point.z() - radius, tileSize); z <= Math.floorDiv(point.z() + radius, tileSize); z++) {
                    for (int x = Math.floorDiv(point.x() - radius, tileSize); x <= Math.floorDiv(point.x() + radius, tileSize); x++) {
                        keys.add(new HydrologyTileKey(x, z));
                    }
                }
            }
        }
        ArrayList<HydrologyTileKey> ordered = new ArrayList<>(keys);
        ordered.sort(HydrologyTileKey::compareTo);
        ArrayList<SurfaceBounds> windows = new ArrayList<>(ordered.size());
        for (HydrologyTileKey key : ordered) {
            windows.add(tileBounds(key));
        }
        return List.copyOf(windows);
    }

    SurfaceBounds tileBounds(HydrologyTileKey key) {
        int size = planner.settings.routing().tileSize();
        return new SurfaceBounds(key.minimumBlockX(size), key.minimumBlockZ(size),
                Math.addExact(key.minimumBlockX(size), size - 1), Math.addExact(key.minimumBlockZ(size), size - 1));
    }

    SurfaceBounds ownerBounds(HydrologyTileKey key) {
        SurfaceBounds tile = tileBounds(key);
        int reach = planner.settings.publicationRadius() + 1;
        return new SurfaceBounds(Math.subtractExact(tile.minimumX(), reach), Math.subtractExact(tile.minimumZ(), reach),
                Math.addExact(tile.maximumX(), reach), Math.addExact(tile.maximumZ(), reach));
    }

    HydrologyCaveCourseFilter.Result include(HydrologyCaveCourseFilter.Result local, HydrologyRegionalNetwork regional) {
        if (regional.courses().isEmpty()) {
            return local;
        }
        HydrologyRegionalNetwork combined = merge(List.of(new HydrologyRegionalNetwork(local.nodes(), local.edges(),
                local.outlets(), local.courses(), List.of(), local.cavePlans()), regional));
        return new HydrologyCaveCourseFilter.Result(combined.nodes(), combined.edges(), combined.outlets(),
                combined.courses(), combined.cavePlans());
    }

    HydrologyCaveCourseFilter.Result withoutRegional(HydrologyCaveCourseFilter.Result result, HydrologyTileKey key) {
        if (!enabled()) {
            return result;
        }
        Set<Long> regionalIds = new HashSet<>();
        for (RiverCourse course : coursesIn(ownerBounds(key)).courses()) {
            regionalIds.add(course.id());
        }
        ArrayList<RiverCourse> local = new ArrayList<>();
        for (RiverCourse course : result.courses()) {
            if (!regionalIds.contains(course.id())) {
                local.add(course);
            }
        }
        ArrayList<HydrologyCavePlan> plans = new ArrayList<>();
        for (HydrologyCavePlan plan : result.cavePlans()) {
            if (!regionalIds.contains(plan.source().sourceId())) {
                plans.add(plan);
            }
        }
        return new HydrologyCaveCourseFilter.Result(result.nodes(), result.edges(), result.outlets(), local, plans);
    }

    private int footprintRadius() {
        return (int) StrictMath.ceil(planner.settings.surface().maximumWidth() * 2D
                + Math.max(planner.settings.surface().shoreWidth(), planner.settings.widestShoreBiomeWidth())
                + Math.min(planner.settings.surface().banks().maximumBlendWidth(),
                planner.settings.surface().banks().erosion().excavation().maximumWidth()) + 4D);
    }

    private HydrologySampledGrid sample(HydrologyTileKey key) {
        int spacing = settings.sampleSpacing();
        int size = settings.basinSize(planner.settings.routing());
        int ownerX = key.minimumBlockX(size);
        int ownerZ = key.minimumBlockZ(size);
        int minimumX = Math.subtractExact(ownerX, spacing);
        int minimumZ = Math.subtractExact(ownerZ, spacing);
        int width = size / spacing + 3;
        HydrologyTerrainSample[] terrain = planner.routingSampler.sampleGrid(
                new HydrologyRoutingTerrainSampler.GridRequest(minimumX, minimumZ, width, spacing));
        ArrayList<HydrologyGridNode> nodes = new ArrayList<>(terrain.length);
        for (int index = 0; index < terrain.length; index++) {
            int x = minimumX + index % width * spacing;
            int z = minimumZ + index / width * spacing;
            nodes.add(new HydrologyGridNode(index, index % width, index / width, x, z,
                    HydrologyHash.mix(planner.worldSeed, NODE_SALT, key.tileX(), key.tileZ(), x, z), terrain[index]));
        }
        return new HydrologySampledGrid(minimumX, minimumZ, ownerX, ownerZ, size, width, spacing, List.copyOf(nodes));
    }

    private ArrayList<OutletCandidate> spacedOutlets(List<OutletCandidate> candidates, int maximum) {
        ArrayList<OutletCandidate> selected = new ArrayList<>();
        long spacing = Math.max(settings.minimumLength() / 2, settings.sampleSpacing() * 2);
        for (OutletCandidate candidate : candidates) {
            boolean overlap = false;
            for (OutletCandidate existing : selected) {
                if (existing.outlet().landwardPoint().distanceSquared2D(candidate.outlet().landwardPoint()) < spacing * spacing) {
                    overlap = true;
                    break;
                }
            }
            if (!overlap) {
                selected.add(candidate);
                if (selected.size() == maximum) {
                    break;
                }
            }
        }
        return selected;
    }

    private boolean admitted(HydrologyTileKey owner, RiverCourse course) {
        long rank = HydrologyHash.mix(planner.worldSeed, BASIN_SALT, owner.tileX(), owner.tileZ());
        int size = settings.basinSize(planner.settings.routing());
        int reach = settings.sampleSpacing() * 2 + footprintRadius();
        for (int z = owner.tileZ() - 1; z <= owner.tileZ() + 1; z++) {
            for (int x = owner.tileX() - 1; x <= owner.tileX() + 1; x++) {
                HydrologyTileKey other = new HydrologyTileKey(x, z);
                if (other.equals(owner)) {
                    continue;
                }
                SurfaceBounds otherBounds = new SurfaceBounds(other.minimumBlockX(size), other.minimumBlockZ(size),
                        Math.addExact(other.minimumBlockX(size), size - 1), Math.addExact(other.minimumBlockZ(size), size - 1));
                if (!intersects(course, otherBounds, reach)) {
                    continue;
                }
                long otherRank = HydrologyHash.mix(planner.worldSeed, BASIN_SALT, x, z);
                if (Long.compareUnsigned(otherRank, rank) < 0 && conflicts(course, draft(other).courses())) {
                    return false;
                }
            }
        }
        return true;
    }

    boolean conflicts(RiverCourse course, List<RiverCourse> existing) {
        HydrologyCrossTileSurfaceAdmission.Claim current = claim(course);
        ArrayList<HydrologyCrossTileSurfaceAdmission.RankedClaim> blockers = new ArrayList<>(existing.size());
        for (RiverCourse other : existing) {
            blockers.add(new HydrologyCrossTileSurfaceAdmission.RankedClaim(new HydrologyTileKey(0, 0), 0, claim(other)));
        }
        return !HydrologyCrossTileSurfaceAdmission.admit(List.of(current), blockers).rejections().isEmpty();
    }

    private HydrologyCrossTileSurfaceAdmission.Claim claim(RiverCourse course) {
        ArrayList<HydrologyPoint> points = new ArrayList<>();
        int width = 1;
        for (HydraulicSegment segment : course.segments()) {
            width = Math.max(width, segment.width());
            for (HydrologyPoint point : segment.centerline()) {
                if (points.isEmpty() || points.getLast().distanceSquared2D(point) >= 256L) {
                    points.add(point);
                }
            }
        }
        HydrologyPoint last = course.segments().getLast().end();
        if (points.size() < 2 || !points.getLast().equals(last)) {
            points.add(last);
        }
        int bankWidth = planner.settings.surface().banks().erosion().excavation().maximumWidth();
        int completeWidth = width + 2 * ((int) StrictMath.ceil(planner.settings.surface().shoreWidth()) + bankWidth);
        return new HydrologyCrossTileSurfaceAdmission.Claim(course.id(), course.outletId().orElse(course.id()), last,
                true, completeWidth, points, 0);
    }

    private static boolean intersects(RiverCourse course, SurfaceBounds bounds, int radius) {
        for (HydraulicSegment segment : course.segments()) {
            for (HydrologyPoint point : segment.centerline()) {
                if (point.x() >= bounds.minimumX() - radius && point.x() <= bounds.maximumX() + radius
                        && point.z() >= bounds.minimumZ() - radius && point.z() <= bounds.maximumZ() + radius) {
                    return true;
                }
            }
        }
        return false;
    }

    static HydrologyColumnSample mergeColumn(HydrologyColumnSample first, HydrologyColumnSample second) {
        ArrayList<HydrologyColumnLayer> layers = new ArrayList<>(first.layers());
        for (HydrologyColumnLayer layer : second.layers()) {
            if (!layers.contains(layer)) {
                layers.add(layer);
            }
        }
        return new HydrologyColumnSample(first.x(), first.z(), first.naturalHeight(), first.seaLevel(), first.ocean(),
                first.parentBiomeKey(), layers);
    }

    private static HydrologyRegionalNetwork select(HydrologyRegionalNetwork network, List<RiverCourse> courses) {
        Set<Long> nodeIds = new HashSet<>();
        Set<Long> outletIds = new HashSet<>();
        ArrayList<DrainageEdge> edges = new ArrayList<>();
        for (RiverCourse course : courses) {
            nodeIds.add(course.sourceNodeId().orElseThrow());
            outletIds.add(course.outletId().orElseThrow());
            for (DrainageEdge edge : course.drainageEdges()) {
                nodeIds.add(edge.upstreamNodeId());
                nodeIds.add(edge.downstreamNodeId());
                edges.add(edge);
            }
        }
        ArrayList<DrainageNode> nodes = new ArrayList<>();
        for (DrainageNode node : network.nodes()) {
            if (nodeIds.contains(node.id())) {
                nodes.add(node);
            }
        }
        ArrayList<RiverOutlet> outlets = new ArrayList<>();
        for (RiverOutlet outlet : network.outlets()) {
            if (outletIds.contains(outlet.id()) || courses.stream().anyMatch(course ->
                    course.segments().getFirst().type() == HydrologyFeatureType.MOUTH
                            && course.segments().getFirst().start().distanceSquared2D(outlet.connectionPoint()) == 0L)) {
                outlets.add(outlet);
            }
        }
        Set<Long> courseIds = new HashSet<>();
        for (RiverCourse course : courses) { courseIds.add(course.id()); }
        ArrayList<HydrologyCavePlan> plans = new ArrayList<>();
        for (HydrologyCavePlan plan : network.cavePlans()) {
            if (courseIds.contains(plan.source().sourceId())) { plans.add(plan); }
        }
        return new HydrologyRegionalNetwork(nodes, edges, outlets, courses, List.of(), plans);
    }

    private static void diagnostic(List<HydrologyDiagnosticCandidate> diagnostics, long id, HydrologyPoint point,
                                   boolean coastal, HydrologyCandidateRejection rejection, int detail) {
        if (diagnostics.size() >= MAXIMUM_DIAGNOSTICS) {
            return;
        }
        HydrologyCandidateKind kind = coastal ? HydrologyCandidateKind.COASTAL_CHANNEL : HydrologyCandidateKind.REGIONAL_SOURCE;
        diagnostics.add(new HydrologyDiagnosticCandidate(HydrologyHash.mix(id, BASIN_SALT, rejection.ordinal()), kind,
                coastal ? HydrologyFeatureType.MOUTH : HydrologyFeatureType.SURFACE_POOL, point, rejection, detail));
    }

    private static HydrologyRegionalNetwork merge(List<HydrologyRegionalNetwork> networks) {
        Map<Long, DrainageNode> nodes = new LinkedHashMap<>();
        Map<Long, DrainageEdge> edges = new LinkedHashMap<>();
        Map<Long, RiverOutlet> outlets = new LinkedHashMap<>();
        Map<Long, RiverCourse> courses = new LinkedHashMap<>();
        Map<Long, HydrologyDiagnosticCandidate> diagnostics = new LinkedHashMap<>();
        Map<Long, HydrologyCavePlan> plans = new LinkedHashMap<>();
        for (HydrologyRegionalNetwork network : networks) {
            for (DrainageNode node : network.nodes()) { nodes.put(node.id(), node); }
            for (DrainageEdge edge : network.edges()) { edges.put(edge.id(), edge); }
            for (RiverOutlet outlet : network.outlets()) { outlets.put(outlet.id(), outlet); }
            for (RiverCourse course : network.courses()) { courses.put(course.id(), course); }
            for (HydrologyDiagnosticCandidate candidate : network.diagnostics()) { diagnostics.put(candidate.id(), candidate); }
            for (HydrologyCavePlan plan : network.cavePlans()) { plans.put(plan.source().sourceId(), plan); }
        }
        return new HydrologyRegionalNetwork(new ArrayList<>(nodes.values()), new ArrayList<>(edges.values()),
                new ArrayList<>(outlets.values()), new ArrayList<>(courses.values()), new ArrayList<>(diagnostics.values()), new ArrayList<>(plans.values()));
    }

    private record BuildContext(HydrologyGridNode source, OutletCandidate origin, long basinSeed, RiverOutlet outlet,
                                String profile, HydrologyRegionalFlow flow, HydrologyRegionalMorphology morphology) {
    }
}
