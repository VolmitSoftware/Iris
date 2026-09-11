package art.arcane.iris.probe;

import art.arcane.iris.engine.IrisComplex;
import art.arcane.iris.engine.framework.Engine;
import art.arcane.iris.engine.hydrology.DrainageEdge;
import art.arcane.iris.engine.hydrology.DrainageNode;
import art.arcane.iris.engine.hydrology.HydraulicSegment;
import art.arcane.iris.engine.hydrology.HydrologyCandidateKind;
import art.arcane.iris.engine.hydrology.HydrologyColumnLayer;
import art.arcane.iris.engine.hydrology.HydrologyColumnSample;
import art.arcane.iris.engine.hydrology.HydrologyDiagnosticCandidate;
import art.arcane.iris.engine.hydrology.HydrologyFeatureType;
import art.arcane.iris.engine.object.InferredType;
import art.arcane.iris.engine.hydrology.HydrologyPoint;
import art.arcane.iris.engine.hydrology.HydrologyPlannerSettings;
import art.arcane.iris.engine.hydrology.surface.SurfaceExcavationMetrics;
import art.arcane.iris.engine.hydrology.HydrologyTile;
import art.arcane.iris.engine.hydrology.HydrologyTileKey;
import art.arcane.iris.engine.hydrology.RiverCourse;
import art.arcane.iris.engine.hydrology.RiverCourseType;
import art.arcane.iris.engine.hydrology.RiverFootprint;
import art.arcane.iris.engine.hydrology.runtime.IrisHydrologyRuntime;
import art.arcane.iris.engine.hydrology.surface.SurfaceCenterline;
import art.arcane.iris.engine.hydrology.surface.SurfaceFootprintCompiler;

import javax.imageio.ImageIO;
import java.awt.image.BufferedImage;
import java.io.File;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.TreeMap;

/**
 * Plans one hydrology tile over a real pack and renders every exposed surface course as a
 * top-down plan plus five cross-sections, with a summary of cut depth, bank steps, ocean writes
 * and spilling channel cells. Exit code 1 when any course writes the ocean or spills.
 */
public final class RiverTransectProbe {
    static final int NO_WATER = Integer.MIN_VALUE;
    private static final String PREFIX = "[transect]";
    private static final int MARGIN = 48;
    private static final int HALF_SECTION = 40;
    private static final int SECTIONS = 5;
    private static final int SECTION_SCALE = 4;
    private static final int SECTION_ROW_HEIGHT = 120;
    private static final int MAXIMUM_PLAN_COLUMNS = 6_000_000;
    private static final int MAXIMUM_DETAILS = 16;
    private static final int TABLE_HALF_SECTION = 16;
    private static final int[] HEADWATER_SECTIONS = {0, 6, 12, 18, 24};

    private RiverTransectProbe() {
    }

    enum Role {
        NONE,
        CHANNEL,
        SHORE,
        BANK,
        APRON;

        boolean owned() {
            return this == CHANNEL || this == SHORE || this == BANK;
        }
    }

    record ColumnPlan(boolean ocean, boolean terrainOwned, boolean fluidOwned, boolean mouth, int bedY, long courseId) {
    }

    record ColumnView(int x, int z, int natural, int terrain, int water, Role role, ColumnPlan plan) {
        int cut() {
            return natural - terrain;
        }
    }

    record SummaryOptions(long courseId, SurfaceCenterline centerline, int seaLevel,
                          HydrologyPlannerSettings.Excavation limits) {
        SummaryOptions {
            if (centerline == null || centerline.size() < 1 || limits == null) {
                throw new IllegalArgumentException("A nonempty centerline and excavation limits are required.");
            }
        }
    }

    record CourseSummary(
            long id,
            int stations,
            int ownedColumns,
            int minimumCut,
            int maximumCut,
            int maximumBankStep,
            int oceanWrites,
            int uncontainedWetCells,
            int drySillCuts,
            int invalidDrySillCuts,
            int maximumBankCut,
            long bankExcavation,
            int estimatedBankVolumePerBlock,
            int bankVolumeLimit,
            int bankBudgetViolations,
            int connectedMouthColumns,
            int disconnectedMouthColumns,
            List<String> details
    ) {
        boolean passes() {
            return oceanWrites == 0 && uncontainedWetCells == 0 && invalidDrySillCuts == 0
                    && bankBudgetViolations == 0 && disconnectedMouthColumns == 0;
        }

        String line() {
            return String.format(Locale.ROOT,
                    "%s course=%d %s stations=%d owned=%d cut=%d..%d bankStep=%d oceanWrites=%d uncontained=%d "
                            + "drySillCuts=%d invalidSills=%d bankCut=%d bankVolume=%d estimatedBankVolumePerBlock=%d bankVolumeLimit=%d bankBudgetViolations=%d "
                            + "connectedMouthColumns=%d disconnectedMouthColumns=%d",
                    PREFIX, id, passes() ? "PASS" : "FAIL", stations, ownedColumns, minimumCut, maximumCut,
                    maximumBankStep, oceanWrites, uncontainedWetCells, drySillCuts, invalidDrySillCuts,
                    maximumBankCut, bankExcavation, estimatedBankVolumePerBlock, bankVolumeLimit,
                    bankBudgetViolations, connectedMouthColumns, disconnectedMouthColumns);
        }
    }

