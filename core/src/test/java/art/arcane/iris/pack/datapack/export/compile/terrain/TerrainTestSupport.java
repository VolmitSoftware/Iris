package art.arcane.iris.pack.datapack.export.compile.terrain;

import art.arcane.iris.pack.datapack.DataVersion;
import art.arcane.iris.pack.datapack.export.compile.biome.BiomeCodes;
import art.arcane.iris.pack.datapack.export.compile.noise.NoiseRegistry;
import art.arcane.iris.pack.datapack.export.compile.noise.StyleCompiler;
import art.arcane.iris.pack.datapack.export.ir.ClimateLayout;
import art.arcane.iris.pack.datapack.export.ir.CostClass;
import art.arcane.iris.pack.datapack.export.ir.Field;
import art.arcane.iris.pack.datapack.export.ir.FieldEvaluator;
import art.arcane.iris.pack.datapack.export.ir.NoiseDef;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Deque;
import java.util.IdentityHashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

final class TerrainTestSupport {
    static final String NAMESPACE = "iris_export";
    static final long SEED = 1337L;
    static final String SELECTOR_ID = "test/selector";
    static final NoiseDef SELECTOR = new NoiseDef(SELECTOR_ID, -8, List.of(1.0, 1.0));

    private TerrainTestSupport() {
    }

    static StyleCompiler noiseStyles() {
        return (style, seedSalt, registry, capabilities) -> {
            String id = registry.register(new NoiseDef("test/style_" + Long.toHexString(seedSalt), -5, List.of(1.0)));
            Field noise = new Field.Noise(id, 1.0 / style.getZoom(), 0.0, null, null, null);
            return new Field.Binary(Field.BinaryOp.ADD, new Field.Const(0.5), new Field.Binary(Field.BinaryOp.MUL, new Field.Const(0.5), noise));
        };
    }

    static StyleCompiler constantStyles(double value) {
        return (style, seedSalt, registry, capabilities) -> new Field.Const(value);
    }

    static BiomeCodes twoBiomeCodes(NoiseRegistry registry, double threshold) {
        String selector = registry.register(SELECTOR);
        Field code = new Field.Partition(new Field.Noise(selector, 1.0, 0.0, null, null, null), List.of(threshold),
                List.of(new Field.Const(0.1), new Field.Const(0.3)));
        ClimateLayout layout = new ClimateLayout(List.of(
                new ClimateLayout.ClimateEntry(NAMESPACE + ":a", ClimateLayout.ClimateAxis.TEMPERATURE, 0.05, 0.15),
                new ClimateLayout.ClimateEntry(NAMESPACE + ":b", ClimateLayout.ClimateAxis.TEMPERATURE, 0.25, 0.35)));
        return new BiomeCodes(code, new Field.Const(0.0), new Field.Const(0.0), layout, Map.of(NAMESPACE + ":a", "a", NAMESPACE + ":b", "b"));
    }

    static BiomeCodes singleBiomeCodes(String loadKey) {
        ClimateLayout layout = new ClimateLayout(List.of(
                new ClimateLayout.ClimateEntry(NAMESPACE + ":" + loadKey, ClimateLayout.ClimateAxis.TEMPERATURE, -1.0, 1.0)));
        return new BiomeCodes(new Field.Const(0.0), new Field.Const(0.0), new Field.Const(0.0), layout, Map.of(NAMESPACE + ":" + loadKey, loadKey));
    }

    static double sample(Field field, NoiseRegistry registry, DataVersion target, int x, int y, int z) {
        return FieldEvaluator.sample(field, noises(registry), SEED, target, x, y, z);
    }

    static Map<String, NoiseDef> noises(NoiseRegistry registry) {
        Map<String, NoiseDef> noises = new LinkedHashMap<>();
        for (NoiseDef definition : registry.definitions()) {
            noises.put(registry.namespace() + ":" + definition.id(), definition);
        }
        return noises;
    }

    static boolean contains(Field root, Field target) {
        Set<Field> visited = Collections.newSetFromMap(new IdentityHashMap<>());
        Deque<Field> pending = new ArrayDeque<>();
        pending.push(root);
        while (!pending.isEmpty()) {
            Field field = pending.pop();
            if (field.equals(target)) {
                return true;
            }
            if (visited.add(field)) {
                for (Field child : FieldMacros.children(field)) {
                    pending.push(child);
                }
            }
        }
        return false;
    }

