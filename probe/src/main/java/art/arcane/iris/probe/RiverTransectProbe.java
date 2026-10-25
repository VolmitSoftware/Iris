package art.arcane.iris.probe;

import art.arcane.iris.generation.concurrent.MultiBurst;
import art.arcane.iris.generation.hydrology.config.IrisHydrology;
import art.arcane.iris.generation.hydrology.engine.HydrologyBinding;
import art.arcane.iris.generation.hydrology.engine.IrisHydrologyTerrainSource;
import art.arcane.iris.generation.hydrology.plan.HydrologySettings;
import art.arcane.iris.generation.hydrology.plan.RegionKey;
import art.arcane.iris.generation.hydrology.plan.WindowKey;
import art.arcane.iris.generation.hydrology.plan.WindowPlan;
import art.arcane.iris.generation.hydrology.raster.ChunkWaterRaster;
import art.arcane.iris.generation.hydrology.raster.WaterRole;
import art.arcane.iris.generation.hydrology.water.Pond;
import art.arcane.iris.generation.hydrology.water.PondMask;
import art.arcane.iris.generation.hydrology.water.Reach;
import art.arcane.iris.generation.hydrology.water.ReachStyle;
import art.arcane.iris.generation.runtime.Engine;
import art.arcane.iris.generation.terrain.IrisDimension;

import javax.imageio.ImageIO;
import java.awt.BasicStroke;
import java.awt.Color;
import java.awt.Graphics2D;
import java.awt.RenderingHints;
import java.awt.image.BufferedImage;
import java.io.File;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.EnumMap;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.function.IntBinaryOperator;

public final class RiverTransectProbe {
    static final int NO_WATER = Integer.MIN_VALUE;
    static final int SECTION_HALF = 48;
    private static final double[] STATIONS = {0.1D, 0.3D, 0.5D, 0.7D, 0.9D};
    private static final int MAXIMUM_REACHES = 8;
    private static final int MAXIMUM_WINDOWS = 64;
    private static final int SCALE = 2;
    private static final int PANEL_HEIGHT = 120;
    private static final int PANEL_GAP = 4;

    private RiverTransectProbe() {
    }

    record Configuration(HydrologyWindows.Target target, File output) {
        Configuration {
            if (target == null || output == null || !output.isAbsolute()) {
                throw new IllegalArgumentException("A target and an absolute output path are required.");
            }
            if (target.windows().count() > MAXIMUM_WINDOWS) {
                throw new IllegalArgumentException("The transect probe renders at most " + MAXIMUM_WINDOWS + " windows, got "
                        + target.windows().count() + ".");
            }
        }

        static Configuration parse(String[] args) {
            if (args.length != 6) {
                throw new IllegalArgumentException("Expected <pack> <dimension|-> <seed> <x0..x1,z0..z1> <outputDir> <studio>.");
            }
            return new Configuration(HydrologyWindows.Target.parse(args[0], args[1], args[2], args[3], args[5]), new File(args[4]));
        }
    }

    record Column(int natural, int terrain, int head, WaterRole role) {
    }

    @FunctionalInterface
    interface Columns {
        Column at(int x, int z);
    }

    record Section(long reachId, int point, double station, float x, float z, float tangentX, float tangentZ, Column[] columns) {
    }

    record Rendered(WindowKey key, int reaches, int ponds, int lakes, int sections, File directory) {
        String line() {
            return String.format(Locale.ROOT, "IRIS_RIVER_TRANSECT version=2 wx=%d wz=%d reaches=%d ponds=%d lakes=%d sections=%d output=%s",
                    key.wx(), key.wz(), reaches, ponds, lakes, sections, directory.getAbsolutePath());
        }
    }

    public static void main(String[] args) {
        int exitCode = 2;
        try {
            Configuration configuration = Configuration.parse(args);
            List<Rendered> rendered = run(configuration);
            for (Rendered window : rendered) {
                System.out.println(window.line());
            }
            System.out.println("IRIS_RIVER_TRANSECT_RESULT version=2 windows=" + rendered.size() + " output="
                    + configuration.output().getAbsolutePath());
            exitCode = 0;
        } catch (Throwable failure) {
            failure.printStackTrace(System.out);
        }
        System.exit(exitCode);
    }