    record Configuration(File pack, String dimension, long seed, int tileX, int tileZ, File output, boolean studio) {
        static Configuration parse(String[] args) {
            if (args.length < 6 || args.length > 7) {
                throw new IllegalArgumentException(
                        "usage: <pack> <dimension> <seed> <tileX> <tileZ> <outputDir> [studio]");
            }
            boolean studio = args.length == 7 && RealPackProbeSupport.parseBoolean(args[6], "studio");
            return new Configuration(
                    new File(args[0]),
                    args[1],
                    Long.parseLong(args[2].trim()),
                    Integer.parseInt(args[3].trim()),
                    Integer.parseInt(args[4].trim()),
                    new File(args[5]),
                    studio
            );
        }
    }

    private record Bounds(int minimumX, int minimumZ, int maximumX, int maximumZ) {
        static Bounds of(SurfaceCenterline centerline, int margin) {
            int minimumX = Integer.MAX_VALUE;
            int minimumZ = Integer.MAX_VALUE;
            int maximumX = Integer.MIN_VALUE;
            int maximumZ = Integer.MIN_VALUE;
            for (int station = 0; station < centerline.size(); station++) {
                minimumX = Math.min(minimumX, centerline.x()[station]);
                minimumZ = Math.min(minimumZ, centerline.z()[station]);
                maximumX = Math.max(maximumX, centerline.x()[station]);
                maximumZ = Math.max(maximumZ, centerline.z()[station]);
            }
            return new Bounds(minimumX - margin, minimumZ - margin, maximumX + margin, maximumZ + margin);
        }

        int width() {
            return maximumX - minimumX + 1;
        }

        int depth() {
            return maximumZ - minimumZ + 1;
        }
    }

    public static void main(String[] args) {
        Configuration configuration;
        try {
            configuration = Configuration.parse(args);
        } catch (Throwable failure) {
            System.out.println(PREFIX + " FAIL: " + failure.getMessage());
            System.exit(2);
            return;
        }
        int exitCode;
        try {
            exitCode = run(configuration) ? 0 : 1;
        } catch (Throwable failure) {
            System.out.println(PREFIX + " FAIL: probe execution failed");
            failure.printStackTrace(System.out);
            exitCode = 2;
        }
        System.exit(exitCode);
    }

    static boolean run(Configuration configuration) throws Exception {
        try (RealPackProbeSupport.Workspace workspace = RealPackProbeSupport.openWorkspace(
                configuration.pack(), configuration.dimension(), PREFIX);
             RealPackProbeSupport.EngineSession session = workspace.openEngine(
                     configuration.seed(), configuration.studio(), "transect")) {
            Engine engine = session.engine();
            IrisComplex complex = engine.getComplex();
            IrisHydrologyRuntime runtime = complex.getHydrologyRuntime();
            if (runtime == null) {
                throw new IllegalStateException("Dimension '" + configuration.dimension() + "' has no hydrology runtime.");
            }
            Files.createDirectories(configuration.output().toPath());
            HydrologyTileKey key = new HydrologyTileKey(configuration.tileX(), configuration.tileZ());
            long planStart = System.nanoTime();
            HydrologyTile tile = runtime.tile(key);
            double planMillis = (System.nanoTime() - planStart) / 1_000_000D;
            int seaLevel = runtime.settings().seaLevel();
            System.out.println(String.format(Locale.ROOT,
                    "%s tile=%d,%d seed=%d courses=%d planMs=%.1f",
                    PREFIX, key.tileX(), key.tileZ(), configuration.seed(), tile.courses().size(), planMillis));
            for (RiverCourse course : tile.courses()) {
                if (course.type() != RiverCourseType.SEA_CAVE) {
                    continue;
                }
                HydraulicSegment chamber = course.segments().getFirst();
                System.out.println(String.format(Locale.ROOT,
                        "%s seaCave id=%016x inner=%d,%d,%d mouth=%d,%d,%d cavePlan=%s",
                        PREFIX, course.id(),
                        chamber.start().x(), chamber.start().y(), chamber.start().z(),
                        chamber.end().x(), chamber.end().y(), chamber.end().z(),
                        tile.cavePlan(course.id()).map((plan) -> plan.accepted() ? "accepted" : "rejected").orElse("none")));
            }

            for (String line : rejectionLines(tile)) {
                System.out.println(PREFIX + " " + line);
            }
            writeTileDiagnostics(new File(configuration.output(), "tile-diagnostics.png"), tile, complex, runtime, seaLevel);
            List<CourseSummary> summaries = new ArrayList<>();
            for (RiverCourse course : tile.courses()) {
                if (course.type() != RiverCourseType.SURFACE && course.type() != RiverCourseType.SURFACE_POOL) {
                    continue;
                }
                List<HydrologyPoint> exposedPath = exposedPath(course);
                if (exposedPath.size() < 2) {
                    continue;
                }
                SurfaceCenterline centerline = SurfaceCenterline.densify(exposedPath);
                Bounds bounds = Bounds.of(centerline, MARGIN);
                Map<Long, ColumnView> columns = sampleColumns(complex, runtime, bounds);
                CourseSummary summary = summarize(new SummaryOptions(course.id(), centerline, seaLevel,
                        runtime.settings().surface().banks().erosion().excavation()), columns);
                summaries.add(summary);
                writePlan(new File(configuration.output(), "course-" + course.id() + ".png"), bounds, columns);
                writeSections(new File(configuration.output(), "course-" + course.id() + "-sections.png"),
                        centerline, complex, runtime);
                writeSectionTable(new File(configuration.output(), "course-" + course.id() + "-sections.txt"),
                        centerline, complex, runtime);
                System.out.println(summary.line());
                for (String detail : summary.details()) {
                    System.out.println(PREFIX + "   " + detail);
                }
            }
            writeSummary(new File(configuration.output(), "summary.txt"), configuration, tile, summaries);
            boolean pass = !summaries.isEmpty() && summaries.stream().allMatch(CourseSummary::passes);
            System.out.println(PREFIX + " " + (pass ? "PASS" : "FAIL")
                    + " surfaceCourses=" + summaries.size()
                    + " output=" + configuration.output().getAbsolutePath());
            return pass;
        }
    }

