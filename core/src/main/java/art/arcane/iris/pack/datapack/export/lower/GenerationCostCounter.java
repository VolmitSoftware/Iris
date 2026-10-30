package art.arcane.iris.pack.datapack.export.lower;

import art.arcane.iris.pack.datapack.DataVersion;
import art.arcane.iris.pack.datapack.export.GenerationCost;
import art.arcane.iris.pack.datapack.export.WorldgenExportTargets;
import art.arcane.iris.pack.datapack.export.ir.AquiferDef;
import art.arcane.iris.pack.datapack.export.ir.Field;
import art.arcane.iris.pack.datapack.export.ir.RouterDef;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Deque;
import java.util.EnumMap;
import java.util.HashMap;
import java.util.HashSet;
import java.util.IdentityHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;

public final class GenerationCostCounter {
    private static final int INTERPOLATION_CORNERS = 8;
    private static final int MAX_SURFACE_STEPS = 4096;

    private final Map<Field, Emission> emissions = new IdentityHashMap<>();
    private final Map<Field, Double> expanded = new IdentityHashMap<>();
    private final Map<Field, Double> worstCase = new IdentityHashMap<>();
    private final Map<Field, Long> inline = new IdentityHashMap<>();
    private final Map<Field, Integer> shapes = new IdentityHashMap<>();
    private final Map<List<Object>, Integer> shapeIds = new HashMap<>();
    private final VanillaBounds bounds;

    private GenerationCostCounter(VanillaBounds bounds, DataVersion target) {
        if (!WorldgenExportTargets.SUPPORTED.contains(target)) {
            throw WorldgenExportTargets.unsupported(target.getVersion());
        }
        this.bounds = Objects.requireNonNull(bounds);
    }

    public static GenerationCost count(RouterDef router, VanillaBounds bounds, DataVersion target) {
        GenerationCostCounter counter = new GenerationCostCounter(bounds, target);
        Map<GenerationCost.Root, GenerationCost.RootCost> roots = new EnumMap<>(GenerationCost.Root.class);
        for (GenerationCost.Root root : GenerationCost.Root.values()) {
            Field field = field(router, root);
            double expandedNodes = counter.expanded(field);
            roots.put(root, new GenerationCost.RootCost(counter.worstCase(field), expandedNodes, counter.distinct(field)));
        }
        return new GenerationCost(roots, 0L);
    }

    public static double worstCase(Field field, VanillaBounds bounds, DataVersion target) {
        GenerationCostCounter counter = new GenerationCostCounter(bounds, target);
        counter.expanded(field);
        return counter.worstCase(field);
    }

    private static Field field(RouterDef router, GenerationCost.Root root) {
        AquiferDef aquifer = router.aquifer();
        return switch (root) {
            case TEMPERATURE -> router.temperature();
            case VEGETATION -> router.vegetation();
            case CONTINENTS -> router.continents();
            case EROSION -> router.erosion();
            case DEPTH -> router.depth();
            case RIDGES -> router.ridges();
            case CHUNK_SURFACE_LEVEL -> router.chunkSurfaceLevel();
            case FINAL_DENSITY -> router.finalDensity();
            case AQUIFER_BARRIER -> aquifer.barrier();
            case AQUIFER_FLOODEDNESS -> aquifer.floodedness();
            case AQUIFER_SPREAD -> aquifer.spread();
            case AQUIFER_LAVA -> aquifer.lava();
            case AQUIFER_EXCLUSION -> aquifer.exclusion();
            case AQUIFER_SURFACE_LEVEL -> aquifer.surfaceLevel();
        };
    }

    private double expanded(Field root) {
        for (Field field : postOrder(root, expanded, false)) {
            Emission emission = emission(field);
            double total = emission.own();
            for (Field child : emission.children()) {
                total += expanded.get(child);
            }
            expanded.put(field, total);
        }
        return expanded.get(root);
    }

    private double worstCase(Field root) {
        for (Field field : postOrder(root, worstCase, false)) {
            worstCase.put(field, pointCost(field));
        }
        return worstCase.get(root);
    }

