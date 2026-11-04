package art.arcane.iris.probe;

import art.arcane.iris.generation.concurrent.MultiBurst;
import art.arcane.iris.generation.hydrology.engine.HydrologyBinding;
import art.arcane.iris.generation.hydrology.engine.IrisHydrologyTerrainSource;
import art.arcane.iris.generation.hydrology.plan.HydrologySettings;
import art.arcane.iris.generation.hydrology.plan.WindowKey;
import art.arcane.iris.generation.hydrology.plan.WindowPlan;
import art.arcane.iris.generation.hydrology.raster.WaterRole;
import art.arcane.iris.generation.hydrology.underground.Grotto;
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
import java.io.FileOutputStream;
import java.io.OutputStreamWriter;
import java.io.PrintWriter;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.TreeSet;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.zip.GZIPOutputStream;

public final class MouthProbe {
    private static final String LOG = "[mouth-probe]";
    private static final int[][] SIDES = {{1, 0}, {-1, 0}, {0, 1}, {0, -1}};
    private static final int LEVELS = 64;
    private static final int BELOW = 16;
    private static final int SCALE = 5;
    private static final int AIR = 0xc8dcff;

    private MouthProbe() {
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
                case "dump" -> dump(target, out, Integer.parseInt(arguments[6]), Integer.parseInt(arguments[7]));
                case "sections" -> sections(target, out, arguments);
                case "digest" -> digest(target, out);
                case "chunks" -> chunks(target, out);
                default -> throw new IllegalArgumentException("Unknown mode " + mode);
            };
            System.out.println(LOG + " failures=" + failures);
            code = failures == 0L ? 0 : 1;
        } catch (Throwable failure) {
            failure.printStackTrace(System.out);
        }
        System.exit(code);
    }

    private static HydrologySettings settings(Engine engine) {
        IrisDimension dimension = engine.getDimension();
        return HydrologySettings.compile(dimension.getHydrology(), engine.getSeedManager().getBodies(), dimension.getFluidHeight(), 0, engine.getHeight());
    }

    private static HydrologyPlanHarness.Plans plans(Engine engine, HydrologySettings settings, HydrologyWindows.WindowRange range) {
        return new HydrologyPlanHarness.Plans(settings, terrain(engine, settings), terrain(engine, settings)::naturalSurface, range);
    }

    private static IrisHydrologyTerrainSource terrain(Engine engine, HydrologySettings settings) {
        return new IrisHydrologyTerrainSource(engine.getComplex(), engine.getDimension(), settings, MultiBurst.hydrology,
                MultiBurst.hydrology.parallelism(), HydrologyBinding.UNBOUND);
    }

    private static List<Reach> mouths(HydrologyPlanHarness.Plans plans, HydrologyWindows.WindowRange windows) {
        List<Reach> mouths = new ArrayList<>();
        for (Map.Entry<WindowKey, WindowPlan> entry : plans.windowPlans().entrySet()) {
            if (windows.contains(entry.getKey())) {
                for (Reach reach : entry.getValue().reaches()) {
                    if (MouthMetrics.seaMouth(reach) && !TidalReachProbe.tidal(reach)) {
                        mouths.add(reach);
                    }
                }
            }
        }
        return mouths;
    }

    private static TidalReachProbe.Columns columns(Engine engine, HydrologySettings settings, HydrologyPlanHarness.Plans plans) {
        HydrologyPlanHarness.RasterCache rasters = new HydrologyPlanHarness.RasterCache(1 << 14, plans::rasterize);
        return new TidalReachProbe.Columns(rasters, terrain(engine, settings), plans, settings.seaLevel());
    }

    private static boolean openSea(TidalReachProbe.Columns columns, int x, int z, int sea) {
        int head = columns.head(x, z);
        return head == sea || head == Integer.MIN_VALUE && columns.ocean(x, z) && columns.height(x, z) < sea;
    }

    private static int[] rasterEdge(Reach reach, TidalReachProbe.Columns columns, int sea) {
        double[] along = MouthMetrics.along(reach);
        double end = along[along.length - 1];
        int worst = 0;
        int worstWidth = 0;
        int worstX = 0;
        int worstZ = 0;
        Set<Long> seen = new TreeSet<>();
        for (int point = 0; point < along.length; point++) {
            if (end - along[point] > MouthMetrics.APPROACH) {
                continue;
            }
            int reachOut = (int) Math.ceil(reach.halfWidth()[point]) + 2;
            int cx = Math.round(reach.xs()[point]);
            int cz = Math.round(reach.zs()[point]);
            for (int z = cz - reachOut; z <= cz + reachOut; z++) {
                for (int x = cx - reachOut; x <= cx + reachOut; x++) {
                    if (!seen.add(((long) x << 32) ^ (z & 0xffffffffL))) {
                        continue;
                    }
                    int head = columns.head(x, z);
                    if (head <= sea) {
                        continue;
                    }
                    for (int[] side : SIDES) {
                        if (openSea(columns, x + side[0], z + side[1], sea)) {
                            int drop = head - sea;
                            if (drop > worst) {
                                worst = drop;
                                worstX = x;
                                worstZ = z;
                            }
                            if (drop > 2) {
                                worstWidth++;
                            }
                            break;
                        }
                    }
                }
            }
        }
        return new int[]{worst, worstWidth, worstX, worstZ};
    }

    private static int[] coast(Reach reach, TidalReachProbe.Columns columns, int sea) {
        int high = Math.max(0, MouthMetrics.lastAboveSea(reach, sea));
        int next = Math.min(reach.xs().length - 1, high + 1);
        int before = Math.max(0, high - 1);
        double dx = reach.xs()[next] - reach.xs()[before];
        double dz = reach.zs()[next] - reach.zs()[before];
        double span = Math.max(1e-3D, Math.hypot(dx, dz));
        double nx = -dz / span;
        double nz = dx / span;
        double half = reach.halfWidth()[high];
        int[] peak = new int[2];
        for (int side = 0; side < 2; side++) {
            double sign = side == 0 ? 1D : -1D;
            int top = Integer.MIN_VALUE;
            for (double out = half + 2D; out <= half + 32D; out += 1D) {
                top = Math.max(top, columns.natural((int) Math.floor(reach.xs()[high] + sign * nx * out), (int) Math.floor(reach.zs()[high] + sign * nz * out)));
            }
            peak[side] = top - sea;
        }
        int ground = columns.natural(Math.round(reach.xs()[high]), Math.round(reach.zs()[high])) - sea;
        return new int[]{Math.min(peak[0], peak[1]), Math.max(peak[0], peak[1]), ground};
    }

    private static long metrics(HydrologyWindows.Target target, File out) throws Exception {
        try (RealPackProbeSupport.Workspace workspace = RealPackProbeSupport.openWorkspace(target.pack(), target.dimension(), LOG);
             RealPackProbeSupport.EngineSession session = workspace.openEngine(target.seed(), false, "metrics");
             PrintWriter writer = new PrintWriter(new File(out, "mouths-" + target.seed() + ".tsv"))) {
            Engine engine = session.engine();
            HydrologySettings settings = settings(engine);
            int sea = settings.seaLevel();
            HydrologyPlanHarness.Plans plans = plans(engine, settings, target.windows().grown(1));
            TidalReachProbe.Columns columns = columns(engine, settings, plans);
            writer.println("seed\tid\ttier\tmouthX\tmouthZ\tlength\tfinalDrop\tseaRun\tlargestStep64\tlargestStep32\trasterDrop\trasterDamColumns\tlowBank\thighBank\tground\tfall\thighGrade32\tpond\tworstX\tworstZ\tworstLinked");
            int count = 0;
            Map<Long, Long> downstream = new HashMap<>();
            for (WindowPlan plan : plans.windowPlans().values()) {
                for (Reach reach : plan.reaches()) {
                    downstream.put(reach.id(), reach.downstreamId());
                }
            }
            for (Reach reach : mouths(plans, target.windows())) {
                int last = reach.xs().length - 1;
                int[] edge = rasterEdge(reach, columns, sea);
                int[] coast = coast(reach, columns, sea);
                boolean fall = false;
                for (ReachStyle style : reach.style()) {
                    fall |= style == ReachStyle.WATERFALL;
                }
                double[] along = MouthMetrics.along(reach);
                writer.printf(Locale.ROOT, "%d\t%d\t%s\t%.0f\t%.0f\t%.0f\t%d\t%.1f\t%d\t%d\t%d\t%d\t%d\t%d\t%d\t%b\t%d\t%b\t%d\t%d\t%b%n", target.seed(), reach.id(), reach.tier().name(),
                        reach.xs()[last], reach.zs()[last], along[last], MouthMetrics.finalDrop(reach, sea), MouthMetrics.seaRun(reach, sea),
                        MouthMetrics.largestStep(reach, sea, 64D), MouthMetrics.largestStep(reach, sea, 32D), edge[0], edge[1], coast[0], coast[1], coast[2], fall, highGrade(reach, sea), nearOutlet(reach), edge[2], edge[3], linked(columns.feature(edge[2], edge[3]), reach.id(), downstream));
                count++;
            }
            System.out.println(LOG + " seed " + target.seed() + " mouths " + count + " planFailures " + plans.failures().size());
            return plans.failures().size();
        }
    }

    private static boolean linked(long feature, long mouth, Map<Long, Long> downstream) {
        long at = feature;
        for (int hop = 0; hop < 64 && at != 0L; hop++) {
            if (at == mouth) {
                return true;
            }
            at = downstream.getOrDefault(at, 0L);
        }
        return false;
    }

    private static int highGrade(Reach reach, int sea) {
        double[] along = MouthMetrics.along(reach);
        int firstMouth = reach.style().length - 1;
        while (firstMouth > 0 && reach.style()[firstMouth - 1] == ReachStyle.MOUTH) {
            firstMouth--;
        }
        int high = 0;
        for (int point = firstMouth; point >= 0 && along[firstMouth] - along[point] <= 32D; point--) {
            high = Math.max(high, reach.grade()[point] - sea);
        }
        return high;
    }

    private static boolean nearOutlet(Reach reach) {
        for (ReachStyle style : reach.style()) {
            if (style == ReachStyle.POND_OUTLET) {
                return true;
            }
        }
        return false;
    }

    private static Reach nearest(List<Reach> reaches, int x, int z) {
        return reaches.stream().min(Comparator.comparingDouble((Reach reach) -> {
            int last = reach.xs().length - 1;
            return Math.hypot(reach.xs()[last] - x, reach.zs()[last] - z);
        })).orElseThrow();
    }

    private static long dump(HydrologyWindows.Target target, File out, int x, int z) throws Exception {
        try (RealPackProbeSupport.Workspace workspace = RealPackProbeSupport.openWorkspace(target.pack(), target.dimension(), LOG);
             RealPackProbeSupport.EngineSession session = workspace.openEngine(target.seed(), false, "dump");
             PrintWriter writer = new PrintWriter(new File(out, "dump-" + target.seed() + "-" + x + "_" + z + ".txt"))) {
            Engine engine = session.engine();
            HydrologySettings settings = settings(engine);
            int sea = settings.seaLevel();
            HydrologyPlanHarness.Plans plans = plans(engine, settings, target.windows().grown(1));
            TidalReachProbe.Columns columns = columns(engine, settings, plans);
            Reach reach = nearest(mouths(plans, target.windows()), x, z);
            double[] along = MouthMetrics.along(reach);
            double end = along[along.length - 1];
            writer.println("sea " + sea + " reach " + reach.id() + " tier " + reach.tier().name() + " estuary " + reach.tier().estuary() + " points " + along.length + " length " + end + " stepHeight " + reach.tier().stepHeight());
            writer.println("toEnd\tx\tz\tgrade\tsnap\thalf\tdepth\tstyle\tnatural\tnaturalL\tnaturalR\toceanIntent\tcolHead\tcolTerrain\trole");
            for (int point = 0; point < along.length; point++) {
                if (end - along[point] > 256D) {
                    continue;
                }
                int cx = (int) Math.floor(reach.xs()[point]);
                int cz = (int) Math.floor(reach.zs()[point]);
                int before = Math.max(0, point - 1);
                int next = Math.min(along.length - 1, point + 1);
                double dx = reach.xs()[next] - reach.xs()[before];
                double dz = reach.zs()[next] - reach.zs()[before];
                double span = Math.max(1e-3D, Math.hypot(dx, dz));
                double reachOut = reach.halfWidth()[point] + 3D;
                int lx = (int) Math.floor(cx - dz / span * reachOut);
                int lz = (int) Math.floor(cz + dx / span * reachOut);
                int rx = (int) Math.floor(cx + dz / span * reachOut);
                int rz = (int) Math.floor(cz - dx / span * reachOut);
                writer.printf(Locale.ROOT, "%.1f\t%.1f\t%.1f\t%d\t%d\t%.1f\t%.1f\t%s\t%d\t%d\t%d\t%b\t%d\t%d\t%s%n", end - along[point], reach.xs()[point], reach.zs()[point],
                        reach.grade()[point] - sea, MouthMetrics.snapped(reach, point, sea) - sea, reach.halfWidth()[point], reach.depth()[point], reach.style()[point],
                        columns.natural(cx, cz) - sea, columns.natural(lx, lz) - sea, columns.natural(rx, rz) - sea, columns.ocean(cx, cz),
                        head(columns, cx, cz, sea), columns.height(cx, cz) - sea, columns.role(cx, cz));
            }
            writer.println();
            for (Map.Entry<WindowKey, WindowPlan> entry : plans.windowPlans().entrySet()) {
                for (Reach near : entry.getValue().reaches()) {
                    double best = Double.POSITIVE_INFINITY;
                    int at = 0;
                    for (int point = 0; point < near.xs().length; point++) {
                        double apart = Math.hypot(near.xs()[point] - x, near.zs()[point] - z);
                        if (apart < best) {
                            best = apart;
                            at = point;
                        }
                    }
                    if (best < 32D) {
                        int last = near.xs().length - 1;
                        writer.printf(Locale.ROOT, "near reach %d window %d,%d tier %s down %d start %.0f,%.0f end %.0f,%.0f nearest %.0f grade there +%d style %s endGrade +%d lastStyle %s%n", near.id(), entry.getKey().wx(), entry.getKey().wz(), near.tier().name(), near.downstreamId(),
                                near.xs()[0], near.zs()[0], near.xs()[last], near.zs()[last], best, near.grade()[at] - sea, near.style()[at], near.grade()[last] - sea, near.style()[last]);
                    }
                }
            }
            writer.println();
            writer.println("heads above sea around " + x + "," + z + " (x right, z down); . dry land, ~ open sea, # dry below sea");
            for (int gz = z - 40; gz <= z + 40; gz += 1) {
                StringBuilder line = new StringBuilder();
                for (int gx = x - 40; gx <= x + 40; gx += 1) {
                    int head = columns.head(gx, gz);
                    WaterRole role = columns.role(gx, gz);
                    if (head != Integer.MIN_VALUE) {
                        int up = head - sea;
                        line.append(up == 0 ? (role == WaterRole.MOUTH ? 'm' : '0') : up < 10 ? (char) ('0' + up) : '+');
                    } else {
                        boolean ocean = columns.ocean(gx, gz);
                        int ground = columns.height(gx, gz);
                        line.append(ground < sea ? (ocean ? '~' : '#') : '.');
                    }
                }
                writer.println(line);
            }
            System.out.println(LOG + " dumped reach " + reach.id());
            return 0L;
        }
    }

    private static int head(TidalReachProbe.Columns columns, int x, int z, int sea) {
        int head = columns.head(x, z);
        return head == Integer.MIN_VALUE ? -999 : head - sea;
    }

    private static long sections(HydrologyWindows.Target target, File out, String[] arguments) throws Exception {
        try (RealPackProbeSupport.Workspace workspace = RealPackProbeSupport.openWorkspace(target.pack(), target.dimension(), LOG);
             RealPackProbeSupport.EngineSession session = workspace.openEngine(target.seed(), false, "sections")) {
            Engine engine = session.engine();
            HydrologySettings settings = settings(engine);
            int sea = settings.seaLevel();
            HydrologyPlanHarness.Plans plans = plans(engine, settings, target.windows().grown(1));
            List<Reach> mouths = mouths(plans, target.windows());
            List<Reach> chosen = new ArrayList<>();
            for (int index = 6; index + 1 < arguments.length; index += 2) {
                chosen.add(nearest(mouths, Integer.parseInt(arguments[index]), Integer.parseInt(arguments[index + 1])));
            }
            Set<Long> chunks = new TreeSet<>();
            for (Reach reach : chosen) {
                int last = reach.xs().length - 1;
                double[] along = MouthMetrics.along(reach);
                for (int point = 0; point <= last; point++) {
                    if (along[last] - along[point] <= MouthMetrics.APPROACH + 48D) {
                        for (int dz = -64; dz <= 64; dz += 16) {
                            for (int dx = -64; dx <= 64; dx += 16) {
                                chunks.add(TidalReachProbe.key((Math.round(reach.xs()[point]) + dx) >> 4, (Math.round(reach.zs()[point]) + dz) >> 4));
                            }
                        }
                    }
                }
            }
            int height = engine.getTarget().getHeight();
            Map<Long, String[]> states = new ConcurrentHashMap<>();
            Map<Long, int[]> colors = new ConcurrentHashMap<>();
            ExecutorService pool = Executors.newFixedThreadPool(6);
            try {
                List<Future<?>> futures = new ArrayList<>();
                for (long chunk : chunks) {
                    futures.add(pool.submit(() -> {
                        int chunkX = (int) (chunk >> 32);
                        int chunkZ = (int) chunk;
                        Hunk<NativeBlockState> blocks = Hunk.newArrayHunk(16, height, 16);
                        Hunk<NativeBiome> biomes = Hunk.newArrayHunk(16, height, 16);
                        engine.generate(chunkX << 4, chunkZ << 4, blocks, biomes, false);
                        int[] painted = new int[256 * LEVELS];
                        for (int lz = 0; lz < 16; lz++) {
                            for (int lx = 0; lx < 16; lx++) {
                                for (int level = 0; level < LEVELS; level++) {
                                    NativeBlockState state = blocks.get(lx, sea - BELOW + level, lz);
                                    painted[(lz << 4 | lx) * LEVELS + level] = state == null || state.isAir() ? AIR : TidalReachProbe.blockColor(state);
                                }
                            }
                        }
                        colors.put(chunk, painted);
                        return null;
                    }));
                }
                for (Future<?> future : futures) {
                    future.get();
                }
            } finally {
                pool.shutdown();
            }
            for (Reach reach : chosen) {
                int last = reach.xs().length - 1;
                String name = target.seed() + "-" + Math.round(reach.xs()[last]) + "_" + Math.round(reach.zs()[last]);
                ImageIO.write(sheet(reach, colors, sea), "png", new File(out, "mouth-" + name + ".png"));
                System.out.println(LOG + " wrote " + name + " finalDrop " + MouthMetrics.finalDrop(reach, sea));
            }
        }
        return 0L;
    }

    private static BufferedImage sheet(Reach reach, Map<Long, int[]> colors, int sea) {
        List<BufferedImage> parts = new ArrayList<>();
        parts.add(topView(reach, colors));
        parts.add(longProfile(reach, colors));
        double[] along = MouthMetrics.along(reach);
        double end = along[along.length - 1];
        for (double back : new double[]{0D, 8D, 16D, 32D, 48D, 64D}) {
            int point = along.length - 1;
            while (point > 1 && end - along[point] < back) {
                point--;
            }
            double dx = reach.xs()[Math.min(along.length - 1, point + 1)] - reach.xs()[point - 1];
            double dz = reach.zs()[Math.min(along.length - 1, point + 1)] - reach.zs()[point - 1];
            double span = Math.max(1e-3D, Math.hypot(dx, dz));
            parts.add(cross(reach.xs()[point], reach.zs()[point], -dz / span, dx / span, Math.round(back) + " back, grade +" + (reach.grade()[point] - sea), colors));
        }
        int width = 0;
        int total = 0;
        for (BufferedImage part : parts) {
            width = Math.max(width, part.getWidth());
            total += part.getHeight() + 4;
        }
        BufferedImage image = new BufferedImage(width, total, BufferedImage.TYPE_INT_RGB);
        Graphics2D graphics = image.createGraphics();
        int y = 0;
        for (BufferedImage part : parts) {
            graphics.drawImage(part, 0, y, null);
            y += part.getHeight() + 4;
        }
        graphics.dispose();
        return image;
    }

    private static BufferedImage topView(Reach reach, Map<Long, int[]> colors) {
        double[] along = MouthMetrics.along(reach);
        int centre = along.length - 1;
        while (centre > 0 && along[along.length - 1] - along[centre] < 24D) {
            centre--;
        }
        int cx = Math.round(reach.xs()[centre]);
        int cz = Math.round(reach.zs()[centre]);
        int half = 72;
        int scale = 5;
        BufferedImage image = new BufferedImage((2 * half + 1) * scale, (2 * half + 1) * scale, BufferedImage.TYPE_INT_RGB);
        for (int z = -half; z <= half; z++) {
            for (int x = -half; x <= half; x++) {
                int bx = cx + x;
                int bz = cz + z;
                int[] painted = colors.get(TidalReachProbe.key(bx >> 4, bz >> 4));
                int rgb = 0;
                if (painted != null) {
                    int offset = ((bz & 15) << 4 | (bx & 15)) * LEVELS;
                    for (int level = LEVELS - 1; level >= 0; level--) {
                        if (painted[offset + level] != AIR) {
                            double shade = Math.clamp(0.72D + 0.06D * (level - BELOW), 0.35D, 1.25D);
                            int base = painted[offset + level];
                            int red = Math.min(255, (int) (((base >> 16) & 255) * shade));
                            int green = Math.min(255, (int) (((base >> 8) & 255) * shade));
                            int blue = Math.min(255, (int) ((base & 255) * shade));
                            rgb = red << 16 | green << 8 | blue;
                            break;
                        }
                    }
                }
                for (int dz = 0; dz < scale; dz++) {
                    for (int dx = 0; dx < scale; dx++) {
                        image.setRGB((x + half) * scale + dx, (z + half) * scale + dz, rgb);
                    }
                }
            }
        }
        Graphics2D graphics = image.createGraphics();
        graphics.setColor(Color.WHITE);
        graphics.drawString("from above, centre " + cx + "," + cz, 4, 12);
        graphics.dispose();
        return image;
    }

    private static BufferedImage longProfile(Reach reach, Map<Long, int[]> colors) {
        double[] along = MouthMetrics.along(reach);
        double end = along[along.length - 1];
        int lastPoint = along.length - 1;
        double ox = reach.xs()[lastPoint] - reach.xs()[lastPoint - 1];
        double oz = reach.zs()[lastPoint] - reach.zs()[lastPoint - 1];
        double span = Math.max(1e-3D, Math.hypot(ox, oz));
        ox /= span;
        oz /= span;
        int width = MouthMetrics.APPROACH + 48 + 1;
        int[][] line = new int[width][2];
        int point = 0;
        for (int across = 0; across < width; across++) {
            double distance = end - MouthMetrics.APPROACH + across;
            double x;
            double z;
            if (distance >= end) {
                x = reach.xs()[lastPoint] + ox * (distance - end);
                z = reach.zs()[lastPoint] + oz * (distance - end);
            } else {
                while (point < lastPoint - 1 && along[point + 1] < distance) {
                    point++;
                }
                double t = Math.clamp((distance - along[point]) / Math.max(1e-3D, along[point + 1] - along[point]), 0D, 1D);
                x = reach.xs()[point] + t * (reach.xs()[point + 1] - reach.xs()[point]);
                z = reach.zs()[point] + t * (reach.zs()[point + 1] - reach.zs()[point]);
            }
            line[across][0] = (int) Math.floor(x);
            line[across][1] = (int) Math.floor(z);
        }
        return paint(line, "along the river, last " + MouthMetrics.APPROACH + " and 48 out to sea, " + line[MouthMetrics.APPROACH][0] + "," + line[MouthMetrics.APPROACH][1], colors);
    }

    private static BufferedImage cross(double x, double z, double nx, double nz, String label, Map<Long, int[]> colors) {
        int width = 129;
        int[][] line = new int[width][2];
        for (int across = 0; across < width; across++) {
            line[across][0] = (int) Math.floor(x + nx * (across - 64));
            line[across][1] = (int) Math.floor(z + nz * (across - 64));
        }
        return paint(line, label + " at " + Math.round(x) + "," + Math.round(z), colors);
    }

    private static BufferedImage paint(int[][] line, String label, Map<Long, int[]> colors) {
        BufferedImage image = new BufferedImage(line.length * SCALE, LEVELS * SCALE + 14, BufferedImage.TYPE_INT_RGB);
        for (int across = 0; across < line.length; across++) {
            int[] painted = colors.get(TidalReachProbe.key(line[across][0] >> 4, line[across][1] >> 4));
            for (int level = 0; level < LEVELS; level++) {
                int rgb = painted == null ? 0 : painted[((line[across][1] & 15) << 4 | (line[across][0] & 15)) * LEVELS + level];
                for (int dx = 0; dx < SCALE; dx++) {
                    for (int dy = 0; dy < SCALE; dy++) {
                        image.setRGB(across * SCALE + dx, (LEVELS - 1 - level) * SCALE + dy + 14, rgb);
                    }
                }
            }
        }
        Graphics2D graphics = image.createGraphics();
        graphics.setColor(new Color(255, 80, 80));
        int seaRow = (LEVELS - 1 - BELOW) * SCALE + 14;
        graphics.drawLine(0, seaRow, 6, seaRow);
        graphics.setColor(Color.WHITE);
        graphics.fillRect(0, 0, image.getWidth(), 14);
        graphics.setColor(Color.BLACK);
        graphics.drawString(label, 2, 11);
        graphics.dispose();
        return image;
    }

    private static long digest(HydrologyWindows.Target target, File out) throws Exception {
        try (RealPackProbeSupport.Workspace workspace = RealPackProbeSupport.openWorkspace(target.pack(), target.dimension(), LOG);
             RealPackProbeSupport.EngineSession session = workspace.openEngine(target.seed(), false, "digest");
             PrintWriter writer = new PrintWriter(new OutputStreamWriter(new GZIPOutputStream(new FileOutputStream(new File(out, "reaches-" + target.seed() + ".tsv.gz"))), StandardCharsets.UTF_8))) {
            Engine engine = session.engine();
            HydrologySettings settings = settings(engine);
            HydrologyPlanHarness.Plans plans = plans(engine, settings, target.windows());
            Map<String, Integer> totals = new HashMap<>();
            for (Map.Entry<WindowKey, WindowPlan> entry : plans.windowPlans().entrySet()) {
                String window = entry.getKey().wx() + "\t" + entry.getKey().wz();
                for (Reach reach : entry.getValue().reaches()) {
                    String kind = TidalReachProbe.tidal(reach) ? "A" : MouthMetrics.seaMouth(reach) ? "M" : "R";
                    writer.println(kind + "\t" + window + "\t" + reach.id() + "\t" + reach.downstreamId() + "\t" + TidalReachProbe.stable(reach) + "\t" + join(reach.xs()) + "\t" + join(reach.zs()) + "\t" + Arrays.toString(reach.grade()).replace(" ", ""));
                    totals.merge(kind, 1, Integer::sum);
                }
                for (Pond pond : entry.getValue().ponds()) {
                    writer.println("P\t" + window + "\t" + pond.id() + "\t" + pond.outletReachId() + "\t" + pond.level() + "\t" + pond.floorX() + "\t" + pond.floorZ() + "\t" + pond.kind());
                    totals.merge("P", 1, Integer::sum);
                }
                for (Grotto grotto : entry.getValue().grottos()) {
                    writer.println("G\t" + window + "\t" + grotto.id() + "\t" + Arrays.hashCode(grotto.xs()) + "\t" + Arrays.hashCode(grotto.zs()) + "\t" + Arrays.hashCode(grotto.halfWidth())
                            + "\t" + Arrays.hashCode(grotto.roof()) + "\t" + Arrays.hashCode(grotto.floor()) + "\t" + Arrays.hashCode(grotto.plugs()));
                    totals.merge("G", 1, Integer::sum);
                }
            }
            System.out.println(LOG + " seed " + target.seed() + " reaches " + totals + " planFailures " + plans.failures().size());
            return plans.failures().size();
        }
    }

    private static String join(float[] values) {
        StringBuilder text = new StringBuilder();
        for (float value : values) {
            if (!text.isEmpty()) {
                text.append(',');
            }
            text.append(Math.round(value * 10f) / 10f);
        }
        return text.toString();
    }

    private static long chunks(HydrologyWindows.Target target, File out) throws Exception {
        try (RealPackProbeSupport.Workspace workspace = RealPackProbeSupport.openWorkspace(target.pack(), target.dimension(), LOG);
             RealPackProbeSupport.EngineSession session = workspace.openEngine(target.seed(), false, "chunks");
             PrintWriter writer = new PrintWriter(new File(out, "chunks-" + target.seed() + ".tsv"))) {
            Engine engine = session.engine();
            int height = engine.getTarget().getHeight();
            List<WindowKey> keys = target.windows().keys();
            Map<Long, Long> hashes = new ConcurrentHashMap<>();
            ExecutorService pool = Executors.newFixedThreadPool(6);
            try {
                List<Future<?>> futures = new ArrayList<>();
                for (WindowKey key : keys) {
                    for (int dz = 0; dz < HydrologyWindows.CHUNKS; dz++) {
                        for (int dx = 0; dx < HydrologyWindows.CHUNKS; dx++) {
                            int chunkX = key.wx() * HydrologyWindows.CHUNKS + dx;
                            int chunkZ = key.wz() * HydrologyWindows.CHUNKS + dz;
                            futures.add(pool.submit(() -> {
                                Hunk<NativeBlockState> blocks = Hunk.newArrayHunk(16, height, 16);
                                Hunk<NativeBiome> biomes = Hunk.newArrayHunk(16, height, 16);
                                engine.generate(chunkX << 4, chunkZ << 4, blocks, biomes, false);
                                hashes.put(TidalReachProbe.key(chunkX, chunkZ), TidalReachProbe.blockHash(blocks, biomes, height));
                                return null;
                            }));
                        }
                    }
                }
                for (Future<?> future : futures) {
                    future.get();
                }
            } finally {
                pool.shutdown();
            }
            for (Map.Entry<Long, Long> entry : hashes.entrySet()) {
                writer.println((int) (entry.getKey() >> 32) + "\t" + entry.getKey().intValue() + "\t" + entry.getValue());
            }
            System.out.println(LOG + " seed " + target.seed() + " chunks " + hashes.size());
            return 0L;
        }
    }
}