    static List<String> rejectionLines(HydrologyTile tile) {
        TreeMap<String, Integer> counts = new TreeMap<>();
        TreeMap<String, ArrayList<Integer>> details = new TreeMap<>();
        for (HydrologyDiagnosticCandidate candidate : tile.diagnosticCandidates()) {
            String key = "rejected " + candidate.kind() + " " + candidate.projectedType() + " " + candidate.rejection();
            counts.merge(key, 1, Integer::sum);
            if (candidate.detail() != 0) {
                details.computeIfAbsent(key, (String ignored) -> new ArrayList<>()).add(candidate.detail());
            }
        }
        ArrayList<String> lines = new ArrayList<>(counts.size());
        for (Map.Entry<String, Integer> entry : counts.entrySet()) {
            ArrayList<Integer> values = details.get(entry.getKey());
            String suffix = "";
            if (values != null) {
                values.sort(null);
                suffix = " detail=" + values.subList(0, Math.min(values.size(), 24));
            }
            lines.add(entry.getKey() + " x" + entry.getValue() + suffix);
        }
        return lines;
    }

    static int[] sectionStations(int stations) {
        int[] selected = new int[SECTIONS];
        for (int section = 0; section < SECTIONS; section++) {
            double fraction = 0.1D + 0.2D * section;
            selected[section] = Math.max(0, Math.min(stations - 1, (int) Math.floor(stations * fraction)));
        }
        return selected;
    }

    static CourseSummary summarize(SummaryOptions options, Map<Long, ColumnView> columns) {
        int seaLevel = options.seaLevel();
        int bankCutLimit = options.limits().maximumDepth();
        int owned = 0;
        int minimumCut = Integer.MAX_VALUE;
        int maximumCut = Integer.MIN_VALUE;
        int maximumBankStep = 0;
        int oceanWrites = 0;
        int uncontained = 0;
        int drySills = 0;
        int invalidSills = 0;
        int maximumBankCut = 0;
        long bankExcavation = 0L;
        int bankBudgetViolations = 0;
        int connectedMouthColumns = 0;
        int disconnectedMouthColumns = 0;
        HashSet<Long> oceanConnected = oceanConnected(columns, seaLevel);
        ArrayList<String> details = new ArrayList<>();
        String worstStep = null;
        ArrayList<ColumnView> ordered = new ArrayList<>(columns.values());
        ordered.sort(Comparator.comparingInt(ColumnView::z).thenComparingInt(ColumnView::x));
        for (ColumnView column : ordered) {
            boolean submerged = column.natural() < seaLevel || column.plan().ocean();
            if (submerged && (column.terrain() != column.natural() || column.plan().terrainOwned())) {
                oceanWrites++;
                if (details.size() < MAXIMUM_DETAILS) {
                    details.add("oceanWrite " + column.x() + "," + column.z()
                            + " natural=" + column.natural() + " terrain=" + column.terrain() + " role=" + column.role());
                }
            }
            if (column.plan().mouth() && column.role() == Role.CHANNEL && column.water() == seaLevel) {
                if (oceanConnected.contains(RiverFootprint.pack(column.x(), column.z()))) {
                    connectedMouthColumns++;
                } else {
                    disconnectedMouthColumns++;
                }
            }
            if (!column.plan().terrainOwned()) {
                continue;
            }
            if (column.natural() == seaLevel && column.cut() > 0) {
                drySills++;
                if (column.role() != Role.CHANNEL || !column.plan().fluidOwned() || column.water() != seaLevel) {
                    invalidSills++;
                }
            }
            owned++;
            minimumCut = Math.min(minimumCut, column.cut());
            maximumCut = Math.max(maximumCut, column.cut());
            if (column.role() == Role.CHANNEL) {
                if (column.water() != NO_WATER && spills(column, columns, seaLevel)) {
                    uncontained++;
                    if (details.size() < MAXIMUM_DETAILS) {
                        details.add("uncontained " + column.x() + "," + column.z()
                                + " water=" + column.water() + " terrain=" + column.terrain()
                                + " natural=" + column.natural() + " neighbours=" + neighbourHeights(column, columns));
                    }
                }
                continue;
            }
            int bankCut = Math.max(0, column.natural() - column.plan().bedY());
            maximumBankCut = Math.max(maximumBankCut, bankCut);
            bankExcavation += bankCut;
            bankBudgetViolations += bankCut > bankCutLimit ? 1 : 0;
            for (int[] offset : new int[][] {{1, 0}, {0, 1}}) {
                ColumnView neighbour = columns.get(RiverFootprint.pack(column.x() + offset[0], column.z() + offset[1]));
                int step = bankStep(column, neighbour);
                if (step > maximumBankStep) {
                    maximumBankStep = step;
                    worstStep = "bankStep " + step + " at " + column.x() + "," + column.z()
                            + " (" + column.role() + " terrain=" + column.terrain() + " natural=" + column.natural()
                            + ") vs " + neighbour.x() + "," + neighbour.z()
                            + " (" + neighbour.role() + " terrain=" + neighbour.terrain() + " natural=" + neighbour.natural() + ")";
                }
            }
        }
        if (worstStep != null) {
            details.add(worstStep);
        }
        if (owned == 0) {
            minimumCut = 0;
            maximumCut = 0;
        }
        return new CourseSummary(options.courseId(), options.centerline().size(), owned, minimumCut, maximumCut, maximumBankStep, oceanWrites,
                uncontained, drySills, invalidSills, maximumBankCut, bankExcavation,
                estimatedBankVolumePerBlock(options, columns), options.limits().maximumVolumePerBlock(), bankBudgetViolations,
                connectedMouthColumns, disconnectedMouthColumns, List.copyOf(details));
    }

