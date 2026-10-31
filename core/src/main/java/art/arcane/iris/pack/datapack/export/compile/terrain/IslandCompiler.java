package art.arcane.iris.pack.datapack.export.compile.terrain;

import art.arcane.iris.generation.biome.FloatingIslandEdgeProfile;
import art.arcane.iris.generation.biome.IrisBiome;
import art.arcane.iris.generation.biome.IrisBiomeGeneratorLink;
import art.arcane.iris.generation.biome.IrisFloatingChildBiomes;
import art.arcane.iris.generation.terrain.IrisDimension;
import art.arcane.iris.pack.datapack.export.VanillaResourceIdentifier;
import art.arcane.iris.pack.datapack.export.ir.ExportDiagnostic;
import art.arcane.iris.pack.datapack.export.ir.ExportLimit;
import art.arcane.iris.pack.datapack.export.ir.Field;
import art.arcane.iris.pack.datapack.export.lower.ExportCapabilities;
import art.arcane.iris.pack.loading.IrisData;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

final class IslandCompiler {
    private static final String SCOPE = "island";
    private static final String SHARED_PREFIX = "terrain/";
    private static final String RESOURCE_PREFIX = "biomes/";
    private static final double CLOSED = 4096.0;
    private static final double EDGE_STEP = 4.0;
    private static final double MIN_BOTTOM_EXPONENT = 0.1;
    private static final double HALF_BLOCK = 0.5;
    private static final Field ONE = new Field.Const(1.0);

    private final Field height;
    private final BandTreeCompiler bands;
    private final GeneratorCompiler generators;
    private final Field blockY;
    private final IrisDimension dimension;
    private final IrisData data;
    private final ExportCapabilities capabilities;
    private final List<ExportDiagnostic> diagnostics;

    IslandCompiler(Field height, BandTreeCompiler bands, GeneratorCompiler generators, Field blockY, IrisDimension dimension,
                   IrisData data, ExportCapabilities capabilities, List<ExportDiagnostic> diagnostics) {
        this.height = height;
        this.bands = bands;
        this.generators = generators;
        this.blockY = blockY;
        this.dimension = dimension;
        this.data = data;
        this.capabilities = capabilities;
        this.diagnostics = diagnostics;
    }

    List<Field> compile(Map<String, IrisBiome> biomes) {
        List<Field> islands = new ArrayList<>();
        for (Map.Entry<String, IrisBiome> entry : biomes.entrySet()) {
            IrisBiome parent = entry.getValue();
            List<IrisFloatingChildBiomes> children = parent.getFloatingChildBiomes();
            if (children == null || children.isEmpty()) {
                continue;
            }
            String path = SCOPE + "/" + VanillaResourceIdentifier.sanitizePath(parent.getLoadKey());
            Field presence = FieldMacros.share(SHARED_PREFIX + path + "/presence", bands.hard(Map.of(entry.getKey(), 1.0)));
            List<Field> terms = new ArrayList<>(children.size());
            for (int index = 0; index < children.size(); index++) {
                IrisFloatingChildBiomes child = children.get(index);
                report(parent, child);
                terms.add(island(parent, child, presence, path + "/" + index));
            }
            islands.add(parent.isMergeFloatingChildBiomes() || terms.size() == 1 ? union(terms) : pick(children, terms, path));
        }
        return islands;
    }

