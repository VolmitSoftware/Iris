package art.arcane.iris.pack.datapack.export.compile.noise;

import art.arcane.iris.pack.datapack.export.ir.Axis;
import art.arcane.iris.pack.datapack.export.ir.Field;
import art.arcane.iris.pack.datapack.export.ir.NoiseDef;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Deque;
import java.util.HashSet;
import java.util.IdentityHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeMap;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;
import static org.junit.Assert.fail;

final class FieldWalk {
    private static final double LIMIT = 1.0e6D;

    private FieldWalk() {
    }

    static List<Field> nodes(Field root) {
        Set<Field> seen = Collections.newSetFromMap(new IdentityHashMap<>());
        List<Field> ordered = new ArrayList<>();
        Deque<Field> pending = new ArrayDeque<>();
        pending.push(root);
        while (!pending.isEmpty()) {
            Field node = pending.pop();
            if (!seen.add(node)) {
                continue;
            }
            ordered.add(node);
            for (Field child : children(node)) {
                pending.push(child);
            }
        }
        return ordered;
    }

    static List<Field> children(Field field) {
        List<Field> children = new ArrayList<>();
        switch (field) {
            case Field.Const ignored -> {
            }
            case Field.Coord ignored -> {
            }
            case Field.Ref ignored -> {
            }
            case Field.Noise noise -> {
                addIfPresent(children, noise.shiftX());
                addIfPresent(children, noise.shiftY());
                addIfPresent(children, noise.shiftZ());
            }
            case Field.Binary binary -> {
                children.add(binary.left());
                children.add(binary.right());
            }
            case Field.Unary unary -> children.add(unary.input());
            case Field.Clamp clamp -> children.add(clamp.input());
            case Field.Lerp lerp -> {
                children.add(lerp.alpha());
                children.add(lerp.first());
                children.add(lerp.second());
            }
            case Field.Round round -> {
                children.add(round.input());
                children.add(round.multiple());
            }
            case Field.Curve curve -> {
                children.add(curve.input());
                for (Field.Knot knot : curve.knots()) {
                    children.add(knot.value());
                }
            }
            case Field.Partition partition -> {
                children.add(partition.input());
                children.addAll(partition.leaves());
            }
            case Field.Mux mux -> {
                children.add(mux.index());
                children.addAll(mux.cases());
            }
            case Field.Gate gate -> {
                children.add(gate.condition());
                children.add(gate.expr());
            }
            case Field.RangeIf rangeIf -> {
                children.add(rangeIf.input());
                children.add(rangeIf.inRange());
                children.add(rangeIf.outOfRange());
            }
            case Field.Grid grid -> children.add(grid.expr());
            case Field.Shared shared -> children.add(shared.expr());
            case Field.Slice slice -> children.add(slice.expr());
            case Field.TopSurface topSurface -> children.add(topSurface.density());
        }
        return children;
    }

    static <T extends Field> List<T> nodesOf(Field root, Class<T> type) {
        List<T> matches = new ArrayList<>();
        for (Field node : nodes(root)) {
            if (type.isInstance(node)) {
                matches.add(type.cast(node));
            }
        }
        return matches;
    }

    static Map<String, Field> sharedBodies(List<Field> roots) {
        Map<String, Field> bodies = new TreeMap<>();
        for (Field root : roots) {
            for (Field.Shared shared : nodesOf(root, Field.Shared.class)) {
                Field existing = bodies.putIfAbsent(shared.name(), shared.expr());
                if (existing != null && !existing.equals(shared.expr())) {
                    fail("Shared field '" + shared.name() + "' has two different bodies");
                }
            }
        }
        return bodies;
    }

    static void assertConstantsWithinLimit(Field root) {
        for (Field node : nodes(root)) {
            switch (node) {
                case Field.Const constant -> assertWithin(constant.value(), "constant");
                case Field.Coord coord -> {
                    assertWithin(coord.fromValue(), "gradient from value");
                    assertWithin(coord.toValue(), "gradient to value");
                }
                case Field.Clamp clamp -> {
                    assertWithin(clamp.min(), "clamp minimum");
                    assertWithin(clamp.max(), "clamp maximum");
                }
                case Field.RangeIf rangeIf -> {
                    assertWithin(rangeIf.min(), "range minimum");
                    assertWithin(rangeIf.max(), "range maximum");
                }
                case Field.Curve curve -> {
                    for (Field.Knot knot : curve.knots()) {
                        assertWithin(knot.location(), "knot location");
                        assertWithin(knot.derivative(), "knot derivative");
                    }
                }
                case Field.Partition partition -> {
                    for (double threshold : partition.thresholds()) {
                        assertWithin(threshold, "partition threshold");
                    }
                }
                default -> {
                }
            }
        }
    }

    static void assertNoisesRegistered(Field root, NoiseRegistry registry) {
        Set<String> registered = new HashSet<>();
        for (NoiseDef definition : registry.definitions()) {
            registered.add(registry.namespace() + ":" + definition.id());
        }
        for (Field.Noise noise : nodesOf(root, Field.Noise.class)) {
            assertTrue("Noise '" + noise.noiseId() + "' is not registered", registered.contains(noise.noiseId()));
        }
    }

    static void assertTwoDimensional(Field root) {
        assertTrue("Field depends on Y: " + root.axes(), !root.axes().contains(Axis.Y));
    }

    static Map<String, NoiseDef> noiseDefinitions(NoiseRegistry registry) {
        Map<String, NoiseDef> definitions = new TreeMap<>();
        for (NoiseDef definition : registry.definitions()) {
            definitions.put(registry.namespace() + ":" + definition.id(), definition);
        }
        assertEquals(registry.definitions().size(), definitions.size());
        return definitions;
    }

    private static void addIfPresent(List<Field> children, Field child) {
        if (child != null) {
            children.add(child);
        }
    }

    private static void assertWithin(double value, String role) {
        assertTrue(role + " " + value + " exceeds +-1e6", Double.isFinite(value) && Math.abs(value) <= LIMIT);
    }
}