    static int estimatedBankVolumePerBlock(SummaryOptions options, Map<Long, ColumnView> columns) {
        SurfaceCenterline centerline = options.centerline();
        Map<Long, List<Integer>> buckets = new HashMap<>();
        for (int station = 0; station < centerline.size(); station++) {
            long bucket = RiverFootprint.pack(Math.floorDiv(centerline.x()[station], 16),
                    Math.floorDiv(centerline.z()[station], 16));
            buckets.computeIfAbsent(bucket, ignored -> new ArrayList<>()).add(station);
        }
        int[] stationVolumes = new int[centerline.size()];
        for (ColumnView column : columns.values()) {
            if (!column.plan().terrainOwned() || column.role() == Role.CHANNEL
                    || column.plan().courseId() != options.courseId()) {
                continue;
            }
            int cut = Math.max(0, column.natural() - column.plan().bedY());
            if (cut > 0) {
                int station = nearestStation(centerline, buckets, column.x(), column.z());
                stationVolumes[station] = Math.addExact(stationVolumes[station], cut);
            }
        }
        return SurfaceExcavationMetrics.maximumVolumePerBlock(centerline, stationVolumes);
    }

    private static int nearestStation(SurfaceCenterline centerline, Map<Long, List<Integer>> buckets, int x, int z) {
        int bucketX = Math.floorDiv(x, 16);
        int bucketZ = Math.floorDiv(z, 16);
        int bestStation = -1;
        double bestDistance = Double.POSITIVE_INFINITY;
        for (int ring = 0; bestStation < 0 || nearestOutsideDistance(x, z, bucketX, bucketZ, ring - 1) <= bestDistance + 2D; ring++) {
            for (int dz = -ring; dz <= ring; dz++) {
                for (int dx = -ring; dx <= ring; dx++) {
                    if (ring > 0 && Math.abs(dx) != ring && Math.abs(dz) != ring) {
                        continue;
                    }
                    List<Integer> stations = buckets.get(RiverFootprint.pack(bucketX + dx, bucketZ + dz));
                    if (stations == null) {
                        continue;
                    }
                    for (int station : stations) {
                        double distance = centerline.distanceToSegment(station, x, z);
                        if (distance < bestDistance - 1.0e-7D
                                || Math.abs(distance - bestDistance) <= 1.0e-7D && station > bestStation) {
                            bestDistance = distance;
                            bestStation = station;
                        }
                    }
                }
            }
        }
        return bestStation;
    }

    private static double nearestOutsideDistance(int x, int z, int bucketX, int bucketZ, int ring) {
        double minX = ((double) bucketX - ring) * 16D;
        double minZ = ((double) bucketZ - ring) * 16D;
        double maxX = ((double) bucketX + ring + 1) * 16D;
        double maxZ = ((double) bucketZ + ring + 1) * 16D;
        return Math.min(Math.min(x - minX, maxX - x), Math.min(z - minZ, maxZ - z));
    }

