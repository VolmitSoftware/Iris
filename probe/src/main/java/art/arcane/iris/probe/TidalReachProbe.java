package art.arcane.iris.probe;

import art.arcane.iris.generation.concurrent.MultiBurst;
import art.arcane.iris.generation.decoration.IrisProceduralBlocks;
import art.arcane.iris.generation.hydrology.engine.HydrologyBinding;
import art.arcane.iris.generation.hydrology.engine.HydrologyPlanners;
import art.arcane.iris.generation.hydrology.engine.IrisHydrologyTerrainSource;
import art.arcane.iris.generation.hydrology.plan.HydrologyPlanCodec;
import art.arcane.iris.generation.hydrology.plan.HydrologySettings;
import art.arcane.iris.generation.hydrology.plan.RegionKey;
import art.arcane.iris.generation.hydrology.plan.RegionPlan;
import art.arcane.iris.generation.hydrology.plan.WindowKey;
import art.arcane.iris.generation.hydrology.plan.WindowPlan;
import art.arcane.iris.generation.hydrology.plan.WindowPlanner;
import art.arcane.iris.generation.hydrology.raster.ChunkWaterRaster;
import art.arcane.iris.generation.hydrology.raster.WaterRole;
import art.arcane.iris.generation.hydrology.water.Pond;
import art.arcane.iris.generation.hydrology.water.Reach;
import art.arcane.iris.generation.hydrology.water.ReachStyle;
import art.arcane.iris.generation.runtime.Engine;
import art.arcane.iris.generation.terrain.IrisDimension;
import art.arcane.volmlib.nativelib.terrain.NativeBiome;
import art.arcane.volmlib.nativelib.terrain.NativeBlockState;
import art.arcane.volmlib.util.hunk.Hunk;

import javax.imageio.ImageIO;
import java.awt.Color;
import java.awt.Graphics2D;
import java.awt.image.BufferedImage;
import java.io.File;
import java.io.PrintWriter;
import java.security.MessageDigest;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.HexFormat;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.TreeMap;
import java.util.TreeSet;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;

public final class TidalReachProbe {
    private static final String LOG = "[tidal-reach-probe]";
    private static final int HISTOGRAM = 65;
    private static final int[][] SIDES = {{1, 0}, {-1, 0}, {0, 1}, {0, -1}};
    private static final int CROP_MARGIN = 48;
    private static final int MONTAGE_WIDTH = 1800;
    private static final int RAIL = 5;

    private TidalReachProbe() {
    }

    public static void main(String[] arguments) {
        int code = 2;
        try {
            String mode = arguments[0];
            HydrologyWindows.Target target = HydrologyWindows.Target.parse(arguments[1], arguments[2], arguments[3], arguments[4], "false");
            File out = new File(arguments[5]);
            out.mkdirs();
            long failures = switch (mode) {
                case "metrics" -> metrics(target, out);
                case "blocks" -> blocks(target, out, arguments.length > 6 ? Integer.parseInt(arguments[6]) : 6);
                case "digest" -> digests(target, out);
                case "rivers" -> rivers(target, out);
                default -> throw new IllegalArgumentException("Unknown mode " + mode);
            };
            System.out.println(LOG + " failures=" + failures);
            code = failures == 0L ? 0 : 1;
        } catch (Throwable failure) {
            failure.printStackTrace(System.out);
        }
        System.exit(code);
    }

    static boolean tidal(Reach reach) {
        return reach.style()[0] == ReachStyle.TIDAL;
    }

    private static HydrologySettings settings(Engine engine) {
        IrisDimension dimension = engine.getDimension();
        return HydrologySettings.compile(dimension.getHydrology(), engine.getSeedManager().getBodies(), dimension.getFluidHeight(), 0, engine.getHeight());
    }

    private static HydrologyPlanHarness.Plans plans(Engine engine, HydrologySettings settings, HydrologyWindows.WindowRange range) {
        IrisHydrologyTerrainSource terrain = new IrisHydrologyTerrainSource(engine.getComplex(), engine.getDimension(), settings, MultiBurst.hydrology,
                MultiBurst.hydrology.parallelism(), HydrologyBinding.UNBOUND);
        return new HydrologyPlanHarness.Plans(settings, terrain, terrain::naturalSurface, range);
    }

    private static List<Reach> owned(HydrologyPlanHarness.Plans plans, HydrologyWindows.WindowRange windows, boolean tidal) {
        List<Reach> reaches = new ArrayList<>();
        for (Map.Entry<WindowKey, WindowPlan> entry : plans.windowPlans().entrySet()) {
            if (windows.contains(entry.getKey())) {
                for (Reach reach : entry.getValue().reaches()) {
                    if (tidal(reach) == tidal) {
                        reaches.add(reach);
                    }
                }
            }
        }
        return reaches;
    }

