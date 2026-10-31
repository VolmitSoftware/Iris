package art.arcane.iris.pack.datapack.export.compile.terrain;

import art.arcane.iris.generation.noise.IrisInterpolator;
import art.arcane.iris.pack.datapack.export.VanillaResourceIdentifier;
import art.arcane.iris.pack.datapack.export.compile.biome.BiomeCodes;
import art.arcane.iris.pack.datapack.export.ir.ClimateLayout;
import art.arcane.iris.pack.datapack.export.ir.Field;
import art.arcane.volmlib.util.interpolation.InterpolationMethod;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.Deque;
import java.util.HashMap;
import java.util.IdentityHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;

public final class BandTreeCompiler {
    private static final double LATTICE_STARCAST_SPAN = Math.sqrt(7.0);
    private static final double POINT_STARCAST_SPAN = Math.sqrt(6.0);
    private static final double LADDER_BASE = 4.0;
    private static final double LADDER_SPREAD = Math.sqrt(7.0 / 6.0);
    private static final int LADDER_FIRST = 1;
    private static final int LATTICES_PER_BAND = 3;
    private static final Comparator<ClimateLayout.ClimateEntry> ENTRY_ORDER = Comparator
            .comparingDouble(ClimateLayout.ClimateEntry::min)
            .thenComparingDouble(ClimateLayout.ClimateEntry::max)
            .thenComparing(ClimateLayout.ClimateEntry::biomeId);

    private final Field selector;
    private final List<ClimateLayout.ClimateEntry> entries;
    private final List<Double> boundaries;
    private final String prefix;
    private final Map<Integer, Field> latticeCodes = new HashMap<>();

    public BandTreeCompiler(BiomeCodes codes, String scope) {
        Objects.requireNonNull(codes, "codes");
        entries = surfaceEntries(codes.layout());
        if (entries.isEmpty()) {
            throw new IllegalArgumentException("The biome layout has no surface entries on the temperature axis");
        }
        boundaries = boundaries(entries);
        prefix = "terrain/" + VanillaResourceIdentifier.requirePath(scope, "band scope") + "/blend/";
        selector = FieldMacros.share(prefix + "selector", selector(codes));
    }

    public static Field selector(BiomeCodes codes) {
        Field.Shared base = findShared(codes.surfaceCode(), BiomeCodes.BASE_CODE);
        Field root = base == null ? codes.surfaceCode() : base;
        if (findShared(root, HeightCompiler.HEIGHT) != null) {
            throw new IllegalStateException("The surface biome code reads the terrain height; wrap its height-free base code in Shared(\""
                    + BiomeCodes.BASE_CODE + "\", ...) so the terrain bands can follow the base biome");
        }
        return root;
    }

    public static List<String> surfaceBiomeIds(BiomeCodes codes) {
        Set<String> ids = new LinkedHashSet<>();
        for (ClimateLayout.ClimateEntry entry : surfaceEntries(codes.layout())) {
            ids.add(entry.biomeId());
        }
        return List.copyOf(ids);
    }

    public static boolean blends(IrisInterpolator interpolator) {
        return kernel(interpolator.getFunction()) != Kernel.HARD && interpolator.getHorizontalScale() > 0.0;
    }

    public static List<Integer> lattices(IrisInterpolator interpolator) {
        double radius = interpolator.getHorizontalScale();
        return switch (kernel(interpolator.getFunction())) {
            case HARD -> List.of();
            case LATTICE -> List.of(Math.max(1, (int) Math.round(radius)));
            case POINT_STARCAST -> ladder(POINT_STARCAST_SPAN * radius);
            case LATTICE_STARCAST -> ladder(LATTICE_STARCAST_SPAN * radius);
        };
    }

    public Field band(Map<String, Double> values, IrisInterpolator interpolator) {
        Objects.requireNonNull(values, "values");
        Field hard = hard(values);
        if (hard instanceof Field.Const || !blends(interpolator)) {
            return hard;
        }
        List<Integer> cells = lattices(interpolator);
        List<Field> terms = new ArrayList<>(cells.size());
        for (int cell : cells) {
            terms.add(new Field.Grid(layoutBand(latticeCode(cell), values), cell, 1));
        }
        return FieldMacros.scale(FieldMacros.sum(terms), 1.0 / terms.size());
    }

