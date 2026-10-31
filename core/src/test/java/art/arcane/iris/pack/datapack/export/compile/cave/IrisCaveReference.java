package art.arcane.iris.pack.datapack.export.compile.cave;

import art.arcane.iris.generation.cave.IrisCaveFieldModule;
import art.arcane.iris.generation.cave.IrisCaveProfile;
import art.arcane.iris.generation.noise.IrisStyledRange;
import art.arcane.iris.pack.datapack.export.compile.noise.NoiseRegistry;
import art.arcane.iris.pack.datapack.export.compile.noise.StyleCompiler;
import art.arcane.iris.pack.datapack.export.ir.Field;
import art.arcane.iris.pack.datapack.export.lower.ExportCapabilities;
import art.arcane.iris.pack.value.IrisRange;

import java.util.ArrayList;
import java.util.List;

final class IrisCaveReference {
    private static final long BASE_SALT = 934_447L;
    private static final long DETAIL_SALT = 612_991L;
    private static final long THRESHOLD_SALT = 489_112L;
    private static final long SURFACE_BREAK_SALT = 341_219L;
    private static final long MODULE_SALT = 1_000_003L;
    private static final long MODULE_SALT_STEP = 65_537L;
    private static final int CEILING_FADE_DEPTH = 12;
    private static final double CLOSURE_EPSILON = 0.000001D;

    private final IrisCaveProfile profile;
    private final NoiseRegistry registry;
    private final Field height;
    private final Field base;
    private final Field detail;
    private final List<Field> modules;
    private final Field threshold;
    private final Field surfaceBreak;
    private final int worldMinY;
    private final int worldHeight;
    private final double normalization;

    IrisCaveReference(IrisCaveProfile profile, StyleCompiler styles, NoiseRegistry registry, ExportCapabilities capabilities, Field height) {
        this.profile = profile;
        this.registry = registry;
        this.height = height;
        this.worldMinY = CaveTestPacks.MIN_Y;
        this.worldHeight = CaveTestPacks.MAX_Y - CaveTestPacks.MIN_Y;
        base = styles.compile(profile.getBaseDensityStyle(), BASE_SALT, registry, capabilities);
        detail = styles.compile(profile.getDetailDensityStyle(), DETAIL_SALT, registry, capabilities);
        modules = new ArrayList<>();
        double weight = Math.abs(profile.getBaseWeight()) + Math.abs(profile.getDetailWeight());
        for (int index = 0; index < profile.getModules().size(); index++) {
            IrisCaveFieldModule module = profile.getModules().get(index);
            modules.add(styles.compile(module.getStyle(), MODULE_SALT + index * MODULE_SALT_STEP, registry, capabilities));
            weight += Math.abs(module.getWeight());
        }
        normalization = weight <= 0 ? 1D : weight;
        IrisStyledRange range = profile.getDensityThreshold();
        threshold = range.getMin() == range.getMax() || range.getStyle().isFlat() ? null
                : styles.compile(range.getStyle(), THRESHOLD_SALT, registry, capabilities);
        surfaceBreak = profile.isAllowSurfaceBreak() ? styles.compile(profile.getSurfaceBreakStyle(), SURFACE_BREAK_SALT, registry, capabilities) : null;
    }