    private static long metrics(HydrologyWindows.Target target, File out) throws Exception {
        long failures = 0L;
        try (RealPackProbeSupport.Workspace workspace = RealPackProbeSupport.openWorkspace(target.pack(), target.dimension(), LOG);
             RealPackProbeSupport.EngineSession session = workspace.openEngine(target.seed(), false, "metrics")) {
            Engine engine = session.engine();
            HydrologySettings settings = settings(engine);
            HydrologyWindows.WindowRange windows = target.windows();
            HydrologyPlanHarness.Plans plans = plans(engine, settings, windows.grown(1));
            if (!plans.failures().isEmpty()) {
                failures += plans.failures().size();
                System.out.println(LOG + " plan failures " + plans.failures());
            }
            IrisHydrologyTerrainSource terrain = new IrisHydrologyTerrainSource(engine.getComplex(), engine.getDimension(), settings, MultiBurst.hydrology,
                    MultiBurst.hydrology.parallelism(), HydrologyBinding.UNBOUND);
            HydrologyPlanHarness.RasterCache rasters = new HydrologyPlanHarness.RasterCache(1 << 14, plans::rasterize);
            Columns columns = new Columns(rasters, terrain, plans, settings.seaLevel());
            List<Reach> arms = owned(plans, windows, true);
            List<Reach> rivers = owned(plans, windows, false);
            long seed = target.seed();
            long[] armEdges = new long[HISTOGRAM];
            long[] riverEdges = new long[HISTOGRAM];
            List<BufferedImage> crops = new ArrayList<>();
            try (PrintWriter writer = new PrintWriter(new File(out, "arms-" + seed + ".tsv"))) {
                writer.println("seed\tid\tmouthX\tmouthZ\theadX\theadZ\tlength\tsinuosity\tstraight\tturn\tmouthHalf\theadHalf\tbesideShare\twet\tdisconnected\toverlap\tlowerDry\tcut\tseamHead\tseamTerrain\tedgeP90\tedge4");
                for (Reach arm : arms) {
                    ArmStats stats = armStats(arm, columns, armEdges);
                    int last = arm.xs().length - 1;
                    writer.printf(Locale.ROOT, "%d\t%d\t%.0f\t%.0f\t%.0f\t%.0f\t%.1f\t%.3f\t%.0f\t%.1f\t%.2f\t%.2f\t%.3f\t%d\t%d\t%d\t%d\t%d\t%d\t%d\t%.0f\t%.3f%n", seed, arm.id(), arm.xs()[last], arm.zs()[last],
                            arm.xs()[0], arm.zs()[0], TidalReachMetrics.length(arm.xs(), arm.zs()), TidalReachMetrics.sinuosity(arm.xs(), arm.zs()),
                            TidalReachMetrics.straightRun(arm.xs(), arm.zs()), TidalReachMetrics.sharpestTurn(arm.xs(), arm.zs()), stats.mouthHalf, stats.headHalf,
                            stats.besideShare, stats.wet, stats.disconnected, stats.overlap, stats.lowerDry, stats.cut, stats.seamHead, stats.seamTerrain, stats.edgeP90, stats.edge4);
                    crops.add(crop(arm, columns));
                }
            }
            try (PrintWriter writer = new PrintWriter(new File(out, "rivers-" + seed + ".tsv"))) {
                writer.println("seed\tid\ttier\tlength\tsinuosity\tstraight\tturn");
                int measured = 0;
                for (Reach river : rivers) {
                    double length = TidalReachMetrics.length(river.xs(), river.zs());
                    if (length < 48D) {
                        continue;
                    }
                    writer.printf(Locale.ROOT, "%d\t%d\t%s\t%.1f\t%.3f\t%.0f\t%.1f%n", seed, river.id(), river.tier().name(), length, TidalReachMetrics.sinuosity(river.xs(), river.zs()),
                            TidalReachMetrics.straightRun(river.xs(), river.zs()), TidalReachMetrics.sharpestTurn(river.xs(), river.zs()));
                    if (measured++ < 40) {
                        edges(river, columns, riverEdges);
                    }
                }
            }
            try (PrintWriter writer = new PrintWriter(new File(out, "edges-" + seed + ".tsv"))) {
                writer.println("kind\tstep\tcount");
                for (int step = 0; step < HISTOGRAM; step++) {
                    writer.println("arm\t" + step + "\t" + armEdges[step]);
                    writer.println("river\t" + step + "\t" + riverEdges[step]);
                }
            }
            if (!crops.isEmpty()) {
                ImageIO.write(montage(crops), "png", new File(out, "plan-seed-" + seed + ".png"));
            }
            System.out.println(LOG + " seed " + seed + " arms " + arms.size());
        }
        return failures;
    }

    static final class Columns {
        private final HydrologyPlanHarness.RasterCache rasters;
        private final IrisHydrologyTerrainSource terrain;
        private final HydrologyPlanHarness.Plans plans;
        private final int sea;
        private final Map<Long, Boolean> ocean = new HashMap<>();
        private final Map<Long, Integer> natural = new HashMap<>();

        Columns(HydrologyPlanHarness.RasterCache rasters, IrisHydrologyTerrainSource terrain, HydrologyPlanHarness.Plans plans, int sea) {
            this.rasters = rasters;
            this.terrain = terrain;
            this.plans = plans;
            this.sea = sea;
        }

        ChunkWaterRaster raster(int x, int z) {
            return rasters.get(x >> 4, z >> 4);
        }

        int natural(int x, int z) {
            return natural.computeIfAbsent(key(x, z), ignored -> (int) Math.round(terrain.naturalSurface(x, z)));
        }

        boolean ocean(int x, int z) {
            return ocean.computeIfAbsent(key(x, z), ignored -> plans.ocean(x, z));
        }

        int height(int x, int z) {
            ChunkWaterRaster raster = raster(x, z);
            return raster.empty() ? natural(x, z) : raster.terrainHeight(x & 15, z & 15);
        }

        int head(int x, int z) {
            ChunkWaterRaster raster = raster(x, z);
            return raster.empty() ? Integer.MIN_VALUE : raster.waterHead(x & 15, z & 15);
        }