    public Field hard(Map<String, Double> values) {
        return layoutBand(selector, values);
    }

    private Field latticeCode(int cell) {
        return latticeCodes.computeIfAbsent(cell, size -> new Field.Shared(prefix + "lattice/" + size, new Field.Grid(selector, size, 1)));
    }

    private Field layoutBand(Field code, Map<String, Double> values) {
        List<Double> thresholds = new ArrayList<>(boundaries.size());
        List<Field> leaves = new ArrayList<>(entries.size());
        leaves.add(new Field.Const(values.getOrDefault(entries.getFirst().biomeId(), 0.0)));
        for (int index = 1; index < entries.size(); index++) {
            Field leaf = new Field.Const(values.getOrDefault(entries.get(index).biomeId(), 0.0));
            if (leaf.equals(leaves.getLast())) {
                continue;
            }
            thresholds.add(boundaries.get(index - 1));
            leaves.add(leaf);
        }
        return leaves.size() == 1 ? leaves.getFirst() : new Field.Partition(code, thresholds, leaves);
    }

    private static List<Integer> ladder(double width) {
        double center = width / LADDER_SPREAD;
        int middle = Math.max(LADDER_FIRST + 1, (int) Math.round(2.0 * Math.log(center / LADDER_BASE) / Math.log(2.0)));
        List<Integer> cells = new ArrayList<>(LATTICES_PER_BAND);
        for (int step = middle - LATTICES_PER_BAND / 2; step <= middle + LATTICES_PER_BAND / 2; step++) {
            cells.add((int) Math.round(LADDER_BASE * Math.pow(2.0, step / 2.0)));
        }
        return List.copyOf(cells);
    }

    private static List<ClimateLayout.ClimateEntry> surfaceEntries(ClimateLayout layout) {
        List<ClimateLayout.ClimateEntry> surface = new ArrayList<>();
        for (ClimateLayout.ClimateEntry entry : layout.entries()) {
            if (entry.axis() == ClimateLayout.ClimateAxis.TEMPERATURE) {
                surface.add(entry);
            }
        }
        surface.sort(ENTRY_ORDER);
        return Collections.unmodifiableList(surface);
    }

    private static List<Double> boundaries(List<ClimateLayout.ClimateEntry> entries) {
        List<Double> boundaries = new ArrayList<>(Math.max(0, entries.size() - 1));
        for (int index = 1; index < entries.size(); index++) {
            boundaries.add((entries.get(index - 1).max() + entries.get(index).min()) / 2.0);
        }
        return boundaries;
    }

    private static Field.Shared findShared(Field root, String name) {
        Set<Field> visited = Collections.newSetFromMap(new IdentityHashMap<>());
        Deque<Field> pending = new ArrayDeque<>();
        pending.push(root);
        while (!pending.isEmpty()) {
            Field field = pending.pop();
            if (!visited.add(field)) {
                continue;
            }
            if (field instanceof Field.Shared shared && shared.name().equals(name)) {
                return shared;
            }
            for (Field child : FieldMacros.children(field)) {
                pending.push(child);
            }
        }
        return null;
    }

    private static Kernel kernel(InterpolationMethod method) {
        return switch (method) {
            case NONE -> Kernel.HARD;
            case STARCAST_3, STARCAST_6, STARCAST_9, STARCAST_12 -> Kernel.POINT_STARCAST;
            case BILINEAR_STARCAST_3, BILINEAR_STARCAST_6, BILINEAR_STARCAST_9, BILINEAR_STARCAST_12,
                 HERMITE_STARCAST_3, HERMITE_STARCAST_6, HERMITE_STARCAST_9, HERMITE_STARCAST_12 -> Kernel.LATTICE_STARCAST;
            default -> Kernel.LATTICE;
        };
    }

    private enum Kernel {
        HARD,
        LATTICE,
        POINT_STARCAST,
        LATTICE_STARCAST
    }
}