    static List<Field> nodes(Field root) {
        List<Field> nodes = new ArrayList<>();
        Set<Field> visited = Collections.newSetFromMap(new IdentityHashMap<>());
        Deque<Field> pending = new ArrayDeque<>();
        pending.push(root);
        while (!pending.isEmpty()) {
            Field field = pending.pop();
            if (visited.add(field)) {
                nodes.add(field);
                for (Field child : FieldMacros.children(field)) {
                    pending.push(child);
                }
            }
        }
        return nodes;
    }

    static List<String> unguardedExpensiveBranches(Field root) {
        List<String> violations = new ArrayList<>();
        walkGuards(root, "root", violations, Collections.newSetFromMap(new IdentityHashMap<>()));
        return violations;
    }

    private static void walkGuards(Field field, String path, List<String> violations, Set<Field> visited) {
        if (!visited.add(field)) {
            return;
        }
        switch (field) {
            case Field.Binary binary -> {
                if (binary.op() == Field.BinaryOp.MUL) {
                    checkProduct(binary, path, violations);
                }
                walkGuards(binary.left(), path + "/left", violations, visited);
                walkGuards(binary.right(), path + "/right", violations, visited);
            }
            case Field.Lerp lerp -> {
                checkBranch(lerp.first(), path + "/lerp.first", violations);
                checkBranch(lerp.second(), path + "/lerp.second", violations);
                walkGuards(lerp.alpha(), path + "/alpha", violations, visited);
                walkGuards(lerp.first(), path + "/first", violations, visited);
                walkGuards(lerp.second(), path + "/second", violations, visited);
            }
            case Field.RangeIf rangeIf -> {
                checkBranch(rangeIf.inRange(), path + "/range.in", violations);
                checkBranch(rangeIf.outOfRange(), path + "/range.out", violations);
                walkGuards(rangeIf.input(), path + "/input", violations, visited);
                walkGuards(rangeIf.inRange(), path + "/in", violations, visited);
                walkGuards(rangeIf.outOfRange(), path + "/out", violations, visited);
            }
            case Field.Partition partition -> {
                for (Field leaf : partition.leaves()) {
                    checkBranch(leaf, path + "/partition.leaf", violations);
                }
                walkGuards(partition.input(), path + "/input", violations, visited);
            }
            case Field.Unary unary -> walkGuards(unary.input(), path + "/" + unary.op(), violations, visited);
            case Field.Clamp clamp -> walkGuards(clamp.input(), path + "/clamp", violations, visited);
            case Field.Round round -> {
                walkGuards(round.input(), path + "/round", violations, visited);
                walkGuards(round.multiple(), path + "/multiple", violations, visited);
            }
            case Field.Curve curve -> {
                walkGuards(curve.input(), path + "/curve", violations, visited);
                for (Field.Knot knot : curve.knots()) {
                    walkGuards(knot.value(), path + "/knot", violations, visited);
                }
            }
            case Field.Mux mux -> {
                walkGuards(mux.index(), path + "/index", violations, visited);
                for (Field selected : mux.cases()) {
                    walkGuards(selected, path + "/case", violations, visited);
                }
            }
            case Field.Gate gate -> {
                walkGuards(gate.condition(), path + "/condition", violations, visited);
                walkGuards(gate.expr(), path + "/gated", violations, visited);
            }
            case Field.Grid grid -> walkGuards(grid.expr(), path + "/grid", violations, visited);
            case Field.Shared shared -> walkGuards(shared.expr(), path + "/" + shared.name(), violations, visited);
            case Field.Slice slice -> walkGuards(slice.expr(), path + "/slice", violations, visited);
            case Field.TopSurface topSurface -> walkGuards(topSurface.density(), path + "/top", violations, visited);
            case Field.Noise noise -> {
                for (Field shift : new Field[]{noise.shiftX(), noise.shiftY(), noise.shiftZ()}) {
                    if (shift != null) {
                        walkGuards(shift, path + "/shift", violations, visited);
                    }
                }
            }
            case Field.Const ignored -> {
            }
            case Field.Coord ignored -> {
            }
            case Field.Ref ignored -> {
            }
        }
    }

    private static void checkProduct(Field.Binary product, String path, List<String> violations) {
        boolean leftShields = product.left() instanceof Field.Const || FieldMacros.isGuard(product.left());
        boolean rightShields = product.right() instanceof Field.Const || FieldMacros.isGuard(product.right());
        if (!rightShields) {
            checkBranch(product.left(), path + "/mul.left", violations);
        }
        if (!leftShields) {
            checkBranch(product.right(), path + "/mul.right", violations);
        }
    }

    private static void checkBranch(Field branch, String path, List<String> violations) {
        if (branch.cost() == CostClass.EXPENSIVE && !FieldMacros.isGuard(branch)) {
            violations.add(path + " " + branch.getClass().getSimpleName());
        }
    }
}
