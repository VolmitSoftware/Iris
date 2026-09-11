package art.arcane.iris.engine.hydrology;

import art.arcane.iris.engine.hydrology.cave.CavePosition;
import art.arcane.iris.engine.hydrology.cave.CaveVoxelPrecondition;
import art.arcane.iris.engine.hydrology.cave.CaveVoxelView;
import art.arcane.iris.engine.hydrology.cave.HydrologyCaveAction;
import art.arcane.iris.engine.hydrology.cave.HydrologyCavePlan;
import art.arcane.iris.engine.hydrology.surface.SurfaceBounds;
import art.arcane.iris.engine.hydrology.surface.SurfaceFootprint;
import art.arcane.iris.engine.hydrology.surface.SurfaceFootprintCompiler;
import art.arcane.iris.engine.hydrology.surface.SurfaceLayerColumn;
import it.unimi.dsi.fastutil.longs.LongOpenHashSet;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;

final class HydrologyRegionalFalls implements HydrologyCaveVoxelViewFactory.PlannedSurface {
    private static final int WINDOW_SIZE = 128;
    private static final int MAXIMUM_ROOF_WINDOWS = 4;
    private static final int[][] NEIGHBORS = {{0, 0}, {1, 0}, {-1, 0}, {0, 1}, {0, -1}};

    private final HydrologyPlanner planner;
    private final RiverCourse course;
    private final HydrologySurfaceDropRaster drops;
    private final HydrologyTerrainSampler receiver;
    private final LongOpenHashSet witnessColumns;
    private final ArrayList<SurfaceLayerColumn> exposed;
    private final SurfaceFootprintCompiler surface;
    private final LinkedHashMap<HydrologyTileKey, Map<Long, HydrologyColumnSample>> roofWindows;

    HydrologyRegionalFalls(HydrologyPlanner planner, RiverCourse course) {
        this.planner = planner;
        this.course = course;
        this.receiver = HydrologyOceanReceiver.forCourse(planner.settings, this::sample, course);
        this.drops = HydrologySurfaceDropRaster.compile(planner.settings, receiver, planner.geometrySampler, course);
        this.witnessColumns = new LongOpenHashSet();
        this.exposed = new ArrayList<>();
        this.surface = new SurfaceFootprintCompiler(planner.settings, this::sample, planner.geometrySampler);
        this.roofWindows = new LinkedHashMap<>(MAXIMUM_ROOF_WINDOWS + 1, 1F, true);
        for (HydrologyColumnSample column : drops.columns()) {
            for (int[] offset : NEIGHBORS) {
                witnessColumns.add(RiverFootprint.pack(column.x() + offset[0], column.z() + offset[1]));
            }
        }
    }

    void retain(SurfaceFootprint footprint) {
        for (SurfaceLayerColumn column : footprint.columns()) {
            if (witnessColumns.contains(RiverFootprint.pack(column.x(), column.z()))) {
                exposed.add(column);
            }
        }
    }

    List<HydrologyCavePlan> validate(HydrologyRegionalNetwork network, List<HydrologyDiagnosticCandidate> diagnostics) {
        if (drops.columns().isEmpty()) {
            return List.of();
        }
        HydrologyFootprintCompiler compiler = new HydrologyFootprintCompiler(planner.settings, receiver, planner.geometrySampler);
        HydrologyFootprintCompiler.ValidationRaster validation = compiler.compileSurfaceDropValidation(course,
                new SurfaceFootprint(exposed, 0, null, 0, 0L), drops);
        HydrologyObservedPlannedSurface planned = new HydrologyObservedPlannedSurface(this);
        CaveVoxelView view = Objects.requireNonNull(planner.caveViewFactory.create(planned));
        ArrayList<HydrologyDiagnosticCandidate> failures = new ArrayList<>();
        HydrologyCaveCourseFilter.Result result = new HydrologyCaveCourseFilter(view,
                new HydrologyCaveCourseFilter.Options(planner.settings.underground().connectToExistingCaves(),
                        planner.settings.outlets().coastalGrotto().maximumVolume(),
                        planner.settings.outlets().inlandGrotto().maximumVolume()), null, null, planned)
                .filter(network.nodes(), network.edges(), network.outlets(), List.of(course), validation, failures);
        for (HydrologyDiagnosticCandidate failure : failures) {
            if (diagnostics.size() >= 64) {
                break;
            }
            diagnostics.add(new HydrologyDiagnosticCandidate(failure.id(), HydrologyCandidateKind.REGIONAL_SOURCE,
                    failure.projectedType(), failure.point(), failure.rejection(), failure.detail()));
        }
        return result.courses().isEmpty() ? null : result.cavePlans();
    }