    private Field island(IrisBiome parent, IrisFloatingChildBiomes child, Field presence, String path) {
        Field footprint = generators.style(child.getFootprintStyle(), 1.0, path + "/footprint");
        Field signed = FieldMacros.share(SHARED_PREFIX + path + "/footprint", FieldMacros.sum(List.of(FieldMacros.scale(footprint, 2.0), new Field.Const(-1.0))));
        double cut = 2.0 * Math.max(0.0, Math.min(1.0, child.getFootprintThreshold())) - 1.0;
        Field edge = FieldMacros.signedDistance(signed, cut, EDGE_STEP);
        int width = FloatingIslandEdgeProfile.clampWidth(child.getEdgeTaperWidth());
        Field fade = FieldMacros.smoothstep(edge, 1.0, 1.0 + width);
        double exponent = FloatingIslandEdgeProfile.clampExponent(child.getEdgeTaperExponent());
        if (exponent != FloatingIslandEdgeProfile.DEFAULT_EXPONENT) {
            fade = new Field.Binary(Field.BinaryOp.POW, fade, new Field.Const(exponent));
        }
        fade = FieldMacros.share(SHARED_PREFIX + path + "/fade", fade);
        Field base = FieldMacros.share(SHARED_PREFIX + path + "/base", base(child, path));
        Field top = FieldMacros.share(SHARED_PREFIX + path + "/top",
                new Field.Binary(Field.BinaryOp.ADD, base, rounded(FieldMacros.product(fade, topHeight(parent, child, path)))));
        Field bottom = new Field.Binary(Field.BinaryOp.SUB, base, rounded(FieldMacros.product(fade, depth(child, path))));
        bottom = new Field.Binary(Field.BinaryOp.MAX, bottom, FieldMacros.sum(List.of(top, new Field.Const(1.0 - Math.max(1, child.getMaxThickness())))));
        if (child.getMinAbsoluteY() != null) {
            bottom = new Field.Binary(Field.BinaryOp.MAX, bottom, new Field.Const(child.getMinAbsoluteY()));
        }
        if (child.getMaxAbsoluteY() != null) {
            top = new Field.Binary(Field.BinaryOp.MIN, top, new Field.Const(child.getMaxAbsoluteY()));
        }
        Field aboveBottom = FieldMacros.sum(List.of(new Field.Binary(Field.BinaryOp.SUB, blockY, bottom), new Field.Const(HALF_BLOCK)));
        Field belowTop = FieldMacros.sum(List.of(new Field.Binary(Field.BinaryOp.SUB, top, blockY), new Field.Const(HALF_BLOCK)));
        Field lens = new Field.Binary(Field.BinaryOp.MIN, aboveBottom, belowTop);
        Field terrain = new Field.Binary(Field.BinaryOp.SUB, height, blockY);
        Field open = FieldMacros.sum(List.of(new Field.Binary(Field.BinaryOp.SUB, lens, terrain), new Field.Const(CLOSED)));
        Field condition = FieldMacros.product(presence, fade);
        return FieldMacros.sum(List.of(new Field.Gate(condition, open), new Field.Const(-CLOSED)));
    }

    private Field base(IrisFloatingChildBiomes child, String path) {
        int worldMin = dimension.getMinHeight();
        int minimum = Math.max(0, child.getMinHeightAboveSurface() - worldMin);
        int maximum = Math.max(minimum, child.getMaxHeightAboveSurface() - worldMin);
        Field lowest = new Field.Const(worldMin + minimum);
        if (maximum == minimum) {
            return lowest;
        }
        Field altitude = unit(generators.style(child.getAltitudeStyle(), 1.0, path + "/altitude"));
        return new Field.Binary(Field.BinaryOp.ADD, lowest, rounded(FieldMacros.scale(altitude, maximum - minimum)));
    }

    private Field topHeight(IrisBiome parent, IrisFloatingChildBiomes child, String path) {
        int maximum = Math.max(0, child.getMaxTopHeight());
        if (maximum == 0) {
            return new Field.Const(0.0);
        }
        return switch (child.getTopShapeMode()) {
            case FLAT -> new Field.Const(maximum);
            case NOISE -> rounded(FieldMacros.scale(unit(generators.style(child.getTopShapeStyle(), 1.0, path + "/top")),
                    Math.max(0.0, Math.min(1.0, child.getTopShapeAmp())) * maximum));
            case BIOME -> new Field.Binary(Field.BinaryOp.MIN, new Field.Const(maximum),
                    new Field.Binary(Field.BinaryOp.MAX, new Field.Const(0.0), rounded(targetHeight(target(parent, child)))));
        };
    }