        WaterRole role(int x, int z) {
            ChunkWaterRaster raster = raster(x, z);
            return raster.empty() ? WaterRole.NONE : raster.role(x & 15, z & 15);
        }

        long feature(int x, int z) {
            ChunkWaterRaster raster = raster(x, z);
            return raster.empty() ? 0L : raster.featureId(x & 15, z & 15);
        }
    }

    private static final class ArmStats {
        private double mouthHalf;
        private double headHalf;
        private double besideShare;
        private int wet;
        private int disconnected;
        private int overlap;
        private int lowerDry;
        private int cut;
        private int seamHead;
        private int seamTerrain;
        private double edgeP90;
        private double edge4;
    }

    private static int[] box(Reach reach, int grow) {
        float minX = Float.POSITIVE_INFINITY;
        float minZ = Float.POSITIVE_INFINITY;
        float maxX = Float.NEGATIVE_INFINITY;
        float maxZ = Float.NEGATIVE_INFINITY;
        for (int point = 0; point < reach.xs().length; point++) {
            minX = Math.min(minX, reach.xs()[point]);
            minZ = Math.min(minZ, reach.zs()[point]);
            maxX = Math.max(maxX, reach.xs()[point]);
            maxZ = Math.max(maxZ, reach.zs()[point]);
        }
        return new int[]{(int) Math.floor(minX) - grow, (int) Math.floor(minZ) - grow, (int) Math.ceil(maxX) + grow, (int) Math.ceil(maxZ) + grow};
    }

    private static boolean inside(Reach reach, int x, int z) {
        for (int segment = 0; segment + 1 < reach.xs().length; segment++) {
            double ax = reach.xs()[segment];
            double az = reach.zs()[segment];
            double dx = reach.xs()[segment + 1] - ax;
            double dz = reach.zs()[segment + 1] - az;
            double span = dx * dx + dz * dz;
            double t = span <= 0D ? 0D : Math.clamp(((x - ax) * dx + (z - az) * dz) / span, 0D, 1D);
            double width = reach.halfWidth()[segment] + t * (reach.halfWidth()[segment + 1] - reach.halfWidth()[segment]);
            if (Math.hypot(x - (ax + t * dx), z - (az + t * dz)) <= width) {
                return true;
            }
        }
        return false;
    }

    private static ArmStats armStats(Reach arm, Columns columns, long[] edgeHistogram) {
        ArmStats stats = new ArmStats();
        float[] widths = arm.halfWidth();
        int points = widths.length;
        stats.mouthHalf = average(widths, Math.max(0, points - 1 - Math.max(1, points / 16)), points);
        stats.headHalf = average(widths, Math.min(points - 1, points / 8), Math.min(points, points / 8 + Math.max(1, points / 16)));
        int beside = 0;
        for (int point = 0; point < points; point++) {
            float radius = widths[point] + 10f;
            boolean sea = false;
            for (int direction = 0; direction < 8 && !sea; direction++) {
                double angle = 2D * Math.PI * direction / 8D;
                sea = columns.ocean((int) Math.floor(arm.xs()[point] + radius * Math.cos(angle)), (int) Math.floor(arm.zs()[point] + radius * Math.sin(angle)));
            }
            beside += sea ? 1 : 0;
        }
        stats.besideShare = beside / (double) points;
        int[] box = box(arm, Math.round(columns.plans.settings().underground().seaInlets().mouthHalfWidth().max()) + columns.plans.settings().underground().seaInlets().bankBand() + 4);
        Set<Long> wet = new HashSet<>();
        ArrayDeque<int[]> queue = new ArrayDeque<>();
        Set<Long> reached = new HashSet<>();
        for (int z = box[1]; z <= box[3]; z++) {
            for (int x = box[0]; x <= box[2]; x++) {
                boolean own = columns.feature(x, z) == arm.id() && columns.role(x, z) == WaterRole.INLET;
                if (own) {
                    wet.add(key(x, z));
                    stats.wet++;
                    if (columns.head(x, z) != columns.sea) {
                        stats.disconnected++;
                    }
                    for (int[] side : SIDES) {
                        int nx = x + side[0];
                        int nz = z + side[1];
                        if (columns.head(nx, nz) == Integer.MIN_VALUE && columns.height(nx, nz) < columns.sea && !columns.ocean(nx, nz)) {
                            stats.lowerDry++;
                        }
                        if (columns.head(nx, nz) == Integer.MIN_VALUE && columns.ocean(nx, nz) && reached.add(key(x, z))) {
                            queue.add(new int[]{x, z});
                        }
                    }
                    if (columns.ocean(x, z) && reached.add(key(x, z))) {
                        queue.add(new int[]{x, z});
                    }
                } else if (inside(arm, x, z)) {
                    stats.overlap++;
                }
                if (columns.feature(x, z) == arm.id() && (columns.role(x, z) == WaterRole.BANK || columns.role(x, z) == WaterRole.SHORE)) {
                    stats.cut = Math.max(stats.cut, columns.natural(x, z) - columns.height(x, z));
                }
            }
        }
        while (!queue.isEmpty()) {
            int[] column = queue.poll();
            for (int[] side : SIDES) {
                long next = key(column[0] + side[0], column[1] + side[1]);
                if (wet.contains(next) && reached.add(next)) {
                    queue.add(new int[]{column[0] + side[0], column[1] + side[1]});
                }
            }
        }
        stats.disconnected += wet.size() - reached.size();
        seams(arm, columns, box, stats);
        long[] edges = new long[HISTOGRAM];
        edges(arm, columns, edges);
        long total = 0L;
        long high = 0L;
        for (int step = 0; step < HISTOGRAM; step++) {
            edgeHistogram[step] += edges[step];
            total += edges[step];
            high += step >= 4 ? edges[step] : 0L;
        }
        stats.edge4 = total == 0L ? 0D : high / (double) total;
        long seen = 0L;
        for (int step = 0; step < HISTOGRAM; step++) {
            seen += edges[step];
            if (seen >= 0.9D * total) {
                stats.edgeP90 = step;
                break;
            }
        }
        return stats;
    }