    static HydrologyCavePlan clip(HydrologyCavePlan plan, SurfaceBounds bounds) {
        LinkedHashMap<CavePosition, HydrologyCaveAction> actions = new LinkedHashMap<>();
        LinkedHashMap<CavePosition, CaveVoxelPrecondition> preconditions = new LinkedHashMap<>();
        plan.forEachActionIn(bounds.minimumX(), bounds.minimumZ(), Math.addExact(bounds.maximumX(), 1),
                Math.addExact(bounds.maximumZ(), 1), actions::put);
        plan.allPreconditionsIn(bounds.minimumX(), bounds.minimumZ(), Math.addExact(bounds.maximumX(), 1),
                Math.addExact(bounds.maximumZ(), 1), (position, precondition) -> {
                    preconditions.put(position, precondition);
                    return true;
                });
        return new HydrologyCavePlan(plan.source(), plan.rejection(), actions, preconditions, plan.arbitrationWinnerSourceId());
    }

    @Override
    public int resolve(int x, int z, int naturalHeight) {
        HydrologyColumnSample column = column(x, z);
        return column == null ? naturalHeight : column.terrainHeight();
    }

    @Override
    public boolean ownsTerrain(int x, int z) {
        HydrologyColumnSample column = column(x, z);
        HydrologyColumnLayer layer = column == null ? null : column.primarySurfaceLayerOrNull();
        return layer != null && layer.terrainOwned();
    }

    private HydrologyColumnSample column(int x, int z) {
        HydrologyTileKey key = HydrologyTileKey.fromBlock(x, z, WINDOW_SIZE);
        Map<Long, HydrologyColumnSample> columns = roofWindows.get(key);
        if (columns == null) {
            SurfaceBounds bounds = new SurfaceBounds(key.minimumBlockX(WINDOW_SIZE), key.minimumBlockZ(WINDOW_SIZE),
                    Math.addExact(key.minimumBlockX(WINDOW_SIZE), WINDOW_SIZE - 1),
                    Math.addExact(key.minimumBlockZ(WINDOW_SIZE), WINDOW_SIZE - 1));
            LinkedHashMap<Long, HydrologyColumnSample> built = new LinkedHashMap<>();
            for (SurfaceLayerColumn column : surface.compile(course, bounds).columns()) {
                HydrologyTerrainSample terrain = column.terrain();
                HydrologyColumnSample value = new HydrologyColumnSample(column.x(), column.z(), terrain.naturalHeight(),
                        planner.settings.seaLevel(), terrain.ocean(), terrain.parentBiomeKey(), List.of(column.layer()));
                built.merge(RiverFootprint.pack(column.x(), column.z()), value, HydrologyRegionalPlanner::mergeColumn);
            }
            columns = Map.copyOf(built);
            roofWindows.put(key, columns);
            if (roofWindows.size() > MAXIMUM_ROOF_WINDOWS) {
                roofWindows.remove(roofWindows.sequencedKeySet().getFirst());
            }
        }
        return columns.get(RiverFootprint.pack(x, z));
    }

    private HydrologyTerrainSample sample(int x, int z) {
        return planner.naturalSampler == null ? planner.sampler.sample(x, z) : planner.naturalSampler.sampleBasisWithoutSlope(x, z);
    }
}