    private static String neighbourHeights(ColumnView channel, Map<Long, ColumnView> columns) {
        StringBuilder text = new StringBuilder();
        for (int[] offset : new int[][] {{1, 0}, {-1, 0}, {0, 1}, {0, -1}}) {
            ColumnView neighbour = columns.get(RiverFootprint.pack(channel.x() + offset[0], channel.z() + offset[1]));
            if (!text.isEmpty()) {
                text.append(' ');
            }
            text.append(neighbour == null ? "?" : neighbour.role() + ":" + neighbour.terrain());
        }
        return text.toString();
    }

    private static boolean spills(ColumnView channel, Map<Long, ColumnView> columns, int seaLevel) {
        int[][] offsets = {{1, 0}, {-1, 0}, {0, 1}, {0, -1}};
        for (int[] offset : offsets) {
            ColumnView neighbour = columns.get(RiverFootprint.pack(channel.x() + offset[0], channel.z() + offset[1]));
            if (neighbour == null || neighbour.role() == Role.CHANNEL) {
                continue;
            }
            if (neighbour.plan().ocean() && neighbour.natural() < seaLevel && channel.water() <= seaLevel) {
                continue;
            }
            if (neighbour.terrain() < channel.water()) {
                return true;
            }
        }
        return false;
    }

    private static HashSet<Long> oceanConnected(Map<Long, ColumnView> columns, int seaLevel) {
        HashSet<Long> visited = new HashSet<>();
        ArrayDeque<Long> queue = new ArrayDeque<>();
        for (Map.Entry<Long, ColumnView> entry : columns.entrySet()) {
            ColumnView column = entry.getValue();
            if (column.plan().ocean() && column.natural() < seaLevel && !column.plan().terrainOwned()) {
                visited.add(entry.getKey());
                queue.add(entry.getKey());
            }
        }
        while (!queue.isEmpty()) {
            ColumnView current = columns.get(queue.removeFirst());
            for (int[] offset : new int[][]{{1, 0}, {-1, 0}, {0, 1}, {0, -1}}) {
                long key = RiverFootprint.pack(current.x() + offset[0], current.z() + offset[1]);
                ColumnView next = columns.get(key);
                if (next != null && next.water() == seaLevel && next.terrain() < seaLevel && visited.add(key)) {
                    queue.addLast(key);
                }
            }
        }
        return visited;
    }

    private static int bankStep(ColumnView bank, ColumnView neighbour) {
        if (neighbour == null || neighbour.role() == Role.CHANNEL) {
            return 0;
        }
        return Math.abs(bank.terrain() - neighbour.terrain());
    }

    private static List<HydrologyPoint> exposedPath(RiverCourse course) {
        ArrayList<HydrologyPoint> path = new ArrayList<>();
        for (HydraulicSegment segment : course.segments()) {
            if (!SurfaceFootprintCompiler.exposedSegment(segment)) {
                break;
            }
            for (HydrologyPoint point : segment.centerline()) {
                if (!path.isEmpty()) {
                    HydrologyPoint last = path.getLast();
                    if (last.x() == point.x() && last.z() == point.z()) {
                        continue;
                    }
                }
                path.add(point);
            }
        }
        return path;
    }

    private static Map<Long, ColumnView> sampleColumns(IrisComplex complex, IrisHydrologyRuntime runtime, Bounds bounds) {
        long area = (long) bounds.width() * bounds.depth();
        if (area > MAXIMUM_PLAN_COLUMNS) {
            throw new IllegalStateException("Course bounding box too large to render: " + area + " columns.");
        }
        HashMap<Long, ColumnView> columns = new HashMap<>((int) Math.min(Integer.MAX_VALUE, area * 2));
        for (int z = bounds.minimumZ(); z <= bounds.maximumZ(); z++) {
            for (int x = bounds.minimumX(); x <= bounds.maximumX(); x++) {
                columns.put(RiverFootprint.pack(x, z), column(complex, runtime, x, z));
            }
        }
        return columns;
    }

    private static ColumnView column(IrisComplex complex, IrisHydrologyRuntime runtime, int x, int z) {
        Optional<HydrologyColumnSample> sample = runtime.sample(x, z);
        int natural = sample.map(HydrologyColumnSample::naturalHeight)
                .orElseGet(() -> (int) Math.round(complex.getNaturalHeightStream().getDouble(x, z)));
        int terrain = (int) Math.round(complex.getHeightStream().getDouble(x, z));
        HydrologyColumnLayer surface = sample.flatMap(HydrologyColumnSample::primarySurfaceLayer).orElse(null);
        HydrologyColumnLayer fluid = sample.flatMap(HydrologyColumnSample::primarySurfaceFluidLayer).orElse(null);
        int water = fluid == null ? NO_WATER : fluid.fluidHeadY();
        boolean ocean = sample.map(HydrologyColumnSample::ocean)
                .orElseGet(() -> complex.getBridgeStream().get(x, z) == InferredType.SEA);
        ColumnPlan plan = new ColumnPlan(ocean, surface != null && surface.terrainOwned(),
                surface != null && surface.fluidOwned(),
                surface != null && surface.feature().type() == HydrologyFeatureType.MOUTH,
                surface == null ? natural : surface.bedY(), surface == null ? 0L : surface.feature().courseId());
        return new ColumnView(x, z, natural, terrain, water, role(surface), plan);
    }