    private static void seams(Reach arm, Columns columns, int[] box, ArmStats stats) {
        for (int line = Math.ceilDiv(box[0], WindowKey.SIZE) * WindowKey.SIZE; line <= box[2]; line += WindowKey.SIZE) {
            for (int z = box[1]; z <= box[3]; z++) {
                seam(arm, columns, line - 1, z, line, z, stats);
            }
        }
        for (int line = Math.ceilDiv(box[1], WindowKey.SIZE) * WindowKey.SIZE; line <= box[3]; line += WindowKey.SIZE) {
            for (int x = box[0]; x <= box[2]; x++) {
                seam(arm, columns, x, line - 1, x, line, stats);
            }
        }
    }

    private static void seam(Reach arm, Columns columns, int ax, int az, int bx, int bz, ArmStats stats) {
        if (columns.feature(ax, az) != arm.id() && columns.feature(bx, bz) != arm.id()) {
            return;
        }
        int headA = columns.head(ax, az);
        int headB = columns.head(bx, bz);
        if (headA != Integer.MIN_VALUE && headB != Integer.MIN_VALUE && headA != headB) {
            stats.seamHead++;
        }
        int shaped = Math.abs(columns.height(ax, az) - columns.height(bx, bz));
        int natural = Math.abs(columns.natural(ax, az) - columns.natural(bx, bz));
        if (shaped - natural >= 4) {
            stats.seamTerrain++;
        }
    }

    private static double average(float[] values, int from, int to) {
        double sum = 0D;
        for (int index = from; index < to; index++) {
            sum += values[index];
        }
        return to <= from ? 0D : sum / (to - from);
    }

    private static void edges(Reach reach, Columns columns, long[] histogram) {
        float[] xs = reach.xs();
        float[] zs = reach.zs();
        double next = 0D;
        double along = 0D;
        for (int point = 1; point < xs.length; point++) {
            along += Math.hypot(xs[point] - xs[point - 1], zs[point] - zs[point - 1]);
            if (along < next) {
                continue;
            }
            next = along + 2D;
            double dx = xs[point] - xs[point - 1];
            double dz = zs[point] - zs[point - 1];
            double span = Math.hypot(dx, dz);
            if (span <= 0D) {
                continue;
            }
            for (int side = -1; side <= 1; side += 2) {
                double nx = -dz / span * side;
                double nz = dx / span * side;
                edge(reach, columns, xs[point], zs[point], nx, nz, reach.halfWidth()[point], histogram);
            }
        }
    }

    private static void edge(Reach reach, Columns columns, double x, double z, double nx, double nz, double half, long[] histogram) {
        int surface = Integer.MIN_VALUE;
        for (double out = Math.max(0D, half - 3D); out <= half + 10D; out += 0.5D) {
            int cx = (int) Math.floor(x + nx * out);
            int cz = (int) Math.floor(z + nz * out);
            int head = columns.head(cx, cz);
            if (head != Integer.MIN_VALUE) {
                surface = head;
                if (columns.feature(cx, cz) != reach.id()) {
                    return;
                }
                continue;
            }
            if (surface == Integer.MIN_VALUE) {
                return;
            }
            if (columns.ocean(cx, cz)) {
                return;
            }
            histogram[Math.clamp(columns.height(cx, cz) - surface, 0, HISTOGRAM - 1)]++;
            return;
        }
    }

    private static BufferedImage crop(Reach arm, Columns columns) {
        int[] box = box(arm, CROP_MARGIN);
        int width = box[2] - box[0] + 1;
        int depth = box[3] - box[1] + 1;
        BufferedImage image = new BufferedImage(width, depth, BufferedImage.TYPE_INT_RGB);
        int[] heights = new int[width * depth];
        int[] heads = new int[width * depth];
        for (int z = 0; z < depth; z++) {
            for (int x = 0; x < width; x++) {
                heights[z * width + x] = columns.height(box[0] + x, box[1] + z);
                heads[z * width + x] = columns.head(box[0] + x, box[1] + z);
            }
        }
        for (int z = 0; z < depth; z++) {
            for (int x = 0; x < width; x++) {
                int height = heights[z * width + x];
                int head = heads[z * width + x];
                int rgb;
                if (head != Integer.MIN_VALUE || height < columns.sea) {
                    int deep = Math.clamp((head == Integer.MIN_VALUE ? columns.sea : head) - height, 0, 12);
                    boolean own = head != Integer.MIN_VALUE && !columns.ocean(box[0] + x, box[1] + z) && columns.feature(box[0] + x, box[1] + z) == arm.id();
                    int blue = 200 - deep * 8;
                    rgb = own ? new Color(60, 120, blue).getRGB() : new Color(25, 50 + (12 - deep) * 4, blue - 40).getRGB();
                } else {
                    int up = z > 0 ? heights[(z - 1) * width + x] : height;
                    int left = x > 0 ? heights[z * width + x - 1] : height;
                    double shade = Math.clamp(0.82D + 0.09D * ((up - height) + (left - height)), 0.35D, 1.25D);
                    rgb = hill(height - columns.sea, shade);
                }
                image.setRGB(x, z, rgb);
            }
        }
        BufferedImage framed = new BufferedImage(width + 60, depth + 16, BufferedImage.TYPE_INT_RGB);
        Graphics2D graphics = framed.createGraphics();
        graphics.drawImage(image, 50, 16, null);
        graphics.setColor(Color.WHITE);
        graphics.drawString(box[0] + "," + box[1] + " len " + Math.round(TidalReachMetrics.length(arm.xs(), arm.zs())), 50, 12);
        for (int z = (box[1] + 31) / 32 * 32; z <= box[3]; z += 32) {
            graphics.drawString(Integer.toString(z), 2, z - box[1] + 20);
            graphics.drawLine(46, z - box[1] + 16, 49, z - box[1] + 16);
        }
        graphics.dispose();
        return framed;
    }