    static List<Rendered> run(Configuration configuration) throws Exception {
        HydrologyWindows.Target target = configuration.target();
        try (RealPackProbeSupport.Workspace workspace = RealPackProbeSupport.openWorkspace(target.pack(), target.dimension(), "[river-transect]");
             RealPackProbeSupport.EngineSession session = workspace.openEngine(target.seed(), target.studio(), "river-transect")) {
            Engine engine = session.engine();
            IrisDimension dimension = engine.getDimension();
            IrisHydrology config = dimension.getHydrology();
            if (config == null || !config.isEnabled()) {
                throw new IllegalStateException("The dimension " + target.dimension() + " must enable hydrology.");
            }
            HydrologySettings settings = HydrologySettings.compile(config, engine.getSeedManager().getBodies(), dimension.getFluidHeight(), 0,
                    engine.getHeight());
            IrisHydrologyTerrainSource terrain = new IrisHydrologyTerrainSource(engine.getComplex(), dimension, settings, MultiBurst.hydrology,
                    MultiBurst.hydrology.parallelism(), HydrologyBinding.UNBOUND);
            HydrologyPlanHarness.Plans plans = new HydrologyPlanHarness.Plans(settings, terrain, terrain::naturalHeight, target.windows());
            List<Rendered> rendered = new ArrayList<>();
            for (WindowKey key : target.windows().keys()) {
                rendered.add(render(plans, key, terrain::naturalHeight, target.windows(), configuration.output()));
            }
            return rendered;
        }
    }

    static Rendered render(HydrologyPlanHarness.Plans plans, WindowKey key, IntBinaryOperator natural, HydrologyWindows.WindowRange range,
                           File output) throws Exception {
        Map<Long, ChunkWaterRaster> rasters = new HashMap<>();
        Columns columns = (x, z) -> {
            if (!range.containsBlock(x, z)) {
                return null;
            }
            ChunkWaterRaster raster = rasters.computeIfAbsent(HydrologyVoxels.chunkKey(x >> 4, z >> 4),
                    ignored -> plans.rasterize(x >> 4, z >> 4));
            int ground = natural.applyAsInt(x, z);
            return new Column(ground, raster.empty() ? ground : raster.terrainHeight(x & 15, z & 15), raster.waterHead(x & 15, z & 15),
                    raster.role(x & 15, z & 15));
        };
        WindowPlan plan = plans.window(key).plan();
        int minX = key.wx() * WindowKey.SIZE;
        int minZ = key.wz() * WindowKey.SIZE;
        List<Pond> lakes = new ArrayList<>();
        for (RegionKey region : RegionKey.lakeOwners(minX, minZ, minX + WindowKey.SIZE - 1, minZ + WindowKey.SIZE - 1)) {
            for (Pond lake : plans.region(region).lakes()) {
                if (touches(lake.mask(), minX, minZ)) {
                    lakes.add(lake);
                }
            }
        }
        List<Section> sections = new ArrayList<>();
        for (Reach reach : longest(plan.reaches())) {
            for (int point : stations(reach)) {
                sections.add(section(reach, point, columns));
            }
        }
        File directory = new File(output, "window-" + key.wx() + "-" + key.wz());
        Files.createDirectories(directory.toPath());
        ImageIO.write(planImage(key, columns, plan, plans.settings()), "png", new File(directory, "plan.png"));
        ImageIO.write(sectionImage(sections), "png", new File(directory, "sections.png"));
        Files.writeString(new File(directory, "summary.txt").toPath(), String.join("\n", summary(key, plan, lakes, sections, plans.settings())) + "\n",
                StandardCharsets.UTF_8);
        return new Rendered(key, plan.reaches().size(), plan.ponds().size(), lakes.size(), sections.size(), directory);
    }