    private double pointCost(Field field) {
        return switch (field) {
            case Field.Curve curve -> 1.0 + splineCost(curve);
            case Field.Partition partition -> 1.0 + worstCase.get(partition.input()) + worstOf(partition.leaves());
            case Field.RangeIf rangeIf -> 1.0 + worstCase.get(rangeIf.input()) + Math.max(worstCase.get(rangeIf.inRange()), worstCase.get(rangeIf.outOfRange()));
            case Field.Mux mux -> muxCost(mux);
            case Field.Gate gate -> gateCost(gate);
            case Field.Grid grid -> 1.0 + INTERPOLATION_CORNERS * expanded(grid.expr());
            case Field.TopSurface topSurface -> 1.0 + surfaceSteps(topSurface) * worstCase.get(topSurface.density());
            default -> sumCost(field);
        };
    }

    private double sumCost(Field field) {
        Emission emission = emission(field);
        double total = emission.own();
        for (Field child : emission.children()) {
            total += worstCase.get(child);
        }
        return total;
    }

    private double splineCost(Field.Curve curve) {
        List<Field.Knot> knots = curve.knots();
        double[] values = new double[knots.size()];
        for (int index = 0; index < values.length; index++) {
            values[index] = knots.get(index).value() instanceof Field.Curve nested ? splineCost(nested) : 0.0;
        }
        double worstPair = values.length == 1 ? values[0] : 0.0;
        for (int index = 0; index + 1 < values.length; index++) {
            worstPair = Math.max(worstPair, values[index] + values[index + 1]);
        }
        return 1.0 + worstCase.get(curve.input()) + worstPair;
    }

    private double muxCost(Field.Mux mux) {
        if (mux.cases().size() == 1) {
            return worstCase.get(mux.cases().getFirst());
        }
        double worstOption = 0.0;
        for (Field option : mux.cases()) {
            if (!(option instanceof Field.Const)) {
                worstOption = Math.max(worstOption, 1.0 + worstCase.get(option));
            }
        }
        return 2.0 + worstCase.get(mux.index()) + worstOption;
    }

    private double gateCost(Field.Gate gate) {
        double open = gate.expr() instanceof Field.Const ? 0.0 : 1.0 + worstCase.get(gate.expr());
        return 2.0 + worstCase.get(gate.condition()) + open;
    }

    private double worstOf(List<Field> options) {
        double worst = 0.0;
        for (Field option : options) {
            worst = Math.max(worst, worstCase.get(option));
        }
        return worst;
    }

    private static double surfaceSteps(Field.TopSurface topSurface) {
        double steps = Math.floor((double) (topSurface.upper() - topSurface.lower()) / topSurface.step()) + 1.0;
        return Math.clamp(steps, 1.0, MAX_SURFACE_STEPS);
    }

    private long distinct(Field root) {
        Set<Integer> files = new HashSet<>();
        long total = inline(root);
        for (Field field : postOrder(root, Map.of(), false)) {
            if (field instanceof Field.Shared shared && files.add(shape(shared))) {
                total += 1L + inline(shared.expr());
            }
        }
        return total;
    }

    private long inline(Field root) {
        for (Field field : postOrder(root, inline, true)) {
            long total = 0L;
            if (!(field instanceof Field.Shared)) {
                Emission emission = emission(field);
                total = emission.own();
                for (Field child : emission.children()) {
                    total += inline.get(child);
                }
            }
            inline.put(field, total);
        }
        return inline.get(root);
    }

    private int shape(Field root) {
        for (Field field : postOrder(root, shapes, false)) {
            Field folded = field instanceof Field.Binary binary ? bounds.fold(binary) : field;
            if (folded != field) {
                shapes.put(field, shapes.get(folded));
                continue;
            }
            List<Object> key = shapeKey(field);
            Integer id = shapeIds.get(key);
            if (id == null) {
                id = shapeIds.size();
                shapeIds.put(key, id);
            }
            shapes.put(field, id);
        }
        return shapes.get(root);
    }

