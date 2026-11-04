package art.arcane.iris.probe;

import art.arcane.iris.generation.concurrent.MultiBurst;
import art.arcane.iris.generation.decoration.IrisProceduralBlocks;
import art.arcane.iris.generation.hydrology.engine.HydrologyBinding;
import art.arcane.iris.generation.hydrology.engine.IrisHydrologyTerrainSource;
import art.arcane.iris.generation.hydrology.plan.HydrologySettings;
import art.arcane.iris.generation.hydrology.plan.WindowKey;
import art.arcane.iris.generation.hydrology.plan.WindowPlan;
import art.arcane.iris.generation.hydrology.raster.ChunkWaterRaster;
import art.arcane.iris.generation.hydrology.underground.Grotto;
import art.arcane.iris.generation.hydrology.underground.GrottoShape;
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
import java.nio.file.Files;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
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

public final class GrottoProbe {
    private static final String LOG = "[grotto-probe]";
    private static final int BELOW = 26;
    private static final int LEVELS = 110;
    private static final int MARGIN = 12;
    private static final int SECTION_SCALE = 5;
    private static final int SECTION_HALF = 28;
    private static final int[][] SIDES = {{1, 0}, {-1, 0}, {0, 1}, {0, -1}};
    private static final int WATER_COLOR = 0x2f62c8;
    private static final long TRACE_CHUNK = System.getenv("GROTTO_TRACE") == null ? Long.MIN_VALUE : Long.parseLong(System.getenv("GROTTO_TRACE"));
    private static final List<List<String>> TRACE = Collections.synchronizedList(new ArrayList<>());
    private static final int HASH_RING = 3;
    private static final int TOUCH_RING = 1;

    private GrottoProbe() {
    }