    private static Role role(HydrologyColumnLayer layer) {
        if (layer == null) {
            return Role.NONE;
        }
        if (layer.oceanApron()) {
            return Role.APRON;
        }
        if (layer.channel()) {
            return Role.CHANNEL;
        }
        if (layer.shore()) {
            return Role.SHORE;
        }
        return layer.terrainOwned() ? Role.BANK : Role.NONE;
    }

    /**
     * One pixel per four blocks over the tile: natural height in gray, sea in blue, courses in white,
     * diagnostic candidates as dots coloured by their rejection.
     */
    private static void writeTileDiagnostics(
            File file,
            HydrologyTile tile,
            IrisComplex complex,
            IrisHydrologyRuntime runtime,
            int seaLevel
    ) throws IOException {
        int scale = 4;
        int tileSize = tile.tileSize();
        int size = tileSize / scale;
        int minimumX = tile.key().minimumBlockX(tileSize);
        int minimumZ = tile.key().minimumBlockZ(tileSize);
        int[][] natural = new int[size][size];
        int lowest = Integer.MAX_VALUE;
        int highest = Integer.MIN_VALUE;
        for (int pz = 0; pz < size; pz++) {
            for (int px = 0; px < size; px++) {
                int height = (int) Math.round(complex.getNaturalHeightStream().getDouble(minimumX + px * scale, minimumZ + pz * scale));
                natural[px][pz] = height;
                lowest = Math.min(lowest, height);
                highest = Math.max(highest, height);
            }
        }
        double range = Math.max(1, highest - lowest);
        BufferedImage image = new BufferedImage(size, size, BufferedImage.TYPE_INT_RGB);
        for (int pz = 0; pz < size; pz++) {
            for (int px = 0; px < size; px++) {
                int height = natural[px][pz];
                int rgb;
                if (height <= seaLevel) {
                    rgb = 0x1E3F8A;
                } else {
                    int gray = 40 + (int) Math.round(190D * (height - lowest) / range);
                    rgb = (gray << 16) | (gray << 8) | gray;
                }
                image.setRGB(px, pz, rgb);
            }
        }
        Map<Long, DrainageNode> nodes = new HashMap<>();
        for (DrainageNode node : tile.nodes()) {
            nodes.put(node.id(), node);
            plot(image, (node.naturalPoint().x() - minimumX) / scale, (node.naturalPoint().z() - minimumZ) / scale, 0x00E0E0, 0);
        }
        for (DrainageEdge edge : tile.edges()) {
            DrainageNode from = nodes.get(edge.upstreamNodeId());
            DrainageNode to = nodes.get(edge.downstreamNodeId());
            if (from == null || to == null) {
                continue;
            }
            line(image, (from.naturalPoint().x() - minimumX) / scale, (from.naturalPoint().z() - minimumZ) / scale,
                    (to.naturalPoint().x() - minimumX) / scale, (to.naturalPoint().z() - minimumZ) / scale, 0x20C020);
        }
        // Sea-cave chambers: every column the chamber owns in dark blue, so the cave shows on the coast with
        // its open side to the sea; the centerline on top in white like a surface course.
        HashSet<Long> seaCaveIds = new HashSet<>();
        for (RiverCourse course : tile.courses()) {
            if (course.type() == RiverCourseType.SEA_CAVE) {
                seaCaveIds.add(course.id());
            }
        }
        if (!seaCaveIds.isEmpty()) {
            for (HydrologyColumnSample column : tile.footprint().columns().values()) {
                for (HydrologyColumnLayer layer : column.layers()) {
                    if (seaCaveIds.contains(layer.feature().courseId()) && layer.terrainOwned() && layer.channel()) {
                        plot(image, (column.x() - minimumX) / scale, (column.z() - minimumZ) / scale, 0x000080, 0);
                        break;
                    }
                }
            }
        }
        for (RiverCourse course : tile.courses()) {
            int rgb = course.type() == RiverCourseType.SURFACE || course.type() == RiverCourseType.SEA_CAVE
                    ? 0xFFFFFF
                    : 0xFF00FF;
            for (HydraulicSegment segment : course.segments()) {
                for (HydrologyPoint point : segment.centerline()) {
                    plot(image, (point.x() - minimumX) / scale, (point.z() - minimumZ) / scale, rgb, 1);
                }
            }
        }
        for (HydrologyDiagnosticCandidate candidate : tile.diagnosticCandidates()) {
            int rgb = switch (candidate.rejection()) {
                case NO_DRAINAGE_PATH -> 0xFF2020;
                case COURSE_TOO_SHORT -> 0xFFB000;
                case SOURCE_SPACING -> 0xFFFF40;
                case SURFACE_CORRIDOR_UNSUPPORTED -> 0xFF60FF;
                case OUTLET_LIMIT -> 0x20FF20;
                default -> 0x00FFFF;
            };
            int radius = candidate.kind() == HydrologyCandidateKind.OUTLET ? 1 : 2;
            plot(image, (candidate.point().x() - minimumX) / scale, (candidate.point().z() - minimumZ) / scale, rgb, radius);
        }
        ImageIO.write(image, "png", file);
    }