    private List<Field> postOrder(Field root, Map<Field, ?> done, boolean stopAtShared) {
        List<Field> order = new ArrayList<>();
        if (done.containsKey(root)) {
            return order;
        }
        Set<Field> visited = Collections.newSetFromMap(new IdentityHashMap<>());
        Deque<Field> pending = new ArrayDeque<>();
        Deque<Boolean> ready = new ArrayDeque<>();
        pending.push(root);
        ready.push(Boolean.FALSE);
        while (!pending.isEmpty()) {
            Field field = pending.pop();
            if (ready.pop()) {
                order.add(field);
                continue;
            }
            if (!visited.add(field)) {
                continue;
            }
            pending.push(field);
            ready.push(Boolean.TRUE);
            if (stopAtShared && field instanceof Field.Shared) {
                continue;
            }
            for (Field child : emission(field).children()) {
                if (!visited.contains(child) && !done.containsKey(child)) {
                    pending.push(child);
                    ready.push(Boolean.FALSE);
                }
            }
        }
        return order;
    }

    private Emission emission(Field field) {
        Emission known = emissions.get(field);
        if (known != null) {
            return known;
        }
        Emission computed = emit(field);
        emissions.put(field, computed);
        return computed;
    }

    private Emission emit(Field field) {
        return switch (field) {
            case Field.Const ignored -> Emission.LEAF_CONSTANT;
            case Field.Coord ignored -> Emission.LEAF;
            case Field.Ref ignored -> Emission.LEAF;
            case Field.Noise noise -> node(1, present(noise.shiftX(), noise.shiftY(), noise.shiftZ()));
            case Field.Binary binary -> binary(binary);
            case Field.Unary unary -> node(1, List.of(FieldDomains.unaryInput(unary)));
            case Field.Clamp clamp -> node(1, List.of(clamp.input()));
            case Field.Lerp lerp -> node(1, List.of(lerp.alpha(), lerp.first(), lerp.second()));
            case Field.Round round -> node(1, List.of(round.input(), round.multiple()));
            case Field.Curve curve -> curve(curve);
            case Field.Partition partition -> node(1, prepend(partition.input(), partition.leaves()));
            case Field.Mux mux -> mux(mux);
            case Field.Gate gate -> gate(gate);
            case Field.RangeIf rangeIf -> node(1, List.of(rangeIf.input(), rangeIf.inRange(), rangeIf.outOfRange()));
            case Field.Grid grid -> node(1, List.of(grid.expr()));
            case Field.Shared shared -> node(1, List.of(shared.expr()));
            case Field.Slice slice -> node(1, List.of(slice.expr()));
            case Field.TopSurface topSurface -> node(1, List.of(topSurface.density()));
        };
    }

    private Emission binary(Field.Binary binary) {
        Field folded = bounds.fold(binary);
        if (folded != binary) {
            return node(0, List.of(folded));
        }
        if (binary.op() == Field.BinaryOp.POW) {
            return node(1, List.of(FieldDomains.clampPowBase(binary.left(), binary.right()), binary.right()));
        }
        return node(1, List.of(binary.left(), binary.right()));
    }

    private static Emission curve(Field.Curve curve) {
        List<Field> coordinates = new ArrayList<>();
        int bodies = splineBodies(curve, coordinates);
        return node(1 + bodies, coordinates);
    }

    private static int splineBodies(Field.Curve curve, List<Field> coordinates) {
        coordinates.add(curve.input());
        int bodies = 1;
        for (Field.Knot knot : curve.knots()) {
            if (knot.value() instanceof Field.Curve nested) {
                bodies += splineBodies(nested, coordinates);
            }
        }
        return bodies;
    }

    private Emission mux(Field.Mux mux) {
        if (mux.cases().size() == 1) {
            return node(0, List.of(mux.cases().getFirst()));
        }
        List<Field> children = new ArrayList<>(mux.cases().size() + 1);
        children.add(mux.index());
        int own = 2;
        for (Field option : mux.cases()) {
            if (!(option instanceof Field.Const)) {
                children.add(option);
                own++;
            }
        }
        return node(own, children);
    }