    private static boolean touches(PondMask mask, int minX, int minZ) {
        int half = mask.step() >> 1;
        int left = mask.originX() - half;
        int top = mask.originZ() - half;
        int right = mask.originX() + mask.width() * mask.step() + half;
        int bottom = mask.originZ() + mask.depth() * mask.step() + half;
        return left < minX + WindowKey.SIZE && right > minX && top < minZ + WindowKey.SIZE && bottom > minZ;
    }

    static List<Reach> longest(List<Reach> reaches) {
        List<Reach> sorted = new ArrayList<>(reaches);
        sorted.sort(Comparator.comparingDouble(RiverTransectProbe::length).reversed().thenComparingLong(Reach::id));
        return sorted.subList(0, Math.min(MAXIMUM_REACHES, sorted.size()));
    }

    static int[] stations(Reach reach) {
        float[] distance = distances(reach);
        float total = distance[distance.length - 1];
        int[] stations = new int[STATIONS.length];
        for (int index = 0; index < STATIONS.length; index++) {
            double target = total * STATIONS[index];
            int point = 0;
            while (point < distance.length - 1 && distance[point] < target) {
                point++;
            }
            stations[index] = point;
        }
        return stations;
    }

    static Section section(Reach reach, int point, Columns columns) {
        int before = Math.max(0, point - 1);
        int after = Math.min(reach.xs().length - 1, point + 1);
        float dx = reach.xs()[after] - reach.xs()[before];
        float dz = reach.zs()[after] - reach.zs()[before];
        float length = (float) Math.sqrt(dx * dx + dz * dz);
        float tangentX = length > 1e-6f ? dx / length : 1f;
        float tangentZ = length > 1e-6f ? dz / length : 0f;
        Column[] cut = new Column[2 * SECTION_HALF + 1];
        for (int offset = -SECTION_HALF; offset <= SECTION_HALF; offset++) {
            int x = (int) Math.floor(reach.xs()[point] - offset * tangentZ);
            int z = (int) Math.floor(reach.zs()[point] + offset * tangentX);
            cut[offset + SECTION_HALF] = columns.at(x, z);
        }
        float[] distance = distances(reach);
        return new Section(reach.id(), point, distance[point] / Math.max(1e-6f, distance[distance.length - 1]), reach.xs()[point],
                reach.zs()[point], tangentX, tangentZ, cut);
    }

    static BufferedImage planImage(WindowKey key, Columns columns, WindowPlan plan, HydrologySettings settings) {
        int originX = key.wx() * WindowKey.SIZE;
        int originZ = key.wz() * WindowKey.SIZE;
        BufferedImage image = new BufferedImage(WindowKey.SIZE, WindowKey.SIZE, BufferedImage.TYPE_INT_RGB);
        for (int z = 0; z < WindowKey.SIZE; z++) {
            for (int x = 0; x < WindowKey.SIZE; x++) {
                Column column = columns.at(originX + x, originZ + z);
                image.setRGB(x, z, column == null ? 0 : color(column, settings.seaLevel(), settings.maxY()));
            }
        }
        Graphics2D graphics = image.createGraphics();
        graphics.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
        for (Reach reach : plan.reaches()) {
            graphics.setColor(tierColor(reach, settings));
            graphics.setStroke(new BasicStroke(1.5f));
            for (int point = 1; point < reach.xs().length; point++) {
                graphics.drawLine(Math.round(reach.xs()[point - 1] - originX), Math.round(reach.zs()[point - 1] - originZ),
                        Math.round(reach.xs()[point] - originX), Math.round(reach.zs()[point] - originZ));
                if (reach.style()[point] == ReachStyle.WATERFALL || reach.style()[point] == ReachStyle.CASCADE) {
                    graphics.fillOval(Math.round(reach.xs()[point] - originX) - 2, Math.round(reach.zs()[point] - originZ) - 2, 4, 4);
                }
            }
        }
        graphics.setColor(Color.YELLOW);
        for (Pond pond : plan.ponds()) {
            outline(graphics, pond.mask(), originX, originZ);
        }
        graphics.dispose();
        return image;
    }