    private static void line(BufferedImage image, int x0, int z0, int x1, int z1, int rgb) {
        int steps = Math.max(1, Math.max(Math.abs(x1 - x0), Math.abs(z1 - z0)));
        for (int step = 0; step <= steps; step++) {
            int x = x0 + (x1 - x0) * step / steps;
            int z = z0 + (z1 - z0) * step / steps;
            if (x >= 0 && z >= 0 && x < image.getWidth() && z < image.getHeight()) {
                image.setRGB(x, z, rgb);
            }
        }
    }

    private static void plot(BufferedImage image, int x, int z, int rgb, int radius) {
        for (int dz = -radius; dz <= radius; dz++) {
            for (int dx = -radius; dx <= radius; dx++) {
                int px = x + dx;
                int pz = z + dz;
                if (px >= 0 && pz >= 0 && px < image.getWidth() && pz < image.getHeight()) {
                    image.setRGB(px, pz, rgb);
                }
            }
        }
    }

    private static void writePlan(File file, Bounds bounds, Map<Long, ColumnView> columns) throws IOException {
        int minimumNatural = Integer.MAX_VALUE;
        int maximumNatural = Integer.MIN_VALUE;
        for (ColumnView column : columns.values()) {
            minimumNatural = Math.min(minimumNatural, column.natural());
            maximumNatural = Math.max(maximumNatural, column.natural());
        }
        double range = Math.max(1, maximumNatural - minimumNatural);
        BufferedImage image = new BufferedImage(bounds.width(), bounds.depth(), BufferedImage.TYPE_INT_RGB);
        for (int z = bounds.minimumZ(); z <= bounds.maximumZ(); z++) {
            for (int x = bounds.minimumX(); x <= bounds.maximumX(); x++) {
                ColumnView column = columns.get(RiverFootprint.pack(x, z));
                int gray = 40 + (int) Math.round(190D * (column.natural() - minimumNatural) / range);
                int red = gray;
                int green = gray;
                int blue = gray;
                switch (column.role()) {
                    case CHANNEL -> {
                        red = 30;
                        green = 90;
                        blue = 220;
                    }
                    case SHORE -> {
                        red = 214;
                        green = 184;
                        blue = 122;
                    }
                    case BANK -> {
                        int darken = Math.min(120, column.cut() * 12);
                        red = Math.max(0, gray - darken + 20);
                        green = Math.max(0, gray - darken);
                        blue = Math.max(0, gray - darken - 20);
                    }
                    case APRON -> {
                        red = 120;
                        green = 170;
                        blue = 230;
                    }
                    case NONE -> {
                        if (column.terrain() != column.natural()) {
                            red = 220;
                            green = 40;
                            blue = 40;
                        }
                    }
                }
                image.setRGB(x - bounds.minimumX(), z - bounds.minimumZ(), (red << 16) | (green << 8) | blue);
            }
        }
        ImageIO.write(image, "png", file);
    }

    private static void writeSections(
            File file,
            SurfaceCenterline centerline,
            IrisComplex complex,
            IrisHydrologyRuntime runtime
    ) throws IOException {
        int[] stations = sectionStations(centerline.size());
        int span = HALF_SECTION * 2 + 1;
        int width = span * SECTION_SCALE;
        BufferedImage image = new BufferedImage(width, SECTION_ROW_HEIGHT * SECTIONS, BufferedImage.TYPE_INT_RGB);
        for (int section = 0; section < SECTIONS; section++) {
            int station = stations[section];
            double normalX = centerline.normalX(station);
            double normalZ = centerline.normalZ(station);
            ColumnView[] profile = new ColumnView[span];
            int minimum = Integer.MAX_VALUE;
            int maximum = Integer.MIN_VALUE;
            for (int offset = -HALF_SECTION; offset <= HALF_SECTION; offset++) {
                int x = (int) Math.round(centerline.x()[station] + normalX * offset);
                int z = (int) Math.round(centerline.z()[station] + normalZ * offset);
                ColumnView column = column(complex, runtime, x, z);
                profile[offset + HALF_SECTION] = column;
                minimum = Math.min(minimum, Math.min(column.natural(), column.terrain()));
                maximum = Math.max(maximum, Math.max(column.natural(), column.terrain()));
                if (column.water() != NO_WATER) {
                    maximum = Math.max(maximum, column.water());
                }
            }
            int rowTop = section * SECTION_ROW_HEIGHT;
            fill(image, 0, rowTop, width, SECTION_ROW_HEIGHT, 0x1E1E1E);
            double scale = (SECTION_ROW_HEIGHT - 12D) / Math.max(1, maximum - minimum + 1);
            for (int index = 0; index < span; index++) {
                ColumnView column = profile[index];
                int left = index * SECTION_SCALE;
                int naturalY = rowY(rowTop, column.natural(), minimum, scale);
                int terrainY = rowY(rowTop, column.terrain(), minimum, scale);
                fill(image, left, naturalY, SECTION_SCALE, rowTop + SECTION_ROW_HEIGHT - naturalY, 0x555555);
                fill(image, left, terrainY, SECTION_SCALE, rowTop + SECTION_ROW_HEIGHT - terrainY, 0x9A7B4F);
                if (column.water() != NO_WATER && column.water() >= column.terrain()) {
                    int waterY = rowY(rowTop, column.water(), minimum, scale);
                    fill(image, left, waterY, SECTION_SCALE, Math.max(1, terrainY - waterY), 0x2E6BE6);
                }
                if (column.role() == Role.SHORE) {
                    fill(image, left, terrainY - 2, SECTION_SCALE, 2, 0xD6B87A);
                }
            }
            fill(image, HALF_SECTION * SECTION_SCALE, rowTop, 1, 6, 0xFFFFFF);
        }
        ImageIO.write(image, "png", file);
    }