    private static int hill(int above, double shade) {
        int red;
        int green;
        int blue;
        if (above < 3) {
            red = 196;
            green = 186;
            blue = 130;
        } else if (above < 28) {
            red = 104 + above * 2;
            green = 136 - above;
            blue = 70;
        } else {
            int rock = Math.min(240, 130 + above);
            red = rock;
            green = rock;
            blue = rock;
        }
        return new Color(Math.clamp((int) (red * shade), 0, 255), Math.clamp((int) (green * shade), 0, 255), Math.clamp((int) (blue * shade), 0, 255)).getRGB();
    }

    private static BufferedImage montage(List<BufferedImage> crops) {
        int x = 0;
        int y = 0;
        int row = 0;
        List<int[]> places = new ArrayList<>();
        for (BufferedImage crop : crops) {
            if (x + crop.getWidth() > MONTAGE_WIDTH && x > 0) {
                x = 0;
                y += row + RAIL;
                row = 0;
            }
            places.add(new int[]{x, y});
            x += crop.getWidth() + RAIL;
            row = Math.max(row, crop.getHeight());
        }
        BufferedImage image = new BufferedImage(MONTAGE_WIDTH, y + row, BufferedImage.TYPE_INT_RGB);
        Graphics2D graphics = image.createGraphics();
        for (int index = 0; index < crops.size(); index++) {
            graphics.drawImage(crops.get(index), places.get(index)[0], places.get(index)[1], null);
        }
        graphics.dispose();
        return image;
    }

    private static long blocks(HydrologyWindows.Target target, File out, int threads) throws Exception {
        long failures = 0L;
        try (RealPackProbeSupport.Workspace workspace = RealPackProbeSupport.openWorkspace(target.pack(), target.dimension(), LOG)) {
            Map<Long, Long> forward;
            Set<Long> chunks = new TreeSet<>();
            int height;
            int sea;
            long seed = target.seed();
            try (RealPackProbeSupport.EngineSession session = workspace.openEngine(seed, false, "blocks")) {
                Engine engine = session.engine();
                HydrologySettings settings = settings(engine);
                HydrologyPlanHarness.Plans plans = plans(engine, settings, target.windows().grown(1));
                List<Reach> arms = owned(plans, target.windows(), true);
                for (Reach arm : arms) {
                    int[] box = box(arm, 48);
                    for (int chunkZ = box[1] >> 4; chunkZ <= box[3] >> 4; chunkZ++) {
                        for (int chunkX = box[0] >> 4; chunkX <= box[2] >> 4; chunkX++) {
                            chunks.add(key(chunkX, chunkZ));
                        }
                    }
                }
                height = engine.getTarget().getHeight();
                sea = engine.getDimension().getFluidHeight();
                Map<Long, Slab> slabs = new ConcurrentHashMap<>();
                forward = generate(engine, new ArrayList<>(chunks), threads, height, sea, slabs);
                HydrologyPlanHarness.RasterCache rasters = new HydrologyPlanHarness.RasterCache(1 << 14, plans::rasterize);
                List<BufferedImage> crops = new ArrayList<>();
                long[] totals = new long[6];
                try (PrintWriter writer = new PrintWriter(new File(out, "blocks-" + seed + ".tsv"))) {
                    writer.println("seed\tid\twetColumns\tleaks\tcovered\tnotSea\tnonSource\tedge4\tedgeSamples");
                    for (Reach arm : arms) {
                        long[] scan = scan(arm, rasters, slabs, sea, plans);
                        writer.println(seed + "\t" + arm.id() + "\t" + scan[0] + "\t" + scan[1] + "\t" + scan[2] + "\t" + scan[3] + "\t" + scan[4] + "\t" + scan[5] + "\t" + scan[6]);
                        totals[0] += scan[1];
                        totals[1] += scan[2];
                        totals[2] += scan[3];
                        crops.add(blockCrop(arm, slabs, sea));
                    }
                }
                if (!crops.isEmpty()) {
                    ImageIO.write(montage(crops), "png", new File(out, "blocks-seed-" + seed + ".png"));
                }
                failures += totals[0] + totals[1] + totals[2];
                System.out.println(LOG + " seed " + seed + " arms " + arms.size() + " chunks " + chunks.size() + " leaks " + totals[0] + " covered " + totals[1] + " notSea " + totals[2]);
                List<Throwable> reports = RealPackProbeSupport.settleAndDrain(engine);
                if (!reports.isEmpty()) {
                    RealPackProbeSupport.printReports(LOG, "blocks", reports);
                    failures += reports.size();
                }
            }
            List<Long> reversed = new ArrayList<>(chunks);
            Collections.reverse(reversed);
            long mismatches = 0L;
            try (RealPackProbeSupport.EngineSession second = workspace.openEngine(seed, false, "blocks-reverse")) {
                Map<Long, Long> backward = generate(second.engine(), reversed, Math.max(1, threads / 2), height, sea, new ConcurrentHashMap<>());
                for (Map.Entry<Long, Long> entry : forward.entrySet()) {
                    if (!entry.getValue().equals(backward.get(entry.getKey()))) {
                        mismatches++;
                    }
                }
            }
            failures += mismatches;
            System.out.println(LOG + " seed " + seed + " orderMismatches " + mismatches + " of " + forward.size());
        }
        return failures;
    }