    private static void outline(Graphics2D graphics, PondMask mask, int originX, int originZ) {
        int step = mask.step();
        int half = step >> 1;
        for (int j = 0; j < mask.depth(); j++) {
            for (int i = 0; i < mask.width(); i++) {
                if (!mask.node(i, j)) {
                    continue;
                }
                int left = mask.originX() + i * step - half - originX;
                int top = mask.originZ() + j * step - half - originZ;
                if (!mask.node(i - 1, j)) {
                    graphics.drawLine(left, top, left, top + step);
                }
                if (!mask.node(i + 1, j)) {
                    graphics.drawLine(left + step, top, left + step, top + step);
                }
                if (!mask.node(i, j - 1)) {
                    graphics.drawLine(left, top, left + step, top);
                }
                if (!mask.node(i, j + 1)) {
                    graphics.drawLine(left, top + step, left + step, top + step);
                }
            }
        }
    }

    static BufferedImage sectionImage(List<Section> sections) {
        int columns = STATIONS.length;
        int rows = Math.max(1, Math.ceilDiv(sections.size(), columns));
        int panelWidth = (2 * SECTION_HALF + 1) * SCALE;
        BufferedImage image = new BufferedImage(columns * (panelWidth + PANEL_GAP), rows * (PANEL_HEIGHT + PANEL_GAP), BufferedImage.TYPE_INT_RGB);
        Graphics2D graphics = image.createGraphics();
        graphics.setColor(Color.WHITE);
        graphics.fillRect(0, 0, image.getWidth(), image.getHeight());
        for (int index = 0; index < sections.size(); index++) {
            drawSection(graphics, sections.get(index), (index % columns) * (panelWidth + PANEL_GAP), (index / columns) * (PANEL_HEIGHT + PANEL_GAP));
        }
        graphics.dispose();
        return image;
    }

    private static void drawSection(Graphics2D graphics, Section section, int left, int top) {
        int low = Integer.MAX_VALUE;
        int high = Integer.MIN_VALUE;
        for (Column column : section.columns()) {
            if (column != null) {
                low = Math.min(low, Math.min(column.terrain(), column.natural()));
                high = Math.max(high, Math.max(Math.max(column.terrain(), column.natural()), column.head()));
            }
        }
        if (low == Integer.MAX_VALUE) {
            return;
        }
        int base = Math.max(low - 2, high + 4 - PANEL_HEIGHT / SCALE);
        for (int offset = 0; offset < section.columns().length; offset++) {
            Column column = section.columns()[offset];
            if (column == null) {
                continue;
            }
            int x = left + offset * SCALE;
            fill(graphics, new Color(0x82634D), x, top, base, base, column.terrain());
            if (column.head() != NO_WATER && column.head() > column.terrain()) {
                fill(graphics, new Color(0x247AC4), x, top, base, column.terrain() + 1, column.head());
            }
            graphics.setColor(Color.DARK_GRAY);
            graphics.fillRect(x, y(top, base, column.natural()), SCALE, 1);
        }
    }

    private static void fill(Graphics2D graphics, Color color, int x, int top, int base, int from, int to) {
        int upper = y(top, base, to);
        int lower = y(top, base, from - 1);
        graphics.setColor(color);
        graphics.fillRect(x, Math.max(top, upper), SCALE, Math.max(0, Math.min(top + PANEL_HEIGHT, lower) - Math.max(top, upper)));
    }

    private static int y(int top, int base, int height) {
        return top + PANEL_HEIGHT - (height - base + 1) * SCALE;
    }