    private static void writeSectionTable(
            File file,
            SurfaceCenterline centerline,
            IrisComplex complex,
            IrisHydrologyRuntime runtime
    ) throws IOException {
        int[] spaced = sectionStations(centerline.size());
        int[] stations = new int[HEADWATER_SECTIONS.length + spaced.length];
        for (int index = 0; index < HEADWATER_SECTIONS.length; index++) {
            stations[index] = Math.min(centerline.size() - 1, HEADWATER_SECTIONS[index]);
        }
        System.arraycopy(spaced, 0, stations, HEADWATER_SECTIONS.length, spaced.length);
        StringBuilder text = new StringBuilder();
        for (int station : stations) {
            text.append("station ").append(station)
                    .append(" at ").append(centerline.x()[station]).append(',').append(centerline.z()[station])
                    .append(" head=").append(column(complex, runtime, centerline.x()[station], centerline.z()[station]).water())
                    .append('\n');
            text.append("offset natural terrain water role\n");
            double normalX = centerline.normalX(station);
            double normalZ = centerline.normalZ(station);
            for (int offset = -TABLE_HALF_SECTION; offset <= TABLE_HALF_SECTION; offset++) {
                int x = (int) Math.round(centerline.x()[station] + normalX * offset);
                int z = (int) Math.round(centerline.z()[station] + normalZ * offset);
                ColumnView column = column(complex, runtime, x, z);
                int streamNatural = (int) Math.round(complex.getNaturalHeightStream().getDouble(x, z));
                text.append(String.format(Locale.ROOT, "%4d %4d %4d %5s %s%s\n",
                        offset, column.natural(), column.terrain(),
                        column.water() == NO_WATER ? "-" : Integer.toString(column.water()), column.role(),
                        streamNatural == column.natural() ? "" : " stream=" + streamNatural));
            }
        }
        Files.writeString(file.toPath(), text.toString(), StandardCharsets.UTF_8);
    }

    private static int rowY(int rowTop, int height, int minimum, double scale) {
        return rowTop + SECTION_ROW_HEIGHT - 6 - (int) Math.round((height - minimum + 1) * scale);
    }

    private static void fill(BufferedImage image, int left, int top, int width, int height, int rgb) {
        int right = Math.min(image.getWidth(), left + width);
        int bottom = Math.min(image.getHeight(), top + height);
        for (int y = Math.max(0, top); y < bottom; y++) {
            for (int x = Math.max(0, left); x < right; x++) {
                image.setRGB(x, y, rgb);
            }
        }
    }

    private static void writeSummary(
            File file,
            Configuration configuration,
            HydrologyTile tile,
            List<CourseSummary> summaries
    ) throws IOException {
        StringBuilder text = new StringBuilder();
        text.append("tile=").append(tile.key().tileX()).append(',').append(tile.key().tileZ())
                .append(" seed=").append(configuration.seed())
                .append(" dimension=").append(configuration.dimension())
                .append(" courses=").append(tile.courses().size())
                .append(" surfaceCourses=").append(summaries.size())
                .append('\n');
        for (String line : rejectionLines(tile)) {
            text.append(line).append('\n');
        }
        int maximumCut = 0;
        int maximumBankStep = 0;
        int oceanWrites = 0;
        int uncontained = 0;
        for (CourseSummary summary : summaries) {
            text.append(summary.line()).append('\n');
            for (String detail : summary.details()) {
                text.append("  ").append(detail).append('\n');
            }
            maximumCut = Math.max(maximumCut, summary.maximumCut());
            maximumBankStep = Math.max(maximumBankStep, summary.maximumBankStep());
            oceanWrites += summary.oceanWrites();
            uncontained += summary.uncontainedWetCells();
        }
        text.append("maximumCut=").append(maximumCut)
                .append(" maximumBankStep=").append(maximumBankStep)
                .append(" oceanWrites=").append(oceanWrites)
                .append(" uncontainedWetCells=").append(uncontained)
                .append('\n');
        Files.writeString(file.toPath(), text.toString(), StandardCharsets.UTF_8);
    }
}