    private Field targetHeight(IrisBiome target) {
        List<Field> parts = new ArrayList<>();
        for (IrisBiomeGeneratorLink link : TerrainBiomes.links(target)) {
            Field noise = unit(generators.compile(TerrainBiomes.generator(link, data), SCOPE));
            parts.add(FieldMacros.sum(List.of(new Field.Const(link.getMin()), FieldMacros.scale(noise, link.getMax() - link.getMin()))));
        }
        Field sum = FieldMacros.sum(parts);
        return new Field.Clamp(sum, 0.0, dimension.getMaxHeight() - dimension.getMinHeight());
    }

    private Field depth(IrisFloatingChildBiomes child, String path) {
        int minimum = Math.max(0, child.getBottomDepthMin());
        int maximum = Math.max(minimum, child.getBottomDepthMax());
        if (maximum == minimum) {
            return new Field.Const(minimum);
        }
        Field shaped = unit(generators.style(child.getBottomStyle(), 1.0, path + "/bottom"));
        double exponent = Math.max(MIN_BOTTOM_EXPONENT, child.getBottomExponent());
        if (exponent != 1.0) {
            shaped = new Field.Binary(Field.BinaryOp.POW, shaped, new Field.Const(exponent));
        }
        return FieldMacros.sum(List.of(new Field.Const(minimum), FieldMacros.scale(shaped, maximum - minimum)));
    }

    private Field pick(List<IrisFloatingChildBiomes> children, List<Field> terms, String path) {
        Field picker = unit(generators.style(children.getFirst().getPickerStyle(), 1.0, path + "/picker"));
        double total = 0.0;
        for (IrisFloatingChildBiomes child : children) {
            total += 1.0 / Math.max(1, child.getRarity());
        }
        List<Double> thresholds = new ArrayList<>(children.size() - 1);
        List<Field> indices = new ArrayList<>(children.size());
        double cumulative = 0.0;
        for (int index = 0; index < children.size(); index++) {
            if (index > 0) {
                thresholds.add(cumulative / total);
            }
            cumulative += 1.0 / Math.max(1, children.get(index).getRarity());
            indices.add(new Field.Const(index));
        }
        return new Field.Mux(new Field.Partition(picker, thresholds, indices), terms);
    }

    private IrisBiome target(IrisBiome parent, IrisFloatingChildBiomes child) {
        String key = child.getBiome();
        if (key == null || key.isBlank() || key.equals(parent.getLoadKey())) {
            return parent;
        }
        IrisBiome target = data.getBiomeLoader().load(key, false);
        return target == null ? parent : target;
    }

    private void report(IrisBiome parent, IrisFloatingChildBiomes child) {
        List<String> features = new ArrayList<>(3);
        if (child.getWallWarpStyle() != null && child.getWallWarpAmplitude() > 0.0) {
            features.add("wall warp");
        }
        if (child.hasCarvingReference() || (child.getCarveStyle() != null && child.getCarveThreshold() < 1.0)) {
            features.add("interior carving");
        }
        if (child.getLocalFluidHeight() != null && child.getLocalFluidHeight() > 0) {
            features.add("island water");
        }
        if (features.isEmpty()) {
            return;
        }
        diagnostics.add(new ExportDiagnostic(ExportDiagnostic.Severity.WARNING, ExportLimit.FLOATING_ISLAND,
                RESOURCE_PREFIX + parent.getLoadKey(), "Floating island " + String.join(", ", features) + " is not exported"));
    }

    private static Field union(List<Field> terms) {
        Field union = terms.getFirst();
        for (int index = 1; index < terms.size(); index++) {
            union = new Field.Binary(Field.BinaryOp.MAX, union, terms.get(index));
        }
        return union;
    }

    private static Field unit(Field value) {
        return new Field.Clamp(value, 0.0, 1.0);
    }

    private static Field rounded(Field value) {
        if (value instanceof Field.Const constant) {
            return new Field.Const(Math.round(constant.value()));
        }
        return new Field.Round(Field.RoundMode.ROUND, value, ONE);
    }
}