    public static void main(String[] arguments) {
        int code = 2;
        try {
            String mode = arguments[0];
            HydrologyWindows.Target target = HydrologyWindows.Target.parse(arguments[1], arguments[2], arguments[3], arguments[4], "false");
            File out = new File(arguments[5]);
            out.mkdirs();
            int threads = arguments.length > 6 ? Integer.parseInt(arguments[6]) : 6;
            long failures = switch (mode) {
                case "plan" -> plan(target, out);
                case "blocks" -> blocks(target, out, threads, Integer.MAX_VALUE, Integer.MAX_VALUE, arguments.length > 7 ? Integer.parseInt(arguments[7]) : 0);
                case "hash" -> hashes(target, out, threads, arguments.length > 7 ? new File(arguments[7]) : null);
                case "site" -> blocks(target, out, threads, Integer.parseInt(arguments[7]), Integer.parseInt(arguments[8]), 1);
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
        IrisHydrologyTerrainSource terrain = new IrisHydrologyTerrainSource(engine.getComplex(), engine.getDimension(), settings, MultiBurst.hydrology,
                MultiBurst.hydrology.parallelism(), HydrologyBinding.UNBOUND);
        return new HydrologyPlanHarness.Plans(settings, terrain, terrain::naturalSurface, range);
    }

    private static List<Grotto> grottos(HydrologyPlanHarness.Plans plans, HydrologyWindows.WindowRange windows) {
        List<Grotto> grottos = new ArrayList<>();
        for (Map.Entry<WindowKey, WindowPlan> entry : plans.windowPlans().entrySet()) {
            if (windows.contains(entry.getKey())) {
                grottos.addAll(entry.getValue().grottos());
            }
        }
        grottos.sort(Comparator.comparingLong(Grotto::id));
        return grottos;
    }

    private static double length(Grotto grotto) {
        double total = 0D;
        for (int point = grotto.coast() + 1; point < grotto.xs().length; point++) {
            total += Math.hypot(grotto.xs()[point] - grotto.xs()[point - 1], grotto.zs()[point] - grotto.zs()[point - 1]);
        }
        return total;
    }

    private static double sinuosity(Grotto grotto) {
        int last = grotto.xs().length - 1;
        return length(grotto) / Math.max(1D, Math.hypot(grotto.xs()[last] - grotto.xs()[grotto.coast()], grotto.zs()[last] - grotto.zs()[grotto.coast()]));
    }

    private static long plan(HydrologyWindows.Target target, File out) throws Exception {
        try (RealPackProbeSupport.Workspace workspace = RealPackProbeSupport.openWorkspace(target.pack(), target.dimension(), LOG);
             RealPackProbeSupport.EngineSession session = workspace.openEngine(target.seed(), false, "plan");
             PrintWriter writer = new PrintWriter(new File(out, "plan-" + target.seed() + ".tsv"))) {
            Engine engine = session.engine();
            HydrologySettings settings = settings(engine);
            HydrologyPlanHarness.Plans plans = plans(engine, settings, target.windows().grown(1));
            List<Grotto> grottos = grottos(plans, target.windows());
            writer.println("seed\tid\tmouthX\tmouthZ\tstations\tlength\tsinuosity\tmaxWidth\tmaxRoof\tholes\tlift");
            for (Grotto grotto : grottos) {
                writer.printf(Locale.ROOT, "%d\t%d\t%d\t%d\t%d\t%.0f\t%.2f\t%.1f\t%.1f\t%d\t%.1f%n", target.seed(), grotto.id(), grotto.mouthX(), grotto.mouthZ(), grotto.xs().length, length(grotto),
                        sinuosity(grotto), max(grotto.halfWidth()), max(grotto.roof()), grotto.holeX().length, max(grotto.lift()));
            }
            System.out.println(LOG + " seed " + target.seed() + " grottos " + grottos.size() + " failures " + plans.failures());
            return plans.failures().size();
        }
    }

    private static float max(float[] values) {
        float max = 0f;
        for (float value : values) {
            max = Math.max(max, value);
        }
        return max;
    }

    private static long blocks(HydrologyWindows.Target target, File out, int threads, int siteX, int siteZ, int images) throws Exception {
        long failures = 0L;
        try (RealPackProbeSupport.Workspace workspace = RealPackProbeSupport.openWorkspace(target.pack(), target.dimension(), LOG)) {
            long seed = target.seed();
            List<Grotto> grottos;
            List<Long> order;
            Map<Long, Long> forward = new ConcurrentHashMap<>();
            try (RealPackProbeSupport.EngineSession session = workspace.openEngine(seed, false, "blocks")) {
                Engine engine = session.engine();
                HydrologySettings settings = settings(engine);
                HydrologyPlanHarness.Plans plans = plans(engine, settings, target.windows().grown(1));
                failures += plans.failures().size();
                grottos = new ArrayList<>(grottos(plans, target.windows()));
                if (siteX != Integer.MAX_VALUE) {
                    grottos.sort(Comparator.comparingDouble((Grotto grotto) -> Math.hypot(grotto.mouthX() - siteX, grotto.mouthZ() - siteZ)));
                    for (Grotto grotto : grottos.subList(0, Math.min(5, grottos.size()))) {
                        System.out.printf(Locale.ROOT, "%s near site: mouth %d,%d off %.0f length %.0f sinuosity %.2f%n", LOG, grotto.mouthX(), grotto.mouthZ(),
                                Math.hypot(grotto.mouthX() - siteX, grotto.mouthZ() - siteZ), length(grotto), sinuosity(grotto));
                    }
                    grottos = new ArrayList<>(grottos.subList(0, Math.min(images, grottos.size())));
                }
                List<Reach> arms = new ArrayList<>();
                for (WindowPlan plan : plans.windowPlans().values()) {
                    for (Reach reach : plan.reaches()) {
                        if (reach.style()[0] == ReachStyle.TIDAL) {
                            arms.add(reach);
                        }
                    }
                }
                Set<Long> chunks = new TreeSet<>();
                for (Grotto grotto : grottos) {
                    int[] bounds = grotto.bounds();
                    for (int chunkZ = (bounds[1] - MARGIN) >> 4; chunkZ <= (bounds[3] + MARGIN) >> 4; chunkZ++) {
                        for (int chunkX = (bounds[0] - MARGIN) >> 4; chunkX <= (bounds[2] + MARGIN) >> 4; chunkX++) {
                            chunks.add(key(chunkX, chunkZ));
                        }
                    }
                }
                order = new ArrayList<>(chunks);
                int sea = settings.seaLevel();
                Volume volume = new Volume(sea - BELOW);
                generate(engine, order, threads, volume, forward);
                List<Stats> stats = new ArrayList<>();
                try (PrintWriter writer = new PrintWriter(new File(out, "blocks-" + seed + ".tsv"))) {
                    writer.println("seed\tid\tmouthX\tmouthZ\tlength\tsinuosity\twet\tdry\tleaks\tflowed\tcutOff\tmismatch\twetInRiver\tstrayWater\twallRun4\tflatRoof\tflatRoofAlong\tmultiSpan\toverhang\tmouthOpen\tskyOpen");
                    for (Grotto grotto : grottos) {
                        Stats stat = stats(engine, grotto, volume, sea);
                        stats.add(stat);
                        writer.printf(Locale.ROOT, "%d\t%d\t%d\t%d\t%.0f\t%.2f\t%d\t%d\t%d\t%d\t%d\t%d\t%d\t%d\t%.3f\t%.3f\t%.1f\t%.3f\t%.3f\t%d\t%.3f%n", seed, grotto.id(), grotto.mouthX(), grotto.mouthZ(), length(grotto), sinuosity(grotto),
                                stat.wet, stat.dry, stat.leaks, stat.flowed, stat.cutOff, stat.mismatch, stat.wetInRiver, stat.strayWater, stat.wallRun, stat.flatRoof, stat.flatRoofAlong, stat.multiSpan, stat.overhang, stat.mouthOpen, stat.skyOpen);
                        failures += stat.leaks + stat.flowed + stat.cutOff + stat.wetInRiver + stat.strayWater;
                    }
                }
                long joined = joined(grottos);
                long armBreach = armBreach(grottos, arms);
                failures += joined;
                System.out.println(LOG + " seed " + seed + " grottos " + grottos.size() + " chunks " + order.size() + " joined " + joined + " nearArm " + armBreach
                        + " leaks " + stats.stream().mapToLong(stat -> stat.leaks).sum() + " flowed " + stats.stream().mapToLong(stat -> stat.flowed).sum()
                        + " cutOff " + stats.stream().mapToLong(stat -> stat.cutOff).sum() + " mismatch " + stats.stream().mapToLong(stat -> stat.mismatch).sum()
                        + " wetInRiver " + stats.stream().mapToLong(stat -> stat.wetInRiver).sum() + " stray " + stats.stream().mapToLong(stat -> stat.strayWater).sum());
                int rendered = 0;
                for (Grotto grotto : grottos) {
                    if (rendered++ >= images) {
                        break;
                    }
                    render(grotto, volume, sea, new File(out, "grotto-" + seed + "-" + grotto.mouthX() + "_" + grotto.mouthZ() + ".png"));
                }
                List<Throwable> reports = RealPackProbeSupport.settleAndDrain(engine);
                if (!reports.isEmpty()) {
                    RealPackProbeSupport.printReports(LOG, "blocks", reports);
                    failures += reports.size();
                }
            }
            if (!order.isEmpty()) {
                List<Long> reversed = new ArrayList<>(order);
                Collections.reverse(reversed);
                Map<Long, Long> backward = new ConcurrentHashMap<>();
                try (RealPackProbeSupport.EngineSession second = workspace.openEngine(seed, false, "blocks-reverse")) {
                    generate(second.engine(), reversed, Math.max(1, threads / 2), new Volume(0), backward);
                }
                long mismatches = 0L;
                for (Map.Entry<Long, Long> entry : forward.entrySet()) {
                    if (!entry.getValue().equals(backward.get(entry.getKey()))) {
                        mismatches++;
                        int chunkX = (int) (entry.getKey() >> 32);
                        int chunkZ = (int) (long) entry.getKey();
                        boolean inside = false;
                        for (Grotto grotto : grottos) {
                            inside |= grotto.touches(chunkX << 4, chunkZ << 4, (chunkX << 4) + 15, (chunkZ << 4) + 15);
                        }
                        System.out.println(LOG + " seed " + seed + " order differs in chunk " + chunkX + "," + chunkZ + " touching a grotto " + inside);
                    }
                }
                if (TRACE.size() == 2) {
                    int height = TRACE.get(0).size() / 256;
                    for (int index = 0; index < TRACE.get(0).size(); index++) {
                        if (!TRACE.get(0).get(index).equals(TRACE.get(1).get(index))) {
                            System.out.println(LOG + " diff x " + index / height / 16 + " z " + index / height % 16 + " y " + index % height + " " + TRACE.get(0).get(index) + " vs " + TRACE.get(1).get(index));
                        }
                    }
                }
                failures += mismatches;
                System.out.println(LOG + " seed " + seed + " orderMismatches " + mismatches + " of " + forward.size());
            }
        }
        return failures;
    }

    private static long hashes(HydrologyWindows.Target target, File out, int threads, File listed) throws Exception {
        try (RealPackProbeSupport.Workspace workspace = RealPackProbeSupport.openWorkspace(target.pack(), target.dimension(), LOG);
             RealPackProbeSupport.EngineSession session = workspace.openEngine(target.seed(), false, "hash")) {
            Engine engine = session.engine();
            List<Long> chunks = new ArrayList<>();
            Set<Long> touched = new HashSet<>();
            if (listed != null) {
                for (String line : Files.readAllLines(listed.toPath())) {
                    String[] parts = line.split("\\t");
                    chunks.add(Long.parseLong(parts[0]));
                }
            } else {
                HydrologySettings settings = settings(engine);
                HydrologyPlanHarness.Plans plans = plans(engine, settings, target.windows().grown(1));
                Set<Long> wanted = new TreeSet<>();
                for (Grotto grotto : grottos(plans, target.windows())) {
                    int[] bounds = grotto.bounds();
                    for (int chunkZ = (bounds[1] >> 4) - HASH_RING; chunkZ <= (bounds[3] >> 4) + HASH_RING; chunkZ++) {
                        for (int chunkX = (bounds[0] >> 4) - HASH_RING; chunkX <= (bounds[2] >> 4) + HASH_RING; chunkX++) {
                            wanted.add(key(chunkX, chunkZ));
                            if (chunkX >= (bounds[0] >> 4) - TOUCH_RING && chunkX <= (bounds[2] >> 4) + TOUCH_RING
                                    && chunkZ >= (bounds[1] >> 4) - TOUCH_RING && chunkZ <= (bounds[3] >> 4) + TOUCH_RING) {
                                touched.add(key(chunkX, chunkZ));
                            }
                        }
                    }
                }
                chunks.addAll(wanted);
            }
            Map<Long, Long> hashes = new ConcurrentHashMap<>();
            generate(engine, chunks, threads, new Volume(0), hashes);
            try (PrintWriter writer = new PrintWriter(new File(out, "hash-" + target.seed() + ".tsv"))) {
                for (long chunk : chunks) {
                    writer.println(chunk + "\t" + hashes.get(chunk) + "\t" + (touched.contains(chunk) ? 1 : 0));
                }
            }
            System.out.println(LOG + " seed " + target.seed() + " hashed " + chunks.size());
            return 0L;
        }
    }

    private static long joined(List<Grotto> grottos) {
        long joined = 0L;
        for (int a = 0; a < grottos.size(); a++) {
            for (int b = a + 1; b < grottos.size(); b++) {
                if (nearest(grottos.get(a), grottos.get(b)) <= 0D) {
                    joined++;
                }
            }
        }
        return joined;
    }

    private static double nearest(Grotto a, Grotto b) {
        double nearest = Double.POSITIVE_INFINITY;
        double factor = GrottoShape.radialMax(a.roughness());
        double warp = GrottoShape.warp(a.roughness());
        for (int i = 0; i < a.xs().length; i++) {
            for (int j = 0; j < b.xs().length; j++) {
                double apart = Math.hypot(a.xs()[i] - b.xs()[j], a.zs()[i] - b.zs()[j]) - (a.halfWidth()[i] + b.halfWidth()[j]) * factor - 2D * warp;
                nearest = Math.min(nearest, apart);
            }
        }
        return nearest;
    }

    private static long armBreach(List<Grotto> grottos, List<Reach> arms) {
        long near = 0L;
        for (Grotto grotto : grottos) {
            boolean close = false;
            for (Reach arm : arms) {
                for (int i = 0; i < grotto.xs().length && !close; i += 2) {
                    for (int j = 0; j < arm.xs().length && !close; j += 2) {
                        close = Math.hypot(grotto.xs()[i] - arm.xs()[j], grotto.zs()[i] - arm.zs()[j]) < grotto.halfWidth()[i] + arm.halfWidth()[j] + 6D;
                    }
                }
            }
            near += close ? 1L : 0L;
        }
        return near;
    }

    private static void generate(Engine engine, List<Long> chunks, int threads, Volume volume, Map<Long, Long> hashes) throws Exception {
        int height = engine.getTarget().getHeight();
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
                    hashes.put(chunk, hash(blocks, height));
                    if (chunk == TRACE_CHUNK) {
                        List<String> keys = new ArrayList<>();
                        for (int x = 0; x < 16; x++) {
                            for (int z = 0; z < 16; z++) {
                                for (int y = 0; y < height; y++) {
                                    NativeBlockState state = blocks.get(x, y, z);
                                    keys.add((state == null ? "air" : state.key()));
                                }
                            }
                        }
                        TRACE.add(keys);
                    }
                    if (volume.bottom > 0) {
                        volume.capture(chunk, blocks);
                    }
                    return null;
                }));
            }
            for (Future<?> future : futures) {
                future.get();
            }
        } finally {
            pool.shutdown();
        }
    }

    private static long hash(Hunk<NativeBlockState> blocks, int height) {
        long hash = 0xcbf29ce484222325L;
        for (int x = 0; x < 16; x++) {
            for (int z = 0; z < 16; z++) {
                for (int y = 0; y < height; y++) {
                    NativeBlockState state = blocks.get(x, y, z);
                    String key = state == null ? "air" : state.key();
                    if (key.contains("amethyst")) {
                        key = "air";
                    }
                    hash = (hash ^ key.hashCode()) * 0x100000001b3L;
                }
            }
        }
        return hash;
    }

    private static final class Volume {
        final int bottom;
        final Map<Long, int[]> colors = new ConcurrentHashMap<>();
        final Map<Integer, String> names = new ConcurrentHashMap<>();

        Volume(int bottom) {
            this.bottom = bottom;
        }

        void capture(long chunk, Hunk<NativeBlockState> blocks) {
            int[] cells = new int[16 * 16 * LEVELS];
            for (int level = 0; level < LEVELS; level++) {
                for (int lz = 0; lz < 16; lz++) {
                    for (int lx = 0; lx < 16; lx++) {
                        NativeBlockState state = bottom + level < blocks.getHeight() ? blocks.get(lx, bottom + level, lz) : null;
                        cells[level << 8 | lz << 4 | lx] = code(state);
                        if ((cells[level << 8 | lz << 4 | lx] >>> 24) == 0xFF) {
                            names.putIfAbsent(cells[level << 8 | lz << 4 | lx], IrisProceduralBlocks.materialKey(state));
                        }
                    }
                }
            }
            colors.put(chunk, cells);
        }

        int at(int x, int y, int z) {
            int level = y - bottom;
            if (level < 0 || level >= LEVELS) {
                return y < bottom ? 0xFF888888 : 0;
            }
            int[] cells = colors.get(key(x >> 4, z >> 4));
            return cells == null ? -1 : cells[level << 8 | (z & 15) << 4 | (x & 15)];
        }

        String name(int x, int y, int z) {
            int cell = at(x, y, z);
            return (cell >>> 24) == 0xFF ? names.getOrDefault(cell, "?") : "kind" + (cell >>> 24);
        }

        boolean water(int x, int y, int z) {
            int cell = at(x, y, z);
            return cell != -1 && (cell >>> 24) == 2;
        }

        boolean open(int x, int y, int z) {
            int cell = at(x, y, z);
            return cell != -1 && (cell >>> 24) == 0;
        }

        boolean solid(int x, int y, int z) {
            int cell = at(x, y, z);
            return cell != -1 && (cell >>> 24) == 0xFF;
        }

        boolean known(int x, int y, int z) {
            return at(x, y, z) != -1;
        }
    }

    private static int code(NativeBlockState state) {
        if (state == null || state.isAir()) {
            return 0;
        }
        String key = IrisProceduralBlocks.materialKey(state);
        if (key.contains("water") || state.key().contains("waterlogged=true") || key.contains("seagrass") || key.contains("kelp")) {
            return 2 << 24 | WATER_COLOR;
        }
        if (key.contains("lava")) {
            return 3 << 24 | 0xe06010;
        }
        if (key.contains("pointed_dripstone")) {
            return 4 << 24 | 0x907860;
        }
        if (!state.isSolid() || key.contains("leaves")) {
            return key.contains("leaves") ? 4 << 24 | 0x2f6a22 : 0;
        }
        int rgb;
        if (key.contains("grass_block") || key.contains("moss")) {
            rgb = 0x5f9f3f;
        } else if (key.contains("sand")) {
            rgb = 0xd8cc90;
        } else if (key.contains("gravel")) {
            rgb = 0x908880;
        } else if (key.contains("mud") || key.contains("clay")) {
            rgb = 0x706860;
        } else if (key.contains("dirt") || key.contains("podzol") || key.contains("mycelium")) {
            rgb = 0x806040;
        } else if (key.contains("log") || key.contains("wood")) {
            rgb = 0x604020;
        } else if (key.contains("snow") || key.contains("ice")) {
            rgb = 0xe8eef8;
        } else if (key.contains("deepslate") || key.contains("tuff")) {
            rgb = 0x5c5c62;
        } else if (key.contains("stone") || key.contains("andesite") || key.contains("diorite") || key.contains("granite")) {
            rgb = 0x8a8a8a;
        } else {
            rgb = 0x9a8a6a;
        }
        return 0xFF << 24 | rgb;
    }

    private static final class Stats {
        long wet;
        long dry;
        long leaks;
        long flowed;
        long cutOff;
        long mismatch;
        long wetInRiver;
        long strayWater;
        double wallRun;
        double flatRoof;
        double flatRoofAlong;
        double multiSpan;
        double overhang;
        long mouthOpen;
        double skyOpen;
    }

    private static Stats stats(Engine engine, Grotto grotto, Volume volume, int sea) {
        Stats stats = new Stats();
        GrottoShape shape = new GrottoShape(grotto);
        int[] bounds = grotto.bounds();
        Set<Long> wetCells = new HashSet<>();
        long columns = 0L;
        long manySpans = 0L;
        long overhangs = 0L;
        long skyColumns = 0L;
        Map<Long, ChunkWaterRaster> rasters = new HashMap<>();
        Map<String, Integer> mismatchKinds = new TreeMap<>();
        for (int z = bounds[1]; z <= bounds[3]; z++) {
            for (int x = bounds[0]; x <= bounds[2]; x++) {
                if (!shape.near(x, z)) {
                    continue;
                }
                int spans = 0;
                boolean previous = false;
                boolean any = false;
                for (int y = shape.minY(); y <= shape.maxY(); y++) {
                    boolean inside = shape.inside(x, y, z);
                    if (inside && !previous) {
                        spans++;
                    }
                    previous = inside;
                    if (!inside) {
                        continue;
                    }
                    any = true;
                    if (!volume.known(x, y, z)) {
                        continue;
                    }
                    if (y <= sea) {
                        stats.wet++;
                        if (volume.water(x, y, z)) {
                            wetCells.add(cell(x, y, z));
                        } else {
                            stats.mismatch++;
                            mismatchKinds.merge("wet:" + volume.name(x, y, z) + (y >= sea - 2 ? ":top" : ":deep"), 1, Integer::sum);
                        }
                        ChunkWaterRaster raster = rasters.computeIfAbsent(key(x >> 4, z >> 4), chunk -> engine.getComplex().getHydrologyRuntime().raster((int) (chunk >> 32), (int) (long) chunk));
                        if (raster.role(x & 15, z & 15).wet()) {
                            stats.wetInRiver++;
                        }
                    } else {
                        stats.dry++;
                        if (volume.water(x, y, z)) {
                            stats.strayWater++;
                        }
                        if (volume.solid(x, y, z)) {
                            stats.mismatch++;
                            mismatchKinds.merge("dry:" + volume.name(x, y, z) + ":" + (y - sea) / 8, 1, Integer::sum);
                        }
                    }
                }
                if (any) {
                    columns++;
                    manySpans += spans > 1 ? 1L : 0L;
                    boolean over = false;
                    for (int y = sea + 1; y <= shape.maxY() && !over; y++) {
                        if (!shape.inside(x, y, z)) {
                            continue;
                        }
                        for (int[] side : SIDES) {
                            for (int up = 1; up <= 3 && !over; up++) {
                                over = !shape.inside(x, y + up, z) && shape.inside(x + side[0], y + up, z + side[1]) && y + up <= shape.maxY();
                            }
                        }
                    }
                    overhangs += over ? 1L : 0L;
                    boolean sky = true;
                    for (int y = sea + 1; y <= sea + LEVELS - BELOW - 1 && sky; y++) {
                        sky = !volume.solid(x, y, z);
                    }
                    skyColumns += sky ? 1L : 0L;
                }
            }
        }
        for (long cell : wetCells) {
            int x = cellX(cell);
            int y = cellY(cell);
            int z = cellZ(cell);
            boolean leak = volume.open(x, y - 1, z);
            for (int[] side : SIDES) {
                leak |= volume.open(x + side[0], y, z + side[1]);
            }
            stats.leaks += leak ? 1L : 0L;
        }
        if (stats.mismatch > 0L) {
            System.out.println(LOG + " mismatch " + grotto.mouthX() + "," + grotto.mouthZ() + " " + mismatchKinds);
        }
        stats.flowed = ticked(volume, wetCells, sea);
        stats.cutOff = cutOff(grotto, volume, wetCells, sea);
        section(grotto, volume, sea, stats);
        stats.multiSpan = columns == 0L ? 0D : (double) manySpans / columns;
        stats.overhang = columns == 0L ? 0D : (double) overhangs / columns;
        stats.skyOpen = columns == 0L ? 0D : (double) skyColumns / columns;
        stats.mouthOpen = mouthOpen(grotto, volume, sea);
        return stats;
    }

    private static long ticked(Volume volume, Set<Long> wetCells, int sea) {
        ArrayDeque<Long> frontier = new ArrayDeque<>(wetCells);
        Set<Long> flowed = new HashSet<>();
        Set<Long> seen = new HashSet<>(wetCells);
        while (!frontier.isEmpty() && flowed.size() < 100000) {
            long cell = frontier.poll();
            int x = cellX(cell);
            int y = cellY(cell);
            int z = cellZ(cell);
            for (int[] step : new int[][]{{0, -1, 0}, {1, 0, 0}, {-1, 0, 0}, {0, 0, 1}, {0, 0, -1}}) {
                int nx = x + step[0];
                int ny = y + step[1];
                int nz = z + step[2];
                long next = cell(nx, ny, nz);
                if (ny <= sea && volume.open(nx, ny, nz) && seen.add(next)) {
                    flowed.add(next);
                    frontier.add(next);
                }
            }
        }
        return flowed.size();
    }

    private static long cutOff(Grotto grotto, Volume volume, Set<Long> wetCells, int sea) {
        ArrayDeque<Long> frontier = new ArrayDeque<>();
        Set<Long> reached = new HashSet<>();
        int startX = (int) Math.floor(grotto.xs()[0]);
        int startZ = (int) Math.floor(grotto.zs()[0]);
        if (!volume.water(startX, sea, startZ)) {
            return wetCells.size();
        }
        long start = cell(startX, sea, startZ);
        reached.add(start);
        frontier.add(start);
        int[] bounds = grotto.bounds();
        while (!frontier.isEmpty()) {
            long cell = frontier.poll();
            int x = cellX(cell);
            int y = cellY(cell);
            int z = cellZ(cell);
            for (int[] step : new int[][]{{0, -1, 0}, {0, 1, 0}, {1, 0, 0}, {-1, 0, 0}, {0, 0, 1}, {0, 0, -1}}) {
                int nx = x + step[0];
                int ny = y + step[1];
                int nz = z + step[2];
                if (nx < bounds[0] - MARGIN || nx > bounds[2] + MARGIN || nz < bounds[1] - MARGIN || nz > bounds[3] + MARGIN) {
                    continue;
                }
                long next = cell(nx, ny, nz);
                if (volume.water(nx, ny, nz) && reached.add(next)) {
                    frontier.add(next);
                }
            }
        }
        long cut = 0L;
        for (long cell : wetCells) {
            cut += reached.contains(cell) ? 0L : 1L;
        }
        return cut;
    }

    private static long mouthOpen(Grotto grotto, Volume volume, int sea) {
        long open = 0L;
        int end = Math.min(grotto.xs().length - 1, grotto.coast() + 8);
        for (int point = grotto.coast(); point <= end; point++) {
            open += volume.open((int) Math.floor(grotto.xs()[point]), sea + 2, (int) Math.floor(grotto.zs()[point])) ? 1L : 0L;
        }
        return open;
    }

    private static void section(Grotto grotto, Volume volume, int sea, Stats stats) {
        long wallCells = 0L;
        long wallInRuns = 0L;
        double flatSum = 0D;
        int sections = 0;
        double flatAlong = 0D;
        int lastCeiling = Integer.MIN_VALUE;
        double run = 0D;
        int start = Math.min(grotto.xs().length - 1, grotto.coast() + 6);
        for (int point = start; point < grotto.xs().length - 1; point++) {
            double dx = grotto.xs()[point + 1] - grotto.xs()[point];
            double dz = grotto.zs()[point + 1] - grotto.zs()[point];
            double length = Math.max(1e-3D, Math.hypot(dx, dz));
            double nx = -dz / length;
            double nz = dx / length;
            int[] ceilings = new int[2 * SECTION_HALF + 1];
            int[] left = new int[LEVELS];
            int[] right = new int[LEVELS];
            int centreY = sea + 2;
            int width = 0;
            for (int offset = -SECTION_HALF; offset <= SECTION_HALF; offset++) {
                int x = (int) Math.floor(grotto.xs()[point] + offset * nx);
                int z = (int) Math.floor(grotto.zs()[point] + offset * nz);
                int y = centreY;
                ceilings[offset + SECTION_HALF] = Integer.MIN_VALUE;
                if (!volume.open(x, y, z)) {
                    continue;
                }
                while (y < sea + 60 && volume.open(x, y + 1, z)) {
                    y++;
                }
                ceilings[offset + SECTION_HALF] = y;
                width++;
            }
            int centre = SECTION_HALF;
            int low = centre;
            int high = centre;
            while (low > 0 && ceilings[low - 1] != Integer.MIN_VALUE) {
                low--;
            }
            while (high < 2 * SECTION_HALF && ceilings[high + 1] != Integer.MIN_VALUE) {
                high++;
            }
            if (ceilings[centre] == Integer.MIN_VALUE || high - low < 2) {
                lastCeiling = Integer.MIN_VALUE;
                run = 0D;
                continue;
            }
            int longest = 1;
            int current = 1;
            for (int offset = low + 1; offset <= high; offset++) {
                current = ceilings[offset] == ceilings[offset - 1] ? current + 1 : 1;
                longest = Math.max(longest, current);
            }
            flatSum += (double) longest / (high - low + 1);
            sections++;
            run = ceilings[centre] == lastCeiling ? run + length : 0D;
            lastCeiling = ceilings[centre];
            flatAlong = Math.max(flatAlong, run);
            for (int side = -1; side <= 1; side += 2) {
                int edge = Integer.MIN_VALUE;
                int streak = 0;
                for (int y = sea + 1; y <= sea + 24; y++) {
                    int offset = 0;
                    int x = (int) Math.floor(grotto.xs()[point]);
                    int z = (int) Math.floor(grotto.zs()[point]);
                    if (!volume.open(x, y, z)) {
                        if (streak >= 4) {
                            wallInRuns += streak;
                        }
                        break;
                    }
                    while (Math.abs(offset) < SECTION_HALF) {
                        int nextX = (int) Math.floor(grotto.xs()[point] + (offset + side) * nx);
                        int nextZ = (int) Math.floor(grotto.zs()[point] + (offset + side) * nz);
                        if (!volume.open(nextX, y, nextZ)) {
                            break;
                        }
                        offset += side;
                    }
                    wallCells++;
                    if (offset == edge) {
                        streak++;
                    } else {
                        if (streak >= 4) {
                            wallInRuns += streak;
                        }
                        streak = 1;
                        edge = offset;
                    }
                }
                if (streak >= 4) {
                    wallInRuns += streak;
                }
            }
        }
        stats.wallRun = wallCells == 0L ? 0D : (double) wallInRuns / wallCells;
        stats.flatRoof = sections == 0 ? 0D : flatSum / sections;
        stats.flatRoofAlong = flatAlong;
    }

    private static void render(Grotto grotto, Volume volume, int sea, File file) throws Exception {
        int[] bounds = grotto.bounds();
        BufferedImage map = topMap(grotto, volume, sea, bounds);
        BufferedImage along = longitudinal(grotto, volume, sea);
        BufferedImage sections = sections(grotto, volume, sea);
        BufferedImage outside = look(grotto, volume, sea, true);
        BufferedImage inside = look(grotto, volume, sea, false);
        int width = Math.max(map.getWidth() + outside.getWidth() + 8, Math.max(along.getWidth(), sections.getWidth()));
        int height = Math.max(map.getHeight(), outside.getHeight() + inside.getHeight() + 8) + along.getHeight() + sections.getHeight() + 24;
        BufferedImage sheet = new BufferedImage(width, height, BufferedImage.TYPE_INT_RGB);
        Graphics2D graphics = sheet.createGraphics();
        graphics.setColor(new Color(24, 24, 28));
        graphics.fillRect(0, 0, width, height);
        graphics.drawImage(map, 0, 0, null);
        graphics.drawImage(outside, map.getWidth() + 8, 0, null);
        graphics.drawImage(inside, map.getWidth() + 8, outside.getHeight() + 8, null);
        int y = Math.max(map.getHeight(), outside.getHeight() + inside.getHeight() + 8) + 8;
        graphics.drawImage(along, 0, y, null);
        graphics.drawImage(sections, 0, y + along.getHeight() + 8, null);
        graphics.dispose();
        ImageIO.write(sheet, "png", file);
    }

    private static BufferedImage topMap(Grotto grotto, Volume volume, int sea, int[] bounds) {
        int margin = 24;
        int width = bounds[2] - bounds[0] + 1 + 2 * margin;
        int depth = bounds[3] - bounds[1] + 1 + 2 * margin;
        int scale = Math.max(1, Math.min(3, 520 / Math.max(width, depth)));
        BufferedImage image = new BufferedImage(width * scale, depth * scale, BufferedImage.TYPE_INT_RGB);
        for (int pz = 0; pz < depth; pz++) {
            for (int px = 0; px < width; px++) {
                int x = bounds[0] - margin + px;
                int z = bounds[1] - margin + pz;
                int rgb = 0x202024;
                for (int y = volume.bottom + LEVELS - 1; y >= volume.bottom; y--) {
                    int cell = volume.at(x, y, z);
                    if (cell == -1) {
                        break;
                    }
                    if ((cell >>> 24) != 0) {
                        double shade = Math.clamp(0.55D + (y - sea) / 80D, 0.4D, 1.15D);
                        rgb = shaded(cell & 0xFFFFFF, shade);
                        break;
                    }
                }
                if (volume.water(x, sea, z) && volume.at(x, sea + 1, z) != -1 && !volume.open(x, sea + 1, z)) {
                    rgb = blend(rgb, 0x4080ff, 0.6D);
                } else if (volume.water(x, sea, z)) {
                    boolean roofed = false;
                    for (int y = sea + 1; y < sea + 70 && !roofed; y++) {
                        roofed = volume.solid(x, y, z);
                    }
                    if (roofed) {
                        rgb = blend(rgb, 0x40e0ff, 0.65D);
                    }
                }
                for (int sz = 0; sz < scale; sz++) {
                    for (int sx = 0; sx < scale; sx++) {
                        image.setRGB(px * scale + sx, pz * scale + sz, rgb);
                    }
                }
            }
        }
        return image;
    }

    private static int shaded(int rgb, double shade) {
        int r = (int) Math.clamp(((rgb >> 16) & 255) * shade, 0D, 255D);
        int g = (int) Math.clamp(((rgb >> 8) & 255) * shade, 0D, 255D);
        int b = (int) Math.clamp((rgb & 255) * shade, 0D, 255D);
        return r << 16 | g << 8 | b;
    }

    private static int blend(int a, int b, double t) {
        int r = (int) (((a >> 16) & 255) * (1D - t) + ((b >> 16) & 255) * t);
        int g = (int) (((a >> 8) & 255) * (1D - t) + ((b >> 8) & 255) * t);
        int blue = (int) ((a & 255) * (1D - t) + (b & 255) * t);
        return r << 16 | g << 8 | blue;
    }

    private static int sectionPixel(int cell) {
        if (cell == -1) {
            return 0x303034;
        }
        int kind = cell >>> 24;
        return kind == 0 ? 0xe8f2ff : cell & 0xFFFFFF;
    }

    private static BufferedImage longitudinal(Grotto grotto, Volume volume, int sea) {
        double total = 0D;
        List<double[]> points = new ArrayList<>();
        int from = Math.max(0, grotto.coast() - 8);
        for (int point = from; point + 1 < grotto.xs().length; point++) {
            double length = Math.hypot(grotto.xs()[point + 1] - grotto.xs()[point], grotto.zs()[point + 1] - grotto.zs()[point]);
            for (double t = 0D; t < length; t += 1D) {
                double share = t / length;
                points.add(new double[]{grotto.xs()[point] + share * (grotto.xs()[point + 1] - grotto.xs()[point]), grotto.zs()[point] + share * (grotto.zs()[point + 1] - grotto.zs()[point])});
            }
            total += length;
        }
        int scale = total > 400D ? 2 : 3;
        int bottom = sea - 16;
        int top = sea + 54;
        BufferedImage image = new BufferedImage(points.size() * scale, (top - bottom) * scale, BufferedImage.TYPE_INT_RGB);
        for (int index = 0; index < points.size(); index++) {
            int x = (int) Math.floor(points.get(index)[0]);
            int z = (int) Math.floor(points.get(index)[1]);
            for (int y = bottom; y < top; y++) {
                int rgb = sectionPixel(volume.at(x, y, z));
                for (int sx = 0; sx < scale; sx++) {
                    for (int sy = 0; sy < scale; sy++) {
                        image.setRGB(index * scale + sx, (top - 1 - y) * scale + sy, rgb);
                    }
                }
            }
        }
        return image;
    }

    private static BufferedImage sections(Grotto grotto, Volume volume, int sea) {
        int count = 8;
        int half = SECTION_HALF;
        int bottom = sea - 14;
        int top = sea + 34;
        int panelWidth = (2 * half + 1) * 4;
        int panelHeight = (top - bottom) * 4;
        int columns = 4;
        BufferedImage image = new BufferedImage(columns * (panelWidth + 4), (count / columns) * (panelHeight + 4), BufferedImage.TYPE_INT_RGB);
        Graphics2D graphics = image.createGraphics();
        int first = grotto.coast();
        int stations = grotto.xs().length - 2 - first;
        for (int panel = 0; panel < count; panel++) {
            int point = first + (int) Math.round((double) panel / (count - 1) * stations);
            double dx = grotto.xs()[point + 1] - grotto.xs()[point];
            double dz = grotto.zs()[point + 1] - grotto.zs()[point];
            double length = Math.max(1e-3D, Math.hypot(dx, dz));
            double nx = -dz / length;
            double nz = dx / length;
            int originX = (panel % columns) * (panelWidth + 4);
            int originY = (panel / columns) * (panelHeight + 4);
            for (int offset = -half; offset <= half; offset++) {
                int x = (int) Math.floor(grotto.xs()[point] + offset * nx);
                int z = (int) Math.floor(grotto.zs()[point] + offset * nz);
                for (int y = bottom; y < top; y++) {
                    graphics.setColor(new Color(sectionPixel(volume.at(x, y, z))));
                    graphics.fillRect(originX + (offset + half) * 4, originY + (top - 1 - y) * 4, 4, 4);
                }
            }
        }
        graphics.dispose();
        return image;
    }

    private static BufferedImage look(Grotto grotto, Volume volume, int sea, boolean outside) {
        int width = 640;
        int height = 360;
        int coast = grotto.coast();
        double eyeX;
        double eyeZ;
        double aimX;
        double aimZ;
        double eyeY;
        if (outside) {
            int aim = Math.min(grotto.xs().length - 1, coast + 3);
            double dirX = grotto.xs()[aim] - grotto.xs()[0];
            double dirZ = grotto.zs()[aim] - grotto.zs()[0];
            double length = Math.max(1D, Math.hypot(dirX, dirZ));
            eyeX = grotto.xs()[aim] - dirX / length * 30D;
            eyeZ = grotto.zs()[aim] - dirZ / length * 30D;
            aimX = grotto.xs()[aim];
            aimZ = grotto.zs()[aim];
            eyeY = sea + 3.5D;
        } else {
            int at = Math.min(grotto.xs().length - 2, coast + (grotto.xs().length - coast) / 3);
            int aim = Math.min(grotto.xs().length - 1, at + 6);
            double openX = grotto.xs()[at] + 0.5D;
            double openZ = grotto.zs()[at] + 0.5D;
            boolean found = false;
            for (int radius = 0; radius <= 12 && !found; radius++) {
                for (int dz = -radius; dz <= radius && !found; dz++) {
                    for (int dx = -radius; dx <= radius && !found; dx++) {
                        int cx = (int) Math.floor(grotto.xs()[at]) + dx;
                        int cz = (int) Math.floor(grotto.zs()[at]) + dz;
                        if (volume.open(cx, sea + 2, cz) && volume.open(cx + 1, sea + 2, cz) && volume.open(cx - 1, sea + 2, cz) && volume.open(cx, sea + 2, cz + 1) && volume.open(cx, sea + 2, cz - 1)) {
                            openX = cx + 0.5D;
                            openZ = cz + 0.5D;
                            found = true;
                        }
                    }
                }
            }
            eyeX = openX;
            eyeZ = openZ;
            aimX = grotto.xs()[aim] + 0.5D;
            aimZ = grotto.zs()[aim] + 0.5D;
            eyeY = sea + 2.2D;
        }
        double forwardX = aimX - eyeX;
        double forwardZ = aimZ - eyeZ;
        double forwardLength = Math.hypot(forwardX, forwardZ);
        forwardX /= forwardLength;
        forwardZ /= forwardLength;
        double rightX = -forwardZ;
        double rightZ = forwardX;
        BufferedImage image = new BufferedImage(width, height, BufferedImage.TYPE_INT_RGB);
        double fov = 0.85D;
        for (int py = 0; py < height; py++) {
            for (int px = 0; px < width; px++) {
                double sx = (px - width / 2D) / (width / 2D) * fov;
                double sy = (height / 2D - py) / (width / 2D) * fov + (outside ? 0.1D : 0.05D);
                double rx = forwardX + sx * rightX;
                double rz = forwardZ + sx * rightZ;
                double ry = sy;
                double norm = Math.sqrt(rx * rx + ry * ry + rz * rz);
                image.setRGB(px, py, march(volume, eyeX, eyeY, eyeZ, rx / norm, ry / norm, rz / norm, outside));
            }
        }
        return image;
    }

    private static int march(Volume volume, double ox, double oy, double oz, double rx, double ry, double rz, boolean outside) {
        int x = (int) Math.floor(ox);
        int y = (int) Math.floor(oy);
        int z = (int) Math.floor(oz);
        int stepX = rx > 0D ? 1 : -1;
        int stepY = ry > 0D ? 1 : -1;
        int stepZ = rz > 0D ? 1 : -1;
        double tDeltaX = Math.abs(1D / rx);
        double tDeltaY = Math.abs(1D / ry);
        double tDeltaZ = Math.abs(1D / rz);
        double tX = (rx > 0D ? x + 1 - ox : ox - x) * tDeltaX;
        double tY = (ry > 0D ? y + 1 - oy : oy - y) * tDeltaY;
        double tZ = (rz > 0D ? z + 1 - oz : oz - z) * tDeltaZ;
        double t = 0D;
        int axis = 0;
        boolean swam = false;
        int waterTint = 0;
        while (t < 170D) {
            if (tX < tY && tX < tZ) {
                x += stepX;
                t = tX;
                tX += tDeltaX;
                axis = 0;
            } else if (tY < tZ) {
                y += stepY;
                t = tY;
                tY += tDeltaY;
                axis = 1;
            } else {
                z += stepZ;
                t = tZ;
                tZ += tDeltaZ;
                axis = 2;
            }
            int cell = volume.at(x, y, z);
            if (cell == -1) {
                return outside ? 0xa8c8f0 : 0x101014;
            }
            int kind = cell >>> 24;
            if (kind == 2) {
                if (!swam) {
                    swam = true;
                    waterTint = (int) t;
                }
                if (t - waterTint > 7D) {
                    return shaded(WATER_COLOR, light(t, axis, outside) * (outside && roofed(volume, x, y, z) ? 0.3D : 0.8D));
                }
                continue;
            }
            if (kind == 0) {
                continue;
            }
            double shade = light(t, axis, outside);
            if (outside && roofed(volume, x, y, z)) {
                shade *= 0.32D;
            }
            int rgb = shaded(cell & 0xFFFFFF, shade);
            if (swam) {
                rgb = blend(rgb, WATER_COLOR, 0.55D);
            }
            return rgb;
        }
        return swam ? WATER_COLOR : 0xa8c8f0;
    }

    private static boolean roofed(Volume volume, int x, int y, int z) {
        for (int up = 2; up < 40; up++) {
            if (volume.solid(x, y + up, z)) {
                return true;
            }
        }
        return false;
    }

    private static double light(double t, int axis, boolean outside) {
        double face = axis == 1 ? 1D : axis == 0 ? 0.78D : 0.62D;
        double range = outside ? 1D : Math.clamp(1.15D - t / 70D, 0.22D, 1.1D);
        return face * range;
    }

    private static long cell(int x, int y, int z) {
        return (x + 0x200000L) << 42 | (z + 0x200000L) << 20 | (y + 0x1000L);
    }

    private static int cellX(long cell) {
        return (int) ((cell >>> 42) - 0x200000L);
    }

    private static int cellZ(long cell) {
        return (int) (((cell >>> 20) & 0x3FFFFFL) - 0x200000L);
    }

    private static int cellY(long cell) {
        return (int) ((cell & 0xFFFFFL) - 0x1000L);
    }

    private static long key(int x, int z) {
        return ((long) x << 32) | (z & 0xFFFFFFFFL);
    }
}