    boolean carved(int x, int worldY, int z) {
        int y = worldY - worldMinY;
        IrisRange range = profile.getVerticalRange();
        int minY = Math.max(0, (int) Math.floor(range.getMin()));
        int maxY = Math.min(worldHeight - 1, (int) Math.ceil(range.getMax()));
        if (y < minY || y > maxY) {
            return false;
        }
        int surface = (int) Math.round(sample(height, x, 0, z)) - worldMinY;
        int unclampedClearanceTop = surface - Math.max(0, profile.getSurfaceClearance());
        int clearanceTop = Math.min(maxY, Math.max(minY, unclampedClearanceTop));
        boolean breakColumn = surfaceBreak != null && signed(sample(surfaceBreak, x, 0, z)) >= profile.getSurfaceBreakNoiseThreshold();
        int columnTop = breakColumn ? Math.min(maxY, Math.max(minY, surface)) : clearanceTop;
        if (y > columnTop) {
            return false;
        }
        double local = columnThreshold(x, z) - profile.getThresholdBias();
        if (breakColumn && y >= Math.max(minY, surface - Math.max(0, profile.getSurfaceBreakDepth()))) {
            local += Math.max(0, profile.getSurfaceBreakThresholdBoost());
        }
        local -= edgeFade(y, minY, maxY);
        if (!breakColumn && unclampedClearanceTop <= maxY) {
            int distance = columnTop - y;
            double closure = closure(y);
            if (distance >= 0 && distance < CEILING_FADE_DEPTH && local > closure) {
                double progress = distance / (double) CEILING_FADE_DEPTH;
                double smooth = progress * progress * (3D - (2D * progress));
                local = closure + ((local - closure) * smooth);
            }
        }
        return density(x, worldY, z) <= local;
    }

    private double columnThreshold(int x, int z) {
        IrisStyledRange range = profile.getDensityThreshold();
        if (range.getMin() == range.getMax()) {
            return range.getMin();
        }
        if (threshold == null) {
            return range.getMin() + (range.getMax() - range.getMin()) * 0.5;
        }
        return range.getMin() + (range.getMax() - range.getMin()) * sample(threshold, x, 0, z);
    }

    private double density(int x, int worldY, int z) {
        int y = worldY - worldMinY;
        double density = signed(sample(base, x, worldY, z)) * profile.getBaseWeight();
        density += signed(sample(detail, x, worldY, z)) * profile.getDetailWeight();
        for (int index = 0; index < modules.size(); index++) {
            IrisCaveFieldModule module = profile.getModules().get(index);
            if (!active(module, y)) {
                continue;
            }
            double moduleDensity = signed(sample(modules.get(index), x, worldY, z)) - module.getThreshold();
            if (module.isInvert()) {
                moduleDensity = -moduleDensity;
            }
            density += moduleDensity * module.getWeight();
        }
        return density / normalization;
    }

    private double closure(int y) {
        double minimum = -Math.abs(profile.getBaseWeight()) - Math.abs(profile.getDetailWeight());
        for (IrisCaveFieldModule module : profile.getModules()) {
            if (!active(module, y)) {
                continue;
            }
            double rawMin = module.isInvert() ? module.getThreshold() - 1D : -1D - module.getThreshold();
            double rawMax = module.isInvert() ? module.getThreshold() + 1D : 1D - module.getThreshold();
            minimum += Math.min(rawMin * module.getWeight(), rawMax * module.getWeight());
        }
        return minimum / normalization - CLOSURE_EPSILON;
    }

    private double edgeFade(int y, int minY, int maxY) {
        int fadeRange = Math.max(0, profile.getVerticalEdgeFade());
        double strength = Math.max(0D, profile.getVerticalEdgeFadeStrength());
        if (fadeRange <= 0 || maxY <= minY || strength <= 0D) {
            return 0D;
        }
        int edgeDistance = Math.min(y - minY, maxY - y);
        if (edgeDistance >= fadeRange) {
            return 0D;
        }
        double t = Math.max(0D, Math.min(1D, edgeDistance / (double) fadeRange));
        return (1D - t * t * (3D - (2D * t))) * strength;
    }

    private static boolean active(IrisCaveFieldModule module, int y) {
        return y >= (int) Math.floor(module.getVerticalRange().getMin()) && y <= (int) Math.ceil(module.getVerticalRange().getMax());
    }

    private double sample(Field field, int x, int y, int z) {
        return CaveTestPacks.sample(field, registry, x, y, z);
    }

    private static double signed(double unit) {
        return unit * 2D - 1D;
    }
}