    private static Map<Long, Long> generate(Engine engine, List<Long> chunks, int threads, int height, int sea, Map<Long, Slab> slabs) throws Exception {
        Map<Long, Long> hashes = new ConcurrentHashMap<>();
        ExecutorService pool = Executors.newFixedThreadPool(threads);
        try {
            List<Future<?>> futures = new ArrayList<>(chunks.size());
            for (long chunk : chunks) {
                futures.add(pool.submit(() -> {
                    int chunkX = (int) (chunk >> 32);
                    int chunkZ = (int) chunk;
                    Hunk<NativeBlockState> blocks = Hunk.newArrayHunk(16, height, 16);
                    Hunk<NativeBiome> biomes = Hunk.newArrayHunk(16, height, 16);
                    engine.generate(chunkX << 4, chunkZ << 4, blocks, biomes, false);
                    hashes.put(chunk, hash(blocks, biomes, height));
                    slabs.put(chunk, Slab.capture(blocks, height, sea));
                    return null;
                }));
            }
            for (Future<?> future : futures) {
                future.get();
            }
        } finally {
            pool.shutdown();
        }
        return hashes;
    }

    private static long hash(Hunk<NativeBlockState> blocks, Hunk<NativeBiome> biomes, int height) {
        long hash = 0xcbf29ce484222325L;
        for (int x = 0; x < 16; x++) {
            for (int z = 0; z < 16; z++) {
                for (int y = 0; y < height; y++) {
                    NativeBlockState state = blocks.get(x, y, z);
                    NativeBiome biome = biomes.get(x, y, z);
                    hash = (hash ^ (state == null ? 0 : state.key().hashCode())) * 0x100000001b3L;
                    hash = (hash ^ (biome == null ? 0 : biome.key().hashCode())) * 0x100000001b3L;
                }
            }
        }
        return hash;
    }

    private static final class Slab {
        private final int[] top = new int[256];
        private final int[] ground = new int[256];
        private final int[] colors = new int[256];
        private final byte[] seaCell = new byte[256];
        private final byte[] flowing = new byte[256];
        private final byte[][] rows = new byte[8][256];
        private static final int ROW_BOTTOM = -6;

        static Slab capture(Hunk<NativeBlockState> blocks, int height, int sea) {
            Slab slab = new Slab();
            for (int lz = 0; lz < 16; lz++) {
                for (int lx = 0; lx < 16; lx++) {
                    int column = lz << 4 | lx;
                    slab.top[column] = -1;
                    slab.ground[column] = -1;
                    for (int y = height - 1; y >= 0; y--) {
                        NativeBlockState state = blocks.get(lx, y, lz);
                        if (state == null || state.isAir()) {
                            continue;
                        }
                        if (slab.top[column] < 0) {
                            slab.top[column] = y;
                            slab.colors[column] = color(state);
                        }
                        if (state.isSolid() && !foliage(state)) {
                            slab.ground[column] = y;
                            break;
                        }
                    }
                    NativeBlockState cell = blocks.get(lx, sea, lz);
                    slab.seaCell[column] = kind(cell);
                    if (cell != null && "minecraft:water".equals(IrisProceduralBlocks.materialKey(cell))) {
                        String level = IrisProceduralBlocks.propertyValue(cell, "level");
                        slab.flowing[column] = (byte) (level == null || "0".equals(level) ? 0 : 1);
                    }
                    for (int row = 0; row < slab.rows.length; row++) {
                        slab.rows[row][column] = kind(blocks.get(lx, sea + ROW_BOTTOM + row, lz));
                    }
                }
            }
            return slab;
        }

        private static byte kind(NativeBlockState state) {
            if (state == null || state.isAir()) {
                return 0;
            }
            String material = IrisProceduralBlocks.materialKey(state);
            if (material.equals("minecraft:water") || material.equals("minecraft:lava") || material.contains("ice") || state.key().contains("waterlogged=true") || state.key().contains("seagrass") || state.key().contains("kelp")) {
                return 1;
            }
            return (byte) (state.isSolid() ? 2 : 3);
        }

        private static boolean foliage(NativeBlockState state) {
            String key = state.key();
            return key.contains("leaves") || key.contains("_log") || key.contains("_wood") || key.contains("mushroom_block") || key.contains("stem")
                    || key.contains("vine") || key.contains("lichen") || key.contains("lily_pad") || key.startsWith("minecraft:snow");
        }

