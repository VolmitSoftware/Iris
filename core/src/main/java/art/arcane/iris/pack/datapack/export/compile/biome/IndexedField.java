package art.arcane.iris.pack.datapack.export.compile.biome;

import art.arcane.iris.pack.datapack.export.ir.Field;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.SortedSet;
import java.util.TreeSet;
import java.util.function.IntToDoubleFunction;
import java.util.function.IntUnaryOperator;

record IndexedField(Field field, List<Integer> values) {
    private static final Field UNUSED_CASE = new Field.Const(0.0);

    IndexedField {
        Objects.requireNonNull(field);
        values = List.copyOf(new TreeSet<>(values));
        if (values.isEmpty()) {
            throw new IllegalArgumentException("An indexed field needs at least one value");
        }
    }

    static IndexedField constant(int value) {
        return new IndexedField(new Field.Const(value), List.of(value));
    }

    static IndexedField partition(Field input, List<Double> thresholds, List<Integer> leaves) {
        if (leaves.size() != thresholds.size() + 1) {
            throw new IllegalArgumentException("Partition needs " + (thresholds.size() + 1) + " leaves, got " + leaves.size());
        }
        List<Double> kept = new ArrayList<>(thresholds.size());
        List<Integer> keptLeaves = new ArrayList<>(leaves.size());
        keptLeaves.add(leaves.getFirst());
        for (int index = 0; index < thresholds.size(); index++) {
            int next = leaves.get(index + 1);
            if (next == keptLeaves.getLast()) {
                continue;
            }
            kept.add(thresholds.get(index));
            keptLeaves.add(next);
        }
        if (keptLeaves.size() == 1) {
            return constant(keptLeaves.getFirst());
        }
        List<Field> constants = new ArrayList<>(keptLeaves.size());
        for (int leaf : keptLeaves) {
            constants.add(new Field.Const(leaf));
        }
        return new IndexedField(new Field.Partition(input, kept, constants), keptLeaves);
    }

    static IndexedField select(IndexedField index, List<IndexedField> cases) {
        if (index.isConstant()) {
            return cases.get(index.values().getFirst());
        }
        IndexedField first = cases.get(index.values().getFirst());
        boolean uniform = true;
        SortedSet<Integer> reachable = new TreeSet<>();
        for (int value : index.values()) {
            IndexedField selected = cases.get(value);
            uniform &= selected.equals(first);
            reachable.addAll(selected.values());
        }
        if (uniform) {
            return first;
        }
        int count = index.values().getLast() + 1;
        List<Field> muxCases = new ArrayList<>(count);
        for (int value = 0; value < count; value++) {
            muxCases.add(index.values().contains(value) ? cases.get(value).field() : UNUSED_CASE);
        }
        return new IndexedField(new Field.Mux(index.field(), muxCases), List.copyOf(reachable));
    }

    static IndexedField advance(IndexedField current, Map<Integer, IndexedField> moved) {
        int last = current.values().getLast();
        List<IndexedField> cases = new ArrayList<>(last + 1);
        for (int value = 0; value <= last; value++) {
            IndexedField next = moved.get(value);
            cases.add(next == null ? constant(value) : next);
        }
        return select(current, cases);
    }

    static IndexedField sum(List<IndexedField> terms) {
        SortedSet<Integer> values = new TreeSet<>(List.of(0));
        int offset = 0;
        Field field = null;
        for (IndexedField term : terms) {
            SortedSet<Integer> combined = new TreeSet<>();
            for (int value : values) {
                for (int addend : term.values()) {
                    combined.add(value + addend);
                }
            }
            values = combined;
            if (term.isConstant()) {
                offset += term.values().getFirst();
            } else {
                field = field == null ? term.field() : new Field.Binary(Field.BinaryOp.ADD, field, term.field());
            }
        }
        if (field == null) {
            return constant(offset);
        }
        Field total = offset == 0 ? field : new Field.Binary(Field.BinaryOp.ADD, field, new Field.Const(offset));
        return new IndexedField(total, List.copyOf(values));
    }

    static List<Double> midpoints(List<Integer> sorted) {
        List<Double> thresholds = new ArrayList<>(sorted.size() - 1);
        for (int index = 0; index + 1 < sorted.size(); index++) {
            thresholds.add((sorted.get(index) + sorted.get(index + 1)) / 2.0);
        }
        return thresholds;
    }

    boolean isConstant() {
        return values.size() == 1;
    }

    IndexedField remap(IntUnaryOperator mapping) {
        List<Integer> mapped = new ArrayList<>(values.size());
        for (int value : values) {
            mapped.add(mapping.applyAsInt(value));
        }
        if (isConstant()) {
            return constant(mapped.getFirst());
        }
        return partition(field, midpoints(values), mapped);
    }

    Field lookup(IntToDoubleFunction table) {
        if (isConstant()) {
            return new Field.Const(table.applyAsDouble(values.getFirst()));
        }
        List<Double> thresholds = midpoints(values);
        List<Double> keptThresholds = new ArrayList<>(thresholds.size());
        List<Field> leaves = new ArrayList<>(values.size());
        double previous = table.applyAsDouble(values.getFirst());
        leaves.add(new Field.Const(previous));
        for (int index = 1; index < values.size(); index++) {
            double next = table.applyAsDouble(values.get(index));
            if (next == previous) {
                continue;
            }
            keptThresholds.add(thresholds.get(index - 1));
            leaves.add(new Field.Const(next));
            previous = next;
        }
        if (leaves.size() == 1) {
            return leaves.getFirst();
        }
        return new Field.Partition(field, keptThresholds, leaves);
    }

    IndexedField shared(String name) {
        return isConstant() ? this : new IndexedField(new Field.Shared(name, field), values);
    }
}