    static List<String> summary(WindowKey key, WindowPlan plan, List<Pond> lakes, List<Section> sections, HydrologySettings settings) {
        List<String> lines = new ArrayList<>();
        lines.add("window " + key.wx() + "," + key.wz() + " reaches=" + plan.reaches().size() + " ponds=" + plan.ponds().size() + " lakes="
                + lakes.size() + " sections=" + sections.size());
        for (Reach reach : plan.reaches()) {
            EnumMap<ReachStyle, Integer> styles = new EnumMap<>(ReachStyle.class);
            for (ReachStyle style : reach.style()) {
                styles.merge(style, 1, Integer::sum);
            }
            int last = reach.xs().length - 1;
            lines.add(String.format(Locale.ROOT, "reach %d tier=%s length=%.0f from=%.0f,%.0f@%d to=%.0f,%.0f@%d downstream=%d catchment=%.0f styles=%s",
                    reach.id(), reach.tier().name(), length(reach), reach.xs()[0], reach.zs()[0], reach.grade()[0], reach.xs()[last],
                    reach.zs()[last], reach.grade()[last], reach.downstreamId(), reach.catchment(), styles));
        }
        for (Pond pond : plan.ponds()) {
            lines.add(pondLine("pond", pond, settings));
        }
        for (Pond lake : lakes) {
            lines.add(pondLine("lake", lake, settings));
        }
        for (Section section : sections) {
            int wet = 0;
            int deepest = 0;
            for (Column column : section.columns()) {
                if (column != null && column.head() != NO_WATER) {
                    wet++;
                    deepest = Math.max(deepest, column.head() - column.terrain());
                }
            }
            lines.add(String.format(Locale.ROOT, "section reach=%d station=%.0f%% at=%.0f,%.0f wetColumns=%d deepest=%d", section.reachId(),
                    100D * section.station(), section.x(), section.z(), wet, deepest));
        }
        return lines;
    }

    private static String pondLine(String label, Pond pond, HydrologySettings settings) {
        PondMask mask = pond.mask();
        int nodes = 0;
        for (int j = 0; j < mask.depth(); j++) {
            for (int i = 0; i < mask.width(); i++) {
                if (mask.node(i, j)) {
                    nodes++;
                }
            }
        }
        return String.format(Locale.ROOT, "%s %d kind=%s level=%d aboveSea=%d floor=%d area=%d floorAt=%d,%d outlet=%d", label, pond.id(), pond.kind(),
                pond.level(), pond.level() - settings.seaLevel(), pond.floor(), nodes * mask.step() * mask.step(), pond.floorX(), pond.floorZ(),
                pond.outletReachId());
    }

    private static int color(Column column, int seaLevel, int maxY) {
        if (column.head() != NO_WATER) {
            int depth = Math.clamp(column.head() - column.terrain(), 0, 12);
            return new Color(0x10 + 4 * (12 - depth), 0x50 + 6 * (12 - depth), 0xC0).getRGB();
        }
        if (column.role() == WaterRole.DAM) {
            return 0xB03030;
        }
        if (column.role() == WaterRole.SHORE || column.role() == WaterRole.POND_SHORE) {
            return 0xDCCFA5;
        }
        int shade = (int) Math.round(60 + 170D * Math.clamp((column.terrain() - seaLevel) / (double) Math.max(1, maxY - seaLevel), 0D, 1D));
        if (column.terrain() < seaLevel) {
            return new Color(0x20, 0x40, 0x90).getRGB();
        }
        return column.role() == WaterRole.BANK ? new Color(shade, (int) (shade * 0.85D), (int) (shade * 0.7D)).getRGB()
                : new Color(shade, shade, shade).getRGB();
    }

    private static Color tierColor(Reach reach, HydrologySettings settings) {
        int tier = settings.tiers().indexOf(reach.tier());
        return switch (tier) {
            case 0 -> new Color(0x40E0FF);
            case 1 -> new Color(0x1E5AFF);
            default -> new Color(0x0A1E8C);
        };
    }

    static float length(Reach reach) {
        float[] distance = distances(reach);
        return distance[distance.length - 1];
    }

    private static float[] distances(Reach reach) {
        float[] distance = new float[reach.xs().length];
        for (int point = 1; point < distance.length; point++) {
            distance[point] = distance[point - 1] + (float) Math.hypot(reach.xs()[point] - reach.xs()[point - 1], reach.zs()[point] - reach.zs()[point - 1]);
        }
        return distance;
    }
}