        private static int color(NativeBlockState state) {
            String key = IrisProceduralBlocks.materialKey(state);
            if (key.contains("water")) {
                return 0x3060c0;
            }
            if (key.contains("ice")) {
                return 0xa0c0f0;
            }
            if (key.contains("grass_block") || key.contains("moss")) {
                return 0x60a040;
            }
            if (key.contains("sand")) {
                return 0xd8cc90;
            }
            if (key.contains("gravel")) {
                return 0x908880;
            }
            if (key.contains("mud") || key.contains("clay")) {
                return 0x706860;
            }
            if (key.contains("dirt") || key.contains("podzol") || key.contains("mycelium")) {
                return 0x806040;
            }
            if (key.contains("leaves")) {
                return 0x306820;
            }
            if (key.contains("log") || key.contains("wood")) {
                return 0x604020;
            }
            if (key.contains("snow")) {
                return 0xf0f0f0;
            }
            if (key.contains("stone") || key.contains("deepslate") || key.contains("andesite") || key.contains("diorite") || key.contains("granite") || key.contains("tuff")) {
                return 0x888888;
            }
            return 0x70a050;
        }
    }

    private static long[] scan(Reach arm, HydrologyPlanHarness.RasterCache rasters, Map<Long, Slab> slabs, int sea, HydrologyPlanHarness.Plans plans) {
        int[] box = box(arm, 32);
        long[] scan = new long[7];
        for (int z = box[1]; z <= box[3]; z++) {
            for (int x = box[0]; x <= box[2]; x++) {
                ChunkWaterRaster raster = rasters.get(x >> 4, z >> 4);
                if (raster.empty() || raster.role(x & 15, z & 15) != WaterRole.INLET || raster.featureId(x & 15, z & 15) != arm.id()) {
                    continue;
                }
                Slab slab = slabs.get(key(x >> 4, z >> 4));
                if (slab == null) {
                    continue;
                }
                int column = (z & 15) << 4 | (x & 15);
                scan[0]++;
                if (slab.ground[column] > sea) {
                    scan[2]++;
                }
                if (slab.seaCell[column] != 1 && !plans.ocean(x, z)) {
                    scan[3]++;
                }
                scan[4] += slab.flowing[column];
                for (int row = 0; row <= -Slab.ROW_BOTTOM; row++) {
                    if (slab.rows[row][column] != 1) {
                        continue;
                    }
                    for (int[] side : SIDES) {
                        Slab near = slabs.get(key((x + side[0]) >> 4, (z + side[1]) >> 4));
                        if (near != null && near.rows[row][(z + side[1] & 15) << 4 | (x + side[0] & 15)] == 0) {
                            scan[1]++;
                        }
                    }
                }
                for (int[] side : SIDES) {
                    int nx = x + side[0];
                    int nz = z + side[1];
                    Slab near = slabs.get(key(nx >> 4, nz >> 4));
                    if (near != null && near.seaCell[(nz & 15) << 4 | (nx & 15)] != 1) {
                        scan[6]++;
                        scan[5] += near.ground[(nz & 15) << 4 | (nx & 15)] - sea >= 4 ? 1 : 0;
                    }
                }
            }
        }
        return scan;
    }

    private static BufferedImage blockCrop(Reach arm, Map<Long, Slab> slabs, int sea) {
        int[] box = box(arm, 32);
        int width = box[2] - box[0] + 1;
        int depth = box[3] - box[1] + 1;
        BufferedImage image = new BufferedImage(width * 2, depth * 2, BufferedImage.TYPE_INT_RGB);
        for (int z = 0; z < depth; z++) {
            for (int x = 0; x < width; x++) {
                int wx = box[0] + x;
                int wz = box[1] + z;
                Slab slab = slabs.get(key(wx >> 4, wz >> 4));
                int rgb = 0;
                if (slab != null) {
                    int column = (wz & 15) << 4 | (wx & 15);
                    Slab north = slabs.get(key(wx >> 4, (wz - 1) >> 4));
                    Slab west = slabs.get(key((wx - 1) >> 4, wz >> 4));
                    int top = slab.top[column];
                    int up = north == null ? top : north.top[(wz - 1 & 15) << 4 | (wx & 15)];
                    int left = west == null ? top : west.top[(wz & 15) << 4 | (wx - 1 & 15)];
                    double shade = Math.clamp(0.85D + 0.08D * ((up - top) + (left - top)), 0.4D, 1.3D);
                    Color base = new Color(slab.colors[column]);
                    rgb = new Color(Math.clamp((int) (base.getRed() * shade), 0, 255), Math.clamp((int) (base.getGreen() * shade), 0, 255),
                            Math.clamp((int) (base.getBlue() * shade), 0, 255)).getRGB();
                }
                for (int dz = 0; dz < 2; dz++) {
                    for (int dx = 0; dx < 2; dx++) {
                        image.setRGB(x * 2 + dx, z * 2 + dz, rgb);
                    }
                }
            }
        }
        BufferedImage framed = new BufferedImage(image.getWidth(), image.getHeight() + 14, BufferedImage.TYPE_INT_RGB);
        Graphics2D graphics = framed.createGraphics();
        graphics.drawImage(image, 0, 14, null);
        graphics.setColor(Color.WHITE);
        graphics.drawString(box[0] + "," + box[1] + " len " + Math.round(TidalReachMetrics.length(arm.xs(), arm.zs())), 2, 11);
        graphics.dispose();
        return framed;
    }