    private Emission gate(Field.Gate gate) {
        if (gate.expr() instanceof Field.Const) {
            return node(2, List.of(gate.condition()));
        }
        return node(3, List.of(gate.condition(), gate.expr()));
    }

    private List<Object> shapeKey(Field field) {
        List<Object> key = new ArrayList<>(8);
        key.add(field.getClass());
        switch (field) {
            case Field.Const constant -> key.add(constant.value());
            case Field.Coord coord -> key.addAll(List.of(coord.axis(), coord.tiling(), coord.from(), coord.to(), coord.fromValue(), coord.toValue()));
            case Field.Ref ref -> key.add(ref.id());
            case Field.Noise noise -> key.addAll(List.of(noise.noiseId(), noise.xzScale(), noise.yScale(),
                    optionalShape(noise.shiftX()), optionalShape(noise.shiftY()), optionalShape(noise.shiftZ())));
            case Field.Binary binary -> key.add(binary.op());
            case Field.Unary unary -> key.add(unary.op());
            case Field.Clamp clamp -> key.addAll(List.of(clamp.min(), clamp.max()));
            case Field.Round round -> key.add(round.mode());
            case Field.Curve curve -> curveKey(curve, key);
            case Field.Partition partition -> key.addAll(partition.thresholds());
            case Field.RangeIf rangeIf -> key.addAll(List.of(rangeIf.min(), rangeIf.max()));
            case Field.Grid grid -> key.addAll(List.of(grid.cellXZ(), grid.cellY()));
            case Field.Slice slice -> key.addAll(List.of(slice.axis(), slice.value()));
            case Field.TopSurface topSurface -> key.addAll(List.of(topSurface.upper(), topSurface.lower(), topSurface.step()));
            case Field.Lerp ignored -> {
            }
            case Field.Mux ignored -> {
            }
            case Field.Gate ignored -> {
            }
            case Field.Shared ignored -> {
            }
        }
        if (!(field instanceof Field.Noise) && !(field instanceof Field.Curve)) {
            for (Field child : emission(field).children()) {
                key.add(shapes.get(child));
            }
        }
        if (field instanceof Field.Mux || field instanceof Field.Gate) {
            key.add(constantCases(field));
        }
        return key;
    }

    private Object optionalShape(Field field) {
        return field == null ? -1 : shapes.get(field);
    }

    private void curveKey(Field.Curve curve, List<Object> key) {
        key.add(shapes.get(curve.input()));
        for (Field.Knot knot : curve.knots()) {
            key.add(knot.location());
            key.add(knot.derivative());
            if (knot.value() instanceof Field.Curve nested) {
                List<Object> nestedKey = new ArrayList<>();
                curveKey(nested, nestedKey);
                key.add(nestedKey);
            } else {
                key.add(((Field.Const) knot.value()).value());
            }
        }
    }

    private static List<Object> constantCases(Field field) {
        List<Field> options = field instanceof Field.Mux mux ? mux.cases() : List.of(((Field.Gate) field).expr());
        List<Object> constants = new ArrayList<>(options.size());
        for (Field option : options) {
            constants.add(option instanceof Field.Const constant ? constant.value() : null);
        }
        return constants;
    }

    private static Emission node(int own, List<Field> children) {
        return new Emission(own, children);
    }

    private static List<Field> present(Field... fields) {
        List<Field> present = new ArrayList<>(fields.length);
        for (Field field : fields) {
            if (field != null) {
                present.add(field);
            }
        }
        return present;
    }

    private static List<Field> prepend(Field first, List<Field> rest) {
        List<Field> fields = new ArrayList<>(rest.size() + 1);
        fields.add(first);
        fields.addAll(rest);
        return fields;
    }

    private record Emission(int own, List<Field> children) {
        private static final Emission LEAF = new Emission(1, List.of());
        private static final Emission LEAF_CONSTANT = new Emission(0, List.of());
    }
}