    private static long digests(HydrologyWindows.Target target, File out) throws Exception {
        long failures = 0L;
        try (RealPackProbeSupport.Workspace workspace = RealPackProbeSupport.openWorkspace(target.pack(), target.dimension(), LOG);
             RealPackProbeSupport.EngineSession session = workspace.openEngine(target.seed(), false, "digest")) {
            Engine engine = session.engine();
            HydrologySettings settings = settings(engine);
            IrisHydrologyTerrainSource terrain = new IrisHydrologyTerrainSource(engine.getComplex(), engine.getDimension(), settings, MultiBurst.hydrology,
                    MultiBurst.hydrology.parallelism(), HydrologyBinding.UNBOUND);
            String first = digest(settings, terrain, target.windows(), 1, false);
            String again = digest(settings, terrain, target.windows(), 1, false);
            String threaded = digest(settings, terrain, target.windows(), 4, false);
            String reversed = digest(settings, terrain, target.windows(), 1, true);
            failures += first.equals(again) ? 0 : 1;
            failures += first.equals(threaded) ? 0 : 1;
            failures += first.equals(reversed) ? 0 : 1;
            long viewFailures = 0L;
            failures += viewFailures;
            try (PrintWriter writer = new PrintWriter(new File(out, "digest-" + target.seed() + ".tsv"))) {
                writer.println(target.seed() + "\t" + first + "\t" + again + "\t" + threaded + "\t" + reversed + "\t" + viewFailures);
            }
            System.out.println(LOG + " seed " + target.seed() + " digest " + first + " repeat " + again + " threads " + threaded + " reversed " + reversed + " viewFailures " + viewFailures);
        }
        return failures;
    }

    private static long rivers(HydrologyWindows.Target target, File out) throws Exception {
        try (RealPackProbeSupport.Workspace workspace = RealPackProbeSupport.openWorkspace(target.pack(), target.dimension(), LOG);
             RealPackProbeSupport.EngineSession session = workspace.openEngine(target.seed(), false, "rivers");
             PrintWriter writer = new PrintWriter(new File(out, "seed-" + target.seed() + ".txt"))) {
            Engine engine = session.engine();
            HydrologyPlanHarness.Plans plans = plans(engine, settings(engine), target.windows());
            writer.println("failures " + plans.failures());
            for (Map.Entry<WindowKey, WindowPlan> entry : plans.windowPlans().entrySet()) {
                String key = entry.getKey().wx() + " " + entry.getKey().wz();
                for (Reach reach : entry.getValue().reaches()) {
                    if (!tidal(reach)) {
                        writer.println("R " + key + " " + reach.id() + " " + reach.xs()[0] + " " + reach.zs()[0] + " " + stable(reach));
                    }
                }
                for (Pond pond : entry.getValue().ponds()) {
                    writer.println("P " + key + " " + pond.id() + " " + Arrays.toString(new long[]{pond.floorX(), pond.floorZ(), pond.level(), pond.outletReachId(), pond.kind().ordinal()}));
                }
            }
            return plans.failures().size();
        }
    }

    private static int stable(Reach reach) {
        int hash = 31 * Long.hashCode(reach.id()) + Long.hashCode(reach.downstreamId());
        hash = 31 * hash + Float.floatToIntBits(reach.catchment());
        hash = 31 * hash + reach.tier().name().hashCode();
        hash = 31 * hash + Arrays.hashCode(reach.xs());
        hash = 31 * hash + Arrays.hashCode(reach.zs());
        hash = 31 * hash + Arrays.hashCode(reach.grade());
        hash = 31 * hash + Arrays.hashCode(reach.halfWidth());
        hash = 31 * hash + Arrays.hashCode(reach.depth());
        for (int point = 0; point < reach.style().length; point++) {
            hash = 31 * hash + reach.style()[point].name().hashCode();
        }
        return 31 * hash + Arrays.hashCode(reach.straightFalls());
    }

    private static String digest(HydrologySettings settings, IrisHydrologyTerrainSource terrain, HydrologyWindows.WindowRange range, int threads, boolean reversed) throws Exception {
        HydrologyPlanners planners = HydrologyPlanners.create(settings, terrain);
        Map<RegionKey, RegionPlan> regions = new TreeMap<>(Comparator.comparingInt(RegionKey::rz).thenComparingInt(RegionKey::rx));
        List<WindowKey> keys = new ArrayList<>(range.keys());
        for (WindowKey key : keys) {
            for (RegionKey region : WindowPlanner.lakeRegions(key)) {
                regions.computeIfAbsent(region, planners.regions()::plan);
            }
        }
        List<WindowKey> order = new ArrayList<>(keys);
        if (reversed) {
            Collections.reverse(order);
        }
        Map<WindowKey, byte[]> encoded = new ConcurrentHashMap<>();
        ExecutorService pool = Executors.newFixedThreadPool(threads);
        try {
            List<Future<?>> futures = new ArrayList<>();
            for (WindowKey key : order) {
                futures.add(pool.submit(() -> {
                    List<RegionPlan> needed = new ArrayList<>();
                    for (RegionKey region : WindowPlanner.lakeRegions(key)) {
                        needed.add(regions.get(region));
                    }
                    encoded.put(key, HydrologyPlanCodec.encode(settings, planners.windows().plan(key, needed)));
                    return null;
                }));
                if (threads == 1) {
                    futures.get(futures.size() - 1).get();
                }
            }
            for (Future<?> future : futures) {
                future.get();
            }
        } finally {
            pool.shutdown();
        }
        MessageDigest sha = MessageDigest.getInstance("SHA-256");
        for (WindowKey key : keys) {
            sha.update(encoded.get(key));
        }
        return HexFormat.of().formatHex(sha.digest(), 0, 8);
    }

    private static long key(int x, int z) {
        return ((long) x << 32) | (z & 0xFFFFFFFFL);
    }
}
